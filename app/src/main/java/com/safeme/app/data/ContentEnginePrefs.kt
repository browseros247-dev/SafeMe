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
 * Content-engine switches (NopoX-parity gates) that live outside the master
 * [BlockingPrefsState.blockingEnabled] so each engine can be armed on its own,
 * mirroring the PreventUninstallPrefs precedent.
 */
data class ContentEnginePrefsState(
    val blockImageVideoSearch: Boolean = false,
)

private val Context.contentEngineDataStore by preferencesDataStore(name = "safeme_content_prefs")

val KEY_BLOCK_IMAGE_VIDEO_SEARCH = booleanPreferencesKey("block_image_video_search")

fun Context.contentEnginePrefs(): Flow<ContentEnginePrefsState> =
    contentEngineDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            ContentEnginePrefsState(
                blockImageVideoSearch = prefs[KEY_BLOCK_IMAGE_VIDEO_SEARCH] ?: false,
            )
        }

suspend fun Context.setBlockImageVideoSearch(enabled: Boolean) {
    contentEngineDataStore.edit { prefs ->
        prefs[KEY_BLOCK_IMAGE_VIDEO_SEARCH] = enabled
    }
}
