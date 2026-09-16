package com.safeme.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.safeme.app.data.ACTIVITY_BLOCK
import com.safeme.app.data.BlockScreenPrefsState
import com.safeme.app.data.addActivity
import com.safeme.app.data.blockScreenPrefs
import com.safeme.app.data.incrementBlockedToday
import com.safeme.app.service.SafeMeAccessibilityService
import com.safeme.app.ui.screens.blockscreen.BlockOverlay
import com.safeme.app.ui.theme.SafeMeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Universal block-gate host: a NopoX-style `TYPE_APPLICATION_OVERLAY` window
 * that raises the block screen over the offending app WITHOUT launching an
 * activity. Used by every gate system — Prevent Uninstall, keyword/title,
 * website and schedule blocks — via [SafeMeAccessibilityService.launchGate] /
 * [launchScheduleGate].
 *
 * The block screen is the SAME self-contained [BlockOverlay] composable that
 * [BlockGateActivity] renders — it is reused and adapted here, not duplicated:
 * the overlay window hosts it in a [ComposeView] wrapped in a touch-consuming
 * [FrameLayout], so visuals, dwell countdown, "why" chip and Close behavior
 * are pixel-identical to the activity gate.
 *
 * Why an overlay:
 *  - First frame ~10–50 ms from the already-running service process, vs
 *    160–235 ms for an activity launch (no ActivityThread, no task, no theme
 *    inflation) — the gate covers while the offending page is still starting,
 *    so the page is never interactive.
 *  - No activity transition on the main looper, so no frame jank during the
 *    cover.
 *
 * Stability contract (highest priority):
 *  - The window type is `TYPE_ACCESSIBILITY_OVERLAY` (2032) — the NopoX
 *    reference's exact type. This is deliberate: Settings-family windows set
 *    `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` (observed live on both Vivo and stock
 *    Android 12+), which makes WMS force-hide every `TYPE_APPLICATION_OVERLAY`
 *    window while Settings is focused — the gate silently never presents and
 *    Uninstall stays tappable (the user's "sometimes good, sometimes bad"
 *    failure). Accessibility overlays are EXEMPT from that force-hide (they
 *    exist so TalkBack-style menus can draw over Settings).
 *  - CRITICAL: the WindowManager MUST come from the AccessibilityService's own
 *    `getSystemService(WINDOW_SERVICE)` — the framework overrides it to inject
 *    the service's window token (`wm.setDefaultToken(mWindowToken)`), which
 *    WMS requires for TYPE_ACCESSIBILITY_OVERLAY windows ("unknown token null
 *    Aborting" otherwise). NopoX does exactly this. Never use the application
 *    context here.
 *  - If the overlay addView fails for any reason, [show] falls back to
 *    launching [BlockGateActivity] — the previous mechanism — so no gate type
 *    can ever break. The activity remains in the codebase as the permanent
 *    fallback.
 *  - A raw WindowManager window has no Activity to supply the
 *    ViewTreeLifecycleOwner + ViewTreeSavedStateRegistryOwner that Compose
 *    1.8 requires on attach (crash without them: "ViewTreeLifecycleOwner not
 *    found" / "Composed into the View which doesn't propagate
 *    ViewTreeSavedStateRegistryOwner" — both observed live on device). The
 *    overlay therefore wires its own [OverlayLifecycleOwner] onto the WRAPPER
 *    (the window's root view — the recomposer lookup runs there) and the
 *    ComposeView BEFORE addView, and moves it to RESUMED after attach
 *    (Compose's lifecycle-aware recomposer starts on ON_START). This is the
 *    standard chat-head/floating-window recipe.
 *  - The overlay is a singleton (NopoX `isPageShow` parity): concurrent
 *    detection paths dedupe on [isShowing], layered on top of the service's
 *    per-type cooldowns.
 *  - Dismissal mirrors [BlockGateActivity] exactly: launch HOME first (the
 *    "Close always goes to HOME" contract), keep the cover up through the
 *    transition so the page underneath never flashes, then remove the window
 *    and signal [SafeMeAccessibilityService.onGateDismissed] so the PU +
 *    keyword cooldowns re-arm and the post-dismissal re-probe chain starts
 *    (no accessibility-event-delivery dependency).
 *  - The overlay is `FLAG_NOT_FOCUSABLE`: BACK goes to the app below (matching
 *    the NopoX reference); dismissal is the dwell-gated Close button. The
 *    wrapper root is clickable so taps never fall through to the app
 *    underneath.
 *  - Some OEMs (Vivo) hide `TYPE_APPLICATION_OVERLAY` windows when the display
 *    turns off and never re-show them after wake — the window stays attached
 *    and "visible" from the client's point of view (isShown()/window
 *    visibility report nothing wrong) while WMS keeps mLastHidden=true, so the
 *    gate is dead with the offending page exposed. The client cannot observe
 *    this, so a SCREEN_ON/USER_PRESENT receiver re-asserts the window on wake
 *    (a fresh window always presents; the ComposeView re-creates its
 *    composition on re-attach — the dwell countdown restarts, which is safe).
 *
 * Threading: [show]/[dismiss] are safe to call from any thread (detection runs
 * on `Dispatchers.Default`); all WindowManager/View/Lifecycle work is posted to
 * the main looper.
 */
object BlockOverlayController {

    private const val TAG = "BlockOverlayController"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Gate type for the scoped Social Media tab cover (see [showTabGate semantics in show]). */
    private const val TYPE_SOCIAL_TAB = "socialTab"

    @Volatile
    private var showing = false

    /**
     * Type of the cover currently up, set SYNCHRONOUSLY in [show] (before the
     * main-thread hop) so concurrent detection paths can distinguish a scoped
     * tab cover from a full gate. Empty when nothing is showing.
     */
    @Volatile
    private var showingType = ""
    private var overlayView: View? = null
    private var wm: WindowManager? = null
    private var overlayLp: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    // Last-show parameters, kept so the overlay can be re-asserted after the
    // system hides it (see [refreshOverlay]).
    private var lastContext: Context? = null
    private var lastPkg = ""
    private var lastMatched = ""
    private var lastType = ""
    private var lastPrefs: BlockScreenPrefsState? = null

    // [H2] Scoped-cover geometry must survive a full rebuild: refreshOverlay
    // reuses the stored LayoutParams (sized window survives), but
    // reattachOverlay reconstructs from these last-show params — without this
    // field a rebuilt tab cover would come back FULL-SCREEN.
    private var lastCoverAboveY: Int? = null

    // [V17] Pre-cached prefs for instant launch path — avoids DataStore IO on critical path
    @Volatile
    private var cachedPrefs: BlockScreenPrefsState? = null

    // [V17] Instant blank view for two-stage overlay — 10-30ms perceived block
    private var instantBlankView: View? = null

    private var screenWakeReceiverRegistered = false
    private val screenWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> refreshOverlay()
            }
        }
    }

    /** How long the cover stays up while HOME is starting (never flash the page below). */
    private const val REMOVE_AFTER_HOME_DELAY_MS = 250L

    /** True while the overlay window is attached (or its show is in flight). */
    fun isShowing(): Boolean = showing

    /** True while the cover currently up is the scoped Social Media TAB cover. */
    fun isShowingTabCover(): Boolean = showing && showingType == TYPE_SOCIAL_TAB

    /** Package the current cover was raised over (empty-safe), or null when nothing is up. */
    fun coveredPackage(): String? {
        if (!showing) return null
        val pkg = lastPkg
        return pkg.ifEmpty { null }
    }

    /** [V17] Pre-cache block screen prefs — avoids DataStore IO on critical path */
    suspend fun preCachePrefs(ctx: Context) {
        try {
            val p = ctx.applicationContext.blockScreenPrefs().first()
            cachedPrefs = p
            lastPrefs = p
        } catch (_: Throwable) {
        }
    }

    /** [V17] Cached prefs or default — no IO, for instant launch path */
    fun getCachedPrefsOrDefault(): BlockScreenPrefsState = cachedPrefs ?: lastPrefs ?: BlockScreenPrefsState()

    /**
     * [V17] Instant launch block — two-stage: blank black view 10-30ms + async upgrade to full BlockOverlay
     * Perceived block ≤50ms even if Compose init 200-400ms. Used for socialWhole + schedule launch only.
     * No PackageManager label fetch, no DataStore read on critical path.
     */
    fun showInstantLaunchBlock(context: Context, pkg: String, matched: String, type: String) {
        val isMain = Looper.myLooper() == Looper.getMainLooper()
        val task = Runnable {
            try {
                val preempt = showing
                if (preempt) {
                    // Immediate removal for launch preempt — no 250ms delay (fixes WMS already-added)
                    removeOverlayNowInternal()
                }
                showing = true
                showingType = type
                lastPkg = pkg
                lastType = type
                lastMatched = matched
                lastContext = context
                attachInstantBlank(context)
                // Stage 2: upgrade to full overlay async with cached prefs (no IO)
                val prefs = getCachedPrefsOrDefault()
                scope.launch {
                    mainHandler.post {
                        try {
                            upgradeToFullOverlay(context, pkg, matched, type, prefs)
                            registerScreenWakeReceiver(context.applicationContext)
                            // Activity feed + counter (not on critical path)
                            if (type != TYPE_SOCIAL_TAB) {
                                scope.launch {
                                    runCatching { context.applicationContext.incrementBlockedToday() }
                                    runCatching {
                                        val label = pkg // instant, no PM IPC
                                        context.applicationContext.addActivity(
                                            ACTIVITY_BLOCK,
                                            blockActivityTitle(type, label, matched),
                                            blockActivitySub(type, matched),
                                        )
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "instant upgrade failed — keeping blank as fallback", t)
                            // Keep blank as fallback — still blocked (fail-closed for launch)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "instant blank addView failed — activity fallback", t)
                showing = false
                showingType = ""
                lastContext = null
                launchFallbackActivity(context, pkg, matched, type)
            }
        }
        if (isMain) task.run() else mainHandler.post(task)
    }

    private fun attachInstantBlank(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val blank = FrameLayout(context).apply {
            setBackgroundColor(0xFF000000.toInt())
            isClickable = true
            setOnClickListener {}
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP
        windowManager.addView(blank, lp)
        instantBlankView = blank
        wm = windowManager
        overlayLp = lp
        lastCoverAboveY = null
    }

    private fun upgradeToFullOverlay(
        context: Context,
        pkg: String,
        matched: String,
        type: String,
        prefs: BlockScreenPrefsState,
    ) {
        try {
            instantBlankView?.let { v ->
                try {
                    if (v.isAttachedToWindow) wm?.removeView(v)
                } catch (_: Throwable) {
                }
            }
            instantBlankView = null
            // If a full overlay already attached (race), remove it first
            overlayView?.let { v ->
                try {
                    if (v.isAttachedToWindow) wm?.removeView(v)
                } catch (_: Throwable) {
                }
            }
            overlayView = null
            lifecycleOwner?.destroy()
            lifecycleOwner = null
            attachOverlay(context, pkg, matched, type, prefs, null)
        } catch (t: Throwable) {
            throw t
        }
    }

    /** [V17] Immediate removal, no delay — for launch preempt path */
    fun removeOverlayNow() {
        mainHandler.post { removeOverlayNowInternal() }
    }

    private fun removeOverlayNowInternal() {
        try {
            overlayView?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        } catch (_: Throwable) {
        }
        try {
            instantBlankView?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        } catch (_: Throwable) {
        }
        overlayView = null
        instantBlankView = null
        wm = null
        overlayLp = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        lastCoverAboveY = null
        // Keep lastContext for reassert? clear for full removal
        // showing flag managed by caller for preempt path
    }

    /**
     * Re-asserts a gate that the system may have removed while the controller
     * still believed it was showing. Called from the accessibility service's
     * onServiceConnected: a11y overlay windows belong to the service
     * connection, so a connection reset (OEM rebind, system churn) silently
     * removes the window — leaving `showing` true with no gate on screen.
     * Re-adding the same view restores it (dwell restarts — safe).
     */
    fun reassertIfShowing(serviceContext: Context? = null) {
        if (!showing) return
        if (serviceContext != null) {
            // A service reconnect gets a new accessibility window token. Keep
            // the pending gate tied to the current service, not the dead one.
            lastContext = serviceContext
            wm = serviceContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        }
        refreshOverlay()
    }

    /** Clears a gate whose accessibility service connection has gone away. */
    fun onAccessibilityServiceDisconnected() {
        mainHandler.post {
            removeOverlay()
        }
    }

    /**
     * Raises the block gate for [pkg] with the given [matched]/[type] context
     * (the exact triple [BlockGateActivity] receives). Falls back to the
     * activity when overlay permission is missing.
     *
     * [coverAboveY] — when non-null (Social Media TAB gate only), the window
     * spans 0..coverAboveY instead of the full screen, leaving the app's
     * bottom nav bar visible AND tappable (the window simply doesn't span
     * it). Every other caller passes nothing → byte-identical full-screen
     * behavior.
     */
    fun show(context: Context, pkg: String, matched: String, type: String, coverAboveY: Int? = null) {
        // [H1] A full gate always wins over a scoped tab cover: mark the old
        // window for immediate removal at attach time (same main-looper
        // queue, so ordering is guaranteed) and proceed. Tab-over-tab and
        // full-over-full still dedupe exactly as before.
        val preemptTabCover = showing && showingType == TYPE_SOCIAL_TAB && type != TYPE_SOCIAL_TAB
        if (showing && !preemptTabCover) return
        // Set synchronously so concurrent detection paths dedupe before the
        // main-thread hop. [context] MUST be the AccessibilityService: the
        // WindowManager for the overlay has to come from the service's own
        // getSystemService (it injects the a11y window token required for
        // TYPE_ACCESSIBILITY_OVERLAY). The application context is used only
        // for DataStore/prefs and bookkeeping.
        showing = true
        showingType = type
        lastPkg = pkg
        lastType = type
        val appContext = context.applicationContext
        scope.launch {
            // [V13] Cache prefs — first gate loads, rest instant (fixes launch delay). Fail-open with defaults.
            val prefs = lastPrefs ?: runCatching { appContext.blockScreenPrefs().first() }
                .getOrDefault(BlockScreenPrefsState())
            mainHandler.post {
                // [H1 race] A tab-cover dismissal posted between show() and
                // this task can flip showing=false first; a preempting full
                // gate must still attach (it was explicitly requested), so
                // only the plain dedupe path honors a concurrent dismissal.
                if (!showing && !preemptTabCover) return@post
                try {
                    if (preemptTabCover) {
                        Log.d(TAG, "full gate ($type) preempts tab cover — removing tab window")
                        detachOverlayWindow()
                        showing = true
                        showingType = type
                    }
                    attachOverlay(context, pkg, matched, type, prefs, coverAboveY)
                    registerScreenWakeReceiver(appContext)
                    // [H5] Bookkeeping that BlockGateActivity performed on
                    // first creation: blocked-today counter + activity feed
                    // entry. TAB covers are excluded — they are a soft,
                    // self-dismissing cover and repeated tab entries would
                    // inflate the counters.
                    if (type != TYPE_SOCIAL_TAB) {
                        scope.launch {
                            runCatching { appContext.incrementBlockedToday() }
                            runCatching {
                                val label = runCatching {
                                    appContext.packageManager.getApplicationLabel(
                                        appContext.packageManager.getApplicationInfo(pkg, 0)
                                    ).toString()
                                }.getOrDefault(pkg.ifBlank { "an app" })
                                appContext.addActivity(
                                    ACTIVITY_BLOCK,
                                    blockActivityTitle(type, label, matched),
                                    blockActivitySub(type, matched),
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    // Never let the gate fail silently: reset and use the
                    // activity fallback so the block still happens. TAB
                    // covers never use the activity fallback — an activity
                    // would eject to HOME and break the keep-the-app-usable
                    // contract; the service's tab watch re-raises instead.
                    Log.e(TAG, "overlay addView failed — activity fallback", t)
                    showing = false
                    showingType = ""
                    lastContext = null
                    if (type != TYPE_SOCIAL_TAB) {
                        launchFallbackActivity(context, pkg, matched, type)
                    }
                }
            }
        }
    }

    /**
     * Builds the overlay window: the real [BlockOverlay] composable inside a
     * [ComposeView] (with a view-tree lifecycle owner wired BEFORE attach, per
     * the floating-window Compose recipe) wrapped in a touch-consuming
     * [FrameLayout] so taps never fall through to the page underneath.
     */
    private fun attachOverlay(
        context: Context,
        pkg: String,
        matched: String,
        type: String,
        prefs: BlockScreenPrefsState,
        coverAboveY: Int? = null,
    ) {
        val owner = OverlayLifecycleOwner().apply { performCreate() }
        // The ComposeView defaults to DisposeOnDetachedFromWindow for
        // programmatic creation, which is what we want: re-attaching after a
        // wake re-assert recreates the composition (dwell restarts — safe).
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                SafeMeApp {
                    BlockOverlay(
                        dwell = prefs.dwell.coerceAtLeast(0),
                        msg = blockGateMessage(
                            prefs.message,
                            context.getString(R.string.bs_preview_msg_default),
                        ),
                        whyOn = prefs.whyOn,
                        // TAB cover Close = stay in the app (no HOME eject) and
                        // keep the 4 s gate cooldown as a natural snooze — the
                        // content-event probe re-raises the cover once it
                        // expires if the user is still on the blocked tab.
                        onClose = { if (type == TYPE_SOCIAL_TAB) dismissTabCover(clearCooldown = false) else dismiss() },
                        whyReason = blockGateWhyReason(
                            type,
                            matched,
                            context.getString(R.string.pu_gate_message),
                            context.getString(R.string.schedule_gate_message),
                        ),
                    )
                }
            }
        }
        // Touch-consuming wrapper: with FLAG_NOT_TOUCH_MODAL, touches that no
        // View consumes pass through to windows below — the wrapper guarantees
        // the background swallows every tap; child controls (why chip, Close)
        // consume their own taps first.
        val wrapper = FrameLayout(context).apply {
            // CRITICAL: Compose's window-recomposer lookup (getWindowRecomposer)
            // runs against the window's ROOT view — the wrapper, not the
            // ComposeView — so the owners must be visible on the wrapper's view
            // tree too (findViewTreeLifecycleOwner only walks UP from the
            // checked view). Without this, the overlay crashes (observed live
            // on device: "ViewTreeLifecycleOwner not found" / "Composed into
            // the View which doesn't propagate ViewTreeSavedStateRegistryOwner").
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            isClickable = true
            setOnClickListener { }
        }
        wrapper.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        // [Tab cover] Scoped window: spans 0..coverAboveY (gravity TOP) so
        // the app's bottom nav bar stays visible AND tappable — touches
        // outside the window bounds reach the app naturally. Full-screen
        // (MATCH_PARENT) for every other caller and for fullscreen-feed tab
        // hits where no nav bar was identified.
        val windowHeight = if (coverAboveY != null && coverAboveY > 0) {
            coverAboveY
        } else {
            WindowManager.LayoutParams.MATCH_PARENT
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            windowHeight,
            // NopoX parity (2032): accessibility overlays are exempt from
            // Settings' HIDE_NON_SYSTEM_OVERLAY_WINDOWS force-hide, so the
            // gate presents over App Info reliably. Requires the a11y service
            // to be enabled — always true here, since detection runs in it.
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        // Reference (NopoX) parity: TOP gravity, dialog animation.
        lp.gravity = Gravity.TOP
        lp.windowAnimations = android.R.style.Animation_Dialog
        // CRITICAL: must be the AccessibilityService's own getSystemService —
        // the framework overrides it to inject the service window token
        // (wm.setDefaultToken) that WMS requires for TYPE_ACCESSIBILITY_OVERLAY.
        // The application context's WindowManager has no token → WMS rejects
        // the window ("unknown token null"). NopoX parity.
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(wrapper, lp)
        // Compose's lifecycle-aware window recomposer starts its loop on
        // ON_START — move the owner to RESUMED now that the window is attached.
        owner.moveToResumed()
        overlayView = wrapper
        wm = windowManager
        overlayLp = lp
        lifecycleOwner = owner
        lastContext = context
        lastPkg = pkg
        lastMatched = matched
        lastType = type
        lastPrefs = prefs
        lastCoverAboveY = coverAboveY
    }

    /**
     * Removes ONLY the window/lifecycle of the current cover, keeping the
     * controller's "showing" bookkeeping intact — used by the [H1] preemption
     * path where a full gate immediately replaces a tab cover (the subsequent
     * attach in the same main-looper task re-populates every field).
     */
    private fun detachOverlayWindow() {
        try {
            overlayView?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
        } catch (_: Throwable) {
        }
        lifecycleOwner?.destroy()
        overlayView = null
        lifecycleOwner = null
    }

    /**
     * Re-asserts the overlay after a screen wake. Vivo hides the window when
     * the display turns off and never re-shows it: measured on the device, the
     * window stays attached and "visible" from the client's point of view while
     * WMS keeps mLastHidden=true, and neither a relayout nor a redraw un-sticks
     * it (updateViewLayout advanced the draw state but mLastHidden stayed). A
     * fresh window always presents, so the deterministic fix is to re-add the
     * view. The ComposeView re-creates its composition on re-attach (the dwell
     * countdown restarts, which is safe).
     */
    private fun refreshOverlay() {
        mainHandler.post {
            if (!showing) return@post
            val view = overlayView ?: return@post
            val windowManager = wm ?: return@post
            try {
                if (!view.isAttachedToWindow) {
                    reattachOverlay()
                    return@post
                }
                val lp = overlayLp ?: return@post
                windowManager.removeView(view)
                windowManager.addView(view, lp)
                lifecycleOwner?.moveToResumed()
            } catch (t: Throwable) {
                // If the re-add failed (e.g. window mid-transition), fall back
                // to a full rebuild; the gate must never stay dead.
                reattachOverlay()
            }
        }
    }

    /** Full re-attach (fresh window) using the last-show parameters. */
    private fun reattachOverlay() {
        val ctx = lastContext ?: return
        val pkg = lastPkg
        val matched = lastMatched
        val type = lastType
        val prefs = lastPrefs ?: BlockScreenPrefsState()
        val coverY = lastCoverAboveY
        try {
            try {
                overlayView?.let { v -> if (v.isAttachedToWindow) wm?.removeView(v) }
            } catch (_: Throwable) {
            }
            overlayView = null
            wm = null
            overlayLp = null
            lifecycleOwner?.destroy()
            lifecycleOwner = null
            // [H2] Geometry rides along so a rebuilt tab cover never comes
            // back full-screen.
            attachOverlay(ctx, pkg, matched, type, prefs, coverY)
        } catch (t: Throwable) {
            removeOverlay()
            if (type != TYPE_SOCIAL_TAB) {
                launchFallbackActivity(ctx, pkg, matched, type)
            }
        }
    }

    private fun registerScreenWakeReceiver(context: Context) {
        if (screenWakeReceiverRegistered) return
        screenWakeReceiverRegistered = true
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(screenWakeReceiver, filter, Context.RECEIVER_EXPORTED)
        } catch (_: Throwable) {
            // Receiver is best-effort; the watchdog still covers re-detection.
        }
    }

    /**
     * Closes the gate. Mirrors [BlockGateActivity.closeGate]: HOME first,
     * keep the cover up through the transition, then remove + signal the
     * service. Safe to call more than once (removal is idempotent).
     */
    fun dismiss() {
        mainHandler.post {
            if (!showing) return@post
            launchHome()
            // [V13] Early clear of showing flag — fixes 5s launch delay dead zone where fast lane was blocked by showing=true during 250ms dismiss animation. Window stays attached for visual continuity (removeOverlay delayed), only flag cleared so next launch can re-gate immediately.
            showing = false
            showingType = ""
            mainHandler.postDelayed(
                {
                    removeOverlay()
                    try {
                        SafeMeAccessibilityService.onGateDismissed()
                    } catch (_: Throwable) {
                    }
                },
                REMOVE_AFTER_HOME_DELAY_MS,
            )
        }
    }

    private fun removeOverlay() {
        val ctx = lastContext
        try {
            overlayView?.let { v ->
                if (v.isAttachedToWindow) wm?.removeView(v)
            }
        } catch (_: Throwable) {
        }
        try {
            instantBlankView?.let { v ->
                if (v.isAttachedToWindow) wm?.removeView(v)
            }
        } catch (_: Throwable) {
        }
        overlayView = null
        instantBlankView = null
        wm = null
        overlayLp = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        lastContext = null
        // Keep lastPrefs and cachedPrefs for instant next gate (V17) — don't clear
        lastCoverAboveY = null
        showing = false
        showingType = ""
        if (screenWakeReceiverRegistered) {
            screenWakeReceiverRegistered = false
            runCatching { ctx?.unregisterReceiver(screenWakeReceiver) }
        }
    }

    /**
     * Removes a TAB cover WITHOUT launching HOME — the user stays in the app
     * (the whole point of the scoped cover). No-op unless a tab cover is up.
     *
     * [clearCooldown] — true when the user navigated away from the blocked
     * tab: signals the standard gate-dismissal path, which re-arms every
     * cooldown (including the per-vertical tab cooldown), so re-entering the
     * tab re-blocks instantly. False for the cover's own Close button: the
     * gate cooldown set at fire time survives as a ~4 s snooze, after which
     * the content probe re-raises the cover if the tab is still active.
     */
    fun dismissTabCover(clearCooldown: Boolean = true) {
        mainHandler.post {
            if (!showing || showingType != TYPE_SOCIAL_TAB) return@post
            Log.d(TAG, "tab cover dismissed (clearCooldown=$clearCooldown)")
            removeOverlay()
            if (clearCooldown) {
                try {
                    SafeMeAccessibilityService.onGateDismissed()
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Re-fits the tab cover when the nav bar moved (rotation, font scale,
     * nav show/hide). Cheap no-op when the height is unchanged; called from
     * the service's tab watch on its throttled probe cadence.
     */
    fun refitTabCover(coverAboveY: Int?) {
        mainHandler.post {
            if (!showing || showingType != TYPE_SOCIAL_TAB) return@post
            val view = overlayView ?: return@post
            val lp = overlayLp ?: return@post
            val wanted = if (coverAboveY != null && coverAboveY > 0) {
                coverAboveY
            } else {
                WindowManager.LayoutParams.MATCH_PARENT
            }
            if (lp.height == wanted) return@post
            lp.height = wanted
            try {
                wm?.updateViewLayout(view, lp)
                Log.d(TAG, "tab cover refit to height=$wanted")
            } catch (_: Throwable) {
                // Mid-transition; the next watch tick retries.
            }
        }
    }

    private fun launchHome() {
        try {
            val appContext = overlayView?.context?.applicationContext
            if (appContext != null) {
                appContext.startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        } catch (_: Throwable) {
            // A plain removal still exits the gate.
        }
    }

    /** Fallback: the previous activity-based gate (used when overlays are denied). */
    private fun launchFallbackActivity(
        context: Context,
        pkg: String,
        matched: String,
        type: String,
    ) {
        try {
            context.startActivity(
                Intent(context, BlockGateActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                    putExtra(BlockGateActivity.EXTRA_PACKAGE, pkg)
                    putExtra(BlockGateActivity.EXTRA_MATCHED, matched)
                    putExtra(BlockGateActivity.EXTRA_TYPE, type)
                }
            )
        } catch (_: Throwable) {
        }
    }

    /**
     * Minimal lifecycle host for a raw WindowManager window. Compose 1.8's
     * AndroidComposeView requires BOTH a ViewTreeLifecycleOwner and a
     * ViewTreeSavedStateRegistryOwner on attach, which a window without an
     * Activity cannot provide — this supplies them. (A ViewModelStoreOwner is
     * not required by the attach checks; the block screen uses no ViewModels.)
     */
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        /** Attach + restore + ON_CREATE — must run BEFORE addView. */
        fun performCreate() {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        /** ON_START + ON_RESUME — must run AFTER the window is attached. */
        fun moveToResumed() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}

/** Pure title for the activity feed entry — mirrors BlockGateActivity.addBlockActivity. */
internal fun blockActivityTitle(type: String, label: String, matched: String): String = when (type) {
    "website" -> "Website blocked"
    "title" -> "Settings page blocked"
    "schedule" -> "Blocked $label"
    "pu" -> "Uninstall blocked"
    "socialWhole" -> "App blocked"
    "socialTab" -> "Tab blocked"
    else -> if (matched.isNotEmpty()) "Keyword blocked" else "Blocked $label"
}

/** Pure subtitle for the activity feed entry — mirrors BlockGateActivity.addBlockActivity. */
internal fun blockActivitySub(type: String, matched: String): String = when (type) {
    "schedule" -> "Launch blocked by schedule"
    "pu" -> "Prevent Uninstall is on"
    "socialWhole" -> if (matched.isNotEmpty()) "Blocked by Social Media Blocking — whole-app ($matched)" else "Blocked by Social Media Blocking — whole-app"
    "socialTab" -> if (matched.isNotEmpty()) "Blocked by Social Media Blocking — tab ($matched)" else "Blocked by Social Media Blocking — tab"
    else -> if (matched.isNotEmpty()) matched else "Blocked by SafeMe"
}

/** Gate message: the persisted custom message, else the resource default. */
internal fun blockGateMessage(custom: String, defaultMessage: String): String =
    custom.ifEmpty { defaultMessage }

/** Single source of truth for the inline "Why am I seeing this?" explanation. */
internal fun blockGateWhyReason(
    type: String,
    matched: String,
    puMessage: String,
    scheduleMessage: String,
): String = when (type) {
    "website" ->
        if (matched.isNotEmpty()) "Why: website blocked by SafeMe ($matched)" else "Why: website blocked by SafeMe"
    "title" ->
        if (matched.isNotEmpty()) "Why: Settings page blocked by SafeMe ($matched)" else "Why: Settings page blocked by SafeMe"
    "pu" -> puMessage
    "schedule" -> scheduleMessage
    "socialWhole" -> if (matched.isNotEmpty()) "Why: app blocked by Social Media Blocking ($matched)" else "Why: app blocked by Social Media Blocking"
    "socialTab" -> if (matched.isNotEmpty()) "Why: tab blocked by Social Media Blocking ($matched)" else "Why: tab blocked by Social Media Blocking"
    else -> if (matched.isNotEmpty()) "Why: $matched blocked by SafeMe" else "Why: this content or action was blocked by an active SafeMe rule"
}
