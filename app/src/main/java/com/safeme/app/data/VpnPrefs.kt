package com.safeme.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.safeme.app.vpn.DnsPreset
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vpnDataStore by preferencesDataStore(name = "vpn_prefs")

val KEY_VPN_ENABLED = booleanPreferencesKey("vpn_enabled")
val KEY_VPN_PRESET = stringPreferencesKey("vpn_preset")
val KEY_VPN_CUSTOM_V4 = stringPreferencesKey("vpn_custom_v4")
val KEY_VPN_CUSTOM_V6 = stringPreferencesKey("vpn_custom_v6")
val KEY_VPN_WHITELIST = stringSetPreferencesKey("vpn_whitelist")
val KEY_VPN_NOTIF_MODE = stringPreferencesKey("vpn_notif_mode")
val KEY_VPN_NOTIF_CUSTOM = stringPreferencesKey("vpn_notif_custom")
val KEY_PRIVATE_DNS_BACKED_UP = booleanPreferencesKey("private_dns_backed_up")
val KEY_PRIVATE_DNS_PREV_MODE = stringPreferencesKey("private_dns_prev_mode")
val KEY_PRIVATE_DNS_PREV_SPECIFIER = stringPreferencesKey("private_dns_prev_specifier")

/** The user's original Private DNS settings, captured before SafeMe overrides them. */
data class PrivateDnsBackup(val mode: String?, val specifier: String?)

data class DnsVpnSettings(
    val enabled: Boolean = false,
    val preset: DnsPreset = DnsPreset.CLOUDFLARE_FAMILY,
    val customV4: String = "",
    val customV6: String = "",
    val whitelist: Set<String> = emptySet(),
    val notifMode: String = NOTIF_DEFAULT,
    val notifCustom: String = "",
)

const val NOTIF_DEFAULT = "Default"
const val NOTIF_HIDE = "Hide"
const val NOTIF_CUSTOM = "Custom"

/**
 * The only supported notification modes. Persisted values are normalized
 * against this set so a stale/corrupt stored mode can never leave the UI
 * without a selected segment or the service without a known text branch.
 */
val VALID_NOTIF_MODES = setOf(NOTIF_DEFAULT, NOTIF_HIDE, NOTIF_CUSTOM)

fun Context.dnsVpnSettings(): Flow<DnsVpnSettings> =
    vpnDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            DnsVpnSettings(
                enabled = prefs[KEY_VPN_ENABLED] ?: false,
                preset = DnsPreset.fromName(prefs[KEY_VPN_PRESET]) ?: DnsPreset.CLOUDFLARE_FAMILY,
                customV4 = prefs[KEY_VPN_CUSTOM_V4] ?: "",
                customV6 = prefs[KEY_VPN_CUSTOM_V6] ?: "",
                whitelist = prefs[KEY_VPN_WHITELIST] ?: emptySet(),
                notifMode = prefs[KEY_VPN_NOTIF_MODE]?.takeIf { it in VALID_NOTIF_MODES } ?: NOTIF_DEFAULT,
                notifCustom = prefs[KEY_VPN_NOTIF_CUSTOM] ?: "",
            )
        }

suspend fun Context.setVpnEnabled(enabled: Boolean) {
    vpnDataStore.edit { it[KEY_VPN_ENABLED] = enabled }
}

suspend fun Context.setVpnPreset(preset: DnsPreset) {
    vpnDataStore.edit { it[KEY_VPN_PRESET] = preset.name }
}

suspend fun Context.setVpnCustomDns(v4: String, v6: String) {
    vpnDataStore.edit {
        it[KEY_VPN_CUSTOM_V4] = v4
        it[KEY_VPN_CUSTOM_V6] = v6
    }
}

suspend fun Context.setVpnWhitelist(whitelist: Set<String>) {
    vpnDataStore.edit { it[KEY_VPN_WHITELIST] = whitelist }
}

suspend fun Context.setVpnNotifMode(mode: String) {
    vpnDataStore.edit { it[KEY_VPN_NOTIF_MODE] = mode.takeIf { m -> m in VALID_NOTIF_MODES } ?: NOTIF_DEFAULT }
}

suspend fun Context.setVpnNotifCustom(message: String) {
    vpnDataStore.edit { it[KEY_VPN_NOTIF_CUSTOM] = message }
}

/** Replaces all VPN/DNS settings in one atomic edit (backup restore). */
suspend fun Context.writeVpnSettings(settings: DnsVpnSettings) {
    vpnDataStore.edit { prefs ->
        prefs[KEY_VPN_ENABLED] = settings.enabled
        prefs[KEY_VPN_PRESET] = settings.preset.name
        prefs[KEY_VPN_CUSTOM_V4] = settings.customV4
        prefs[KEY_VPN_CUSTOM_V6] = settings.customV6
        prefs[KEY_VPN_WHITELIST] = settings.whitelist
        prefs[KEY_VPN_NOTIF_MODE] = settings.notifMode.takeIf { m -> m in VALID_NOTIF_MODES } ?: NOTIF_DEFAULT
        prefs[KEY_VPN_NOTIF_CUSTOM] = settings.notifCustom
    }
}

/** Reads the backed-up Private DNS settings, or null when nothing was backed up. */
suspend fun readPrivateDnsBackup(context: Context): PrivateDnsBackup? =
    context.vpnDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            if (prefs[KEY_PRIVATE_DNS_BACKED_UP] != true) {
                null
            } else {
                PrivateDnsBackup(
                    mode = prefs[KEY_PRIVATE_DNS_PREV_MODE],
                    specifier = prefs[KEY_PRIVATE_DNS_PREV_SPECIFIER],
                )
            }
        }
        .first()

/** Stores the user's original Private DNS settings (mode/specifier may be null = unset). */
suspend fun savePrivateDnsBackup(context: Context, mode: String?, specifier: String?) {
    context.vpnDataStore.edit { prefs ->
        prefs[KEY_PRIVATE_DNS_BACKED_UP] = true
        if (mode != null) prefs[KEY_PRIVATE_DNS_PREV_MODE] = mode else prefs.remove(KEY_PRIVATE_DNS_PREV_MODE)
        if (specifier != null) {
            prefs[KEY_PRIVATE_DNS_PREV_SPECIFIER] = specifier
        } else {
            prefs.remove(KEY_PRIVATE_DNS_PREV_SPECIFIER)
        }
    }
}

/** Drops the backup so a later [readPrivateDnsBackup] reports "nothing to restore". */
suspend fun clearPrivateDnsBackup(context: Context) {
    context.vpnDataStore.edit { prefs ->
        prefs.remove(KEY_PRIVATE_DNS_BACKED_UP)
        prefs.remove(KEY_PRIVATE_DNS_PREV_MODE)
        prefs.remove(KEY_PRIVATE_DNS_PREV_SPECIFIER)
    }
}
