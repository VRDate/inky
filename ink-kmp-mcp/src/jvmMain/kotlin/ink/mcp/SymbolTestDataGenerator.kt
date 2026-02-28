package ink.mcp

import org.slf4j.LoggerFactory

/**
 * Generates test data files in emoji-test.txt format from UnicodeData.txt blocks.
 *
 * This normalizes UnicodeData entries (IPA, math symbols, currency, etc.) into the
 * same format that [UnicodeSymbolParser.parseEmojiTest] can consume, enabling a
 * unified parsing pipeline for all symbol types.
 *
 * Output format:
 *   # group: IPA Extensions
 *   # subgroup: vowels
 *   0250                                       ; fully-qualified     # ɐ -- LATIN SMALL LETTER TURNED A
 */
class SymbolTestDataGenerator(
    private val parser: UnicodeSymbolParser = UnicodeSymbolParser(),
    private val loader: UnicodeDataLoader = UnicodeDataLoader()
) {
    private val log = LoggerFactory.getLogger(SymbolTestDataGenerator::class.java)

    /**
     * Generate emoji-test.txt-format content for a UnicodeData.txt block.
     *
     * @param blockName display name for the group header
     * @param range Unicode codepoint range
     * @param subgroupMapper optional function to categorize entries into subgroups
     */
    fun generateEmojiTestFormat(
        blockName: String,
        range: IntRange,
        subgroupMapper: (UnicodeSymbolParser.UnicodeEntry) -> String = { blockName }
    ): String {
        val lines = loader.loadUnicodeData()
        if (lines.isEmpty()) {
            log.warn("No UnicodeData.txt available for generation")
            return ""
        }
        val result = parser.parseUnicodeData(lines, mapOf(blockName to range))

        return buildString {
            appendLine("""
                # Generated from UnicodeData.txt for block: $blockName
                # group: $blockName
            """.trimIndent())

            var currentSubgroup = ""
            for (entry in result.entries) {
                val subgroup = subgroupMapper(entry)
                if (subgroup != currentSubgroup) {
                    currentSubgroup = subgroup
                    appendLine("# subgroup: $subgroup")
                }
                val hex = entry.codePoints.joinToString(" ") { "%04X".format(it) }
                appendLine("%-40s ; fully-qualified     # %s -- %s".format(
                    hex, entry.symbol, entry.name
                ))
            }
        }
    }

    /** Generate IPA Extensions in emoji-test.txt format with phonetic subgroups */
    fun generateIpaTestData(): String = generateEmojiTestFormat(
        "IPA Extensions",
        0x0250..0x02AF
    ) { entry ->
        // Categorize IPA symbols by general category
        when {
            entry.name.contains("DIGRAPH") -> "digraphs"
            entry.name.contains("CLICK") || entry.name.contains("PERCUSSIVE") -> "clicks"
            entry.name.contains("GLOTTAL") || entry.name.contains("PHARYNGEAL") -> "glottals"
            entry.name.contains("SMALL CAPITAL") -> "small-capitals"
            entry.name.contains("TURNED") || entry.name.contains("REVERSED") -> "rotated"
            entry.name.contains("HOOK") || entry.name.contains("CURL") ||
                entry.name.contains("TAIL") || entry.name.contains("BELT") -> "modified"
            else -> "base"
        }
    }

    /** Generate Currency Symbols in emoji-test.txt format */
    fun generateCurrencyTestData(): String = generateEmojiTestFormat(
        "Currency Symbols",
        0x20A0..0x20CF
    )

    /** Generate Spacing Modifier Letters in emoji-test.txt format */
    fun generateSpacingModifierTestData(): String = generateEmojiTestFormat(
        "Spacing Modifier Letters",
        0x02B0..0x02FF
    ) { entry ->
        when {
            entry.name.contains("MODIFIER LETTER") -> "modifier-letters"
            entry.name.contains("MODIFIER") -> "modifiers"
            else -> "other"
        }
    }
}
