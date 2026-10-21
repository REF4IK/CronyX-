package com.winlator.cmod.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.GsonBuilder
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.steamgrid.SteamGridDBApi
import com.winlator.cmod.steamgrid.SteamGridGridsResponse
import com.winlator.cmod.steamgrid.SteamGridGridsResponseDeserializer
import com.winlator.cmod.steamgrid.SteamGridSearchResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ShortcutCoverFetcher {
    private const val STEAMGRID_BASE_URL = "https://www.steamgriddb.com/api/v2/"
    private const val STEAMGRID_API_KEY = "4765cce5e92f8406ab0f5346c3b5e3ba"
    private const val STEAMGRID_STYLES = "alternate,blurred,white_logo,material,no_logo"
    private const val MATCH_THRESHOLD = 70
    private const val CACHE_PREFS = "cover_art_cache"
    private const val CACHE_VERSION = 2

    private val COVER_ART_EXECUTOR = Executors.newFixedThreadPool(5)
    private val CACHE_LOCK = Any()
    @Volatile private var cacheVersionChecked = false

    private val COVER_ART_HTTP: OkHttpClient = DohOkHttp.get().newBuilder()
        .callTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val STEAMGRID_LAST_FAIL = ConcurrentHashMap<String, Long>()
    private val TITLE_CACHE = ConcurrentHashMap<String, ResolvedTitle>()

    private data class ResolvedTitle(val appId: Int?, val title: String, val stamp: Long)

    private val STEAM_ASSET_HOSTS = arrayOf(
        "cdn.cloudflare.steamstatic.com",
        "cdn.akamai.steamstatic.com",
        "steamcdn-a.akamaihd.net"
    )

    private val ANIMATED_EXTENSIONS = arrayOf(".gif", ".webp", ".apng")

    fun getCachedCoverFile(context: Context, shortcutName: String, landscape: Boolean): File? {
        ensureCacheVersion(context)
        val cacheDir = File(context.filesDir, "coverArtCache")
        for (ext in ANIMATED_EXTENSIONS) {
            val name = if (landscape) "${shortcutName}_l$ext" else "$shortcutName$ext"
            val f = File(cacheDir, name)
            if (f.exists()) return f
        }
        val staticFile = File(cacheDir, coverCacheFileName(shortcutName, landscape))
        if (staticFile.exists()) return staticFile
        return null
    }

    /**
     * One-time migration: covers cached by the old matcher may belong to a DLC/bundle
     * (e.g. Dying Light -> Harran Tactical Unit Bundle). Deleting the app's cover cache
     * once makes every shortcut re-resolve with the new matcher. Custom covers in the
     * container's app_data/cover_arts are untouched.
     */
    private fun ensureCacheVersion(context: Context) {
        if (cacheVersionChecked) return
        synchronized(CACHE_LOCK) {
            if (cacheVersionChecked) return
            try {
                val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                if (prefs.getInt("version", 0) < CACHE_VERSION) {
                    File(context.filesDir, "coverArtCache").listFiles()
                        ?.forEach { it.deleteRecursively() }
                    prefs.edit().putInt("version", CACHE_VERSION).apply()
                }
            } catch (_: Throwable) {
            }
            cacheVersionChecked = true
        }
    }

    /** Forget cached covers for one shortcut (called when it is imported again). */
    @JvmStatic
    fun clearCoverCache(context: Context, shortcutName: String) {
        try {
            val cacheDir = File(context.filesDir, "coverArtCache")
            for (ext in ANIMATED_EXTENSIONS) {
                File(cacheDir, "$shortcutName$ext").delete()
                File(cacheDir, "${shortcutName}_l$ext").delete()
            }
            File(cacheDir, "$shortcutName.png").delete()
            File(cacheDir, "${shortcutName}_l.png").delete()
            STEAMGRID_LAST_FAIL.remove(shortcutName)
            TITLE_CACHE.clear()
        } catch (_: Throwable) {
        }
    }

    private fun coverCacheFileName(shortcutName: String, landscape: Boolean): String {
        return if (landscape) "${shortcutName}_l.png" else "$shortcutName.png"
    }

    fun loadCoverArt(context: Context, shortcut: Shortcut, landscape: Boolean): File? {
        // 1. Check custom cover art
        val customPath = shortcut.customCoverArtPath
        if (!customPath.isNullOrEmpty()) {
            val f = File(customPath)
            if (f.isFile) return f
        }

        // 2. Check default cover art in container folder
        val containerCover = File(File(shortcut.container.rootDir, "app_data/cover_arts"), "${shortcut.name}.png")
        if (containerCover.isFile) return containerCover

        // 3. Check cache
        val cached = getCachedCoverFile(context, shortcut.name, landscape)
        if (cached != null && cached.isFile) return cached

        return null
    }

    fun fetchCoverArt(context: Context, shortcut: Shortcut, landscape: Boolean, onDone: () -> Unit) {
        if (loadCoverArt(context, shortcut, landscape) != null) return

        COVER_ART_EXECUTOR.execute {
            val (knownAppId, title) = resolveIdentity(shortcut)
            try {
                val appId = knownAppId ?: SteamTitleResolver.findAppId(context, title)
                if (appId == null || appId <= 0) {
                    Log.w("CoverArt", "Steam CDN: appid not found title=\"$title\" name=${shortcut.name}, falling back to SteamGridDB")
                    fetchSteamGridCoverArt(context, shortcut, landscape, title, null, onDone)
                    return@execute
                }

                for (url in buildSteamCoverUrls(appId, landscape)) {
                    if (!shortcut.customCoverArtPath.isNullOrEmpty()) return@execute
                    if (getCachedCoverFile(context, shortcut.name, landscape) != null) return@execute

                    val bmp = downloadBitmap(url)
                    if (bmp != null) {
                        cacheCoverArt(context, bmp, shortcut.name, landscape)
                        onDone()
                        Log.i("CoverArt", "Steam CDN cover art downloaded name=${shortcut.name} appId=$appId landscape=$landscape")
                        return@execute
                    }
                }

                Log.w("CoverArt", "Steam CDN: all urls failed name=${shortcut.name} appId=$appId, falling back to SteamGridDB")
                fetchSteamGridCoverArt(context, shortcut, landscape, title, appId, onDone)
            } catch (e: Exception) {
                Log.w("CoverArt", "Steam CDN cover art failed name=${shortcut.name}", e)
                fetchSteamGridCoverArt(context, shortcut, landscape, title, null, onDone)
            }
        }
    }

    /**
     * Exact identity for a shortcut:
     *  - the game's own files (steam_appid.txt, appmanifest, emu ini, ...) win;
     *  - `steamAppId` already stored in Extra Data (Steam library / Steam Info dialog) is
     *    used only after its store name validates against the title, so an old wrong bundle
     *    appId cannot hijack the cover;
     *  - title from the disk footprint (manifest/GOG/PE) or the cleaned shortcut name.
     * Cached per shortcut until its .desktop or exe changes.
     */
    private fun resolveIdentity(shortcut: Shortcut): Pair<Int?, String> {
        val key = shortcut.file?.absolutePath ?: shortcut.name
        val exe = try {
            shortcut.resolveExeFile()
        } catch (_: Throwable) {
            null
        }
        val stamp = maxOf(shortcut.file?.lastModified() ?: 0L, exe?.lastModified() ?: 0L)
        TITLE_CACHE[key]?.takeIf { it.stamp == stamp }?.let { return it.appId to it.title }

        val extraAppId = shortcut.getExtra("steamAppId")?.toIntOrNull()?.takeIf { it > 0 }
        val identity = exe?.let {
            try {
                GameTitleParser.identify(it)
            } catch (_: Throwable) {
                null
            }
        }
        val parsed = if (identity != null && exe != null) GameTitleParser.displayName(identity, exe) else null
        val title = parsed?.takeIf { it.isNotBlank() }
            ?: GameTitleParser.cleanTitle(shortcut.name).takeIf { it.isNotBlank() }
            ?: shortcut.name

        val diskAppId = identity?.appId
        val verifiedExtraAppId = extraAppId?.takeIf { id ->
            val matches = SteamTitleResolver.appIdMatchesTitle(id, title)
            if (matches == false) {
                Log.w("CoverArt", "stored steamAppId=$id does not match title=\"$title\"; ignoring it")
            }
            matches != false
        }
        val appId = diskAppId ?: verifiedExtraAppId

        Log.i(
            "CoverArt",
            "identity name=${shortcut.name} title=\"$title\" diskAppId=$diskAppId " +
                "extraAppId=$extraAppId chosenAppId=$appId"
        )
        TITLE_CACHE[key] = ResolvedTitle(appId, title, stamp)
        return appId to title
    }

    private fun fetchSteamGridCoverArt(
        context: Context,
        shortcut: Shortcut,
        landscape: Boolean,
        title: String,
        appId: Int?,
        onDone: () -> Unit
    ) {
        val lastFail = STEAMGRID_LAST_FAIL[shortcut.name]
        if (lastFail != null && (System.currentTimeMillis() - lastFail) < 10 * 60 * 1000L) {
            fetchSteamHeaderFallback(context, shortcut, landscape, title, onDone)
            return
        }

        if (title.trim().length < 3) {
            Log.w("CoverArt", "SteamGrid: name too short (len<3), skipping search title=\"$title\" name=${shortcut.name}")
            fetchSteamHeaderFallback(context, shortcut, landscape, title, onDone)
            return
        }

        val api = buildSteamGridApi()
        if (appId != null && appId > 0) {
            fetchGrids(
                api, appId, bySteamAppId = true,
                context, shortcut, landscape, title,
                onNoCover = { fetchSteamGridByName(api, context, shortcut, landscape, title, onDone) },
                onDone = onDone,
            )
        } else {
            fetchSteamGridByName(api, context, shortcut, landscape, title, onDone)
        }
    }

    private fun fetchSteamGridByName(
        api: SteamGridDBApi,
        context: Context,
        shortcut: Shortcut,
        landscape: Boolean,
        title: String,
        onDone: () -> Unit
    ) {
        val queries = GameTitleMatcher.searchQueries(title).ifEmpty { listOf(title) }
        searchNextGridGame(api, queries, 0, context, shortcut, landscape, title, onDone)
    }

    private fun searchNextGridGame(
        api: SteamGridDBApi,
        queries: List<String>,
        index: Int,
        context: Context,
        shortcut: Shortcut,
        landscape: Boolean,
        title: String,
        onDone: () -> Unit
    ) {
        if (index >= queries.size) {
            Log.w("CoverArt", "SteamGrid: no good match title=\"$title\" name=${shortcut.name}")
            STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
            fetchSteamHeaderFallback(context, shortcut, landscape, title, onDone)
            return
        }

        val query = queries[index]
        val call = api.searchGame("Bearer $STEAMGRID_API_KEY", query)
        call.enqueue(object : retrofit2.Callback<SteamGridSearchResponse> {
            override fun onResponse(
                call: retrofit2.Call<SteamGridSearchResponse>,
                response: retrofit2.Response<SteamGridSearchResponse>
            ) {
                val data = response.body()?.data
                if (!response.isSuccessful || data.isNullOrEmpty()) {
                    searchNextGridGame(api, queries, index + 1, context, shortcut, landscape, title, onDone)
                    return
                }

                var bestId = -1
                var bestScore = -1
                var bestResultName = ""
                for (result in data) {
                    val resultName = result.name?.trim() ?: continue
                    if (resultName.isEmpty()) continue
                    val score = GameTitleMatcher.matchScore(title, resultName)
                    Log.d("CoverArt", "SteamGrid result: title=\"$title\" query=\"$query\" -> \"$resultName\" id=${result.id} score=$score")
                    if (score > bestScore) {
                        bestScore = score
                        bestId = result.id
                        bestResultName = resultName
                    }
                }

                if (bestId <= 0 || bestScore < MATCH_THRESHOLD) {
                    Log.w("CoverArt", "SteamGrid: no good match query=\"$query\" title=\"$title\" bestScore=$bestScore bestResult=\"$bestResultName\"")
                    searchNextGridGame(api, queries, index + 1, context, shortcut, landscape, title, onDone)
                    return
                }

                Log.i("CoverArt", "SteamGrid: selected match title=\"$title\" query=\"$query\" -> \"$bestResultName\" id=$bestId score=$bestScore")
                fetchGrids(
                    api, bestId, bySteamAppId = false,
                    context, shortcut, landscape, title,
                    onNoCover = {
                        searchNextGridGame(api, queries, index + 1, context, shortcut, landscape, title, onDone)
                    },
                    onDone = onDone,
                )
            }

            override fun onFailure(call: retrofit2.Call<SteamGridSearchResponse>, t: Throwable) {
                Log.w("CoverArt", "SteamGrid search error query=\"$query\" title=\"$title\"", t)
                searchNextGridGame(api, queries, index + 1, context, shortcut, landscape, title, onDone)
            }
        })
    }

    private fun fetchGrids(
        api: SteamGridDBApi,
        id: Int,
        bySteamAppId: Boolean,
        context: Context,
        shortcut: Shortcut,
        landscape: Boolean,
        title: String,
        onNoCover: () -> Unit,
        onDone: () -> Unit
    ) {
        val dimensions = if (landscape) "920x430,460x215" else "600x900"
        val auth = "Bearer $STEAMGRID_API_KEY"

        val animatedCall = if (bySteamAppId) {
            api.getAnimatedGridsBySteamAppId(
                auth, id, STEAMGRID_STYLES, dimensions, "animated", "image/webp", "false", "false"
            )
        } else {
            api.getAnimatedGridsByGameId(
                auth, id, STEAMGRID_STYLES, dimensions, "animated", "image/webp", "false", "false"
            )
        }

        animatedCall.enqueue(object : retrofit2.Callback<SteamGridGridsResponse> {
            override fun onResponse(
                call: retrofit2.Call<SteamGridGridsResponse>,
                response: retrofit2.Response<SteamGridGridsResponse>
            ) {
                val data = response.body()?.data
                if (response.isSuccessful && !data.isNullOrEmpty()) {
                    var best: SteamGridGridsResponse.Grid? = null
                    var apngFallback: SteamGridGridsResponse.Grid? = null
                    for (g in data) {
                        val mime = g.mime?.lowercase(Locale.US) ?: ""
                        val ext = if (g.url != null) urlToExtension(g.url) else ""
                        if (mime.contains("webp") || ext == ".webp" || mime.contains("gif") || ext == ".gif") {
                            best = g
                            break
                        }
                        if (apngFallback == null && (mime.contains("png") || ext == ".png")) {
                            apngFallback = g
                        }
                    }
                    if (best == null) best = apngFallback
                    if (best?.url != null && best.url.isNotEmpty()) {
                        Log.i("CoverArt", "Animated cover found name=${shortcut.name} mime=${best.mime} url=${best.url}")
                        downloadCoverArt(context, best.url, shortcut, landscape, true, best.mime, onDone, onNoCover)
                        return
                    }
                }
                Log.i("CoverArt", "No WebP animated cover for name=${shortcut.name}, trying any animated cover")
                fetchAnyAnimatedGridsFallback(api, id, bySteamAppId, context, shortcut, landscape, title, onNoCover, onDone)
            }

            override fun onFailure(call: retrofit2.Call<SteamGridGridsResponse>, t: Throwable) {
                fetchAnyAnimatedGridsFallback(api, id, bySteamAppId, context, shortcut, landscape, title, onNoCover, onDone)
            }
        })
    }

    private fun fetchAnyAnimatedGridsFallback(
        api: SteamGridDBApi,
        id: Int,
        bySteamAppId: Boolean,
        context: Context,
        shortcut: Shortcut,
        landscape: Boolean,
        title: String,
        onNoCover: () -> Unit,
        onDone: () -> Unit
    ) {
        val dimensions = if (landscape) "920x430,460x215" else "600x900"
        val auth = "Bearer $STEAMGRID_API_KEY"
        val anyAnimatedCall = if (bySteamAppId) {
            api.getAnimatedGridsBySteamAppId(
                auth, id, STEAMGRID_STYLES, dimensions, "animated", null, "false", "false"
            )
        } else {
            api.getAnimatedGridsByGameId(
                auth, id, STEAMGRID_STYLES, dimensions, "animated", null, "false", "false"
            )
        }

        anyAnimatedCall.enqueue(object : retrofit2.Callback<SteamGridGridsResponse> {
            override fun onResponse(
                call: retrofit2.Call<SteamGridGridsResponse>,
                response: retrofit2.Response<SteamGridGridsResponse>
            ) {
                val data = response.body()?.data
                if (response.isSuccessful && !data.isNullOrEmpty()) {
                    val grid = data[0]
                    if (!grid.url.isNullOrEmpty()) {
                        downloadCoverArt(context, grid.url, shortcut, landscape, true, grid.mime, onDone, onNoCover)
                        return
                    }
                }
                fetchStaticGridsFallback(api, id, bySteamAppId, context, shortcut, landscape, title, onNoCover, onDone)
            }

            override fun onFailure(call: retrofit2.Call<SteamGridGridsResponse>, t: Throwable) {
                fetchStaticGridsFallback(api, id, bySteamAppId, context, shortcut, landscape, title, onNoCover, onDone)
            }
        })
    }

    private fun fetchStaticGridsFallback(
        api: SteamGridDBApi,
        id: Int,
        bySteamAppId: Boolean,
        context: Context,
        shortcut: Shortcut,
        landscape: Boolean,
        title: String,
        onNoCover: () -> Unit,
        onDone: () -> Unit
    ) {
        val dimensions = if (landscape) "920x430,460x215" else "600x900"
        val auth = "Bearer $STEAMGRID_API_KEY"
        val gridsCall = if (bySteamAppId) {
            api.getGridsBySteamAppId(auth, id, "alternate", dimensions, "static")
        } else {
            api.getGridsByGameId(auth, id, "alternate", dimensions, "static")
        }

        gridsCall.enqueue(object : retrofit2.Callback<SteamGridGridsResponse> {
            override fun onResponse(
                call: retrofit2.Call<SteamGridGridsResponse>,
                response: retrofit2.Response<SteamGridGridsResponse>
            ) {
                val data = response.body()?.data
                if (!response.isSuccessful || data.isNullOrEmpty()) {
                    Log.w("CoverArt", "SteamGrid grids failed/empty name=${shortcut.name} title=\"$title\"")
                    STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                    onNoCover()
                    return
                }
                val url = data[0].url
                if (url.isNullOrEmpty()) {
                    STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                    onNoCover()
                    return
                }
                downloadCoverArt(context, url, shortcut, landscape, isAnimated = false, mime = null, onDone, onNoCover)
            }

            override fun onFailure(call: retrofit2.Call<SteamGridGridsResponse>, t: Throwable) {
                Log.w("CoverArt", "SteamGrid grids error name=${shortcut.name}", t)
                STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                onNoCover()
            }
        })
    }

    private fun fetchSteamHeaderFallback(
        context: Context,
        shortcut: Shortcut,
        landscape: Boolean,
        title: String,
        onDone: () -> Unit
    ) {
        COVER_ART_EXECUTOR.execute {
            try {
                val appId = SteamTitleResolver.findAppId(context, title)
                if (appId == null || appId <= 0) {
                    Log.w("CoverArt", "Steam fallback: appid not found title=\"$title\" name=${shortcut.name}")
                    return@execute
                }

                for (url in buildSteamHeaderUrls(appId, landscape)) {
                    val bmp = downloadBitmap(url) ?: continue
                    cacheCoverArt(context, bmp, shortcut.name, landscape)
                    onDone()
                    return@execute
                }
                Log.w("CoverArt", "Steam fallback: all header urls failed name=${shortcut.name} appid=$appId")
            } catch (e: Exception) {
                Log.w("CoverArt", "Steam fallback failed name=${shortcut.name}", e)
            }
        }
    }

    private fun downloadCoverArt(
        context: Context,
        url: String,
        shortcut: Shortcut,
        landscape: Boolean,
        isAnimated: Boolean = false,
        mime: String? = null,
        onDone: () -> Unit,
        onError: () -> Unit = {}
    ) {
        COVER_ART_EXECUTOR.execute {
            try {
                val request = Request.Builder().url(url).build()
                COVER_ART_HTTP.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onError()
                        return@execute
                    }
                    val body = response.body ?: run {
                        onError()
                        return@execute
                    }
                    val bytes = body.bytes()
                    if (bytes.isEmpty()) {
                        onError()
                        return@execute
                    }

                    var ext = urlToExtension(url)
                    if (mime != null && mime.contains("webp")) ext = ".webp"
                    val storeAsFile = isAnimated || isAnimatedBytes(bytes, ext)

                    if (storeAsFile) {
                        val cacheDir = File(context.filesDir, "coverArtCache")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        val animExt = detectAnimExtension(bytes, url, mime)
                        val animFileName = if (landscape) "${shortcut.name}_l$animExt" else "${shortcut.name}$animExt"
                        val animFile = File(cacheDir, animFileName)
                        FileOutputStream(animFile).use { fos ->
                            fos.write(bytes)
                            fos.flush()
                        }
                        onDone()
                    } else {
                        val coverArt = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (coverArt != null) {
                            cacheCoverArt(context, coverArt, shortcut.name, landscape)
                            onDone()
                        } else {
                            onError()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CoverArt", "Download failed name=${shortcut.name} url=$url", e)
                onError()
            }
        }
    }

    private fun buildSteamGridApi(): SteamGridDBApi {
        val gson = GsonBuilder()
            .registerTypeAdapter(SteamGridGridsResponse::class.java, SteamGridGridsResponseDeserializer())
            .create()
        return Retrofit.Builder()
            .baseUrl(STEAMGRID_BASE_URL)
            .client(DohOkHttp.get())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SteamGridDBApi::class.java)
    }

    private fun buildSteamCoverUrls(appId: Int, landscape: Boolean): List<String> {
        val urls = ArrayList<String>()
        val primaryHost = "https://shared.steamstatic.com/store_item_assets/steam/apps"

        if (landscape) {
            urls.add("$primaryHost/$appId/library_hero.jpg")
            urls.add("$primaryHost/$appId/header.jpg")
            urls.add("$primaryHost/$appId/capsule_616x353.jpg")
            urls.add("$primaryHost/$appId/capsule_231x87.jpg")

            for (host in STEAM_ASSET_HOSTS) {
                urls.add("https://$host/steam/apps/$appId/library_hero.jpg")
                urls.add("https://$host/steam/apps/$appId/header.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_616x353.jpg")
            }
        } else {
            urls.add("$primaryHost/$appId/library_600x900_2x.jpg")
            urls.add("$primaryHost/$appId/library_600x900.jpg")
            urls.add("$primaryHost/$appId/capsule_616x353.jpg")
            urls.add("$primaryHost/$appId/header.jpg")

            for (host in STEAM_ASSET_HOSTS) {
                urls.add("https://$host/steam/apps/$appId/library_600x900_2x.jpg")
                urls.add("https://$host/steam/apps/$appId/library_600x900.jpg")
                urls.add("https://$host/steam/apps/$appId/header.jpg")
            }
        }
        return urls
    }

    private fun buildSteamHeaderUrls(appId: Int, landscape: Boolean): List<String> {
        val urls = ArrayList<String>()
        for (host in STEAM_ASSET_HOSTS) {
            if (landscape) {
                urls.add("https://$host/steam/apps/$appId/library_hero.jpg")
                urls.add("https://$host/steam/apps/$appId/header.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_616x353.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_231x87.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_184x69.jpg")
            } else {
                urls.add("https://$host/steam/apps/$appId/library_600x900_2x.jpg")
                urls.add("https://$host/steam/apps/$appId/library_600x900.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_616x353.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_184x69.jpg")
                urls.add("https://$host/steam/apps/$appId/header.jpg")
            }
        }
        return urls
    }

    private fun downloadBitmap(url: String): Bitmap? {
        try {
            val request = Request.Builder().url(url).build()
            COVER_ART_HTTP.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val bytes = body.bytes()
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun isAnimatedBytes(bytes: ByteArray, ext: String): Boolean {
        if (".gif".equals(ext, ignoreCase = true)) return true
        if (".png".equals(ext, ignoreCase = true)) {
            if (bytes.size > 8) {
                var offset = 8
                while (offset + 8 <= bytes.size) {
                    val chunkLen = ((bytes[offset].toInt() and 0xFF) shl 24) or
                            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 3].toInt() and 0xFF)
                    val chunkType = String(bytes, offset + 4, 4, StandardCharsets.US_ASCII)
                    if ("acTL" == chunkType) return true
                    if ("IDAT" == chunkType) break
                    offset += 8 + chunkLen + 4
                }
            }
        }
        if (".webp".equals(ext, ignoreCase = true)) {
            if (bytes.size >= 12) {
                val riffSize = ((bytes[7].toInt() and 0xFF) shl 24) or
                        ((bytes[6].toInt() and 0xFF) shl 16) or
                        ((bytes[5].toInt() and 0xFF) shl 8) or
                        (bytes[4].toInt() and 0xFF)
                var offset = 12
                while (offset + 8 <= bytes.size) {
                    val chunk = String(bytes, offset, 4)
                    val chunkSize = ((bytes[offset + 7].toInt() and 0xFF) shl 24) or
                            ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                            ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 4].toInt() and 0xFF)
                    if ("ANIM" == chunk) return true
                    offset += 8 + chunkSize + (chunkSize and 1)
                    if (offset > riffSize + 8) break
                }
            }
        }
        return false
    }

    private fun urlToExtension(url: String): String {
        val path = url.split("[?#]".toRegex()).toTypedArray()[0]
        val dot = path.lastIndexOf('.')
        if (dot in 0 until path.length - 1) {
            val ext = path.substring(dot).lowercase(Locale.US)
            if (ext == ".gif" || ext == ".webp" || ext == ".png" || ext == ".jpg" || ext == ".jpeg") return ext
        }
        return ".png"
    }

    private fun detectAnimExtension(bytes: ByteArray, url: String, mime: String?): String {
        if (mime != null && mime.contains("webp")) return ".webp"
        val urlExt = urlToExtension(url)
        if (".gif" == urlExt) return ".gif"
        if (".webp" == urlExt && isAnimatedBytes(bytes, urlExt)) return ".webp"
        if (".png" == urlExt && isAnimatedBytes(bytes, urlExt)) return ".png"
        return if (isAnimatedBytes(bytes, urlExt)) ".gif" else ".png"
    }

    private fun cacheCoverArt(context: Context, coverArt: Bitmap, shortcutName: String, landscape: Boolean) {
        try {
            val cacheDir = File(context.filesDir, "coverArtCache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val coverFile = File(cacheDir, coverCacheFileName(shortcutName, landscape))
            FileOutputStream(coverFile).use { outputStream ->
                coverArt.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
        } catch (ignored: IOException) {
        }
    }
}
