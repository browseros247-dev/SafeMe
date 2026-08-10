package com.safeme.app.protect

import android.content.Context
import com.safeme.app.data.ScheduleBlock
import com.safeme.app.data.schedulePrefs
import com.safeme.app.service.SafeMeAccessibilityService
import com.safeme.app.service.SafeMeVpnService
import kotlinx.coroutines.flow.first

/**
 * Process-wide coordinator for Schedule-Based App Blocking.
 *
 * The ONLY component that turns schedule rules into enforcement:
 *  - launch-blocked apps → [SafeMeAccessibilityService] (gates the app on open)
 *  - internet-blocked apps → [SafeMeVpnService] (per-app-block tunnel mode)
 *  - next boundary → [ScheduleAlarmReceiver] (precise re-application at the
 *    next window start/end)
 *
 * Mirrors the reference `ScheduleEngine` singleton: idempotent, safe to call
 * from any thread, and only restarts the VPN when the active set actually
 * changes (so a no-op re-evaluation never tears down a healthy tunnel).
 */
object ScheduleEngine {

    @Volatile
    private var internetBlockedPackages: Set<String> = emptySet()

    @Volatile
    private var internetBlockAll = false

    @Volatile
    private var launchBlockedPackages: Set<String> = emptySet()

    @Volatile
    private var launchBlockAll = false

    /** True when [pkg] must not launch right now (launch / UI blocking). */
    fun isLaunchBlocked(pkg: String): Boolean =
        launchBlockAll || pkg in launchBlockedPackages

    /** True when [pkg] must not reach the internet right now. */
    fun isInternetBlocked(pkg: String): Boolean =
        internetBlockAll || pkg in internetBlockedPackages

    fun hasLaunchBlock(): Boolean = launchBlockAll || launchBlockedPackages.isNotEmpty()

    fun hasInternetBlock(): Boolean = internetBlockAll || internetBlockedPackages.isNotEmpty()

    /** True when a "block everything" schedule is currently launch-blocking. */
    fun isLaunchBlockAllActive(): Boolean = launchBlockAll

    // Test/diagnostic accessors.
    fun activeLaunchBlockedPackages(): Set<String> = launchBlockedPackages
    fun activeInternetBlockedPackages(): Set<String> = internetBlockedPackages

    /**
     * Re-load schedules from DataStore and apply. Called by [SafeMeApp] on
     * every persisted change and by [ScheduleAlarmReceiver] at boundaries.
     */
    suspend fun reevaluate(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val schedules = context.schedulePrefs().first().schedules
        apply(context, schedules, nowMillis)
    }

    /**
     * Apply a schedule snapshot. Idempotent; safe to call repeatedly.
     * Never throws — enforcement failures degrade gracefully (a missed
     * boundary is caught by the alarm / safety ticker retry).
     */
    fun apply(
        context: Context,
        schedules: List<ScheduleBlock>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        try {
            val active = ScheduleEvaluator.evaluate(schedules, nowMillis)

            // Accessibility (launch / UI blocking): push the new set and poke
            // the service to re-check the current foreground window so a block
            // that starts while the app is already open takes effect.
            val launchChanged =
                active.launchBlockedPackages != launchBlockedPackages ||
                    active.launchBlockAll != launchBlockAll
            launchBlockedPackages = active.launchBlockedPackages
            launchBlockAll = active.launchBlockAll
            if (launchChanged) {
                SafeMeAccessibilityService.onScheduleSetsChanged()
            }

            // VPN (internet blocking): restart only when the active set changed.
            val internetChanged =
                active.internetBlockedPackages != internetBlockedPackages ||
                    active.internetBlockAll != internetBlockAll
            internetBlockedPackages = active.internetBlockedPackages
            internetBlockAll = active.internetBlockAll
            if (internetChanged) {
                SafeMeVpnService.applyScheduledBlocks(
                    active.internetBlockedPackages,
                    active.internetBlockAll,
                    context,
                )
            }

            // Re-arm the next boundary alarm.
            val next = ScheduleEvaluator.nextBoundary(schedules, nowMillis)
            ScheduleAlarmReceiver.scheduleBoundary(context, next)
        } catch (t: Throwable) {
            // Coordinator failures must never crash callers (App, receiver).
        }
    }
}
