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

/** Default dwell countdown (seconds) before the block gate's Close unlocks. */
const val BLOCK_SCREEN_DEFAULT_DWELL = 5

/** Valid dwell range — mirrors the UI stepper bounds and the prototype (3–120s). */
const val BLOCK_SCREEN_MIN_DWELL = 3
const val BLOCK_SCREEN_MAX_DWELL = 120

/**
 * Block-gate appearance/policy settings edited on the Block Screen. `message`
 * is empty when the user has never customized it — the UI falls back to the
 * resource default so the data layer stays resource-free. `img` is one of the
 * named motivation-image palettes (`sunset`, `ocean`, …) or "" for none.
 */
data class BlockScreenPrefsState(
    val dwell: Int = BLOCK_SCREEN_DEFAULT_DWELL,
    val message: String = "",
    val img: String = "",
    val redirect: String = "",
    val whyOn: Boolean = true,
)

private val Context.blockScreenDataStore by preferencesDataStore(name = "block_screen_prefs")

val KEY_BLOCK_SCREEN_DWELL = intPreferencesKey("gate_dwell")
val KEY_BLOCK_SCREEN_MESSAGE = stringPreferencesKey("gate_message")
val KEY_BLOCK_SCREEN_IMG = stringPreferencesKey("gate_img")
val KEY_BLOCK_SCREEN_REDIRECT = stringPreferencesKey("gate_redirect")
val KEY_BLOCK_SCREEN_WHY_ON = booleanPreferencesKey("gate_why_on")

fun Context.blockScreenPrefs(): Flow<BlockScreenPrefsState> =
    blockScreenDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            BlockScreenPrefsState(
                // A stale/corrupt stored dwell is clamped so it can never leave
                // the UI showing a countdown outside the stepper's range.
                dwell = (prefs[KEY_BLOCK_SCREEN_DWELL] ?: BLOCK_SCREEN_DEFAULT_DWELL)
                    .coerceIn(BLOCK_SCREEN_MIN_DWELL, BLOCK_SCREEN_MAX_DWELL),
                message = prefs[KEY_BLOCK_SCREEN_MESSAGE] ?: "",
                img = prefs[KEY_BLOCK_SCREEN_IMG] ?: "",
                redirect = prefs[KEY_BLOCK_SCREEN_REDIRECT] ?: "",
                whyOn = prefs[KEY_BLOCK_SCREEN_WHY_ON] ?: true,
            )
        }

/** Replaces all block-screen settings in one atomic edit (Save + backup restore). */
suspend fun Context.writeBlockScreenPrefs(state: BlockScreenPrefsState) {
    blockScreenDataStore.edit { prefs ->
        prefs[KEY_BLOCK_SCREEN_DWELL] = state.dwell.coerceIn(BLOCK_SCREEN_MIN_DWELL, BLOCK_SCREEN_MAX_DWELL)
        prefs[KEY_BLOCK_SCREEN_MESSAGE] = state.message
        prefs[KEY_BLOCK_SCREEN_IMG] = state.img
        prefs[KEY_BLOCK_SCREEN_REDIRECT] = state.redirect
        prefs[KEY_BLOCK_SCREEN_WHY_ON] = state.whyOn
    }
}
