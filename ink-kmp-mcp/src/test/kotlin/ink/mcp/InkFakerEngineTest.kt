package ink.mcp

import kotlin.test.*

class InkFakerEngineTest {

    private val manifest = EmojiAssetManifest()
    private val engine = InkFakerEngine(manifest)
    private val mdEngine = InkMdEngine()

    // ── generateItems ────────────────────────────────────────────

    @Test
    fun `generateItems produces MdTable with correct columns`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 3)
        val table = engine.generateItems(config)

        assertEquals("items", table.name)
        assertEquals(listOf("emoji", "name", "type", "base_dmg", "per_level", "level", "total_dmg"), table.columns)
        assertEquals(3, table.rows.size)
    }

    @Test
    fun `generateItems is deterministic with same seed`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 3)
        val table1 = engine.generateItems(config)
        val table2 = engine.generateItems(config)

        assertEquals(table1.rows, table2.rows, "Same seed should produce identical rows")
    }

    @Test
    fun `generateItems different seed produces different data`() {
        val table1 = engine.generateItems(InkFakerEngine.FakerConfig(seed = 42, count = 3))
        val table2 = engine.generateItems(InkFakerEngine.FakerConfig(seed = 99, count = 3))

        assertNotEquals(table1.rows, table2.rows, "Different seeds should produce different rows")
    }

    @Test
    fun `generateItems has formula in total_dmg column`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 2)
        val table = engine.generateItems(config)

        for (row in table.rows) {
            val totalDmg = row["total_dmg"]!!
            assertTrue(totalDmg.startsWith("="), "total_dmg should be a formula: $totalDmg")
        }
    }

    @Test
    fun `generateItems respects category filter`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 3, categories = listOf("potion"))
        val table = engine.generateItems(config)

        for (row in table.rows) {
            assertEquals("⚗️", row["emoji"], "All items should be potion emoji")
        }
    }

    // ── generateCharacters ───────────────────────────────────────

    @Test
    fun `generateCharacters produces DnD stat columns`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 3)
        val table = engine.generateCharacters(config)

        assertEquals("characters", table.name)
        assertTrue("STR" in table.columns)
        assertTrue("DEX" in table.columns)
        assertTrue("CON" in table.columns)
        assertTrue("INT" in table.columns)
        assertTrue("WIS" in table.columns)
        assertTrue("CHA" in table.columns)
        assertTrue("HP" in table.columns)
        assertEquals(3, table.rows.size)
    }

    @Test
    fun `generateCharacters stats in valid range`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 5)
        val table = engine.generateCharacters(config)

        for (row in table.rows) {
            for (stat in listOf("STR", "DEX", "CON", "INT", "WIS", "CHA")) {
                val value = row[stat]!!.toInt()
                assertTrue(value in 3..18, "$stat=$value should be in 3..18")
            }
        }
    }

    @Test
    fun `generateCharacters HP is formula`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 2)
        val table = engine.generateCharacters(config)

        for (row in table.rows) {
            assertTrue(row["HP"]!!.startsWith("="), "HP should be a formula")
        }
    }

    // ── evaluateFormulas ─────────────────────────────────────────

    @Test
    fun `evaluateFormulas computes arithmetic`() {
        val table = InkMdEngine.MdTable(
            name = "test",
            columns = listOf("a", "b", "result"),
            rows = listOf(
                mapOf("a" to "10", "b" to "5", "result" to "=A2+B2")
            )
        )
        val evaluated = engine.evaluateFormulas(table)
        assertEquals("15", evaluated.rows[0]["result"])
    }

    @Test
    fun `evaluateFormulas computes multiplication`() {
        val table = InkMdEngine.MdTable(
            name = "test",
            columns = listOf("base", "mult", "level", "total"),
            rows = listOf(
                mapOf("base" to "10", "mult" to "3", "level" to "2", "total" to "=A2+B2*C2")
            )
        )
        val evaluated = engine.evaluateFormulas(table)
        assertEquals("16", evaluated.rows[0]["total"])
    }

    @Test
    fun `evaluateFormulas handles SUM`() {
        val table = InkMdEngine.MdTable(
            name = "test",
            columns = listOf("val", "total"),
            rows = listOf(
                mapOf("val" to "10", "total" to "0"),
                mapOf("val" to "20", "total" to "0"),
                mapOf("val" to "30", "total" to "=SUM(A2:A4)")
            )
        )
        val evaluated = engine.evaluateFormulas(table)
        assertEquals("60", evaluated.rows[2]["total"])
    }

    @Test
    fun `evaluateFormulas preserves non-formula cells`() {
        val table = InkMdEngine.MdTable(
            name = "test",
            columns = listOf("name", "value"),
            rows = listOf(
                mapOf("name" to "sword", "value" to "42")
            )
        )
        val evaluated = engine.evaluateFormulas(table)
        assertEquals("sword", evaluated.rows[0]["name"])
        assertEquals("42", evaluated.rows[0]["value"])
    }

    @Test
    fun `evaluateFormulas returns same table when no formulas`() {
        val table = InkMdEngine.MdTable(
            name = "test",
            columns = listOf("a", "b"),
            rows = listOf(mapOf("a" to "hello", "b" to "world"))
        )
        val evaluated = engine.evaluateFormulas(table)
        assertEquals(table, evaluated)
    }

    // ── generateStoryMd ──────────────────────────────────────────

    @Test
    fun `generateStoryMd produces valid markdown`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 3, level = 2)
        val markdown = engine.generateStoryMd(config)

        assertTrue(markdown.contains("<!-- seed: 42, level: 2 -->"), "Should have seed comment")
        assertTrue(markdown.contains("# characters"), "Should have characters section")
        assertTrue(markdown.contains("# items"), "Should have items section")
        assertTrue(markdown.contains("```ink"), "Should have ink code blocks")
        assertTrue(markdown.contains("VAR player_name"), "Should have VAR declarations")
        assertTrue(markdown.contains("LIST inventory"), "Should have LIST declaration")
    }

    @Test
    fun `generateStoryMd is parseable by InkMdEngine`() {
        val config = InkFakerEngine.FakerConfig(seed = 42, count = 2)
        val markdown = engine.generateStoryMd(config)
        val parsed = mdEngine.parse(markdown)

        assertTrue(parsed.files.isNotEmpty(), "Should parse ink files from generated MD")
        assertTrue(parsed.tables.isNotEmpty(), "Should parse tables from generated MD")
    }

    // ── per-level modifiers ──────────────────────────────────────

    @Test
    fun `per-level modifiers with same seed produce different totals`() {
        val configL1 = InkFakerEngine.FakerConfig(seed = 42, count = 3, level = 1)
        val configL5 = InkFakerEngine.FakerConfig(seed = 42, count = 3, level = 5)

        val tableL1 = engine.generateItems(configL1)
        val tableL5 = engine.generateItems(configL5)

        // Same seed → same item names
        assertEquals(tableL1.rows[0]["name"], tableL5.rows[0]["name"],
            "Same seed should produce same item names")

        // Different level → different level column
        assertEquals("1", tableL1.rows[0]["level"])
        assertEquals("5", tableL5.rows[0]["level"])
    }
}
