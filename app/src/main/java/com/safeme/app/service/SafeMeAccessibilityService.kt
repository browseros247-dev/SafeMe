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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    @Volatile
    private var lastPuKickToastMs: Long = 0L

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
        if (pkg == ownPackage) return

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
     *  1. Our accessibility-service DETAIL page — evicted via HOME + delayed
     *     toast. NEVER covered by [BlockGateActivity]: Android auto-disables
     *     an accessibility service whose window obscures a11y-management
     *     screens, so covering our own page would kill the engine.
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

            // 1. Our own a11y detail page → evict (never cover).
            if (a11yContext) {
                if (isOurA11yServiceDetailPage(event)) {
                    evictFromOurA11yServicePage()
                    return true
                }
                // Any other a11y-management screen is left untouched.
                return false
            }

            // 2. App Info / Device Admin / force-stop / uninstall pages for OUR app.
            val appName = getString(R.string.app_name)
            // [M1 fix] PU blocks use a separate cooldown key so they don't
            // suppress keyword blocking during the cooldown window.
            val key = "$pkg|pu"
            val now = SystemClock.elapsedRealtime()
            if (lastBlockKey == key && now - lastPuBlockAt < COOLDOWN_MS) return true
            if (!isOurUninstallTargetPage(event, pkg, cls)) {
                Log.d(TAG, "PU: not a target (pkg=$pkg cls=$cls)")
                return false
            }
            Log.d(TAG, "PU: blocking page (pkg=$pkg cls=$cls)")

            lastBlockKey = key
            lastPuBlockAt = now
            launchGate(pkg, MatchResult(appName, "pu"))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "handlePreventUninstall failed — fail open", t)
            false
        }
    }

    /**
     * True when the visible settings window is SafeMe's OWN accessibility
     * service DETAIL page. Layer 1 is a cheap scan of the event text; layer 2
     * is a bounded node-tree probe (throttled — it is the only expensive step
     * and a stream of events must cost nothing).
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

            // Android 16 renders the detail page in a generic SubSettings
            // container and does NOT expose the description to the tree. The
            // detail-only "shortcut" row ("<label> shortcut") carries our
            // service label — the list row shows the label without it.
            val label = getString(R.string.accessibility_service_label)
            if (label.isNotBlank()) {
                val labelLower = label.lowercase(Locale.ROOT)
                val shortcutLower = "shortcut"
                if (collectTexts(event).any {
                        val t = it.lowercase(Locale.ROOT)
                        t.contains(labelLower) && t.contains(shortcutLower)
                    }
                ) {
                    return true
                }
            }

            val now = SystemClock.elapsedRealtime()
            if (now - lastA11yPageProbeMs < A11Y_PAGE_PROBE_THROTTLE_MS) return false
            lastA11yPageProbeMs = now
            // findAccessibilityNodeInfosByText matches the RAW node text (spaces
            // intact), so the normalized [marker] can never match. Probe with the
            // spaced detail-only suffix instead — it appears on the detail page
            // (full description) but never on the list row (summary only).
            val spacedProbe = if (description.startsWith(summary)) {
                description.removePrefix(summary).trim()
            } else {
                description.drop(40).trim()
            }
            if (spacedProbe.length < 8) return false
            return nodeTreeContainsText(spacedProbe.take(30))
        } catch (t: Throwable) {
            Log.w(TAG, "isOurA11yServiceDetailPage failed — assuming not our page", t)
            false
        }
    }

    /**
     * Bounded node-tree substring search. Every node obtained from the active
     * window is recycled afterwards (required on API 26–32, a no-op on 33+).
     */
    private fun nodeTreeContainsText(needle: String): Boolean {
        if (needle.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val nodes = try {
            root.findAccessibilityNodeInfosByText(needle)
        } catch (t: Throwable) {
            recycle(root)
            return false
        }
        return try {
            nodes != null && nodes.isNotEmpty()
        } finally {
            if (nodes != null) nodes.forEach { recycle(it) }
            recycle(root)
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
     * Evict our own a11y detail page: HOME + delayed toast. Delayed so the
     * toast lands over the launcher, never over the a11y screen (a toast is
     * still one of our windows — keep the zero-obscure discipline). Throttled.
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
            val lowerClass = cls.lowercase(Locale.ROOT)
            val appName = getString(R.string.app_name)
            if (appName.isBlank()) return false
            val appNameLower = appName.lowercase(Locale.ROOT)

            val lowerText = collectTexts(event).joinToString(" ").lowercase(Locale.ROOT)
            val appNameInText = lowerText.contains(appNameLower)
            val appNameInNodeTree = if (appName.isNotEmpty() && !appNameInText) {
                runCatching { nodeTreeContainsText(appName) }.getOrDefault(false)
            } else {
                false
            }
            val appIsOnPage = appNameInText || appNameInNodeTree

            // [H1 fix] Removed redundant `|| appNameInNodeTree` — appIsOnPage
            // already includes it via the definition above.

            // Never cover a11y-management content for OUR service. Any page
            // whose texts contain our SERVICE LABEL (distinct from the app
            // name) is the service's list row or detail page — on Android 16
            // the detail page is hosted in a generic SubSettings container
            // whose class carries no a11y marker and whose description text is
            // NOT exposed to the tree, so the description probes fail there.
            // The label is the only reliable signal; covering the page would
            // make Android auto-disable the service (the a11y kill vector).
            val serviceLabelLower = getString(R.string.accessibility_service_label)
                .lowercase(Locale.ROOT)
            if (serviceLabelLower.isNotBlank() && lowerText.contains(serviceLabelLower)) {
                // Best-effort eviction of the DETAIL page: it also shows the
                // detail-only "shortcut" row, which the list row lacks. Never
                // blocks either way; eviction is throttled and fail-open.
                if (lowerText.contains("shortcut")) {
                    evictFromOurA11yServicePage()
                }
                return false
            }

            // The stock uninstall confirmation lives in the packageinstaller
            // package (UninstallerActivity). Only that dialog surface may be
            // gated there — its install/update screens must never be blocked.
            if (ProtectedSystemPages.isUninstallerPackage(pkg)) {
                // The uninstall confirmation is reported either as the
                // UninstallerActivity class or as a generic AlertDialog window
                // (observed on Android 16). Both surfaces are gated on the
                // "uninstall" text so install/update/permission dialogs in the
                // packageinstaller are never blocked.
                val isUninstallSurface = lowerClass.contains("uninstalleractivity") ||
                    lowerClass.contains("alertdialog")
                val hasUninstallText = lowerText.contains("uninstall")
                val matched = isUninstallSurface && appIsOnPage && hasUninstallText
                Log.d(TAG, "PU: packageinstaller pkg=$pkg cls=$lowerClass appIsOnPage=$appIsOnPage hasUninstallText=$hasUninstallText matched=$matched")
                return matched
            }

            if (lowerClass.contains("uninstalleractivity")) {
                if (appIsOnPage) return true
            }

            if (!appIsOnPage) return false

            // Never block our own a11y detail page (the eviction path handles it).
            if (isOurA11yServiceDetailPage(event)) return false

            // DeviceAdminAdd hosts BOTH the activation flow (admin NOT active)
            // and the deactivation flow (admin active). The activation page
            // must never be blocked — its text matches the "device admin" and
            // "uninstall" markers ("device administrator", our own ADD
            // explanation), so a stale PU flag would lock the user out of
            // turning the feature on. Only deactivation is a tamper surface.
            val adminActive = DeviceAdminUtils.isActive(applicationContext)
            if (lowerClass.contains("deviceadminadd") && !adminActive) return false

            val isAppInfoClass = UninstallBlockers.APP_INFO_CLASS_MARKERS.any { lowerClass.contains(it) }
            val hasUninstallKeyword =
                UninstallBlockers.UNINSTALL_KEYWORDS.any { lowerText.contains(it) }
            if (isAppInfoClass || hasUninstallKeyword) return true

            // The Device-admin texts can also appear on the administrators LIST
            // page — block it only while our admin is actually active.
            if (adminActive &&
                UninstallBlockers.DEVICE_ADMIN_TEXTS_TO_MATCH.any { lowerText.contains(it) }
            ) {
                return true
            }

            if (UninstallBlockers.FORCE_STOP_TEXTS_TO_MATCH.any { lowerText.contains(it) }) return true

            false
        } catch (t: Throwable) {
            Log.w(TAG, "isOurUninstallTargetPage failed — fail open", t)
            false
        }
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
                walk(root, out, 0)
            } finally {
                recycle(root)
            }
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
        const val A11Y_PAGE_PROBE_THROTTLE_MS = 10_000L
        const val A11Y_PAGE_KICK_THROTTLE_MS = 15_000L
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
