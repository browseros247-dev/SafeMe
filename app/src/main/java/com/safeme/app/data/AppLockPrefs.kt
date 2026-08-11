package com.safeme.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Lock method. OFF means App Lock is disabled. */
enum class LockType(val storage: String) {
    OFF("off"),
    PIN("pin"),
    PASSWORD("password"),
    PATTERN("pattern");

    companion object {
        fun fromStorage(value: String?): LockType =
            entries.firstOrNull { it.storage == value } ?: OFF
    }
}

/** Auto-lock delay applied when the app leaves the foreground. */
enum class AutoLockDelay(val storage: String, val millis: Long?) {
    IMMEDIATELY("immediately", 0L),
    AFTER_15S("after_15s", 15_000L),
    AFTER_30S("after_30s", 30_000L),
    AFTER_1M("after_1m", 60_000L),
    AFTER_5M("after_5m", 300_000L),
    /** Manual locking only ("Lock now"). */
    OFF("off", null);

    companion object {
        fun fromStorage(value: String?): AutoLockDelay =
            entries.firstOrNull { it.storage == value } ?: IMMEDIATELY
    }
}

/**
 * Persistent App Lock state. [storedHash] holds the PBKDF2 credential as
 * `"<saltHex>:<hashHex>"` — a one-way hash, never the raw credential.
 * [biometricEnabled] / [forgotPasswordDisabled] only matter while a lock is set.
 */
data class AppLockPrefsState(
    val lockType: LockType = LockType.OFF,
    val storedHash: String = "",
    /** Credential length (PIN digits / pattern dots / password chars). The
     *  hash cannot reveal it; the lock UI needs it to render dots and know
     *  when to auto-submit. Leaks only the length, which the prototype's dot
     *  row already shows. */
    val credentialLength: Int = 0,
    val biometricEnabled: Boolean = false,
    val forgotPasswordDisabled: Boolean = false,
    val autoLock: AutoLockDelay = AutoLockDelay.IMMEDIATELY,
)

private val Context.appLockDataStore by preferencesDataStore(name = "safeme_applock_prefs")

val KEY_APP_LOCK_TYPE = stringPreferencesKey("lock_type")
val KEY_APP_LOCK_HASH = stringPreferencesKey("stored_hash")
val KEY_APP_LOCK_LENGTH = intPreferencesKey("lock_length")
val KEY_APP_LOCK_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
val KEY_APP_LOCK_FORGOT = booleanPreferencesKey("forgot_disabled")
val KEY_APP_LOCK_AUTO = stringPreferencesKey("auto_lock")

fun Context.appLockPrefs(): Flow<AppLockPrefsState> =
    appLockDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppLockPrefsState(
                lockType = LockType.fromStorage(prefs[KEY_APP_LOCK_TYPE]),
                storedHash = prefs[KEY_APP_LOCK_HASH] ?: "",
                credentialLength = prefs[KEY_APP_LOCK_LENGTH] ?: 0,
                biometricEnabled = prefs[KEY_APP_LOCK_BIOMETRIC] ?: false,
                forgotPasswordDisabled = prefs[KEY_APP_LOCK_FORGOT] ?: false,
                autoLock = AutoLockDelay.fromStorage(prefs[KEY_APP_LOCK_AUTO]),
            )
        }

/** Persist the lock method + credential hash in one atomic edit. */
suspend fun Context.setAppLock(type: LockType, storedHash: String, credentialLength: Int) {
    appLockDataStore.edit { prefs ->
        prefs[KEY_APP_LOCK_TYPE] = type.storage
        prefs[KEY_APP_LOCK_HASH] = storedHash
        prefs[KEY_APP_LOCK_LENGTH] = credentialLength
    }
}

suspend fun Context.setAppLockBiometric(enabled: Boolean) {
    appLockDataStore.edit { prefs ->
        prefs[KEY_APP_LOCK_BIOMETRIC] = enabled
    }
}

suspend fun Context.setAppLockForgotDisabled(disabled: Boolean) {
    appLockDataStore.edit { prefs ->
        prefs[KEY_APP_LOCK_FORGOT] = disabled
    }
}

suspend fun Context.setAppLockAutoLock(delay: AutoLockDelay) {
    appLockDataStore.edit { prefs ->
        prefs[KEY_APP_LOCK_AUTO] = delay.storage
    }
}

/** Replaces all App Lock settings in one atomic edit (backup restore). */
suspend fun Context.writeAppLockPrefs(state: AppLockPrefsState) {
    appLockDataStore.edit { prefs ->
        prefs[KEY_APP_LOCK_TYPE] = state.lockType.storage
        prefs[KEY_APP_LOCK_HASH] = state.storedHash
        prefs[KEY_APP_LOCK_LENGTH] = state.credentialLength
        prefs[KEY_APP_LOCK_BIOMETRIC] = state.biometricEnabled
        prefs[KEY_APP_LOCK_FORGOT] = state.forgotPasswordDisabled
        prefs[KEY_APP_LOCK_AUTO] = state.autoLock.storage
    }
}
