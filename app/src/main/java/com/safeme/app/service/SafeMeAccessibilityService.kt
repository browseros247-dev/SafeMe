package com.safeme.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.safeme.app.BlockGateActivity
import com.safeme.app.R
import com.safeme.app.data.BlockingPrefsState
import com.safeme.app.data.BundledKeywords
import com.safeme.app.data.TitleBlockRule
import com.safeme.app.data.TitleMatchMode
import com.safeme.app.data.blockingPrefs
import com.safeme.app.data.normalizeDomain
import com.safeme.app.data.preventUninstallPrefs
import com.safeme.app.protect.A11yProtectionGuard
import com.safeme.app.protect.A11yProtectionUtils
import com.safeme.app.protect.DeviceAdminUtils
import com.safeme.app.protect.ProtectedSystemPages
import com.safeme.app.protect.ScheduleEngine
import com.safeme.app.protect.UninstallBlockers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Core blocking engine.
 *
 * Reacts to [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED], walks the active window's
 * node tree collecting visible text/titles/descriptions and URL-ish strings, then matches
 * them against the merged (bundled + user custom) keyword and website lists. Title rules
 * are matched against the window title only ([AccessibilityEvent.getText] on
 * window-state-changed events), never against body text, so a rule like "Apps" can't fire
 * just because the Settings home list shows an "Apps" entry. On a match it raises the
 * block gate over the offending app by launching [BlockGateActivity].
 *
 * Robustness guarantees:
 *  - Every event is processed inside a try/catch; a malformed event or node tree can never
 *    crash the service.
 *  - DataStore failures degrade to a safe default state (empty lists, blocking enabled).
 *  - Dedup + cooldown prevents re-launching the gate for the same window in a loop.
 *  - The engine skips SafeMe's own package, so the gate itself never re-triggers a block.
 */
class SafeMeAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var cachedState: BlockingPrefsState? = null

    @Volatile
    private var cachedPuEnabled: Boolean = false

    @Volatile
    private var lastBlockKey: String? = null

    @Volatile
    private var lastBlockAt: Long = 0L

    @Volatile
    private var titleScopeWarned: Boolean = false

    @Volatile
    private var lastA11yPageProbeMs: Long = 0L

    @Volatile
    private var lastA11yPageKickMs: Long = 0L

    /** Last app-name framework probe timestamp — throttles [nodeTreeContainsText] on watchdog ticks. */
    @Volatile
    private var lastAppNameProbeMs: Long = 0L

    @Volatile
    private var lastPuKickToastMs: Long = 0L

    /** Latest foreground package seen on a window-state-changed event — gates the PU watchdog cheaply. */
    @Volatile
    private var lastForegroundPkg: String? = null

    /** Latest foreground window class — gives the PU watchdog the current page identity for gating. */
    @Volatile
    private var lastForegroundCls: String? = null

    /** PU watchdog job — periodically re-probes the active window so our a11y detail page is evicted on every activation. */
    private var puWatchdogJob: Job? = null

    /** Separate cooldown tracker for PU blocks — [M1 fix] prevents PU cooldown from suppressing keyword blocks. */
    @Volatile
    private var lastPuBlockAt: Long = 0L

    /** Stored runnable for the eviction toast — [M3 fix] allows cancellation on service destroy. */
    private var pendingToastRunnable: Runnable? = null

    /** Separate cooldown tracker for schedule blocks — never suppresses keyword/PU blocks. */
    @Volatile
    private var lastScheduleBlockKey: String? = null

    @Volatile
    private var lastScheduleBlockAt: Long = 0L

    @Volatile
    private var lastScheduleRecheckMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceScope.launch {
            try {
                blockingPrefs().collect { cachedState = it }
            } catch (t: Throwable) {
                cachedState = BlockingPrefsState()
            }
        }
        serviceScope.launch {
            try {
                preventUninstallPrefs().collect { cachedPuEnabled = it.preventUninstallEnabled }
            } catch (t: Throwable) {
                cachedPuEnabled = false
            }
        }
        // PU watchdog: enforces eviction of our own a11y detail page on every
        // activation (not just window-state-changed events).
        startPuWatchdog()
        // Re-arm the accessibility-protection guard + self-heal (background;
        // no-op when the protection toggle is off).
        A11yProtectionUtils.selfHealAllAsync(this)
        A11yProtectionGuard.getInstance().ensureWatching(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            // Never crash the service on a malformed event.
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val ownPackage = applicationContext.packageName ?: return
        // True when OUR full-screen window (the block gate) was foreground
        // before this event — i.e. the gate was just dismissed onto [pkg].
        val prevOwnWindow = lastForegroundPkg == ownPackage
        lastForegroundPkg = pkg
        lastForegroundCls = runCatching { event.className?.toString() }.getOrDefault("")
        if (pkg == ownPackage) return

        // Gate dismissed onto a new window: kick the PU watchdog immediately
        // so a protected page underneath is re-probed now instead of on the
        // next cadence tick. While the gate was up the watchdog skipped
        // probing (see [puWatchdogTick]), so this is the resume point.
        if (prevOwnWindow) {
            // A gate was dismissed: re-arm the PU gate cooldown so a protected
            // page underneath is re-gated at once (not after the 4 s window),
            // then kick the watchdog now.
            lastPuBlockAt = 0L
            try {
                // If the dismissed gate was over OUR a11y detail page, it has
                // been shown once — bounce to HOME now (never let the user
                // reach the page, and never leave a cover looping over an
                // a11y-management screen). Otherwise kick the watchdog to
                // re-probe the revealed window.
                val root = runCatching { rootInActiveWindow }.getOrNull()
                var a11yDetailActive = false
                if (root != null) {
                    try {
                        a11yDetailActive = isOurA11yDetailPageInTree(root)
                    } finally {
                        recycle(root)
                    }
                }
                if (a11yDetailActive) {
                    evictFromOurA11yServicePage()
                } else {
                    puWatchdogTick()
                }
            } catch (_: Throwable) {
            }
        }

        // Prevent Uninstall guards are independent of the master blocking
        // switch: they gate only on the PU DataStore flag.
        if (cachedPuEnabled) {
            if (handlePreventUninstall(event, pkg)) return
        }

        // Schedule-Based App Blocking: launch/UI blocking is independent of the
        // keyword engine and the master blocking switch.
        if (isScheduleBlocked(pkg)) {
            launchScheduleGate(pkg)
            return
        }

        val state = cachedState ?: return
        if (!state.blockingEnabled) return

        val texts = collectTexts(event)
        if (texts.isEmpty()) return

        val match = findMatch(texts, state)
            ?: findTitleMatchIfSettings(pkg, event, state)
            ?: return
        val key = "$pkg|${match.value}"
        val now = SystemClock.elapsedRealtime()
        if (lastBlockKey == key && now - lastBlockAt < COOLDOWN_MS) return

        lastBlockKey = key
        lastBlockAt = now
        launchGate(pkg, match)
    }

    /**
     * Prevent Uninstall (anti-tamper) guards, scoped to SafeMe only.
     *
     * While the feature is ON it protects exactly three surfaces, all of them
     * identified by SafeMe's own package/app-name/service-description on a
     * settings-family window:
     *
     *  1. Our accessibility-service DETAIL page — the PU gate (Block screen)
     *     is raised FIRST on every activation; dismissing it bounces to HOME
     *     (see [handleEvent]), so the user never reaches the page and the gate
     *     is never left covering an a11y-management screen. A persistent cover
     *     is the a11y kill vector (Android auto-disables a service whose
     *     window obscures those screens), so the gate is one-shot, never a
     *     loop.
     *  2. All OTHER a11y-management screens (lists, other apps' detail pages)
     *     — never blocked, only self-healed.
     *  3. SafeMe's own App Info / Device Admin deactivation / force-stop /
     *     uninstall-confirmation pages — blocked with the PU gate. The stock
     *     uninstall confirmation (packageinstaller UninstallerActivity) is
     *     included in the guard surface.
     *
     * Every branch is fail-open: on any error the event is NOT blocked
     * (a false positive on a legitimate Settings page is worse than a false
     * negative).
     *
     * @return true when the event was consumed by a PU guard.
     */
    private fun handlePreventUninstall(event: AccessibilityEvent, pkg: String): Boolean {
        return try {
            if (pkg == "com.android.systemui") return false
            if (!ProtectedSystemPages.isPuSurface(pkg)) return false

            val cls = runCatching { event.className?.toString() }.getOrDefault("").orEmpty()
            // [H2 fix] Removed SubSettings catch-all — only actual a11y management
            // markers should enter the a11y branch. SubSettings is the AOSP container
            // for every Settings sub-page; catching all of them adds unnecessary
            // throttled node-tree probes on non-a11y pages.
            val a11yContext =
                ProtectedSystemPages.isAccessibilityManagementScreen(pkg, cls)

            // 1. Our own a11y detail page → gate first (Block screen), then
            //    HOME on dismissal (see [handleEvent]) — never a silent
            //    redirect, never a looping cover over an a11y screen.
            if (a11yContext) {
                if (isOurA11yServiceDetailPage(event)) {
                    launchPuGate(pkg)
                    return true
                }
                // Any other a11y-management screen is left untouched.
                return false
            }

            // 2. App Info / Device Admin / force-stop / uninstall pages for OUR app.
            if (!isOurUninstallTargetPage(event, pkg, cls)) {
                Log.d(TAG, "PU: not a target (pkg=$pkg cls=$cls)")
                return false
            }
            Log.d(TAG, "PU: blocking page (pkg=$pkg cls=$cls)")
            // [M1 fix] PU blocks use a separate cooldown key (set inside
            // [launchPuGate]) so they don't suppress keyword blocking during
            // the cooldown window.
            launchPuGate(pkg)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "handlePreventUninstall failed — fail open", t)
            false
        }
    }

    /**
     * True when the visible settings window is SafeMe's OWN accessibility
     * service DETAIL page. Layer 1 is a cheap scan of the event text; if that
     * misses (Android 16 does not expose the description to the event) the
     * active-window tree is probed via the shared [isOurA11yDetailPageInTree].
     */
    private fun isOurA11yServiceDetailPage(event: AccessibilityEvent): Boolean {
        return try {
            val description = getString(R.string.accessibility_service_description)
            val summary = getString(R.string.accessibility_service_summary)
            val marker = ProtectedSystemPages.detailOnlyFingerprint(
                ProtectedSystemPages.normalize(description),
                ProtectedSystemPages.normalize(summary)
            )
            if (marker.length < 8) return false // fail open: can't distinguish detail from list
            if (eventTextNormalized(event).contains(marker)) return true

            val root = rootInActiveWindow ?: return false
            try {
                isOurA11yDetailPageInTree(root)
            } finally {
                recycle(root)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "isOurA11yServiceDetailPage failed — assuming not our page", t)
            false
        }
    }

    /**
     * Tree-only detection of SafeMe's OWN accessibility-service DETAIL page,
     * shared by the event path (as a fallback when the event text isn't
     * exposed) and the PU watchdog (which has no event at all). [root] is the
     * active-window root and is NOT recycled here — the caller owns it.
     *
     * Layers (cheap first):
     *  1. Window title (API 33+) — the detail page's window title is our
     *     service label, while the a11y LIST window's title is "Accessibility".
     *  2. Detail-only description fingerprint in the walked node texts
     *     (pre-Android-16: the description IS exposed to the tree).
     *  3. The detail-only "shortcut" row ("<label> shortcut") — Android 16
     *     hosts the detail page in a generic SubSettings container whose
     *     description is not exposed; the shortcut row is the only
     *     label-carrier the list row lacks.
     *  4. Bounded framework substring probe (throttled — it is the only
     *     framework-side search and a stream of events must cost nothing).
     */
    private fun isOurA11yDetailPageInTree(root: AccessibilityNodeInfo): Boolean {
        // Layer 1: window title (cheap, no tree walk).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val title = runCatching { root.window?.title?.toString() }.getOrDefault("").orEmpty()
            val label = getString(R.string.accessibility_service_label)
            if (title.isNotBlank() && label.isNotBlank() &&
                ProtectedSystemPages.normalize(title)
                    .contains(ProtectedSystemPages.normalize(label))
            ) {
                return true
            }
        }

        val description = getString(R.string.accessibility_service_description)
        val summary = getString(R.string.accessibility_service_summary)
        val marker = ProtectedSystemPages.detailOnlyFingerprint(
            ProtectedSystemPages.normalize(description),
            ProtectedSystemPages.normalize(summary)
        )
        val label = getString(R.string.accessibility_service_label)
        val labelLower = label.lowercase(Locale.ROOT)

        // Layers 2 + 3 share one bounded walk of the node tree.
        if (marker.length >= 8 || labelLower.isNotBlank()) {
            val texts = collectTextsFrom(root)
            if (marker.length >= 8 &&
                texts.any { ProtectedSystemPages.normalize(it).contains(marker) }
            ) {
                return true
            }
            if (labelLower.isNotBlank() && texts.any {
                    val t = it.lowercase(Locale.ROOT)
                    t.contains(labelLower) && t.contains("shortcut")
                }
            ) {
                return true
            }
        }

        // Layer 4: findAccessibilityNodeInfosByText matches the RAW node text
        // (spaces intact), so the normalized marker can never match. Probe with
        // the spaced detail-only suffix — it appears on the detail page (full
        // description) but never on the list row (summary only). Throttled.
        val now = SystemClock.elapsedRealtime()
        if (now - lastA11yPageProbeMs < A11Y_PAGE_PROBE_THROTTLE_MS) return false
        lastA11yPageProbeMs = now
        val spacedProbe = if (description.startsWith(summary)) {
            description.removePrefix(summary).trim()
        } else {
            description.drop(40).trim()
        }
        if (spacedProbe.length < 8) return false
        val needle = spacedProbe.take(30)
        val nodes = try {
            root.findAccessibilityNodeInfosByText(needle)
        } catch (t: Throwable) {
            null
        }
        return try {
            nodes != null && nodes.isNotEmpty()
        } finally {
            if (nodes != null) nodes.forEach { recycle(it) }
        }
    }

    /**
     * Starts the PU watchdog loop. Every [PU_WATCHDOG_INTERVAL_MS] while the
     * service is alive it re-checks the active window. The window-state-changed
     * path only fires when the foreground itself changes windows, so it misses
     * activations that arrive without a fresh state change — a guarded page
     * already active when the PU flag flips on, a detection that failed because
     * the tree wasn't ready at the event, or a gate dismissed back onto the
     * page. The tick is a cheap no-op unless PU is on AND the foreground is a
     * PU-surface package (Settings-family or package-installer), and it is
     * skipped entirely while our own gate window is up — [handleEvent] kicks
     * it early the moment the gate dismisses.
     */
    private fun startPuWatchdog() {
        if (puWatchdogJob?.isActive == true) return
        puWatchdogJob = serviceScope.launch {
            while (true) {
                try {
                    puWatchdogTick()
                } catch (t: Throwable) {
                    // Never let a malformed probe kill the watchdog loop.
                }
                delay(PU_WATCHDOG_INTERVAL_MS)
            }
        }
    }

    /**
     * One watchdog tick: if PU is on and the foreground is a PU-surface
     * package (Settings-family or the package-installer), probe the active
     * window and enforce the full guard surface on a page that is ALREADY
     * active — our own a11y detail page raises the PU gate (Block screen
     * first; dismissal bounces to HOME), and app-info / device-admin /
     * force-stop / uninstall pages raise the gate too. This catches
     * activations the event path misses: a page already open when the
     * PU flag flips on, a detection that failed because the tree wasn't ready
     * at the window-state-changed event, or a gate dismissed back onto the
     * page. Kicks and gate launches are throttled (dedupe event floods) and
     * never suppress a genuine re-activation.
     *
     * The tick never probes while our own gate window is foreground (the
     * active tree is the gate's, not the protected page's) — [handleEvent]
     * kicks it immediately when the gate dismisses onto a new window.
     */
    private fun puWatchdogTick() {
        if (!cachedPuEnabled) return
        var pkg = lastForegroundPkg
        var cls = lastForegroundCls
        if (pkg == null) {
            // Service may have connected without a window-state-changed event
            // yet; initialize the foreground identity from the active window.
            val root = try {
                rootInActiveWindow
            } catch (t: Throwable) {
                null
            }
            if (root != null) {
                pkg = try {
                    root.packageName?.toString()
                } catch (t: Throwable) {
                    null
                }
                cls = try {
                    root.className?.toString()
                } catch (t: Throwable) {
                    null
                }
                recycle(root)
                if (pkg != null) lastForegroundPkg = pkg
                if (cls != null) lastForegroundCls = cls
            }
        }
        if (pkg == null) return
        val ownPackage = applicationContext.packageName ?: return
        // Our own full-screen window (the block gate) is foreground — the
        // active tree is the gate's, not the protected page's, so probing is
        // pointless. Skip; [handleEvent] kicks a tick the moment the gate
        // dismisses onto a new window.
        if (pkg == ownPackage) return
        if (!ProtectedSystemPages.isPuSurface(pkg)) return

        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            null
        } ?: return
        try {
            // 1. Our own a11y detail page → raise the PU gate (Block screen)
            //    first. Dismissing it bounces to HOME (see [handleEvent]), so
            //    the cover is one-shot — never left looping over an
            //    a11y-management screen (the a11y kill vector).
            if (isOurA11yDetailPageInTree(root)) {
                launchPuGate(pkg)
                return
            }
            // 2. App Info / Device Admin / force-stop / uninstall pages → gate.
            if (isOurUninstallTargetPageInTree(root, pkg, cls.orEmpty())) {
                launchPuGate(pkg)
            }
        } finally {
            recycle(root)
        }
    }

    /**
     * Bounded node-tree substring search for [needle] (the app name). Every
     * node obtained from the active window is recycled afterwards (required on
     * API 26–32, a no-op on 33+). [root] may be a root the caller already
     * holds (the PU watchdog tick) — it is then NOT recycled here and the
     * caller keeps ownership.
     *
     * Throttled: this is the only framework-side search and must not run on
     * every 2 s watchdog tick; the walked-texts checks in the shared core run
     * unthrottled and catch genuine targets, so the throttle never delays a
     * real activation.
     */
    private fun nodeTreeContainsText(needle: String, root: AccessibilityNodeInfo? = null): Boolean {
        if (needle.isBlank()) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastAppNameProbeMs < PU_APP_NAME_PROBE_THROTTLE_MS) return false
        lastAppNameProbeMs = now
        val owned = root == null
        val actualRoot = root ?: runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val nodes = try {
            actualRoot.findAccessibilityNodeInfosByText(needle)
        } catch (t: Throwable) {
            if (owned) recycle(actualRoot)
            return false
        }
        return try {
            nodes != null && nodes.isNotEmpty()
        } finally {
            if (nodes != null) nodes.forEach { recycle(it) }
            if (owned) recycle(actualRoot)
        }
    }

    /**
     * Nodes obtained from the active-window tree hold Binder references on
     * API 26–32 and must be recycled; leaking them across a long service
     * session can exhaust the framework's node pool. No-op on API 33+.
     */
    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                node.recycle()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Evict our own a11y detail page: HOME + delayed toast. Used as the
     * post-gate bounce-back — the PU gate is shown first (see
     * [handlePreventUninstall] / [puWatchdogTick]); dismissing it lands here
     * so the user never reaches the page and no cover lingers over an
     * a11y-management screen. Delayed toast lands over the launcher, never
     * over the a11y screen (a toast is still one of our windows — keep the
     * zero-obscure discipline). Throttled.
     */
    private fun evictFromOurA11yServicePage() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastA11yPageKickMs < A11Y_PAGE_KICK_THROTTLE_MS) return
        lastA11yPageKickMs = now
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (t: Throwable) {
            Log.w(TAG, "PU: HOME global action failed", t)
        }
        if (now - lastPuKickToastMs < PU_KICK_TOAST_THROTTLE_MS) return
        lastPuKickToastMs = now
        // [M3 fix] Store the Runnable so it can be cancelled on service destroy.
        try {
            pendingToastRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
            val runnable = Runnable {
                try {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.pu_evicted_toast),
                        Toast.LENGTH_LONG
                    ).show()
                } catch (t: Throwable) {
                    Log.w(TAG, "PU: eviction toast failed", t)
                }
            }
            pendingToastRunnable = runnable
            Handler(Looper.getMainLooper()).postDelayed(runnable, PU_KICK_TOAST_DELAY_MS)
        } catch (t: Throwable) {
            Log.w(TAG, "PU: failed to schedule eviction toast", t)
        }
    }

    /**
     * Port of the reference app-info / device-admin / force-stop detection,
     * scoped to SafeMe. Every branch requires our app name on the page (event
     * text OR node tree); a page that is not about SafeMe is never blocked.
     */
    private fun isOurUninstallTargetPage(
        event: AccessibilityEvent,
        pkg: String,
        cls: String,
    ): Boolean {
        return try {
            val lowerText = collectTexts(event).joinToString(" ").lowercase(Locale.ROOT)
            isOurUninstallTargetPage(pkg, cls, lowerText) {
                isOurA11yServiceDetailPage(event)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "isOurUninstallTargetPage failed — fail open", t)
            false
        }
    }

    /**
     * Event-free variant used by the PU watchdog: same detection as
     * [isOurUninstallTargetPage], but the page texts come from [root]'s node
     * tree, so a surface that is ALREADY active (no fresh window-state-changed
     * event) is still gated. The root is NOT recycled here — the caller owns it.
     */
    private fun isOurUninstallTargetPageInTree(
        root: AccessibilityNodeInfo,
        pkg: String,
        cls: String,
    ): Boolean {
        return try {
            val lowerText = collectTextsFrom(root).joinToString(" ").lowercase(Locale.ROOT)
            isOurUninstallTargetPage(pkg, cls, lowerText, probeRoot = root) {
                isOurA11yDetailPageInTree(root)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "isOurUninstallTargetPageInTree failed — fail open", t)
            false
        }
    }

    /**
     * Shared core of the app-info / device-admin / force-stop / uninstall
     * detection. [lowerText] is the collected page text (event + tree for the
     * event path, tree only for the watchdog) and [isOurA11yDetailPage] is the
     * caller-appropriate detail-page check that keeps our own a11y page out of
     * the gate. [probeRoot] is the active-window root when the caller already
     * holds one (the watchdog tick) — the framework app-name probe reuses it
     * instead of fetching a second root; the caller keeps ownership.
     *
     * The decision logic lives in [UninstallBlockers.isOurUninstallTargetPage]
     * (pure + unit-tested); this wrapper gathers the framework-dependent inputs
     * (resources, node-tree probe, Device Admin state) and owns the a11y-label
     * eviction side effect.
     */
    private fun isOurUninstallTargetPage(
        pkg: String,
        cls: String,
        lowerText: String,
        probeRoot: AccessibilityNodeInfo? = null,
        isOurA11yDetailPage: () -> Boolean,
    ): Boolean {
        return try {
            val lowerClass = cls.lowercase(Locale.ROOT)
            val appName = getString(R.string.app_name)
            if (appName.isBlank()) return false
            val appNameLower = appName.lowercase(Locale.ROOT)

            // Never gate a11y-management content for OUR service. Any page
            // whose texts contain our SERVICE LABEL (distinct from the app
            // name) is the service's list row or detail page — on Android 16
            // the detail page is hosted in a generic SubSettings container
            // whose class carries no a11y marker and whose description text is
            // NOT exposed to the tree, so the description probes fail there.
            // The label is the only reliable signal. The DETAIL page (with the
            // detail-only "shortcut" row) is gated here as a fallback for
            // devices the watchdog missed; the list row is never touched.
            // Checked before the app-name probe so a11y pages never trigger it.
            val serviceLabelLower = getString(R.string.accessibility_service_label)
                .lowercase(Locale.ROOT)
            if (serviceLabelLower.isNotBlank() && lowerText.contains(serviceLabelLower)) {
                if (lowerText.contains("shortcut")) {
                    launchPuGate(pkg)
                }
                return false
            }

            val appNameInText = lowerText.contains(appNameLower)
            val appNameInNodeTree = if (!appNameInText) {
                // Throttled framework probe (see [nodeTreeContainsText]) — the
                // walked-texts check above already ran, so this only rescues
                // trees the walk missed; it never runs on a11y pages.
                runCatching { nodeTreeContainsText(appName, probeRoot) }.getOrDefault(false)
            } else {
                false
            }
            val appIsOnPage = appNameInText || appNameInNodeTree

            val matched = UninstallBlockers.isOurUninstallTargetPage(
                pkg = pkg,
                lowerClass = lowerClass,
                lowerText = lowerText,
                appIsOnPage = appIsOnPage,
                isOurA11yDetailPage = isOurA11yDetailPage,
                adminActive = DeviceAdminUtils.isActive(applicationContext),
            )
            if (ProtectedSystemPages.isUninstallerPackage(pkg)) {
                Log.d(
                    TAG,
                    "PU: packageinstaller pkg=$pkg cls=$lowerClass appIsOnPage=$appIsOnPage matched=$matched"
                )
            }
            matched
        } catch (t: Throwable) {
            Log.w(TAG, "isOurUninstallTargetPage failed — fail open", t)
            false
        }
    }

    /**
     * Raises the PU gate over [pkg]'s current window. Shares the same cooldown
     * key as the event path, so a block raised by either path is deduped by
     * the other and the watchdog never double-fires right after a
     * window-state-changed block.
     */
    private fun launchPuGate(pkg: String) {
        val appName = getString(R.string.app_name)
        val key = "$pkg|pu"
        val now = SystemClock.elapsedRealtime()
        if (lastBlockKey == key && now - lastPuBlockAt < COOLDOWN_MS) return
        lastBlockKey = key
        lastPuBlockAt = now
        launchGate(pkg, MatchResult(appName, "pu"))
    }

    /** Normalized (lowercased, spaces stripped) concatenation of the event text. */
    private fun eventTextNormalized(event: AccessibilityEvent): String {
        val sb = StringBuilder()
        try {
            event.text?.forEach { sb.append(it) }
        } catch (_: Throwable) {
        }
        return ProtectedSystemPages.normalize(sb.toString())
    }

    /**
     * Collects candidate window-title strings for a window-state-changed event.
     * The framework populates [AccessibilityEvent.getText] with the window's
     * title (verified on-device: "Settings", "Apps", "Notifications" for the
     * corresponding Settings pages). On API 33+ the window's own title attribute
     * is used as a fallback when the event carries no text.
     */
    private fun collectTitles(event: AccessibilityEvent): List<String> {
        val out = ArrayList<String>()
        try {
            event.text?.forEach { t ->
                t.toString().takeIf { it.isNotBlank() }?.let { out.add(it) }
            }
        } catch (_: Throwable) {
        }
        if (out.isEmpty() && Build.VERSION.SDK_INT >= 33) {
            try {
                val root = rootInActiveWindow ?: return out.distinct()
                try {
                    root.window?.title?.toString()
                        ?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                } finally {
                    recycle(root)
                }
            } catch (_: Throwable) {
            }
        }
        return out.distinct()
    }

    private fun collectTexts(event: AccessibilityEvent): List<String> {
        val out = ArrayList<String>()
        try {
            event.text?.forEach { out.add(it.toString()) }
        } catch (_: Throwable) {
        }
        try {
            val root = rootInActiveWindow ?: return out.distinct()
            try {
                out.addAll(collectTextsFrom(root))
            } finally {
                recycle(root)
            }
        } catch (_: Throwable) {
        }
        return out.distinct()
    }

    /**
     * Bounded walk of [root]'s node tree collecting visible text and
     * contentDescription (capped by MAX_DEPTH/MAX_STRINGS). The root itself is
     * NOT recycled — the caller owns it.
     */
    private fun collectTextsFrom(root: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>()
        try {
            walk(root, out, 0)
        } catch (_: Throwable) {
        }
        return out.distinct()
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_STRINGS) return
        try {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        } catch (_: Throwable) {
        }
        val childCount = try {
            node.childCount
        } catch (_: Throwable) {
            0
        }
        for (i in 0 until childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Throwable) {
                null
            } ?: continue
            try {
                walk(child, out, depth + 1)
            } finally {
                recycle(child)
            }
        }
    }

    private fun findMatch(
        texts: List<String>,
        state: BlockingPrefsState,
    ): MatchResult? {
        val lowerTexts = texts.map { it.lowercase() }

        // Whitelist keywords override the blocklist entirely.
        if (state.whitelistKeywords.any { wl ->
                val needle = wl.lowercase()
                needle.isNotBlank() && lowerTexts.any { it.contains(needle) }
            }
        ) {
            return null
        }

        // Keyword match: case-insensitive substring against collected text.
        state.blocklistKeywords.forEach { kw ->
            val needle = kw.value.lowercase()
            if (needle.isNotBlank() && lowerTexts.any { it.contains(needle) }) {
                return MatchResult(kw.value, "keyword")
            }
        }
        BundledKeywords.keywords.forEach { kw ->
            val needle = kw.value.lowercase()
            if (needle.isNotBlank() && lowerTexts.any { it.contains(needle) }) {
                return MatchResult(kw.value, "keyword")
            }
        }

        // Website match: domain-suffix match at label boundaries.
        val hosts = lowerTexts.mapNotNull { text ->
            normalizeDomain(text).takeIf { it.isNotEmpty() }
        }
        if (hosts.isEmpty()) return null

        if (state.trustedWebsites.any { trusted ->
                val t = trusted.lowercase()
                t.isNotEmpty() && hosts.any { it == t || it.endsWith(".$t") }
            }
        ) {
            return null
        }

        state.blockedWebsites.forEach { site ->
            val d = site.domain.lowercase()
            if (d.isNotEmpty() && hosts.any { it == d || it.endsWith(".$d") }) {
                return MatchResult(site.domain, "website")
            }
        }
        BundledKeywords.websites.forEach { site ->
            val d = site.domain.lowercase()
            if (d.isNotEmpty() && hosts.any { it == d || it.endsWith(".$d") }) {
                return MatchResult(site.domain, "website")
            }
        }

        return null
    }

    private fun findTitleMatch(
        titles: List<String>,
        rules: List<TitleBlockRule>,
        state: BlockingPrefsState,
    ): MatchResult? {
        val enabledRules = rules.filter { it.enabled }
        if (enabledRules.isEmpty() || titles.isEmpty()) return null
        val lowerTitles = titles.map { it.lowercase() }

        // Whitelist keywords suppress title rules too, mirroring findMatch so the
        // whitelist escape hatch is honored consistently across all match paths.
        if (state.whitelistKeywords.any { wl ->
                val needle = wl.lowercase()
                needle.isNotBlank() && lowerTitles.any { it.contains(needle) }
            }
        ) {
            return null
        }

        enabledRules.forEach { rule ->
            val needle = rule.value.lowercase()
            if (needle.isBlank()) return@forEach
            val matched = when (rule.mode) {
                TitleMatchMode.CONTAINS -> lowerTitles.any { it.contains(needle) }
                TitleMatchMode.EXACT -> lowerTitles.any { it == needle }
                TitleMatchMode.STARTS_WITH -> lowerTitles.any { it.startsWith(needle) }
            }
            if (matched) {
                return MatchResult(rule.value, "title")
            }
        }
        return null
    }

    private fun findTitleMatchIfSettings(
        pkg: String,
        event: AccessibilityEvent,
        state: BlockingPrefsState,
    ): MatchResult? {
        // [H3 fix] Delegate to the shared ProtectedSystemPages implementation
        // so title-blocking and PU guards use the same OEM-aware package list.
        if (!ProtectedSystemPages.isSettingsPackage(pkg)) {
            // Warn once per service session when a Settings-looking package is seen
            // but isn't allowlisted. On OEM forks that ship Settings under a
            // different package this is the only diagnostic that title blocking
            // can't fire; plain app windows are ignored so the warning stays
            // meaningful instead of firing on every window change.
            if (!titleScopeWarned && looksLikeSettingsPackage(pkg) && state.titleBlockRules.any { it.enabled }) {
                titleScopeWarned = true
                Log.w(
                    TAG,
                    "Title rules configured, but Settings package \"$pkg\" is not in " +
                        "ProtectedSystemPages allowlist; title blocking won't fire on this device's Settings"
                )
            }
            return null
        }
        return findTitleMatch(collectTitles(event), state.titleBlockRules, state)
    }

    private fun launchGate(pkg: String, match: MatchResult) {
        val intent = Intent(this, BlockGateActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockGateActivity.EXTRA_PACKAGE, pkg)
            putExtra(BlockGateActivity.EXTRA_MATCHED, match.value)
            putExtra(BlockGateActivity.EXTRA_TYPE, match.type)
        }
        startActivity(intent)
    }

    /**
     * True when [pkg] is launch-blocked by an active schedule. With a
     * "block everything" schedule active, critical system surfaces stay
     * reachable so the user is never locked out of the launcher/Settings.
     */
    private fun isScheduleBlocked(pkg: String): Boolean {
        if (!ScheduleEngine.isLaunchBlocked(pkg)) return false
        if (ScheduleEngine.isLaunchBlockAllActive() && pkg in SCHEDULE_SYSTEM_EXEMPT) return false
        return true
    }

    private fun launchScheduleGate(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (lastScheduleBlockKey == pkg && now - lastScheduleBlockAt < SCHEDULE_COOLDOWN_MS) return
        lastScheduleBlockKey = pkg
        lastScheduleBlockAt = now
        val intent = Intent(this, BlockGateActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockGateActivity.EXTRA_PACKAGE, pkg)
            putExtra(BlockGateActivity.EXTRA_MATCHED, "")
            putExtra(BlockGateActivity.EXTRA_TYPE, "schedule")
        }
        startActivity(intent)
    }

    /**
     * Re-checks the current foreground window against the schedule sets.
     * Called by [ScheduleEngine] when the active launch-block set changes, so
     * a block that starts while the app is already open still takes effect.
     * Throttled.
     */
    private fun recheckScheduleBlock() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScheduleRecheckMs < SCHEDULE_RECHECK_THROTTLE_MS) return
        lastScheduleRecheckMs = now
        try {
            val root = rootInActiveWindow ?: return
            try {
                val pkg = root.packageName?.toString() ?: return
                val ownPackage = applicationContext.packageName ?: return
                if (pkg == ownPackage) return
                if (isScheduleBlocked(pkg)) launchScheduleGate(pkg)
            } finally {
                recycle(root)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // [M3 fix] Cancel any pending eviction toast before destroying.
        pendingToastRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        pendingToastRunnable = null
        if (instance === this) instance = null
        // Re-arm on the shared heal executor (survives serviceScope.cancel())
        // — an unbind may be the precursor to the service being disabled.
        A11yProtectionUtils.selfHealAllAsync(this)
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // [M3 fix] Cancel any pending eviction toast before destroying.
        pendingToastRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        pendingToastRunnable = null
        if (instance === this) instance = null
        A11yProtectionUtils.selfHealAllAsync(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class MatchResult(
        val value: String,
        val type: String,
    )

    companion object {
        const val TAG = "SafeMeA11y"
        const val COOLDOWN_MS = 4_000L
        const val MAX_DEPTH = 12
        const val MAX_STRINGS = 200
        // [PU watchdog] Short throttle windows: the a11y-detail eviction must
        // fire on EVERY activation, so these only dedupe event floods — they
        // never suppress a genuine reopen of the page.
        const val A11Y_PAGE_PROBE_THROTTLE_MS = 2_000L
        const val A11Y_PAGE_KICK_THROTTLE_MS = 2_000L
        const val PU_WATCHDOG_INTERVAL_MS = 2_000L
        // [PU watchdog] The app-name framework probe is throttled ABOVE the
        // watchdog cadence so it does not run on every tick; the walked-texts
        // checks run unthrottled and catch genuine targets, so this never
        // delays a real activation.
        const val PU_APP_NAME_PROBE_THROTTLE_MS = 5_000L
        const val PU_KICK_TOAST_THROTTLE_MS = 60_000L
        const val PU_KICK_TOAST_DELAY_MS = 700L
        const val SCHEDULE_COOLDOWN_MS = 4_000L
        const val SCHEDULE_RECHECK_THROTTLE_MS = 5_000L

        @Volatile
        private var instance: SafeMeAccessibilityService? = null

        /**
         * Pokes the live service to re-check the current foreground window.
         * Called by [ScheduleEngine] when the active launch-block set changes.
         */
        fun onScheduleSetsChanged() {
            val service = instance ?: return
            try {
                Handler(Looper.getMainLooper()).post {
                    runCatching { service.recheckScheduleBlock() }
                }
            } catch (_: Throwable) {
            }
        }

        /**
         * Surfaces that must stay reachable during a "block everything"
         * schedule so the user is never locked out of the device.
         */
        private val SCHEDULE_SYSTEM_EXEMPT = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.documentsui",
            "com.android.providers.media",
            "com.android.phone",
            "com.android.server.telecom",
        )
    }

    // [H3 fix] Removed private isSettingsPackage + SETTINGS_PACKAGES — now
    // delegates to ProtectedSystemPages.isSettingsPackage for a single source
    // of truth across PU guards and title-blocking.

    private fun looksLikeSettingsPackage(pkg: String): Boolean =
        pkg.contains("settings", ignoreCase = true)
}
