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
import kotlinx.coroutines.flow.map

private val Context.vpnDataStore by preferencesDataStore(name = "vpn_prefs")

val KEY_VPN_ENABLED = booleanPreferencesKey("vpn_enabled")
val KEY_VPN_PRESET = stringPreferencesKey("vpn_preset")
val KEY_VPN_CUSTOM_V4 = stringPreferencesKey("vpn_custom_v4")
val KEY_VPN_CUSTOM_V6 = stringPreferencesKey("vpn_custom_v6")
val KEY_VPN_WHITELIST = stringSetPreferencesKey("vpn_whitelist")
val KEY_VPN_NOTIF_MODE = stringPreferencesKey("vpn_notif_mode")
val KEY_VPN_NOTIF_CUSTOM = stringPreferencesKey("vpn_notif_custom")

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
                notifMode = prefs[KEY_VPN_NOTIF_MODE] ?: NOTIF_DEFAULT,
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
    vpnDataStore.edit { it[KEY_VPN_NOTIF_MODE] = mode }
}

suspend fun Context.setVpnNotifCustom(message: String) {
    vpnDataStore.edit { it[KEY_VPN_NOTIF_CUSTOM] = message }
}
