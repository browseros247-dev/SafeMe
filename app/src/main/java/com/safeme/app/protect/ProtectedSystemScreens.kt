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
 * automatically (anti-tapjacking / consent-integrity protection) within ~1–5 s
 * of a PERSISTENT cover.
 *
 * SafeMe's [com.safeme.app.BlockGateActivity] therefore may be raised over OUR
 * OWN a11y detail page — the Block screen must appear first — but must never
 * be left LOOPING over it: [com.safeme.app.service.SafeMeAccessibilityService]
 * bounces the dismissed gate to HOME so the cover is brief and one-shot. Pages
 * detected by [isAccessibilityManagementScreen] other than our own are skipped
 * (self-heal territory), never covered.
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

    /**
     * Package-installer packages that host the uninstall confirmation dialog
     * on stock Android (UninstallerActivity). The PU guards may gate ONLY that
     * dialog surface — install/update screens in these packages stay untouched.
     */
    private val UNINSTALLER_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    /** True for AOSP or OEM settings-app packages. */
    fun isSettingsPackage(packageName: String): Boolean {
        return packageName in SETTINGS_PACKAGES
    }

    /** True for the package-installer packages that show the uninstall dialog. */
    fun isUninstallerPackage(packageName: String): Boolean {
        return packageName in UNINSTALLER_PACKAGES
    }

    /**
     * The full Prevent-Uninstall guard surface: settings-family packages plus
     * the package-installer packages hosting the uninstall confirmation.
     */
    fun isPuSurface(packageName: String): Boolean {
        return isSettingsPackage(packageName) || isUninstallerPackage(packageName)
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

    /** Normalize free-form page/description text: lowercase, spaces stripped. */
    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(" ", "")
}
