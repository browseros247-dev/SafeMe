package com.safeme.app.ui.util

import android.content.Context
import com.safeme.app.protect.A11yProtectionUtils

/**
 * True when SafeMe's Accessibility Service is actually available: it is
 * bound right now (running), or it is listed as enabled with the master
 * switch on.
 *
 * Delegates to the protection engine's structural component comparison:
 * Android/OEMs persist `ENABLED_ACCESSIBILITY_SERVICES` in either the long
 * form (`pkg/pkg.Svc`) or the short form (`pkg/.Svc`), and an exact string
 * match against one form reads the other as disabled. That mismatch surfaces
 * as a spurious Home banner when Self-Healing rewrites the list in the other
 * form while the service is actually running.
 */
fun isAccessibilityEnabled(context: Context): Boolean {
    val own = A11yProtectionUtils.ownComponentFlat(context)
    return A11yProtectionUtils.isServiceActuallyBound(context, own) ||
        A11yProtectionUtils.isServiceEffectivelyEnabled(context, own)
}
