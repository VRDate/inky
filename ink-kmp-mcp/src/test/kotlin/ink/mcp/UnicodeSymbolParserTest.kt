package ink.mcp

import kotlin.test.*

class UnicodeSymbolParserTest {

    private val parser = UnicodeSymbolParser()

    // ── emoji-test.txt parsing ────────────────────────────────

    @Test
    fun `parseEmojiTest extracts group hierarchy`() {
        val lines = loadSnippet("unicode/emoji-test-snippet.txt")
        val result = parser.parseEmojiTest(lines)

        assertTrue(result.groups.containsKey("Smileys & Emotion"))
        assertTrue(result.groups.containsKey("Objects"))
        assertTrue(result.groups.containsKey("Animals & Nature"))
        assertTrue(result.groups.containsKey("Symbols"))
        assertTrue(result.groups.containsKey("Flags"))
        assertTrue(result.groups.containsKey("Component"))

        val smileys = result.groups["Smileys & Emotion"]!!
        assertTrue("face-smiling" in smileys)
        assertTrue("face-affection" in smileys)
    }

    @Test
    fun `parseEmojiTest parses fully-qualified entries`() {
        val lines = loadSnippet("unicode/emoji-test-snippet.txt")
        val result = parser.parseEmojiTest(lines)

        val grinning = result.entries.find { it.name == "grinning face" }
        assertNotNull(grinning)
        assertEquals(listOf(0x1F600), grinning.codePoints)
        assertEquals("😀", grinning.symbol)
        assertEquals("Smileys & Emotion", grinning.group)
        assertEquals("face-smiling", grinning.subgroup)
        assertEquals(UnicodeSymbolParser.Status.FULLY_QUALIFIED, grinning.status)
        assertEquals("E1.0", grinning.version)
    }

    @Test
    fun `parseEmojiTest handles multi-codepoint sequences`() {
        val lines = loadSnippet("unicode/emoji-test-snippet.txt")
        val result = parser.parseEmojiTest(lines)

        val dagger = result.entries.find { it.name == "dagger" && it.codePoints.size == 2 }
        assertNotNull(dagger, "Should find fully-qualified dagger with FE0F")
        assertEquals(listOf(0x1F5E1, 0xFE0F), dagger.codePoints)
        assertEquals(UnicodeSymbolParser.Status.FULLY_QUALIFIED, dagger.status)
    }

    @Test
    fun `parseEmojiTest handles skin tone variants`() {
        val lines = loadSnippet("unicode/emoji-test-snippet.txt")
        val result = parser.parseEmojiTest(lines)

        val wavingHands = result.entries.filter { it.name.startsWith("waving hand") }
        assertTrue(wavingHands.size >= 3, "Should have base + skin tone variants")

        val lightSkinTone = wavingHands.find { it.codePoints.size == 2 && it.codePoints[1] == 0x1F3FB }
        assertNotNull(lightSkinTone, "Should find light skin tone variant")
    }

    @Test
    fun `parseEmojiTest distinguishes qualified and unqualified`() {
        val lines = loadSnippet("unicode/emoji-test-snippet.txt")
        val result = parser.parseEmojiTest(lines)

        val smilings = result.entries.filter { it.name == "smiling face" }
        assertEquals(2, smilings.size, "Should have both qualified and unqualified")
        assertTrue(smilings.any { it.status == UnicodeSymbolParser.Status.FULLY_QUALIFIED })
        assertTrue(smilings.any { it.status == UnicodeSymbolParser.Status.UNQUALIFIED })
    }

    @Test
    fun `parseEmojiTest identifies component status`() {
        val lines = loadSnippet("unicode/emoji-test-snippet.txt")
        val result = parser.parseEmojiTest(lines)

        val components = result.entries.filter { it.status == UnicodeSymbolParser.Status.COMPONENT }
        assertTrue(components.isNotEmpty(), "Should have component entries (skin tones)")
        assertTrue(components.all { it.group == "Component" })
    }

    @Test
    fun `parseEmojiTest extracts version correctly`() {
        val lines = loadSnippet("unicode/emoji-test-snippet.txt")
        val result = parser.parseEmojiTest(lines)

        val lion = result.entries.find { it.name == "lion" }
        assertNotNull(lion)
        assertEquals("E1.0", lion.version)

        val eagle = result.entries.find { it.name == "eagle" }
        assertNotNull(eagle)
        assertEquals("E3.0", eagle.version)
    }

    @Test
    fun `parseEmojiTest parses flag entries with regional indicators`() {
        val lines = loadSnippet("unicode/emoji-test-snippet.txt")
        val result = parser.parseEmojiTest(lines)

        val flags = result.entries.filter { it.group == "Flags" }
        assertTrue(flags.isNotEmpty())
        val israel = flags.find { it.name.contains("Israel") }
        assertNotNull(israel)
        assertEquals(listOf(0x1F1EE, 0x1F1F1), israel.codePoints)
    }

    // ── UnicodeData.txt parsing ───────────────────────────────

    @Test
    fun `parseUnicodeData extracts IPA Extensions`() {
        val lines = loadSnippet("unicode/UnicodeData-snippet.txt")
        val result = parser.parseUnicodeData(lines, mapOf(UnicodeSymbolParser.IPA_EXTENSIONS))

        assertTrue(result.entries.isNotEmpty())
        val schwa = result.entries.find { it.name == "LATIN SMALL LETTER SCHWA" }
        assertNotNull(schwa)
        assertEquals(listOf(0x0259), schwa.codePoints)
        assertEquals("ə", schwa.symbol)
        assertEquals("IPA Extensions", schwa.group)
        assertEquals("Ll", schwa.generalCategory)
    }

    @Test
    fun `parseUnicodeData extracts IPA digraphs`() {
        val lines = loadSnippet("unicode/UnicodeData-snippet.txt")
        val result = parser.parseUnicodeData(lines, mapOf(UnicodeSymbolParser.IPA_EXTENSIONS))

        val tesh = result.entries.find { it.name == "LATIN SMALL LETTER TESH DIGRAPH" }
        assertNotNull(tesh)
        assertEquals(listOf(0x02A7), tesh.codePoints)
    }

    @Test
    fun `parseUnicodeData filters to requested blocks only`() {
        val lines = loadSnippet("unicode/UnicodeData-snippet.txt")
        val result = parser.parseUnicodeData(lines, mapOf(UnicodeSymbolParser.IPA_EXTENSIONS))

        for (entry in result.entries) {
            val cp = entry.codePoints[0]
            assertTrue(cp in 0x0250..0x02AF,
                "Entry U+%04X should be in IPA Extensions range".format(cp))
        }
    }

    @Test
    fun `parseUnicodeData extracts Currency Symbols`() {
        val lines = loadSnippet("unicode/UnicodeData-snippet.txt")
        val result = parser.parseUnicodeData(lines, mapOf(UnicodeSymbolParser.CURRENCY_SYMBOLS))

        assertTrue(result.entries.isNotEmpty())
        val euro = result.entries.find { it.name == "EURO SIGN" }
        assertNotNull(euro)
        assertEquals(listOf(0x20AC), euro.codePoints)
        assertEquals("€", euro.symbol)
        assertEquals("Sc", euro.generalCategory)

        val sheqel = result.entries.find { it.name == "NEW SHEQEL SIGN" }
        assertNotNull(sheqel)
        assertEquals("₪", sheqel.symbol)

        val bitcoin = result.entries.find { it.name == "BITCOIN SIGN" }
        assertNotNull(bitcoin)
        assertEquals("₿", bitcoin.symbol)
    }

    @Test
    fun `parseUnicodeData parses multiple blocks simultaneously`() {
        val lines = loadSnippet("unicode/UnicodeData-snippet.txt")
        val blocks = mapOf(
            UnicodeSymbolParser.IPA_EXTENSIONS,
            UnicodeSymbolParser.CURRENCY_SYMBOLS
        )
        val result = parser.parseUnicodeData(lines, blocks)

        val ipaEntries = result.entries.filter { it.group == "IPA Extensions" }
        val currencyEntries = result.entries.filter { it.group == "Currency Symbols" }
        assertTrue(ipaEntries.isNotEmpty(), "Should have IPA entries")
        assertTrue(currencyEntries.isNotEmpty(), "Should have Currency entries")
    }

    @Test
    fun `parseUnicodeData extracts Spacing Modifier Letters`() {
        val lines = loadSnippet("unicode/UnicodeData-snippet.txt")
        val result = parser.parseUnicodeData(lines, mapOf(UnicodeSymbolParser.SPACING_MODIFIER_LETTERS))

        assertTrue(result.entries.isNotEmpty())
        val modH = result.entries.find { it.name == "MODIFIER LETTER SMALL H" }
        assertNotNull(modH)
        assertEquals(listOf(0x02B0), modH.codePoints)
        assertEquals("Lm", modH.generalCategory)
    }

    @Test
    fun `parseUnicodeData returns empty for non-matching blocks`() {
        val lines = loadSnippet("unicode/UnicodeData-snippet.txt")
        val result = parser.parseUnicodeData(lines, mapOf(
            "Nonexistent Block" to (0xFFFF..0xFFFF)
        ))
        assertTrue(result.entries.isEmpty())
    }

    // ── Status parsing ────────────────────────────────────────

    @Test
    fun `parseStatus handles all status values`() {
        assertEquals(UnicodeSymbolParser.Status.FULLY_QUALIFIED,
            UnicodeSymbolParser.parseStatus("fully-qualified"))
        assertEquals(UnicodeSymbolParser.Status.MINIMALLY_QUALIFIED,
            UnicodeSymbolParser.parseStatus("minimally-qualified"))
        assertEquals(UnicodeSymbolParser.Status.UNQUALIFIED,
            UnicodeSymbolParser.parseStatus("unqualified"))
        assertEquals(UnicodeSymbolParser.Status.COMPONENT,
            UnicodeSymbolParser.parseStatus("component"))
    }

    // ── helpers ───────────────────────────────────────────────

    private fun loadSnippet(resourcePath: String): List<String> {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Test resource not found: $resourcePath")
        return stream.bufferedReader().readLines()
    }
}
