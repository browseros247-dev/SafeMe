package com.safeme.app.protect

import com.safeme.app.data.ScheduleBlock
import com.safeme.app.data.ScheduleMode
import java.util.Calendar

/**
 * Pure decision core for Schedule-Based App Blocking.
 *
 * No Android, I/O or side effects — trivially unit-testable (mirrors the
 * reference `ScheduleEvaluator` in the Protect-Yourself project).
 *
 * Day convention: schedules store 0=Mon … 6=Sun (prototype order); this file
 * converts to [Calendar.DAY_OF_WEEK] (1=Sun … 7=Sat) internally.
 *
 * An empty [ScheduleBlock.appPackages] list means "no apps picked" → the rule
 * blocks every app ([ScheduleBlock.blocksAllApps]), per the prototype's
 * "No apps — schedule blocks everything" editor copy.
 */
object ScheduleEvaluator {

    /** Which apps are restricted at a given instant. */
    data class ActiveRules(
        val internetBlockedPackages: Set<String>,
        val internetBlockAll: Boolean,
        val launchBlockedPackages: Set<String>,
        val launchBlockAll: Boolean,
    ) {
        val hasInternetBlock: Boolean get() = internetBlockAll || internetBlockedPackages.isNotEmpty()
        val hasLaunchBlock: Boolean get() = launchBlockAll || launchBlockedPackages.isNotEmpty()
    }

    /** Prototype day index (0=Mon) → [Calendar.DAY_OF_WEEK] (1=Sun). */
    fun calendarDayOfWeek(dayIndex: Int): Int = ((dayIndex + 1) % 7) + 1

    /** True when [dayIndex] (0=Mon..6=Sun) is part of the rule's repeat set. */
    fun matchesDay(rule: ScheduleBlock, dayIndex: Int): Boolean =
        dayIndex in 0..6 && dayIndex in rule.days

    /** True when [minuteOfDay] falls inside the rule's window (same-day only). */
    fun isActiveAt(rule: ScheduleBlock, dayIndex: Int, minuteOfDay: Int): Boolean {
        if (!rule.enabled) return false
        if (!matchesDay(rule, dayIndex)) return false
        if (rule.startMinute !in 0..1439 || rule.endMinute !in 0..1439) return false
        if (rule.endMinute <= rule.startMinute) return false
        return minuteOfDay in rule.startMinute until rule.endMinute
    }

    /**
     * Evaluate all rules at [nowMillis]. Union semantics: if any active rule
     * targets a package, that package is blocked.
     */
    fun evaluate(
        rules: List<ScheduleBlock>,
        nowMillis: Long = System.currentTimeMillis(),
    ): ActiveRules {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        // Map the current Calendar DOW back to a prototype day index (0=Mon).
        val dayIndex = ((dow - 1 + 6) % 7)
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val internetBlocked = LinkedHashSet<String>()
        val launchBlocked = LinkedHashSet<String>()
        var internetBlockAll = false
        var launchBlockAll = false

        for (rule in rules) {
            if (!isActiveAt(rule, dayIndex, minuteOfDay)) continue
            when (rule.mode) {
                ScheduleMode.INTERNET -> {
                    if (rule.blocksAllApps) internetBlockAll = true else internetBlocked.addAll(rule.appPackages)
                }
                ScheduleMode.LAUNCH -> {
                    if (rule.blocksAllApps) launchBlockAll = true else launchBlocked.addAll(rule.appPackages)
                }
                ScheduleMode.BOTH -> {
                    if (rule.blocksAllApps) {
                        internetBlockAll = true
                        launchBlockAll = true
                    } else {
                        internetBlocked.addAll(rule.appPackages)
                        launchBlocked.addAll(rule.appPackages)
                    }
                }
            }
        }

        return ActiveRules(internetBlocked, internetBlockAll, launchBlocked, launchBlockAll)
    }

    /**
     * Earliest future instant (start or end) of any enabled rule, or
     * [Long.MAX_VALUE] when no boundary exists (no rules).
     */
    fun nextBoundary(
        rules: List<ScheduleBlock>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val enabled = rules.filter { it.enabled && it.days.isNotEmpty() }
        if (enabled.isEmpty()) return Long.MAX_VALUE

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        var earliest = Long.MAX_VALUE

        // dayOffset 0..7 covers every day-of-week combination exactly once.
        for (dayOffset in 0..7) {
            val day = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayIndex = ((day.get(Calendar.DAY_OF_WEEK) - 1 + 6) % 7)
            for (rule in enabled) {
                if (dayIndex !in rule.days) continue
                val startAt = day.timeInMillis + rule.startMinute * 60_000L
                if (startAt > nowMillis && startAt < earliest) earliest = startAt
                val endAt = day.timeInMillis + rule.endMinute * 60_000L
                if (endAt > nowMillis && endAt < earliest) earliest = endAt
            }
            if (earliest != Long.MAX_VALUE) break
        }
        return earliest
    }
}
