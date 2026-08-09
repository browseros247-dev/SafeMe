package com.safeme.app.protect

import java.util.Locale

/**
 * Pure, unit-testable rules that identify Android Settings screens relevant to
 * the Prevent Uninstall (anti-tamper) feature. Ported from the reference
 * implementation and scoped to SafeMe: every check returns false for other
 * apps' pages and for unrelated system screens.
 *
 * ## The a11y kill vector (critical constraint)
 *
 * Android actively protects its accessibility-management screens against
 * obscuring: when an accessibility service draws a normal blocking window over
 * the a11y service detail/enable page, the system DISABLES that service
 * automatically (anti-tapjacking / consent-integrity protection) within ~1–5 s.
 *
 * SafeMe's [com.safeme.app.BlockGateActivity] therefore must NEVER be raised
 * over an accessibility-management screen. Pages detected by
 * [isAccessibilityManagementScreen] are skipped (self-heal territory), never
 * covered. Our own a11y detail page is evicted via HOME + toast, never blocked.
 */
object ProtectedSystemPages {

    /**
     * Settings packages (AOSP + OEM variants).
     * [M2 fix] Removed the overly-broad `packageName.contains(".settings")`
     * substring fallback — third-party apps with ".settings" in their package
     * name would trigger unnecessary PU guard processing. Only explicit OEM
     * packages are matched.
     */
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.miui.securitycenter",
        "com.android.settings.miui",
        "com.samsung.android.settings",
        "com.huawei.systemmanager",
        "com.coloros.safecenter",
        "com.oppo.safe",
    )

    /**
     * Class-name markers for screens that let the user manage accessibility
     * services (list, service detail/toggle, a11y settings). AOSP class names
     * embed "accessibility" (Settings$AccessibilitySettingsActivity,
     * accessibility.ToggleAccessibilityServicePreferenceFragment, etc.);
     * Samsung/MIUI/Huawei/OPPO follow the same convention.
     */
    private val A11Y_MANAGE_CLASS_MARKERS = listOf(
        "accessibility",
        "installedservices",
        "servicedetails",
    )

    /** True for AOSP or OEM settings-app packages. */
    fun isSettingsPackage(packageName: String): Boolean {
        return packageName in SETTINGS_PACKAGES
    }

    /**
     * True when [packageName]/[className] identify a screen where accessibility
     * services are listed or toggled. These must NEVER be covered by a block UI
     * — the OS disables the covering service.
     */
    fun isAccessibilityManagementScreen(packageName: String, className: String): Boolean {
        if (className.isBlank()) return false
        if (!isSettingsPackage(packageName)) return false
        val lower = className.lowercase(Locale.ROOT)
        if (lower.contains("uninstalleractivity")) return false
        return A11Y_MANAGE_CLASS_MARKERS.any { lower.contains(it) }
    }

    /** True when normalized page text contains our accessibility description prefix. */
    fun pageTextMatchesOurService(
        pageTextNormalized: String,
        serviceDescriptionNormalized: String,
    ): Boolean {
        if (serviceDescriptionNormalized.length < 8) return false
        val fingerprint = serviceDescriptionNormalized.take(40)
        return pageTextNormalized.contains(fingerprint)
    }

    /** Normalize free-form page/description text: lowercase, spaces stripped. */
    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(" ", "")

    /**
     * A normalized fingerprint that appears ONLY on our accessibility service's
     * DETAIL page, never on the services LIST row (the list shows the summary,
     * a prefix of the description). Returns "" when the inputs can't yield a
     * distinctive marker — the caller then fails open (no blocking).
     */
    fun detailOnlyFingerprint(
        serviceDescriptionNormalized: String,
        serviceSummaryNormalized: String,
    ): String {
        val suffix = if (serviceSummaryNormalized.length >= 8 &&
            serviceDescriptionNormalized.startsWith(serviceSummaryNormalized)
        ) {
            serviceDescriptionNormalized.removePrefix(serviceSummaryNormalized)
        } else {
            serviceDescriptionNormalized.drop(40)
        }
        return suffix.take(40)
    }
}
