package com.safeme.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Social Media Blocking — two independent gates:
 *  1) Whole-app launch gate (persistent, pkg in wholeBlocked → block on TYPE_WINDOW_STATE_CHANGED)
 *  2) In-app tab gate (YouTube Shorts / Facebook Reels / Snapchat Spotlight — overlay only tab node)
 *
 * TikTok / Instagram live ONLY in whole-app (never in tab gate by product spec).
 * Stored as packageNames (never display names) so the gate is device-truthful.
 * Display-count dedupes TikTok's multiple pkgs as 1 for pills.
 */
data class SocialBlockingState(
    val enabled: Boolean = true,
    val wholeBlocked: Set<String> = DEFAULT_WHOLE_BLOCKED,
    val youtube: Boolean = true,
    val facebook: Boolean = true,
    val snapchat: Boolean = true,
) {
    /** Tabs gated count (0..3) when enabled else 0. */
    val activeTabs: Int get() = if (!enabled) 0 else listOf(youtube, facebook, snapchat).count { it }

    /** Display launch count — TikTok family deduped to 1. */
    val displayLaunchCount: Int get() = SocialBlockingPrefs.displayCount(wholeBlocked)

    companion object {
        /** Canonical defaults: 5 display apps → 5 pkgs (TikTok counted once). */
        val DEFAULT_WHOLE_BLOCKED: Set<String> = setOf(
            "com.zhiliaoapp.musically", // TikTok
            "com.instagram.android", // Instagram
            "com.twitter.android", // X / Twitter
            "com.reddit.frontpage", // Reddit
            "tv.twitch.android.app", // Twitch
        )
    }
}

private val Context.socialBlockingDataStore by preferencesDataStore(name = "safeme_social_blocking_prefs")

val KEY_SOCIAL_ENABLED = booleanPreferencesKey("social_blocking_enabled")
val KEY_SOCIAL_WHOLE_BLOCKED = stringSetPreferencesKey("social_whole_blocked")
val KEY_SOCIAL_YOUTUBE = booleanPreferencesKey("social_youtube")
val KEY_SOCIAL_FACEBOOK = booleanPreferencesKey("social_facebook")
val KEY_SOCIAL_SNAPCHAT = booleanPreferencesKey("social_snapchat")

object SocialBlockingPrefs {

    /** TikTok package family — all count as display "TikTok" = 1. */
    val TIKTOK_PACKAGES: Set<String> = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.aweme.lite",
    )

    /** Instagram family deduped similarly (lite + main = 1). */
    val INSTAGRAM_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.lite",
    )

    /** Facebook family (main + Lite) — same product, different install footprint. */
    val FACEBOOK_PACKAGES: Set<String> = setOf(
        "com.facebook.katana",
        "com.facebook.lite",
    )

    /** Snapchat family (main + Lite). */
    val SNAPCHAT_PACKAGES: Set<String> = setOf(
        "com.snapchat.android",
        "com.snapchat.android.lite",
    )

    /**
     * All installable variants of the same product family for [pkg] (e.g. TikTok,
     * TikTok Lite, TikTok Go), or null when the package has no family. Enforcement
     * and UI both key off this so a row toggled on covers the variant actually
     * installed on the device.
     */
    fun familyOf(pkg: String): Set<String>? = when {
        pkg in TIKTOK_PACKAGES -> TIKTOK_PACKAGES
        pkg in INSTAGRAM_PACKAGES -> INSTAGRAM_PACKAGES
        pkg in FACEBOOK_PACKAGES -> FACEBOOK_PACKAGES
        pkg in SNAPCHAT_PACKAGES || pkg.startsWith("com.snapchat") -> SNAPCHAT_PACKAGES
        else -> null
    }

    /** True when [pkg] or any sibling of its family is present in [blocked]. */
    fun isFamilyBlocked(pkg: String, blocked: Set<String>): Boolean =
        blocked.contains(pkg) || familyOf(pkg)?.any { it in blocked } == true

    /**
     * Pure toggle core: any family member present -> remove the whole family;
     * none present -> add the whole family. Keeps the stored set family-consistent
     * so gate/UI never disagree about a row's state.
     */
    fun toggleFamilyInSet(current: Set<String>, family: Set<String>): Set<String> =
        if (family.isNotEmpty() && family.any { it in current }) current - family
        else current + family

    private fun displayKeyFor(pkg: String): String = when (pkg) {
        in TIKTOK_PACKAGES -> "TikTok"
        in INSTAGRAM_PACKAGES -> "Instagram"
        else -> pkg
    }

    /** Deduped display count: TikTok/Instagram families collapse to 1 each. */
    fun displayCount(pkgs: Set<String>): Int = pkgs.map { displayKeyFor(it) }.toSet().size

    /** True when the set already blocks the logical TikTok app (any TikTok pkg). */
    fun containsTikTok(pkgs: Set<String>): Boolean = pkgs.any { it in TIKTOK_PACKAGES }

    /** True when the set already blocks logical Instagram (any Instagram pkg). */
    fun containsInstagram(pkgs: Set<String>): Boolean = pkgs.any { it in INSTAGRAM_PACKAGES }
}

fun Context.socialBlockingPrefs(): Flow<SocialBlockingState> =
    socialBlockingDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            SocialBlockingState(
                enabled = prefs[KEY_SOCIAL_ENABLED] ?: true,
                wholeBlocked = prefs[KEY_SOCIAL_WHOLE_BLOCKED] ?: SocialBlockingState.DEFAULT_WHOLE_BLOCKED,
                youtube = prefs[KEY_SOCIAL_YOUTUBE] ?: true,
                facebook = prefs[KEY_SOCIAL_FACEBOOK] ?: true,
                snapchat = prefs[KEY_SOCIAL_SNAPCHAT] ?: true,
            )
        }

suspend fun Context.setSocialBlockingEnabled(enabled: Boolean) {
    socialBlockingDataStore.edit { it[KEY_SOCIAL_ENABLED] = enabled }
}

suspend fun Context.setSocialWholeBlocked(pkgs: Set<String>) {
    val cleaned = pkgs.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    socialBlockingDataStore.edit { it[KEY_SOCIAL_WHOLE_BLOCKED] = cleaned }
}

/**
 * Atomic toggles: the read-modify-write happens inside DataStore's serialized
 * transaction, so rapid consecutive taps can never be swallowed by a stale
 * in-memory state read. Each returns the resulting value for UI feedback.
 */
suspend fun Context.toggleSocialEnabled(): Boolean {
    var result = false
    socialBlockingDataStore.edit { prefs ->
        result = !(prefs[KEY_SOCIAL_ENABLED] ?: true)
        prefs[KEY_SOCIAL_ENABLED] = result
    }
    return result
}

suspend fun Context.toggleSocialWholeBlocked(family: Set<String>): Boolean {
    var blocked = false
    socialBlockingDataStore.edit { prefs ->
        val current = prefs[KEY_SOCIAL_WHOLE_BLOCKED] ?: SocialBlockingState.DEFAULT_WHOLE_BLOCKED
        val next = SocialBlockingPrefs.toggleFamilyInSet(current, family)
        prefs[KEY_SOCIAL_WHOLE_BLOCKED] = next
        blocked = next.containsAll(family)
    }
    return blocked
}

suspend fun Context.toggleSocialVertical(key: Preferences.Key<Boolean>): Boolean {
    var on = false
    socialBlockingDataStore.edit { prefs ->
        on = !(prefs[key] ?: false)
        prefs[key] = on
    }
    return on
}

suspend fun Context.setSocialYoutube(enabled: Boolean) {
    socialBlockingDataStore.edit { it[KEY_SOCIAL_YOUTUBE] = enabled }
}

suspend fun Context.setSocialFacebook(enabled: Boolean) {
    socialBlockingDataStore.edit { it[KEY_SOCIAL_FACEBOOK] = enabled }
}

suspend fun Context.setSocialSnapchat(enabled: Boolean) {
    socialBlockingDataStore.edit { it[KEY_SOCIAL_SNAPCHAT] = enabled }
}

suspend fun Context.applySocialPreset(preset: String) {
    socialBlockingDataStore.edit { prefs ->
        when (preset) {
            "deep" -> {
                prefs[KEY_SOCIAL_ENABLED] = true
                prefs[KEY_SOCIAL_WHOLE_BLOCKED] = SocialBlockingState.DEFAULT_WHOLE_BLOCKED
                prefs[KEY_SOCIAL_YOUTUBE] = true
                prefs[KEY_SOCIAL_FACEBOOK] = true
                prefs[KEY_SOCIAL_SNAPCHAT] = true
            }
            "balanced" -> {
                prefs[KEY_SOCIAL_ENABLED] = true
                prefs[KEY_SOCIAL_WHOLE_BLOCKED] = emptySet()
                prefs[KEY_SOCIAL_YOUTUBE] = true
                prefs[KEY_SOCIAL_FACEBOOK] = true
                prefs[KEY_SOCIAL_SNAPCHAT] = true
            }
            "relax" -> {
                prefs[KEY_SOCIAL_ENABLED] = false
                // keep wholeBlocked + tabs as-is (dims elsewhere) — only master flips. If user had nothing, keep cleared.
                // To match prototype's relax semantics (all paused + clear whole), we clear whole but keep tabs true for next enable.
                // Prototype relax: enabled=false (preserves? but UI shows 0 launch). We clear whole for visual zero.
                // Keep tabs true so re-enabling deep doesn't lose state? Prototype clears whole only. Mirror that.
                prefs[KEY_SOCIAL_WHOLE_BLOCKED] = emptySet()
                // tabs stay true — next balanced/deep re-enables them.
            }
        }
    }
}

/**
 * Atomic restore for BackupManager — replaces entire Social Blocking state in one edit
 * so backup restore is transactional and never leaves a half-written mix.
 */
suspend fun Context.writeSocialBlockingPrefs(state: SocialBlockingState) {
    socialBlockingDataStore.edit { prefs ->
        prefs[KEY_SOCIAL_ENABLED] = state.enabled
        prefs[KEY_SOCIAL_WHOLE_BLOCKED] = state.wholeBlocked.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        prefs[KEY_SOCIAL_YOUTUBE] = state.youtube
        prefs[KEY_SOCIAL_FACEBOOK] = state.facebook
        prefs[KEY_SOCIAL_SNAPCHAT] = state.snapchat
    }
}
