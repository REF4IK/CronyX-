package com.winlator.cmod.core

import java.util.Locale

/**
 * Fuzzy title matching for SteamGridDB / Steam search results.
 *
 * The key trick (from Bannerlator's GameMatcher): split camelCase and letter/digit boundaries
 * before tokenizing, so "Witcher3" and "Crysis3Remastered" become real token sets. Edition and
 * repack noise is dropped from both sides, and a sequel marker mismatch ("Hades" vs "Hades II")
 * is rejected instead of silently picking the wrong game.
 */
object GameTitleMatcher {

    /** Edition / marketing / repack tokens dropped from both sides. "-the" is symmetric and safe. */
    private val NOISE_TOKENS = setOf(
        "the", "a", "an",
        "remastered", "remaster", "remake", "edition", "goty", "definitive", "deluxe",
        "complete", "enhanced", "ultimate", "hd", "gold", "collection", "anniversary",
        "directors", "cut", "repack", "reloaded", "codex", "steam", "tm",
        "fitgirl", "dodi", "plaza", "skidrow", "rune", "empress", "flt", "tenoke",
        "elamigos", "razor1911", "hoodlum", "gog", "multi", "goldberg",
        "ankergames", "steamrip", "onlinefix", "online-fix", "gload", "fitgirlrepacks",
    )

    private val EDITION_PHRASE_RE = Regex(
        """(?i)\b(game of the year|director'?s cut|complete edition|definitive edition|""" +
            """legendary edition|deluxe edition|ultimate edition|gold edition|enhanced edition|""" +
            """anniversary edition)\b"""
    )

    private val EDITION_WORD_RE = Regex(
        """(?i)\b(complete|definitive|legendary|goty|game of the year|deluxe|ultimate|""" +
            """remastered|remaster|enhanced|anniversary|gold|collection)\b"""
    )

    /** Tokens that distinguish a sequel. Roman numerals are canonicalised to digits. */
    private val SEQUEL_MARKERS = setOf("2", "3", "4", "5", "6", "7", "8", "9", "10")
    private val ROMAN = mapOf(
        "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5",
        "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10",
    )

    /** Extra content that should not win over the base game unless the query asks for it. */
    private val EXTRA_CONTENT_TOKENS = setOf(
        "dlc", "expansion", "soundtrack", "artbook", "demo", "beta", "trial", "bonus",
        "bundle", "pack", "addon", "season", "pass", "upgrade", "key", "preorder",
    )

    /** Compact aliases (letters/digits only). Keep the old ShortcutCoverFetcher list. */
    private val ALIASES = mapOf(
        "gta4" to "Grand Theft Auto IV",
        "gtaiv" to "Grand Theft Auto IV",
        "gta5" to "Grand Theft Auto V",
        "gtav" to "Grand Theft Auto V",
        "gtasa" to "Grand Theft Auto San Andreas",
        "gtavc" to "Grand Theft Auto Vice City",
        "gta3" to "Grand Theft Auto III",
        "gtaiii" to "Grand Theft Auto III",
        "gta6" to "Grand Theft Auto VI",
        "gtavi" to "Grand Theft Auto VI",
        "re1" to "Resident Evil",
        "re2" to "Resident Evil 2",
        "re3" to "Resident Evil 3",
        "re4" to "Resident Evil 4",
        "re5" to "Resident Evil 5",
        "re6" to "Resident Evil 6",
        "re7" to "Resident Evil 7",
        "re8" to "Resident Evil Village",
        "rdr" to "Red Dead Redemption",
        "rdr1" to "Red Dead Redemption",
        "rdr2" to "Red Dead Redemption 2",
        "doom2016" to "DOOM",
        "doom4" to "DOOM",
        "doometernal" to "Doom Eternal",
        "witcher1" to "The Witcher",
        "tw1" to "The Witcher",
        "witcher2" to "The Witcher 2",
        "tw2" to "The Witcher 2",
        "witcher3" to "The Witcher 3 Wild Hunt",
        "tw3" to "The Witcher 3 Wild Hunt",
        "kcd" to "Kingdom Come Deliverance",
        "kingdomcome" to "Kingdom Come Deliverance",
        "kcd2" to "Kingdom Come Deliverance II",
        "kingdomcome2" to "Kingdom Come Deliverance II",
        "cp2077" to "Cyberpunk 2077",
        "cyberpunk2077" to "Cyberpunk 2077",
        "d3" to "Diablo III",
        "diablo3" to "Diablo III",
        "d4" to "Diablo IV",
        "diablo4" to "Diablo IV",
        "hl1" to "Half-Life",
        "halflife" to "Half-Life",
        "hl2" to "Half-Life 2",
        "halflife2" to "Half-Life 2",
        "sms" to "Super Mario Sunshine",
        "supermarioshine" to "Super Mario Sunshine",
        "hades1" to "Hades",
    )

    /**
     * Split a raw title into normalized tokens: lowercases, breaks camelCase and letter/digit
     * boundaries, strips non-alphanumeric separators, drops edition/repack noise, canonicalises
     * standalone roman numerals.
     */
    @JvmStatic
    fun tokenize(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val cleaned = EDITION_PHRASE_RE.replace(raw, " ")
        val spaced = StringBuilder(cleaned.length * 2)
        val chars = cleaned.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            if (i > 0) {
                val prev = chars[i - 1]
                val boundary =
                    (c.isUpperCase() && !prev.isUpperCase()) ||
                        (c.isDigit() != prev.isDigit() && c.isLetterOrDigit() && prev.isLetterOrDigit())
                if (boundary) spaced.append(' ')
            }
            spaced.append(c)
        }
        return spaced.toString()
            .lowercase(Locale.US)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() && it !in NOISE_TOKENS }
            .map { ROMAN[it] ?: it }
    }

    /** Letters/digits-only key, used for cache keys and exact comparisons. */
    @JvmStatic
    fun normalize(raw: String): String = tokenize(raw).joinToString("")

    /**
     * How well [resultName] matches [queryName]:
     * 100 = same tokens, ~80 = the query is fully inside a longer official title
     * ("Witcher 3" -> "The Witcher 3: Wild Hunt"), ~70 = the result is a shorter base title,
     * 0 = no plausible match. Values below ~70 are intentionally not trusted by callers.
     */
    @JvmStatic
    fun matchScore(queryName: String, resultName: String): Int {
        val q = tokenize(queryName)
        val c = tokenize(resultName)
        if (q.isEmpty() || c.isEmpty()) return 0
        if (sequelMismatch(q, c)) return 0

        val qSet = q.toSet()
        val cSet = c.toSet()
        val qKey = q.joinToString("")
        val cKey = c.joinToString("")

        if (qKey == cKey) return 100

        val qInC = qSet.all { it in cSet }
        val cInQ = cSet.all { it in qSet }

        var score = when {
            qInC -> 80 - (c.size - q.size).coerceAtMost(10) * 2
            cInQ -> 70 - (q.size - c.size).coerceAtMost(10) * 2
            else -> {
                val matched = qSet.count { it in cSet }
                val coverage = matched.toDouble() / maxOf(qSet.size, cSet.size)
                when {
                    coverage >= 0.75 -> 50 + (coverage * 20).toInt()
                    qKey.contains(cKey) || cKey.contains(qKey) -> 55
                    else -> return 0
                }
            }
        }

        // Prefer titles that continue with the query over ones that only contain it.
        if (qInC && cKey.startsWith(qKey)) score += 5

        // A bare generic token ("Doom") should not beat its own exact result with a random
        // longer title; keep a modest penalty so exact matches always win.
        if (q.size == 1 && q[0].length < 4 && qKey != cKey) score -= 40

        // DLC / bundle pages are not the game itself. This must push them below the caller's
        // confidence threshold even when the base name is a perfect prefix ("Dying Light" vs
        // "Dying Light - Harran Tactical Unit Bundle").
        if (c.any { it in EXTRA_CONTENT_TOKENS } && q.none { it in EXTRA_CONTENT_TOKENS }) score -= 60

        return score.coerceIn(0, 100)
    }

    /**
     * Ordered search terms for Steam / SteamGridDB. Alias first (GTA/RE/RDR...), then the
     * cleaned title, then the same without edition words, then the base title before ":"/" - ".
     */
    @JvmStatic
    fun searchQueries(raw: String): List<String> {
        val cleaned = GameTitleParser.cleanTitle(raw).trim()
        if (cleaned.isEmpty()) return emptyList()
        val alias = aliasFor(cleaned)
        val edition = stripEdition(cleaned)
        val base = baseTitle(cleaned)
        val noThe = removeLeadingThe(edition)
        // A DLC/bundle suffix ("Dying Light - Harran Tactical Unit Bundle") must not become
        // the primary query: try the base game first.
        val ordered = if (base != cleaned && subtitleHasExtraContent(cleaned)) {
            listOfNotNull(alias, base, cleaned, edition, noThe)
        } else {
            listOfNotNull(alias, cleaned, edition, base, noThe)
        }
        return ordered
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinctBy { it.lowercase(Locale.US) }
            .take(4)
    }

    /** The first search query, or [raw] when nothing usable was produced. */
    @JvmStatic
    fun bestQuery(raw: String): String = searchQueries(raw).firstOrNull() ?: raw.trim()

    private fun aliasFor(raw: String): String? {
        val key = raw.lowercase(Locale.US).filter { it.isLetterOrDigit() }
        return ALIASES[key]
    }

    private fun stripEdition(raw: String): String {
        var s = EDITION_PHRASE_RE.replace(raw, " ")
        s = EDITION_WORD_RE.replace(s, " ")
        return s.replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('-', ':', '–', '—')
            .trim()
            .ifEmpty { raw }
    }

    private fun baseTitle(raw: String): String {
        val separators = listOf(":", " - ")
        var cut = raw
        for (sep in separators) {
            val idx = cut.indexOf(sep)
            if (idx >= 4) cut = cut.substring(0, idx).trim()
        }
        return cut.ifEmpty { raw }
    }

    private fun removeLeadingThe(raw: String): String =
        if (raw.startsWith("the ", ignoreCase = true)) raw.substring(4).trim() else raw

    /** True when the part after ':'/' - ' names DLC/bundle content rather than a subtitle. */
    private fun subtitleHasExtraContent(raw: String): Boolean {
        val base = baseTitle(raw)
        if (base == raw || base.isEmpty()) return false
        val tail = raw.substring(base.length)
        return tokenize(tail).any { it in EXTRA_CONTENT_TOKENS }
    }

    private fun sequelMismatch(q: List<String>, c: List<String>): Boolean {
        val qm = q.filter { it in SEQUEL_MARKERS }.toSet()
        val cm = c.filter { it in SEQUEL_MARKERS }.toSet()
        return qm != cm
    }
}
