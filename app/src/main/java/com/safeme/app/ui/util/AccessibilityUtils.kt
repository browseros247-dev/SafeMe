package com.safeme.app.ui.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.safeme.app.service.SafeMeAccessibilityService

fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED, 0
    ) == 1
    if (!enabled) return false
    val services = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return services
        .split(':')
        .any {
            it == ComponentName(
                context, SafeMeAccessibilityService::class.java
            ).flattenToString()
        }
}
