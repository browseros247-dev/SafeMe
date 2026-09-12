package com.safeme.app.ui.util

import android.app.AlarmManager
import android.content.Context
import android.os.Build

/**
 * True when the app may schedule exact alarms: always true below API 31
 * (the permission model doesn't exist there), otherwise whatever the user
 * last chose in Settings → "Alarms & reminders".
 *
 * Thin Android wrapper (mirrors [isAccessibilityEnabled]): the banner
 * *decision* is the unit-tested pure `shouldShowExactAlarmWarning`.
 */
fun isExactAlarmGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return am.canScheduleExactAlarms()
}
