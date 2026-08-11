package com.safeme.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Persistent state for the Accessibility Service protection feature.
 *
 * [protectionEnabled] is the master on/off switch: when off the guard performs
 * NO reads-for-write and NO writes — protection is fully inactive.
 * [protectedComponents] is the set of flat ComponentNames (pkg/svc) the user
 * has selected. SafeMe's own accessibility service is always protected while
 * the toggle is on and is NOT stored here.
 */
data class A11yProtectionPrefsState(
    val protectionEnabled: Boolean = false,
    val protectedComponents: Set<String> = emptySet(),
)

private val Context.a11yProtectionDataStore by preferencesDataStore(name = "a11y_protection_prefs")

val KEY_A11Y_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
val KEY_A11Y_PROTECTED_COMPONENTS = stringSetPreferencesKey("protected_components")

fun Context.a11yProtectionPrefs(): Flow<A11yProtectionPrefsState> =
    a11yProtectionDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            A11yProtectionPrefsState(
                protectionEnabled = prefs[KEY_A11Y_PROTECTION_ENABLED] ?: false,
                protectedComponents = prefs[KEY_A11Y_PROTECTED_COMPONENTS] ?: emptySet(),
            )
        }

suspend fun Context.setA11yProtectionEnabled(enabled: Boolean) {
    a11yProtectionDataStore.edit { prefs ->
        prefs[KEY_A11Y_PROTECTION_ENABLED] = enabled
    }
}

suspend fun Context.addProtectedA11yComponent(flat: String) {
    if (flat.isBlank()) return
    a11yProtectionDataStore.edit { prefs ->
        val current = prefs[KEY_A11Y_PROTECTED_COMPONENTS] ?: emptySet()
        prefs[KEY_A11Y_PROTECTED_COMPONENTS] = current + flat
    }
}

suspend fun Context.removeProtectedA11yComponent(flat: String) {
    if (flat.isBlank()) return
    a11yProtectionDataStore.edit { prefs ->
        val current = prefs[KEY_A11Y_PROTECTED_COMPONENTS] ?: emptySet()
        prefs[KEY_A11Y_PROTECTED_COMPONENTS] = current - flat
    }
}

/** Replaces all Accessibility Protection settings in one atomic edit (backup restore). */
suspend fun Context.writeA11yProtectionPrefs(state: A11yProtectionPrefsState) {
    a11yProtectionDataStore.edit { prefs ->
        prefs[KEY_A11Y_PROTECTION_ENABLED] = state.protectionEnabled
        prefs[KEY_A11Y_PROTECTED_COMPONENTS] = state.protectedComponents
    }
}
