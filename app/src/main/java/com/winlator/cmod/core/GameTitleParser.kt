package com.winlator.cmod.core

import com.winlator.cmod.win32.PEParser
import java.io.File
import java.util.Locale

/**
 * Best-effort identification of a Windows game from its on-disk footprint.
 *
 * Ported from Bannerlator's GameIdentifier, adapted to Winlator CMOD's PEParser:
 *  1. steam_appid.txt (or steam_settings/steam_appid.txt)  -> appId
 *  2. AppId= in steam_emu.ini / ColdClientLoader.ini / ... -> appId
 *  3. appmanifest_<id>.acf                                  -> appId + real title
 *  4. goggame-<id>.info                                     -> real title
 *  5. PE ProductName / FileDescription                      -> title
 *  6. cleaned folder / exe name                             -> weak fallback
 *
 * Used by the import dialogs (suggested shortcut name) and by the cover fetcher
 * (exact Steam appId without a network search). Never throws.
 */
object GameTitleParser {

    enum class Source { STEAM_APPID_TXT, STEAM_MANIFEST_ACF, STEAM_EMU_INI, GOG_INFO, PE_VERSION, FILENAME, NONE }
    enum class Confidence { HIGH, MEDIUM, LOW }

    data class GameIdentity(
        val appId: Int? = null,
        val gogId: String? = null,
        val name: String? = null,
        val source: Source = Source.NONE,
        val confidence: Confidence = Confidence.LOW,
        val nameSource: Source = Source.NONE,
    )

    private const val MAX_PARENT_DEPTH = 6
    private const val MAX_TEXT_BYTES = 1024L * 1024L

    /** Folders an exe sits in below its game folder ("Binaries/Win64"). */
    private val BUILD_DIRS = setOf(
        "bin", "bin32", "bin64", "binaries", "win32", "win64", "x86", "x64", "x86_64", "amd64",
        "retail", "shipping", "release",
    )

    /** Folders that hold games rather than being one. */
    private val NOT_A_GAME_FOLDER = setOf(
        "games", "game", "download", "downloads", "desktop", "documents", "common", "steamapps",
        "program files", "program files (x86)", "0", "emulated", "sdcard", "storage", "files",
    )

    private val TRUSTED_NAMES = setOf(Source.STEAM_MANIFEST_ACF, Source.GOG_INFO, Source.PE_VERSION)

    private val REPACK_TOKENS = setOf(
        "fitgirl", "dodi", "codex", "plaza", "skidrow", "rune", "empress", "flt", "tenoke",
        "reloaded", "elamigos", "razor1911", "hoodlum", "gog", "repack", "multi", "goldberg",
        "ankergames", "steamrip", "onlinefix", "online-fix", "gload", "fitgirlrepacks",
    )

    private const val EXE_NOISE_WORDS =
        """win64|win32|shipping|wingdk|client|launcher|game|x64|x86|64bit|32bit"""

    /** Unanchored: decides whether an exe name is engine/arch boilerplate. */
    private val EXE_NOISE_RE = Regex("""(?i)[-_. ]*(?:$EXE_NOISE_WORDS)""")

    /** Anchored: strips trailing engine/arch noise from a title. */
    private val EXE_SUFFIX_RE = Regex("""(?i)(?:[-_. ]*(?:$EXE_NOISE_WORDS))+$""")

    private val VERSION_TOKEN_RE = Regex("""(?i)\b(v?\d+([._]\d+)+|build[-_ ]?\d+|update[-_ ]?\d+)\b""")

    /**
     * A PE name that describes a launcher/loader rather than the game. Keep this close to
     * Bannerlator's list, plus generic engine labels that are never a title.
     */
    private val JUNK_NAME_RE = Regex(
        """(?i)^(gse|steam|steam client|goldberg.*|launcher|game|play|start|setup|application|""" +
            """program|unity|unityplayer|unreal|unrealengine|.* launcher.*|.*launcher redirector|""" +
            """.* redirector|rockstar games launcher.*|epic games launcher|smartsteamemu|""" +
            """coldclient.*|steamemu.*)$"""
    )

    /** Identify [exeFile] from disk. Never throws; returns an empty identity if nothing matched. */
    @JvmStatic
    fun identify(exeFile: File): GameIdentity {
        val dirs = ancestorDirs(exeFile)
        val steamAppId = dirs.firstNotNullOfOrNull { readSteamAppIdTxt(it) }
        val emuAppId = dirs.firstNotNullOfOrNull { readEmuIniAppId(it) }
        val acf = resolveSteamManifest(dirs)
        val gog = dirs.firstNotNullOfOrNull { readGogInfo(it) }
        val peName = peName(exeFile)?.takeUnless { isJunkPeName(it) }

        val appId = acf?.appId ?: steamAppId ?: emuAppId

        val nameHit: Pair<String, Source>? = when {
            !acf?.name.isNullOrBlank() -> acf!!.name!! to Source.STEAM_MANIFEST_ACF
            !gog?.name.isNullOrBlank() -> gog!!.name!! to Source.GOG_INFO
            peName != null -> peName to Source.PE_VERSION
            else -> cleanName(exeFile.nameWithoutExtension)?.let { it to Source.FILENAME }
        }

        val primary: Source = when {
            acf?.appId != null -> Source.STEAM_MANIFEST_ACF
            steamAppId != null -> Source.STEAM_APPID_TXT
            emuAppId != null -> Source.STEAM_EMU_INI
            else -> nameHit?.second ?: Source.NONE
        }

        val confidence = when {
            appId != null -> Confidence.HIGH
            nameHit?.second == Source.GOG_INFO -> Confidence.HIGH
            nameHit?.second == Source.PE_VERSION -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        return GameIdentity(
            appId = appId,
            gogId = gog?.id,
            name = nameHit?.first?.let(::normalizeName)?.takeIf { it.isNotBlank() },
            source = primary,
            confidence = confidence,
            nameSource = nameHit?.second ?: Source.NONE,
        )
    }

    /**
     * The name an added game should show: the game's own title when disk names one reliably,
     * else the game folder's cleaned name, else the cleaned exe name. Null only when every
     * source is empty.
     */
    @JvmStatic
    fun suggestName(exeFile: File): String? {
        val identity = identify(exeFile)
        return displayName(identity, exeFile)
            ?: cleanName(exeFile.nameWithoutExtension)?.let(::normalizeName)?.takeIf { it.isNotBlank() }
    }

    @JvmStatic
    fun displayName(identity: GameIdentity, exe: File, folder: File? = gameFolderOf(exe)): String? {
        val folderName = folder?.name?.trim()
            ?.takeUnless { it.isEmpty() || it.lowercase(Locale.US) in NOT_A_GAME_FOLDER }
        val trusted = identity.name?.takeIf { identity.nameSource in TRUSTED_NAMES && it.isNotBlank() }
        if (trusted != null) {
            if (identity.nameSource != Source.PE_VERSION) return normalizeName(trusted)
            val spaced = underscoresToSpaces(trusted)
            // An Unreal game's version resource names its project, often a codename:
            // when it shares nothing with the game folder's name, the folder names the game.
            if (isUnrealShipping(exe) && folderName != null) {
                fun key(s: String) = s.lowercase(Locale.US).filter { it.isLetterOrDigit() }
                val a = key(spaced)
                val b = key(folderName)
                if (a.isNotEmpty() && b.isNotEmpty() && a !in b && b !in a) {
                    cleanName(folderName)?.let(::normalizeName)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            return normalizeName(folderName?.let { betterSpelling(spaced, it) } ?: spaced)
                .ifBlank { trusted }
        }
        // Фолбэк без доверенного ресурса: имя папки осмысленнее имени exe-файла
        // («Assassins Creed Rogue» вместо «ACC»), по нему же матчатся обложки
        return folderName?.let { cleanName(it) }?.let(::normalizeName)?.takeIf { it.isNotBlank() }
            ?: identity.name?.takeIf { it.isNotBlank() }?.let(::normalizeName)
    }

    /** The appId the game's own files declare, without walking the network. */
    @JvmStatic
    fun resolveAppIdFromDisk(exeFile: File): Int? {
        val dirs = ancestorDirs(exeFile)
        return resolveSteamManifest(dirs)?.appId
            ?: dirs.firstNotNullOfOrNull { readSteamAppIdTxt(it) }
            ?: dirs.firstNotNullOfOrNull { readEmuIniAppId(it) }
    }

    /** Strip brackets, repack tags, version tokens and engine suffixes; make a search-friendly title. */
    @JvmStatic
    fun cleanTitle(raw: String): String {
        return cleanName(raw) ?: raw.trim()
    }

    /**
     * Make a title readable and filesystem-safe (the shortcut name is its .desktop filename):
     * removes trademark marks, turns ':' into " - ", drops illegal characters and trailing
     * generic descriptors ("DiRT 3 Executable" -> "DiRT 3").
     */
    @JvmStatic
    fun normalizeName(raw: String): String {
        var s = raw
        s = s.replace("\u2122", "").replace("\u00AE", "").replace("\u00A9", "")
        s = s.replace(Regex("""(?i)\(\s*(tm|r|c)\s*\)"""), "")
        s = s.replace(":", " - ")
        s = s.replace(Regex("""[\\/]"""), " ")
        s = s.replace(Regex("""["*?<>|]"""), "")
        s = s.replace(Regex("""[\u0000-\u001F]"""), " ")
        s = s.replace(
            Regex("""(?i)[ \-]+(executable|application|launcher|redistributable|installer|setup)$"""),
            "",
        )
        s = s.replace(Regex("""\s+"""), " ").trim()
        return s.trim(' ', '-').trim()
    }

    /** "Watch_Dogs" -> "Watch Dogs": an underscore in a version-resource title is a programmer's space. */
    @JvmStatic
    fun underscoresToSpaces(name: String): String =
        name.replace('_', ' ').replace(Regex("""\s+"""), " ").trim().ifEmpty { name }

    /**
     * The exe's version resource often spells the title the way a programmer typed it ("Insane2")
     * while the folder has the real spacing ("Insane 2"). When both spell the same letters and
     * digits, the one with more word breaks wins; a tie keeps mixed case over all one case.
     */
    @JvmStatic
    fun betterSpelling(peName: String, folderName: String): String {
        fun key(s: String) = s.lowercase(Locale.US).filter { it.isLetterOrDigit() }
        if (key(peName) != key(folderName) || folderName.isBlank()) return peName
        fun breaks(s: String) = s.count { !it.isLetterOrDigit() }
        fun mixed(s: String) = s.any { it.isUpperCase() } && s.any { it.isLowerCase() }
        return when {
            breaks(folderName) > breaks(peName) -> folderName.trim()
            breaks(folderName) < breaks(peName) -> peName
            mixed(folderName) && !mixed(peName) -> folderName.trim()
            else -> peName
        }
    }

    /** True when an exe basename looks like a launcher/loader rather than the game itself. */
    @JvmStatic
    fun isLauncherExeName(base: String): Boolean {
        val lower = base.lowercase(Locale.US)
        if (lower == "gse" || lower.endsWith("launcher") || lower.endsWith("redirector")) return true
        for (p in arrayOf("play", "launch", "start", "run")) {
            if (!lower.startsWith(p)) continue
            val rest = base.substring(p.length)
            if (rest.isEmpty()) return true
            val c = rest[0]
            if (!c.isLetterOrDigit() || c.isUpperCase()) return true
        }
        return false
    }

    /** True when a PE name is a launcher/loader label rather than a real game title. */
    @JvmStatic
    fun isJunkPeName(name: String): Boolean {
        val n = name.trim()
        return n.isEmpty() || JUNK_NAME_RE.matches(n)
    }

    /** The folder an exe's game lives in: its own, or above it past build folders such as Binaries/Win64. */
    @JvmStatic
    fun gameFolderOf(exe: File): File? {
        var dir = exe.absoluteFile.parentFile
        repeat(3) { if (dir != null && dir!!.name.lowercase(Locale.US) in BUILD_DIRS) dir = dir!!.parentFile }
        if (isUnrealShipping(exe)) {
            val parent = dir?.parentFile
            if (parent != null && File(parent, "Engine").isDirectory) dir = parent
        }
        return dir
    }

    // ── directory walk / signal readers (best-effort; null on anything unexpected) ──

    private fun ancestorDirs(exeFile: File): List<File> {
        val out = ArrayList<File>(MAX_PARENT_DEPTH + 1)
        var d: File? = exeFile.absoluteFile.parentFile
        var depth = 0
        while (d != null && depth <= MAX_PARENT_DEPTH) {
            out.add(d)
            d = d.parentFile
            depth++
        }
        return out
    }

    private fun isUnrealShipping(exe: File): Boolean =
        exe.nameWithoutExtension.let { it.endsWith("-Win64-Shipping", true) || it.endsWith("-Win32-Shipping", true) }

    private fun readSteamAppIdTxt(dir: File): Int? {
        for (candidate in arrayOf(File(dir, "steam_appid.txt"), File(dir, "steam_settings/steam_appid.txt"))) {
            val id = readSmallTextFile(candidate)?.removePrefix("\uFEFF")?.trim()
                ?.takeWhile { it.isDigit() }?.toIntOrNull()
            if (id != null && id > 0) return id
        }
        return null
    }

    /**
     * First `AppId=<n>` in a crack/emu config .ini next to the game (ColdClientLoader, steam_emu,
     * flt, hlm, ...). Known names are tried first, then any other .ini in the dir; game settings
     * .ini files do not carry an AppId key.
     */
    private fun readEmuIniAppId(dir: File): Int? {
        val re = Regex("""(?im)^\s*appid\s*=\s*(\d+)""")
        val named = arrayOf(
            "ColdClientLoader.ini", "steam_emu.ini", "flt.ini", "hlm.ini",
            "valve.ini", "cream_api.ini", "SmartSteamEmu.ini", "GreenLuma.ini",
        )
        val seen = HashSet<String>()
        val candidates = ArrayList<File>()
        for (n in named) {
            val f = File(dir, n)
            if (f.isFile) {
                candidates.add(f)
                seen.add(n.lowercase(Locale.US))
            }
        }
        val others = dir.listFiles { f ->
            f.isFile && f.name.endsWith(".ini", ignoreCase = true) &&
                f.name.lowercase(Locale.US) !in seen
        } ?: emptyArray()
        others.sortedBy { it.name.lowercase(Locale.US) }.forEach { candidates.add(it) }
        for (f in candidates) {
            val text = readSmallTextFile(f) ?: continue
            val id = re.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (id != null && id > 0) return id
        }
        return null
    }

    private data class Acf(val appId: Int?, val name: String?)

    /**
     * Find the appmanifest for this game: locate the `common` ancestor, read the sibling
     * `steamapps/appmanifest_*.acf`, and match installdir == the game folder under common.
     */
    private fun resolveSteamManifest(dirs: List<File>): Acf? {
        val commonIdx = dirs.indexOfFirst { it.name.equals("common", ignoreCase = true) }
        if (commonIdx < 0) return null
        val commonDir = dirs[commonIdx]
        val steamapps = commonDir.parentFile ?: return null
        val manifests = steamapps.listFiles { f ->
            f.isFile && f.name.startsWith("appmanifest_") && f.name.endsWith(".acf")
        } ?: return null
        if (manifests.isEmpty()) return null

        val gameFolder = if (commonIdx > 0) dirs[commonIdx - 1].name else null
        val match = manifests.firstOrNull { m ->
            val text = readSmallTextFile(m) ?: return@firstOrNull false
            val installdir = vdfValue(text, "installdir")
            gameFolder != null && installdir != null && installdir.equals(gameFolder, ignoreCase = true)
        } ?: return null

        val text = readSmallTextFile(match) ?: return null
        val appId = vdfValue(text, "appid")?.toIntOrNull()?.takeIf { it > 0 }
        val name = vdfValue(text, "name")?.takeIf { it.isNotBlank() }
        return if (appId == null && name == null) null else Acf(appId, name)
    }

    private data class Gog(val id: String?, val name: String?)

    private fun readGogInfo(dir: File): Gog? {
        val files = dir.listFiles { f ->
            f.isFile && f.name.startsWith("goggame-") && f.name.endsWith(".info")
        } ?: return null
        for (f in files) {
            val text = readSmallTextFile(f) ?: continue
            val name = jsonStringValue(text, "name")?.takeIf { it.isNotBlank() } ?: continue
            val id = f.name.removePrefix("goggame-").removeSuffix(".info").takeIf { it.isNotBlank() }
            return Gog(id, name)
        }
        return null
    }

    private fun peName(exe: File): String? {
        return try {
            val info = PEParser.getFileVersionInfo(exe) ?: return null
            bestPeName(info.ProductName, info.FileDescription)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * ProductName is usually the cleanest title, but is sometimes an internal abbreviation
     * (God of War ships ProductName "GoW" while FileDescription is "God of War"). When
     * ProductName is a single spaceless token and FileDescription is a normal multi-word
     * title, prefer FileDescription.
     */
    private fun bestPeName(productRaw: String?, descriptionRaw: String?): String? {
        val product = productRaw?.trim()?.takeIf { it.isNotEmpty() }
        val desc = descriptionRaw?.trim()?.takeIf { it.isNotEmpty() }
        val chosen = if (product != null && desc != null &&
            !product.contains(' ') && desc.contains(' ') && desc.length <= 60
        ) desc else product ?: desc
        return chosen?.takeUnless { isJunkPeName(it) }
    }

    /** Strip bracketed tags, repack-group names, engine/arch suffixes and version tokens; tidy separators. */
    private fun cleanName(raw: String): String? {
        var s = raw
        s = s.replace(Regex("""[\[(][^\])]*[\])]"""), " ")
        s = VERSION_TOKEN_RE.replace(s, " ")
        s = EXE_SUFFIX_RE.replace(s, " ")
        // Keep an explicit subtitle break (" - ") so callers can tell a base game from a
        // DLC/bundle suffix. A hyphen inside a word (Half-Life) is still normalized to a space.
        s = s.replace(Regex("""\s+-\s+"""), " : ")
        s = s.replace(Regex("""[._]+"""), " ")
        s = s.split(Regex("""[\s-]+"""))
            .filter { it.isNotBlank() && it.lowercase(Locale.US) !in REPACK_TOKENS }
            .joinToString(" ")
        s = s.trim()
        return s.ifBlank { null }
    }

    private fun readSmallTextFile(f: File, maxBytes: Long = MAX_TEXT_BYTES): String? = try {
        val len = f.length()
        if (f.isFile && len > 0 && len <= maxBytes) f.readText(Charsets.UTF_8) else null
    } catch (_: Throwable) {
        null
    }

    /** Value of a Valve KeyValues (.acf/.vdf) `"key"  "value"` pair, first match, case-insensitive key. */
    private fun vdfValue(text: String, key: String): String? =
        Regex(""""${Regex.escape(key)}"\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()

    /** Value of a JSON `"key": "value"` pair; sufficient for flat goggame .info files. */
    private fun jsonStringValue(text: String, key: String): String? =
        Regex(""""${Regex.escape(key)}"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
            .find(text)?.groupValues?.getOrNull(1)
            ?.replace("\\\"", "\"")?.replace("\\\\", "\\")?.trim()
}
