package com.winlator.cmod.core

import android.content.Context
import android.util.Log
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Steam store lookups used by cover art and the Steam Info dialog.
 *
 * Blocking: call from a worker thread. Every failure degrades to null / an empty list.
 * Positive appId lookups are cached forever; misses are retried after a week.
 */
object SteamTitleResolver {

    private const val TAG = "SteamTitle"
    private const val PREFS = "steam_appid_by_name_v3"
    private const val RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    private val NAME_CACHE = ConcurrentHashMap<Int, String>()
    private val INVALID_APP_IDS = ConcurrentHashMap.newKeySet<Int>()

    private val HTTP by lazy {
        DohOkHttp.get().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    data class Suggestion(val appId: Int, val name: String)

    /**
     * The Steam appId of [name], or null when the store has no confident match.
     * Cleans the title, tries a few search variants and validates every result with
     * [GameTitleMatcher] so an incomplete name ("Witcher3") still finds its game while
     * "Hades" does not silently become "Hades II".
     */
    @JvmStatic
    fun findAppId(context: Context, name: String): Int? {
        val cleaned = GameTitleParser.cleanTitle(name).trim()
        if (cleaned.length < 3) return null
        val key = GameTitleMatcher.normalize(cleaned)
        if (key.isEmpty()) return null

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(key, null)?.let { saved ->
            saved.toIntOrNull()?.let { return it }
            val since = saved.removePrefix("none:").toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - since < RETRY_AFTER_MS) return null
        }

        val queries = GameTitleMatcher.searchQueries(cleaned).ifEmpty { listOf(cleaned) }
        for (query in queries.take(3)) {
            val suggestions = searchByName(query)
            if (suggestions.isEmpty()) continue
            var best: Suggestion? = null
            var bestScore = -1
            for (s in suggestions) {
                val score = GameTitleMatcher.matchScore(query, s.name)
                if (score > bestScore) {
                    bestScore = score
                    best = s
                }
            }
            if (best != null && bestScore >= 70) {
                prefs.edit().putString(key, best.appId.toString()).apply()
                Log.i(TAG, "\"$name\" -> appId=${best.appId} \"${best.name}\" score=$bestScore query=\"$query\"")
                return best.appId
            }
        }

        prefs.edit().putString(key, "none:${System.currentTimeMillis()}").apply()
        Log.w(TAG, "no confident match for \"$name\"")
        return null
    }

    /** Up to 12 Steam store search hits, best-first as Steam returned them. Blocking. */
    @JvmStatic
    fun searchByName(query: String): List<Suggestion> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        return try {
            val encoded = URLEncoder.encode(term, "UTF-8")
            val body = httpGet(
                "https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US"
            ) ?: return emptyList()
            val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(items.length(), 12)) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optInt("id", -1)
                    val name = item.optString("name", "").trim()
                    if (id > 0 && name.isNotEmpty()) add(Suggestion(id, name))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchByName failed for \"$term\": ${e.message}")
            emptyList()
        }
    }

    /** The authoritative store name for [appId], or null when unknown/delisted. Blocking. */
    @JvmStatic
    fun resolveName(appId: Int): String? {
        if (appId <= 0) return null
        NAME_CACHE[appId]?.let { return it }
        return try {
            val body = httpGet("https://store.steampowered.com/api/appdetails?appids=$appId&filters=basic")
                ?: return null
            val entry = JSONObject(body).optJSONObject(appId.toString())
            if (entry == null || !entry.optBoolean("success", false)) {
                // Bundles/packages (e.g. Dying Light - Harran Tactical Unit Bundle, 1543420)
                // are valid store search ids but not real apps: appdetails reports success=false.
                INVALID_APP_IDS.add(appId)
                return null
            }
            val name = entry.optJSONObject("data")?.optString("name", "")?.takeIf { it.isNotBlank() }
            if (name == null) {
                INVALID_APP_IDS.add(appId)
                return null
            }
            NAME_CACHE[appId] = name
            name
        } catch (e: Exception) {
            Log.w(TAG, "resolveName failed for $appId: ${e.message}")
            null
        }
    }

    /**
     * Whether [appId]'s store name matches the base title. Null when the store cannot be
     * queried (offline/unknown), so callers may fall back to trusting the stored id.
     * Uses [GameTitleMatcher.bestQuery], so a folder named after a DLC/bundle validates
     * the base game's appId but a stored bundle appId is rejected.
     */
    @JvmStatic
    fun appIdMatchesTitle(appId: Int, title: String): Boolean? {
        if (appId <= 0 || title.isBlank()) return null
        val storeName = resolveName(appId)
        if (storeName == null) {
            // A definitive "no such app" answer means the stored id is a bundle/package or
            // delisted; a network failure is unknown and the caller may keep the stored id.
            return if (INVALID_APP_IDS.contains(appId)) false else null
        }
        val query = GameTitleMatcher.bestQuery(title)
        return GameTitleMatcher.matchScore(query, storeName) >= 70
    }

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            HTTP.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP failed: ${e.message}")
            null
        }
    }
}
