package com.safeme.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * Blocking mode of a schedule. Mirrors the prototype's segmented control
 * (Internet | Launch | Both).
 */
enum class ScheduleMode(val label: String) {
    INTERNET("Internet"),
    LAUNCH("Launch"),
    BOTH("Both");

    companion object {
        /** Resilient lookup — unknown/corrupt persisted names fall back to null. */
        fun fromName(name: String?): ScheduleMode? = entries.firstOrNull { it.name == name }
    }
}

/**
 * A recurring app-blocking rule.
 *
 * @param days Day-of-week indices in the prototype's order: 0=Mon … 6=Sun.
 * @param startMinute Minutes from midnight when the window starts (0..1439).
 * @param endMinute Minutes from midnight when the window ends; always greater
 *   than [startMinute] (the UI validates "Start must be before end", matching
 *   the prototype — schedules never wrap past midnight in this design).
 * @param appPackages Packages targeted by this rule. Empty means "no apps
 *   picked" → the schedule blocks everything ([blocksAllApps]).
 */
data class ScheduleBlock(
    val id: String,
    val name: String,
    val days: List<Int>,
    val startMinute: Int,
    val endMinute: Int,
    val mode: ScheduleMode,
    val appPackages: List<String>,
    val enabled: Boolean = true,
) {
    val blocksAllApps: Boolean get() = appPackages.isEmpty()
}

data class SchedulePrefsState(
    val schedules: List<ScheduleBlock> = emptyList(),
    /** User dismissed the "Accessibility Service required" banner. */
    val a11yWarningDismissed: Boolean = false,
)

private val Context.scheduleDataStore by preferencesDataStore(name = "schedule_prefs")

val KEY_SCHEDULES_JSON = stringPreferencesKey("schedules_json")
val KEY_A11Y_WARN_DISMISSED = booleanPreferencesKey("a11y_warn_dismissed")

/** Prototype day order: Mon … Sun. */
val SCHEDULE_DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun Context.schedulePrefs(): Flow<SchedulePrefsState> =
    scheduleDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            SchedulePrefsState(
                schedules = schedulesFromJson(prefs[KEY_SCHEDULES_JSON]),
                a11yWarningDismissed = prefs[KEY_A11Y_WARN_DISMISSED] ?: false,
            )
        }

/** Persist the "Accessibility Service required" banner dismissal. */
suspend fun Context.setA11yWarningDismissed(dismissed: Boolean) {
    scheduleDataStore.edit { prefs ->
        prefs[KEY_A11Y_WARN_DISMISSED] = dismissed
    }
}

fun schedulesToJson(list: List<ScheduleBlock>): String {
    val arr = JSONArray()
    list.forEach { s ->
        val o = JSONObject()
        o.put("id", s.id)
        o.put("n", s.name)
        o.put("d", JSONArray(s.days))
        o.put("st", s.startMinute)
        o.put("en", s.endMinute)
        o.put("m", s.mode.name)
        o.put("a", JSONArray(s.appPackages))
        o.put("e", s.enabled)
        arr.put(o)
    }
    return arr.toString()
}

fun schedulesFromJson(json: String?): List<ScheduleBlock> {
    val text = json ?: ""
    if (text.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(text)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val name = o.optString("n").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                val days = buildList {
                    val d = o.optJSONArray("d")
                    if (d != null) {
                        for (j in 0 until d.length()) {
                            val v = d.optInt(j, -1)
                            if (v in 0..6 && v !in this) add(v)
                        }
                    }
                }
                if (days.isEmpty()) continue
                val start = o.optInt("st", -1)
                val end = o.optInt("en", -1)
                if (start !in 0..1439 || end !in 0..1439 || end <= start) continue
                val mode = ScheduleMode.fromName(o.optString("m")) ?: continue
                val apps = buildList {
                    val a = o.optJSONArray("a")
                    if (a != null) {
                        for (j in 0 until a.length()) {
                            val p = a.optString(j).trim()
                            if (p.isNotEmpty() && p !in this) add(p)
                        }
                    }
                }
                add(
                    ScheduleBlock(
                        id = id,
                        name = name,
                        days = days,
                        startMinute = start,
                        endMinute = end,
                        mode = mode,
                        appPackages = apps,
                        enabled = o.optBoolean("e", true),
                    )
                )
            }
        }
    } catch (e: JSONException) {
        emptyList()
    }
}

suspend fun Context.addSchedule(schedule: ScheduleBlock) {
    scheduleDataStore.edit { prefs ->
        val list = schedulesFromJson(prefs[KEY_SCHEDULES_JSON])
        if (list.none { it.id == schedule.id }) {
            prefs[KEY_SCHEDULES_JSON] = schedulesToJson(list + schedule)
        }
    }
}

suspend fun Context.updateSchedule(schedule: ScheduleBlock) {
    scheduleDataStore.edit { prefs ->
        val list = schedulesFromJson(prefs[KEY_SCHEDULES_JSON])
        val updated = list.map { if (it.id == schedule.id) schedule else it }
        prefs[KEY_SCHEDULES_JSON] = schedulesToJson(updated)
    }
}

suspend fun Context.deleteSchedule(id: String) {
    scheduleDataStore.edit { prefs ->
        val list = schedulesFromJson(prefs[KEY_SCHEDULES_JSON])
        prefs[KEY_SCHEDULES_JSON] = schedulesToJson(list.filter { it.id != id })
    }
}

suspend fun Context.toggleSchedule(id: String, enabled: Boolean) {
    scheduleDataStore.edit { prefs ->
        val list = schedulesFromJson(prefs[KEY_SCHEDULES_JSON])
        val updated = list.map { if (it.id == id) it.copy(enabled = enabled) else it }
        prefs[KEY_SCHEDULES_JSON] = schedulesToJson(updated)
    }
}

/** Factory for new schedule ids — mirrors [addTitleBlockRule] in BlockingPrefs. */
fun newScheduleId(): String = UUID.randomUUID().toString()

/** True when a schedule mode needs SafeMe's accessibility service (launch blocking). */
fun requiresAccessibility(mode: ScheduleMode): Boolean =
    mode == ScheduleMode.LAUNCH || mode == ScheduleMode.BOTH

/**
 * Visibility of the "Accessibility Service required" banner: at least one
 * ENABLED schedule needs launch blocking, the service is off, and the user
 * hasn't dismissed the warning. A paused schedule never nags.
 */
fun shouldShowA11yWarning(
    schedules: List<ScheduleBlock>,
    a11yEnabled: Boolean,
    dismissed: Boolean,
): Boolean {
    if (a11yEnabled || dismissed) return false
    return schedules.any { it.enabled && requiresAccessibility(it.mode) }
}

// ---------------------------------------------------------------- pure helpers

/** Prototype `daysLabel`: "Daily" for all 7 days, else "Mon · Wed · Fri". */
fun scheduleDaysLabel(days: List<Int>): String {
    val normalized = days.distinct().filter { it in 0..6 }
    if (normalized.isEmpty()) return ""
    if (normalized.size == 7) return "Daily"
    return normalized.sorted().joinToString(" · ") { SCHEDULE_DAY_NAMES[it] }
}

/** Prototype `modeTxt`: Both → "Internet + Launch", etc. */
fun scheduleModeLabel(mode: ScheduleMode): String = when (mode) {
    ScheduleMode.BOTH -> "Internet + Launch"
    ScheduleMode.INTERNET -> "Internet blocked"
    ScheduleMode.LAUNCH -> "Launch blocked"
}

/** 24h "HH:mm" formatting (prototype `.sched-time-txt` / hero boundary text). */
fun scheduleTimeLabel(minute: Int): String =
    "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

/** "21:00 – 23:00" card time row (prototype uses an en-dash with spaces). */
fun scheduleWindowLabel(startMinute: Int, endMinute: Int): String =
    "${scheduleTimeLabel(startMinute)} – ${scheduleTimeLabel(endMinute)}"
