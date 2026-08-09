package com.safeme.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Persistent "Prevent Uninstall" (anti-tamper) switch.
 *
 * The user-facing protection is real regardless of this flag (an active Device
 * Admin always replaces Uninstall with Disable), but this flag gates the
 * accessibility-driven page guards (blocking the App-Info / Device-Admin /
 * our-a11y-detail pages) so those blocks only ever fire when the user opted in.
 */
data class PreventUninstallPrefsState(
    val preventUninstallEnabled: Boolean = false,
)

private val Context.preventUninstallDataStore by preferencesDataStore(name = "safeme_pu_prefs")

val KEY_PREVENT_UNINSTALL_ENABLED = booleanPreferencesKey("prevent_uninstall_enabled")

fun Context.preventUninstallPrefs(): Flow<PreventUninstallPrefsState> =
    preventUninstallDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            PreventUninstallPrefsState(
                preventUninstallEnabled = prefs[KEY_PREVENT_UNINSTALL_ENABLED] ?: false,
            )
        }

suspend fun Context.setPreventUninstallEnabled(enabled: Boolean) {
    preventUninstallDataStore.edit { prefs ->
        prefs[KEY_PREVENT_UNINSTALL_ENABLED] = enabled
    }
}
