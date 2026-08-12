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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

enum class BlockedCategory(val label: String) {
    ADULT("Adult"),
    GAMBLING("Gambling"),
    SOCIAL_MEDIA("Social media"),
    SHOPPING("Shopping"),
    DISTRACTION("Distraction"),
    CUSTOM("Custom"),
}

data class BlockedKeyword(
    val value: String,
    val category: BlockedCategory,
)

data class BlockedWebsite(
    val domain: String,
    val category: BlockedCategory,
)

enum class TitleMatchMode {
    CONTAINS,
    EXACT,
    STARTS_WITH,
}

data class TitleBlockRule(
    val id: String,
    val value: String,
    val mode: TitleMatchMode,
    val enabled: Boolean = true,
)

data class BlockingPrefsState(
    val blocklistKeywords: List<BlockedKeyword> = emptyList(),
    val whitelistKeywords: List<String> = emptyList(),
    val blockedWebsites: List<BlockedWebsite> = emptyList(),
    val trustedWebsites: List<String> = emptyList(),
    val titleBlockRules: List<TitleBlockRule> = emptyList(),
    val blockingEnabled: Boolean = true,
    val blockedToday: Int = 0,
)

private val Context.blockingDataStore by preferencesDataStore(name = "safeme_prefs")

val KEY_BLOCKLIST_KEYWORDS = stringPreferencesKey("blocklist_keywords_json")
val KEY_WHITELIST_KEYWORDS = stringPreferencesKey("whitelist_keywords_json")
val KEY_BLOCKED_WEBSITES = stringPreferencesKey("blocked_websites_json")
val KEY_TRUSTED_WEBSITES = stringPreferencesKey("trusted_websites_json")
val KEY_TITLE_BLOCK_RULES = stringPreferencesKey("title_block_rules_json")
val KEY_BLOCKING_ENABLED = booleanPreferencesKey("blocking_enabled")
val KEY_BLOCKED_TODAY = intPreferencesKey("blocked_today")

internal fun keywordsToJson(list: List<BlockedKeyword>): String {
    val arr = JSONArray()
    list.forEach { k ->
        val o = JSONObject()
        o.put("v", k.value)
        o.put("c", k.category.name)
        arr.put(o)
    }
    return arr.toString()
}

internal fun keywordsFromJson(json: String?, strict: Boolean = false): List<BlockedKeyword> {
    val text = json ?: ""
    if (text.isBlank()) return emptyList()
    val arr = try {
        JSONArray(text)
    } catch (e: JSONException) {
        if (strict) throw IllegalArgumentException("blocklistKeywords is not a JSON array", e)
        return emptyList()
    }
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                if (strict) throw IllegalArgumentException("blocklistKeywords[$i] is not an object")
                continue
            }
            val v = o.optString("v").trim().lowercase()
            if (v.isEmpty()) {
                if (strict) throw IllegalArgumentException("blocklistKeywords[$i] has no value")
                continue
            }
            val cat = runCatching {
                BlockedCategory.valueOf(o.optString("c"))
            }.getOrDefault(BlockedCategory.CUSTOM)
            add(BlockedKeyword(v, cat))
        }
    }
}

internal fun websitesToJson(list: List<BlockedWebsite>): String {
    val arr = JSONArray()
    list.forEach { w ->
        val o = JSONObject()
        o.put("d", w.domain)
        o.put("c", w.category.name)
        arr.put(o)
    }
    return arr.toString()
}

internal fun websitesFromJson(json: String?, strict: Boolean = false): List<BlockedWebsite> {
    val text = json ?: ""
    if (text.isBlank()) return emptyList()
    val arr = try {
        JSONArray(text)
    } catch (e: JSONException) {
        if (strict) throw IllegalArgumentException("blockedWebsites is not a JSON array", e)
        return emptyList()
    }
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                if (strict) throw IllegalArgumentException("blockedWebsites[$i] is not an object")
                continue
            }
            val d = o.optString("d").trim().lowercase()
            if (d.isEmpty()) {
                if (strict) throw IllegalArgumentException("blockedWebsites[$i] has no domain")
                continue
            }
            val cat = runCatching {
                BlockedCategory.valueOf(o.optString("c"))
            }.getOrDefault(BlockedCategory.CUSTOM)
            add(BlockedWebsite(d, cat))
        }
    }
}

internal fun stringsToJson(list: List<String>): String {
    val arr = JSONArray()
    list.forEach { arr.put(it) }
    return arr.toString()
}

internal fun titleRulesToJson(list: List<TitleBlockRule>): String {
    val arr = JSONArray()
    list.forEach { r ->
        val o = JSONObject()
        o.put("id", r.id)
        o.put("v", r.value)
        o.put("m", r.mode.name)
        o.put("e", r.enabled)
        arr.put(o)
    }
    return arr.toString()
}

internal fun titleRulesFromJson(json: String?, strict: Boolean = false): List<TitleBlockRule> {
    val text = json ?: ""
    if (text.isBlank()) return emptyList()
    val arr = try {
        JSONArray(text)
    } catch (e: JSONException) {
        if (strict) throw IllegalArgumentException("titleBlockRules is not a JSON array", e)
        return emptyList()
    }
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                if (strict) throw IllegalArgumentException("titleBlockRules[$i] is not an object")
                continue
            }
            val id = o.optString("id").trim()
            val v = o.optString("v").trim().lowercase()
            if (id.isEmpty() || v.isEmpty()) {
                if (strict) throw IllegalArgumentException("titleBlockRules[$i] is missing id or value")
                continue
            }
            val mode = runCatching {
                TitleMatchMode.valueOf(o.optString("m"))
            }.getOrDefault(TitleMatchMode.CONTAINS)
            add(TitleBlockRule(id, v, mode, o.optBoolean("e", true)))
        }
    }
}

internal fun stringsFromJson(json: String?, strict: Boolean = false): List<String> {
    val text = json ?: ""
    if (text.isBlank()) return emptyList()
    val arr = try {
        JSONArray(text)
    } catch (e: JSONException) {
        if (strict) throw IllegalArgumentException("expected a JSON string array", e)
        return emptyList()
    }
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            if (item !is String) {
                if (strict) throw IllegalArgumentException("entry $i is not a string")
                continue
            }
            val s = item.trim().lowercase()
            if (s.isNotEmpty()) add(s)
        }
    }
}

fun Context.blockingPrefs(): Flow<BlockingPrefsState> =
    blockingDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            BlockingPrefsState(
                blocklistKeywords = keywordsFromJson(prefs[KEY_BLOCKLIST_KEYWORDS]),
                whitelistKeywords = stringsFromJson(prefs[KEY_WHITELIST_KEYWORDS]),
                blockedWebsites = websitesFromJson(prefs[KEY_BLOCKED_WEBSITES]),
                trustedWebsites = stringsFromJson(prefs[KEY_TRUSTED_WEBSITES]),
                titleBlockRules = titleRulesFromJson(prefs[KEY_TITLE_BLOCK_RULES]),
                blockingEnabled = prefs[KEY_BLOCKING_ENABLED] ?: true,
                blockedToday = prefs[KEY_BLOCKED_TODAY] ?: 0,
            )
        }

fun Context.blockingEnabled(): Flow<Boolean> =
    blockingDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_BLOCKING_ENABLED] ?: true }

fun Context.blockedTodayFlow(): Flow<Int> =
    blockingDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_BLOCKED_TODAY] ?: 0 }

suspend fun Context.setBlockingEnabled(enabled: Boolean) {
    blockingDataStore.edit { it[KEY_BLOCKING_ENABLED] = enabled }
}

suspend fun Context.incrementBlockedToday() {
    blockingDataStore.edit { prefs ->
        prefs[KEY_BLOCKED_TODAY] = (prefs[KEY_BLOCKED_TODAY] ?: 0) + 1
    }
}


suspend fun Context.addBlockedKeyword(value: String, category: BlockedCategory) {
    val normalized = value.trim().lowercase()
    if (normalized.isEmpty()) return
    blockingDataStore.edit { prefs ->
        val list = keywordsFromJson(prefs[KEY_BLOCKLIST_KEYWORDS])
        if (list.none { it.value == normalized }) {
            prefs[KEY_BLOCKLIST_KEYWORDS] = keywordsToJson(list + BlockedKeyword(normalized, category))
        }
    }
}

suspend fun Context.updateBlockedKeyword(
    oldValue: String,
    newValue: String,
    category: BlockedCategory,
) {
    val normalized = newValue.trim().lowercase()
    if (normalized.isEmpty()) return
    blockingDataStore.edit { prefs ->
        val list = keywordsFromJson(prefs[KEY_BLOCKLIST_KEYWORDS])
        val updated = list.map {
            if (it.value == oldValue) BlockedKeyword(normalized, category) else it
        }
        prefs[KEY_BLOCKLIST_KEYWORDS] = keywordsToJson(updated)
    }
}

suspend fun Context.removeBlockedKeyword(value: String) {
    blockingDataStore.edit { prefs ->
        val list = keywordsFromJson(prefs[KEY_BLOCKLIST_KEYWORDS])
        prefs[KEY_BLOCKLIST_KEYWORDS] = keywordsToJson(list.filter { it.value != value })
    }
}

suspend fun Context.addWhitelistKeyword(value: String) {
    val normalized = value.trim().lowercase()
    if (normalized.isEmpty()) return
    blockingDataStore.edit { prefs ->
        val list = stringsFromJson(prefs[KEY_WHITELIST_KEYWORDS])
        if (normalized !in list) {
            prefs[KEY_WHITELIST_KEYWORDS] = stringsToJson(list + normalized)
        }
    }
}

suspend fun Context.removeWhitelistKeyword(value: String) {
    blockingDataStore.edit { prefs ->
        val list = stringsFromJson(prefs[KEY_WHITELIST_KEYWORDS])
        prefs[KEY_WHITELIST_KEYWORDS] = stringsToJson(list.filter { it != value })
    }
}

suspend fun Context.addBlockedWebsite(domain: String, category: BlockedCategory) {
    val normalized = normalizeDomain(domain)
    if (normalized.isEmpty()) return
    blockingDataStore.edit { prefs ->
        val list = websitesFromJson(prefs[KEY_BLOCKED_WEBSITES])
        if (list.none { it.domain == normalized }) {
            prefs[KEY_BLOCKED_WEBSITES] = websitesToJson(list + BlockedWebsite(normalized, category))
        }
    }
}

suspend fun Context.updateBlockedWebsite(oldDomain: String, newDomain: String, category: BlockedCategory) {
    val normalized = normalizeDomain(newDomain)
    if (normalized.isEmpty()) return
    blockingDataStore.edit { prefs ->
        val list = websitesFromJson(prefs[KEY_BLOCKED_WEBSITES])
        val updated = list.map {
            if (it.domain == oldDomain) BlockedWebsite(normalized, category) else it
        }
        prefs[KEY_BLOCKED_WEBSITES] = websitesToJson(updated)
    }
}

suspend fun Context.removeBlockedWebsite(domain: String) {
    blockingDataStore.edit { prefs ->
        val list = websitesFromJson(prefs[KEY_BLOCKED_WEBSITES])
        prefs[KEY_BLOCKED_WEBSITES] = websitesToJson(list.filter { it.domain != domain })
    }
}

suspend fun Context.addTrustedWebsite(domain: String) {
    val normalized = normalizeDomain(domain)
    if (normalized.isEmpty()) return
    blockingDataStore.edit { prefs ->
        val list = stringsFromJson(prefs[KEY_TRUSTED_WEBSITES])
        if (normalized !in list) {
            prefs[KEY_TRUSTED_WEBSITES] = stringsToJson(list + normalized)
        }
    }
}

suspend fun Context.removeTrustedWebsite(domain: String) {
    blockingDataStore.edit { prefs ->
        val list = stringsFromJson(prefs[KEY_TRUSTED_WEBSITES])
        prefs[KEY_TRUSTED_WEBSITES] = stringsToJson(list.filter { it != domain })
    }
}

/**
 * Replaces all user blocking configuration in one atomic edit (backup restore).
 * `blockedToday` is a transient daily counter and is intentionally untouched.
 */
suspend fun Context.writeBlockingPrefs(state: BlockingPrefsState) {
    blockingDataStore.edit { prefs ->
        prefs[KEY_BLOCKLIST_KEYWORDS] = keywordsToJson(state.blocklistKeywords)
        prefs[KEY_WHITELIST_KEYWORDS] = stringsToJson(state.whitelistKeywords)
        prefs[KEY_BLOCKED_WEBSITES] = websitesToJson(state.blockedWebsites)
        prefs[KEY_TRUSTED_WEBSITES] = stringsToJson(state.trustedWebsites)
        prefs[KEY_TITLE_BLOCK_RULES] = titleRulesToJson(state.titleBlockRules)
        prefs[KEY_BLOCKING_ENABLED] = state.blockingEnabled
    }
}

suspend fun Context.resetUserBlockingPrefs() {
    blockingDataStore.edit { prefs ->
        prefs.remove(KEY_BLOCKLIST_KEYWORDS)
        prefs.remove(KEY_WHITELIST_KEYWORDS)
        prefs.remove(KEY_BLOCKED_WEBSITES)
        prefs.remove(KEY_TRUSTED_WEBSITES)
        prefs.remove(KEY_TITLE_BLOCK_RULES)
    }
}

suspend fun Context.addTitleBlockRule(value: String, mode: TitleMatchMode) {
    val normalized = value.trim().lowercase()
    if (normalized.isEmpty()) return
    blockingDataStore.edit { prefs ->
        val list = titleRulesFromJson(prefs[KEY_TITLE_BLOCK_RULES])
        if (list.none { it.value == normalized && it.mode == mode }) {
            val id = UUID.randomUUID().toString()
            prefs[KEY_TITLE_BLOCK_RULES] = titleRulesToJson(list + TitleBlockRule(id, normalized, mode))
        }
    }
}

suspend fun Context.updateTitleBlockRule(id: String, value: String, mode: TitleMatchMode) {
    val normalized = value.trim().lowercase()
    if (normalized.isEmpty()) return
    blockingDataStore.edit { prefs ->
        val list = titleRulesFromJson(prefs[KEY_TITLE_BLOCK_RULES])
        val updated = list.map {
            if (it.id == id) TitleBlockRule(id, normalized, mode, it.enabled) else it
        }
        prefs[KEY_TITLE_BLOCK_RULES] = titleRulesToJson(updated)
    }
}

suspend fun Context.deleteTitleBlockRule(id: String) {
    blockingDataStore.edit { prefs ->
        val list = titleRulesFromJson(prefs[KEY_TITLE_BLOCK_RULES])
        prefs[KEY_TITLE_BLOCK_RULES] = titleRulesToJson(list.filter { it.id != id })
    }
}

suspend fun Context.toggleTitleBlockRule(id: String, enabled: Boolean) {
    blockingDataStore.edit { prefs ->
        val list = titleRulesFromJson(prefs[KEY_TITLE_BLOCK_RULES])
        val updated = list.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        prefs[KEY_TITLE_BLOCK_RULES] = titleRulesToJson(updated)
    }
}

fun normalizeDomain(input: String): String {
    var d = input.trim().lowercase()
    while (d.startsWith("https://") || d.startsWith("http://")) {
        d = d.substring(d.indexOf("://") + 3)
    }
    d = d.substringBefore("/")
    d = d.substringBefore("?")
    d = d.substringBefore("#")
    d = d.trimEnd('.')
    return d
}
