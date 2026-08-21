package com.safeme.app.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.safeme.app.data.clearPrivateDnsBackup
import com.safeme.app.data.readPrivateDnsBackup
import com.safeme.app.data.savePrivateDnsBackup

/**
 * System-wide DNS filtering via Android's Private DNS (DNS-over-TLS).
 *
 * WHY THIS EXISTS: the VPN tunnel can serve exactly one master at a time.
 * In DNS-filter mode (no routes) it filters every app's DNS but cannot
 * black-hole a schedule-targeted app; in per-app-block mode (routes + allow
 * list) it blocks the targeted apps but everyone else bypasses the VPN
 * entirely and resolves through the router — DNS presets silently stop
 * applying. A 24/7 internet-block schedule therefore starved the preset
 * filtering (the "DNS reset not working" report).
 *
 * Private DNS breaks that deadlock: `private_dns_mode=hostname` +
 * `private_dns_specifier=<family resolver>` sends EVERY app's system
 * resolution through the family DoT resolver, independent of any VPN. The
 * tunnel keeps doing per-app schedule blocking while the filter applies
 * system-wide — and it even keeps filtering when the tunnel is down.
 *
 * Strict mode is fail-closed: if the DoT hostname is unreachable, resolution
 * fails rather than leaking unfiltered. The family resolvers are anycast and
 * highly available, which makes that tradeoff the safe direction for a
 * protection app.
 *
 * Requires WRITE_SECURE_SETTINGS (signature-level; granted once via ADB —
 * the anti-tamper screen already documents that flow). Without the grant the
 * filter degrades to VPN-advertised DNS only.
 *
 * CUSTOM preset is VPN-only: Private DNS demands a hostname with a valid
 * TLS certificate, an arbitrary user-typed resolver IP cannot provide one.
 */
object PrivateDnsFilter {

    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    private const val KEY_MODE = "private_dns_mode"
    private const val KEY_SPECIFIER = "private_dns_specifier"
    private const val MODE_HOSTNAME = "hostname"

    const val CLOUDFLARE_FAMILY_HOSTNAME = "family.cloudflare-dns.com"
    const val ADGUARD_FAMILY_HOSTNAME = "family.adguard-dns.com"

    /**
     * The DoT hostname for a preset, or null when the preset cannot be
     * expressed as Private DNS (custom resolver IPs).
     */
    fun dotHostname(preset: DnsPreset): String? = when (preset) {
        DnsPreset.CLOUDFLARE_FAMILY -> CLOUDFLARE_FAMILY_HOSTNAME
        DnsPreset.ADGUARD_FAMILY -> ADGUARD_FAMILY_HOSTNAME
        DnsPreset.CUSTOM -> null
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Points the system resolver at [preset]'s family DoT hostname. The
     * user's previous Private DNS settings are backed up (once) so
     * [restore] can put them back exactly. No-op for CUSTOM and when the
     * WRITE_SECURE_SETTINGS grant is missing. Returns true when the system
     * settings were written.
     */
    suspend fun apply(context: Context, preset: DnsPreset): Boolean {
        val hostname = dotHostname(preset) ?: return false
        if (!hasPermission(context)) return false
        return try {
            saveBackupOnce(context)
            Settings.Global.putString(context.contentResolver, KEY_MODE, MODE_HOSTNAME)
            Settings.Global.putString(context.contentResolver, KEY_SPECIFIER, hostname)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Puts the user's original Private DNS settings back (a null pair clears
     * both keys, returning the system to default opportunistic mode). Safe to
     * call repeatedly; only the first call after [apply] rewrites anything.
     */
    suspend fun restore(context: Context): Boolean {
        if (!hasPermission(context)) return false
        return try {
            val backup = readPrivateDnsBackup(context) ?: return true
            Settings.Global.putString(context.contentResolver, KEY_MODE, backup.mode)
            Settings.Global.putString(context.contentResolver, KEY_SPECIFIER, backup.specifier)
            clearPrivateDnsBackup(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Snapshot the current Private DNS settings, but only the first time. */
    private suspend fun saveBackupOnce(context: Context) {
        if (readPrivateDnsBackup(context) != null) return
        val resolver = context.contentResolver
        savePrivateDnsBackup(
            context,
            Settings.Global.getString(resolver, KEY_MODE),
            Settings.Global.getString(resolver, KEY_SPECIFIER),
        )
    }
}
