package ink.mcp

/**
 * Parser for Unicode consortium data files:
 *   - emoji-test.txt  (group/subgroup/emoji hierarchy)
 *   - UnicodeData.txt (full Unicode character database, semicolon-delimited)
 *
 * Produces [UnicodeEntry] records that [EmojiAssetManifest] consumes
 * to auto-populate symbol categories.
 *
 * Permanent URLs:
 *   - https://unicode.org/Public/emoji/latest/emoji-test.txt
 *   - https://unicode.org/Public/UNIDATA/UnicodeData.txt
 */
class UnicodeSymbolParser {

    /** A single parsed Unicode entry (emoji or symbol). */
    data class UnicodeEntry(
        val codePoints: List<Int>,
        val symbol: String,
        val name: String,
        val group: String,
        val subgroup: String,
        val status: Status = Status.FULLY_QUALIFIED,
        val version: String = "",
        val generalCategory: String = ""
    )

    enum class Status {
        FULLY_QUALIFIED, MINIMALLY_QUALIFIED, UNQUALIFIED, COMPONENT
    }

    /** Parsed result with group hierarchy preserved. */
    data class ParseResult(
        val entries: List<UnicodeEntry>,
        val groups: Map<String, List<String>>
    )

    // ── emoji-test.txt parser ──────────────────────────────────

    /**
     * Parse emoji-test.txt format.
     *
     * Line format: `code points ; status # emoji E-version name`
     * Group headers: `# group: Smileys & Emotion`
     * Subgroup headers: `# subgroup: face-smiling`
     */
    fun parseEmojiTest(lines: List<String>): ParseResult {
        val entries = mutableListOf<UnicodeEntry>()
        val groups = mutableMapOf<String, MutableList<String>>()
        var currentGroup = ""
        var currentSubgroup = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# group:") -> {
                    currentGroup = trimmed.removePrefix("# group:").trim()
                    groups.getOrPut(currentGroup) { mutableListOf() }
                }
                trimmed.startsWith("# subgroup:") -> {
                    currentSubgroup = trimmed.removePrefix("# subgroup:").trim()
                    groups[currentGroup]?.add(currentSubgroup)
                }
                trimmed.isEmpty() || trimmed.startsWith("#") -> { /* skip comments and blanks */ }
                trimmed.contains(";") -> {
                    val entry = parseEmojiTestLine(trimmed, currentGroup, currentSubgroup)
                    if (entry != null) entries.add(entry)
                }
            }
        }

        return ParseResult(entries, groups)
    }

    private fun parseEmojiTestLine(line: String, group: String, subgroup: String): UnicodeEntry? {
        // Format: "1F600                                      ; fully-qualified     # 😀 E1.0 grinning face"
        val semiIdx = line.indexOf(';')
        val hashIdx = line.indexOf('#', semiIdx)
        if (semiIdx < 0 || hashIdx < 0) return null

        val codePointsStr = line.substring(0, semiIdx).trim()
        val statusStr = line.substring(semiIdx + 1, hashIdx).trim()
        val afterHash = line.substring(hashIdx + 1).trim()

        val codePoints = codePointsStr.split(" ")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull(16) }
        if (codePoints.isEmpty()) return null

        val symbol = codePoints.joinToString("") { codePointToString(it) }

        // afterHash: "😀 E1.0 grinning face"
        // First token is the rendered emoji, second may be E-version, rest is name
        // Skip the first token (emoji) by finding the first space after it
        val tokens = afterHash.split(" ").filter { it.isNotBlank() }
        val versionIdx = tokens.indexOfFirst { it.startsWith("E") && it.length > 1 && it[1].isDigit() }

        val version: String
        val name: String
        if (versionIdx >= 0) {
            version = tokens[versionIdx]
            name = tokens.drop(versionIdx + 1).joinToString(" ")
        } else {
            version = ""
            name = tokens.drop(1).joinToString(" ") // skip emoji token
        }

        return UnicodeEntry(
            codePoints = codePoints,
            symbol = symbol,
            name = name,
            group = group,
            subgroup = subgroup,
            status = parseStatus(statusStr),
            version = version
        )
    }

    // ── UnicodeData.txt parser ─────────────────────────────────

    /**
     * Parse UnicodeData.txt format (semicolon-delimited, 15 fields).
     * Filters to specified Unicode blocks.
     *
     * @param lines raw file lines
     * @param blocks map of block-name to codepoint range
     */
    fun parseUnicodeData(
        lines: List<String>,
        blocks: Map<String, IntRange>
    ): ParseResult {
        val entries = mutableListOf<UnicodeEntry>()
        val groups = mutableMapOf<String, MutableList<String>>()

        // Build inverted index: codepoint → block name
        val cpToBlock = mutableMapOf<Int, String>()
        for ((name, range) in blocks) {
            groups[name] = mutableListOf(name)
            for (cp in range) cpToBlock[cp] = name
        }

        for (line in lines) {
            if (line.isBlank()) continue
            val fields = line.split(";")
            if (fields.size < 3) continue

            val cp = fields[0].trim().toIntOrNull(16) ?: continue
            val blockName = cpToBlock[cp] ?: continue

            val name = fields[1].trim()
            val generalCategory = fields[2].trim()

            // Skip <control>, <reserved>, range markers
            if (name.startsWith("<")) continue

            val symbol = codePointToString(cp)
            entries.add(UnicodeEntry(
                codePoints = listOf(cp),
                symbol = symbol,
                name = name,
                group = blockName,
                subgroup = blockName,
                generalCategory = generalCategory
            ))
        }

        return ParseResult(entries, groups)
    }

    companion object {
        /** Convert a Unicode code point to a String (multiplatform-safe). */
        private fun codePointToString(cp: Int): String = buildString {
            if (cp <= 0xFFFF) {
                append(cp.toChar())
            } else {
                val offset = cp - 0x10000
                append((0xD800 + (offset shr 10)).toChar())
                append((0xDC00 + (offset and 0x3FF)).toChar())
            }
        }

        /** Well-known Unicode blocks for symbol parsing. */
        val IPA_EXTENSIONS = "IPA Extensions" to (0x0250..0x02AF)
        val SPACING_MODIFIER_LETTERS = "Spacing Modifier Letters" to (0x02B0..0x02FF)
        val COMBINING_DIACRITICAL = "Combining Diacritical Marks" to (0x0300..0x036F)
        val PHONETIC_EXTENSIONS = "Phonetic Extensions" to (0x1D00..0x1D7F)
        val PHONETIC_EXTENSIONS_SUPPLEMENT = "Phonetic Extensions Supplement" to (0x1D80..0x1DBF)
        val MATHEMATICAL_OPERATORS = "Mathematical Operators" to (0x2200..0x22FF)
        val MISCELLANEOUS_SYMBOLS = "Miscellaneous Symbols" to (0x2600..0x26FF)
        val DINGBATS = "Dingbats" to (0x2700..0x27BF)
        val CURRENCY_SYMBOLS = "Currency Symbols" to (0x20A0..0x20CF)

        /** All default blocks to parse from UnicodeData.txt */
        val DEFAULT_BLOCKS = mapOf(
            IPA_EXTENSIONS,
            SPACING_MODIFIER_LETTERS,
            PHONETIC_EXTENSIONS,
            PHONETIC_EXTENSIONS_SUPPLEMENT,
            MATHEMATICAL_OPERATORS,
            MISCELLANEOUS_SYMBOLS,
            DINGBATS,
            CURRENCY_SYMBOLS
        )

        fun parseStatus(s: String): Status = when (s.trim()) {
            "fully-qualified" -> Status.FULLY_QUALIFIED
            "minimally-qualified" -> Status.MINIMALLY_QUALIFIED
            "unqualified" -> Status.UNQUALIFIED
            "component" -> Status.COMPONENT
            else -> Status.FULLY_QUALIFIED
        }
    }
}
