package com.safeme.app.protect

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.safeme.app.service.SafeMeAccessibilityService
import java.util.concurrent.Executors

/**
 * Cached copy of the protection prefs, fed by a DataStore collector in
 * [com.safeme.app.SafeMeApp] (and by the ViewModel / boot receiver). The
 * guard and heal paths read this instead of blocking on DataStore.
 */
object A11yProtectionStateHolder {
    @Volatile
    var protectionEnabled: Boolean = false

    @Volatile
    var protectedComponents: Set<String> = emptySet()
}

/** One row in the protected/picker service list. */
data class ProtectedServiceEntry(
    val flatComponent: String,
    val appLabel: String,
    val serviceClass: String,
    val isOurs: Boolean,
    val icon: Drawable?,
    val enabledNow: Boolean,
)

/**
 * Accessibility Service protection engine.
 *
 * ## What Android allows
 *
 * Reading the enabled list / master switch is always allowed. WRITING
 * `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` / `ACCESSIBILITY_ENABLED`
 * requires the `WRITE_SECURE_SETTINGS` permission (signature|privileged|
 * development) — grantable only via `adb shell pm grant`. Every write path
 * here is a no-op without it.
 *
 * ## Add-only discipline
 *
 * [selfHealAll] is strictly ADDITIVE: it appends the own service + selected
 * services and never removes any entry, so services the user did not select
 * are never affected. It only writes when the value would actually change
 * (no observer-churn loop).
 */
object A11yProtectionUtils {

    const val TAG = "SafeMeA11yProtect"

    private const val KEY_ENABLED_SERVICES = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"

    /** Serialized background executor for all heal work (survives scopes). */
    private val healExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SafeMeA11yHeal").apply { isDaemon = true }
    }

    /** Throttle for churning a listed-but-unbound entry to force a rebind. */
    private const val REBIND_THROTTLE_MS = 5 * 60 * 1000L
    private val lastRebindMs = HashMap<String, Long>()

    fun ownComponentFlat(context: Context): String =
        ComponentName(context, SafeMeAccessibilityService::class.java).flattenToString()

    fun isWriteSecureSettingsGranted(context: Context): Boolean = try {
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            "android.permission.WRITE_SECURE_SETTINGS"
        ) == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Log.w(TAG, "permission check failed", t)
        false
    }

    /**
     * Pure parser replicating [ComponentName.unflattenFromString] semantics:
     * `pkg/.Svc` (short form) resolves to package `pkg`, class `pkg.Svc`.
     * Returns `(package, fullClass)` or null for malformed input. Kept free
     * of framework calls so it is unit-testable on the JVM.
     */
    fun parseFlatComponent(flat: String): Pair<String, String>? {
        if (flat.isBlank()) return null
        val sep = flat.indexOf('/')
        if (sep < 0 || sep + 1 >= flat.length) return null
        val pkg = flat.substring(0, sep)
        var cls = flat.substring(sep + 1)
        if (cls.startsWith('.')) cls = pkg + cls
        return pkg to cls
    }

    /**
     * Structural equality for flat component strings. Android/OEMs may store
     * either `pkg/pkg.Svc` or the short form `pkg/.Svc`; compare structurally
     * via [parseFlatComponent] and fall back to exact equality.
     */
    fun componentEntriesMatch(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a == b) return true
        val pa = parseFlatComponent(a) ?: return false
        val pb = parseFlatComponent(b) ?: return false
        return pa == pb
    }

    fun getEnabledServicesSet(context: Context): Set<String> = try {
        val raw = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            KEY_ENABLED_SERVICES
        ) ?: return emptySet()
        if (raw.isBlank()) emptySet()
        else raw.split(':').filter { it.isNotBlank() }.toCollection(LinkedHashSet())
    } catch (t: Throwable) {
        Log.w(TAG, "read enabled list failed", t)
        emptySet()
    }

    /** Master accessibility switch; defaults to enabled when the key is absent. */
    fun isMasterEnabled(context: Context): Boolean = try {
        Settings.Secure.getInt(
            context.applicationContext.contentResolver,
            KEY_ACCESSIBILITY_ENABLED,
            1
        ) == 1
    } catch (t: Throwable) {
        Log.w(TAG, "read master switch failed", t)
        true
    }

    fun isServiceEnabled(context: Context, flat: String): Boolean {
        if (flat.isBlank()) return false
        return getEnabledServicesSet(context).any { componentEntriesMatch(it, flat) }
    }

    /** Entry present AND master switch on — i.e. actually functional. */
    fun isServiceEffectivelyEnabled(context: Context, flat: String): Boolean =
        isServiceEnabled(context, flat) && isMasterEnabled(context)

    /** Actually bound right now (catches OEM kills that leave the list intact). */
    fun isServiceActuallyBound(context: Context, flat: String): Boolean = try {
        if (flat.isBlank()) return false
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            val si = info.resolveInfo?.serviceInfo ?: return@any false
            componentEntriesMatch(
                ComponentName(si.packageName, si.name).flattenToString(),
                flat
            )
        }
    } catch (t: Throwable) {
        Log.w(TAG, "bound check failed", t)
        false
    }

    /**
     * Pure canonical ADD-ONLY rewrite. Returns the new entry list (deduped by
     * component identity, existing order preserved, nothing removed, [append]
     * added once each) or null when nothing would change.
     */
    fun canonicalAppendOnly(current: List<String>, append: List<String>): List<String>? {
        val out = LinkedHashSet<String>()
        var changed = false
        for (entry in current) {
            if (entry.isBlank()) {
                // Dropping a blank entry is itself a change.
                changed = true
                continue
            }
            if (out.any { componentEntriesMatch(it, entry) }) {
                changed = true
            } else {
                out.add(entry)
            }
        }
        for (add in append) {
            if (add.isBlank()) continue
            if (out.any { componentEntriesMatch(it, add) }) continue
            out.add(add)
            changed = true
        }
        return if (changed) out.toList() else null
    }

    /** Own service + selected components that are still installed. */
    fun protectedTargets(context: Context): List<String> {
        val own = ownComponentFlat(context)
        val selected = A11yProtectionStateHolder.protectedComponents
        val pm = context.packageManager
        return buildList {
            add(own)
            for (flat in selected) {
                if (flat == own || flat.isBlank()) continue
                val parsed = parseFlatComponent(flat) ?: continue
                if (runCatching { pm.getApplicationInfo(parsed.first, 0) }.isFailure) continue
                add(flat)
            }
        }
    }

    /**
     * Canonical add-only self-heal: append own + selected entries, repair the
     * master switch, only when the toggle is on. Strictly never removes other
     * entries. No-op (returns true) when protection is off.
     */
    @Synchronized
    fun selfHealAll(context: Context): Boolean {
        if (!A11yProtectionStateHolder.protectionEnabled) return true
        val appCtx = context.applicationContext
        val current = getEnabledServicesSet(appCtx).toList()
        val next = canonicalAppendOnly(current, protectedTargets(appCtx))
        val master = isMasterEnabled(appCtx)
        if (next == null && master) return true

        if (!isWriteSecureSettingsGranted(appCtx)) return false
        val cr = appCtx.contentResolver
        var listOk = true
        if (next != null) {
            listOk = try {
                Settings.Secure.putString(cr, KEY_ENABLED_SERVICES, next.joinToString(":"))
            } catch (t: Throwable) {
                Log.w(TAG, "list write failed", t)
                false
            }
        }
        var masterOk = true
        if (!master && listOk) {
            masterOk = try {
                Settings.Secure.putInt(cr, KEY_ACCESSIBILITY_ENABLED, 1)
            } catch (t: Throwable) {
                Log.w(TAG, "master write failed", t)
                false
            }
        }
        return listOk && masterOk
    }

    /**
     * A protected service can be LISTED but UNBOUND (OEM battery killers).
     * Churn its entry (remove + re-add) to force the system to rebind it.
     * Throttled per component to avoid write loops. No-op when off.
     */
    @Synchronized
    fun rebindIfListedButUnbound(context: Context): Boolean {
        if (!A11yProtectionStateHolder.protectionEnabled) return true
        val appCtx = context.applicationContext
        val current = getEnabledServicesSet(appCtx)
        val now = System.currentTimeMillis()
        var wrote = true
        for (flat in protectedTargets(appCtx)) {
            if (!current.any { componentEntriesMatch(it, flat) }) continue
            if (isServiceActuallyBound(appCtx, flat)) continue
            if (now - (lastRebindMs[flat] ?: 0L) < REBIND_THROTTLE_MS) continue
            if (!isWriteSecureSettingsGranted(appCtx)) return false
            val next = canonicalAppendOnly(current.filterNot { componentEntriesMatch(it, flat) }, listOf(flat))
                ?: continue
            try {
                Settings.Secure.putString(
                    appCtx.contentResolver,
                    KEY_ENABLED_SERVICES,
                    next.joinToString(":")
                )
                lastRebindMs[flat] = now
                Log.i(TAG, "rebound unbound protected service $flat")
            } catch (t: Throwable) {
                Log.w(TAG, "rebind write failed", t)
                wrote = false
            }
        }
        return wrote
    }

    /** Serialized background heal (list + rebind). Safe from any scope. */
    fun selfHealAllAsync(context: Context) {
        healExecutor.execute {
            try {
                selfHealAll(context)
                rebindIfListedButUnbound(context)
            } catch (t: Throwable) {
                Log.w(TAG, "selfHealAllAsync failed", t)
            }
        }
    }

    /** Enumerate every installed accessibility service (own first, then alpha). */
    fun listAllAccessibilityServices(context: Context): List<ProtectedServiceEntry> {
        val pm = context.packageManager
        val ownPkg = context.packageName
        val enabledNow = getEnabledServicesSet(context)
        val master = isMasterEnabled(context)
        val out = ArrayList<ProtectedServiceEntry>()
        try {
            val installed = pm.getInstalledPackages(PackageManager.GET_SERVICES)
            for (pkg in installed) {
                val services = pkg.services ?: continue
                for (svc in services) {
                    if (svc.permission != "android.permission.BIND_ACCESSIBILITY_SERVICE") continue
                    val flat = ComponentName(pkg.packageName, svc.name).flattenToString()
                    val appInfo = pkg.applicationInfo
                    val label = try {
                        if (appInfo != null) pm.getApplicationLabel(appInfo).toString() else pkg.packageName
                    } catch (_: Throwable) {
                        pkg.packageName
                    }
                    val icon = try {
                        if (appInfo != null) pm.getApplicationIcon(appInfo) else null
                    } catch (_: Throwable) {
                        null
                    }
                    out.add(
                        ProtectedServiceEntry(
                            flatComponent = flat,
                            appLabel = label,
                            serviceClass = svc.name,
                            isOurs = ownPkg == pkg.packageName,
                            icon = icon,
                            enabledNow = master && enabledNow.any { componentEntriesMatch(it, flat) },
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "service enumeration failed", t)
        }
        out.sortWith(compareByDescending<ProtectedServiceEntry> { it.isOurs }.thenBy { it.appLabel.lowercase() })
        return out
    }
}
