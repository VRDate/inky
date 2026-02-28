package ink.mcp

import kotlin.test.*

/**
 * Unit tests for InkMdEngine — Ink+Markdown template engine.
 *
 * Tests: parse (ink blocks, tables, empty input, header levels, file naming),
 * render (VAR injection from single-row tables, LIST generation from multi-row
 * tables, passthrough when no matching table).
 */
class InkMdEngineTest {

    private val engine = InkMdEngine()

    companion object {
        /** Markdown with one ink block under an H1, plus a table. */
        private val singleBlockMd = """
            # characters

            | name    | role   | health |
            |---------|--------|--------|
            | Arthur  | knight | 100    |

            ```ink
            VAR player_name = "Arthur"
            === start ===
            Hello {player_name}!
            -> END
            ```
        """.trimIndent()

        /** Markdown with two ink blocks under different headers, plus two tables. */
        private val multiBlockMd = """
            # characters

            | name    | role   | health |
            |---------|--------|--------|
            | Arthur  | knight | 100    |

            ```ink
            VAR player_name = "Arthur"
            === start ===
            You are {player_name}.
            -> END
            ```

            # items

            | item   | price | effect     |
            |--------|-------|------------|
            | sword  | 50    | damage +10 |
            | potion | 25    | health +20 |

            ```ink
            LIST items = sword, potion
            === shop ===
            Welcome to the shop!
            -> END
            ```
        """.trimIndent()

        /** Markdown with nested headers (H1 + H2). */
        private val nestedHeaderMd = """
            # Story

            ## Chapter 1

            ```ink
            === chapter_1 ===
            It was a dark and stormy night.
            -> END
            ```

            ## Chapter 2

            ```ink
            === chapter_2 ===
            The sun rose over the hills.
            -> END
            ```
        """.trimIndent()

        /** Markdown with a table but no ink blocks. */
        private val tableOnlyMd = """
            # Config

            | Variable | Value |
            |----------|-------|
            | health   | 100   |
            | gold     | 50    |
        """.trimIndent()

        /** Markdown with a multi-row table sharing header name with ink block. */
        private val multiRowTableMd = """
            # inventory

            | item   | price | effect     |
            |--------|-------|------------|
            | sword  | 50    | damage +10 |
            | potion | 25    | health +20 |
            | shield | 75    | armor +15  |

            ```ink
            === shop ===
            What would you like to buy?
            -> END
            ```
        """.trimIndent()

        /** Markdown with a single-row table sharing header name with ink block. */
        private val singleRowTableMd = """
            # player

            | name   | class  | level |
            |--------|--------|-------|
            | Frodo  | hobbit | 5     |

            ```ink
            === intro ===
            Your journey begins.
            -> END
            ```
        """.trimIndent()
    }

    // ── parse: ink block extraction ──────────────────────────────

    @Test
    fun `parse extracts ink files from markdown`() {
        val result = engine.parse(singleBlockMd)
        assertEquals(1, result.files.size, "Should extract 1 ink file")

        val file = result.files[0]
        assertEquals("characters.ink", file.name, "File name should derive from H1 header")
        assertTrue(file.inkSource.contains("VAR player_name"),
            "Ink source should contain VAR declaration")
        assertTrue(file.inkSource.contains("=== start ==="),
            "Ink source should contain knot declaration")
        assertTrue(file.inkSource.contains("-> END"),
            "Ink source should contain divert to END")
    }

    @Test
    fun `parse extracts multiple ink files from markdown`() {
        val result = engine.parse(multiBlockMd)
        assertEquals(2, result.files.size, "Should extract 2 ink files")

        val names = result.files.map { it.name }
        assertTrue("characters.ink" in names, "Should have characters.ink")
        assertTrue("items.ink" in names, "Should have items.ink")

        val itemsFile = result.files.first { it.name == "items.ink" }
        assertTrue(itemsFile.inkSource.contains("LIST items"),
            "items.ink should contain LIST declaration")
    }

    @Test
    fun `parse assigns header level to ink files`() {
        val result = engine.parse(nestedHeaderMd)
        assertEquals(2, result.files.size, "Should extract 2 files from nested headers")

        // Both ink blocks are under H2 headers
        for (file in result.files) {
            assertEquals(2, file.headerLevel,
                "File '${file.name}' should have header level 2")
        }
    }

    @Test
    fun `parse uses header text as file name with ink suffix`() {
        val result = engine.parse(nestedHeaderMd)
        val names = result.files.map { it.name }
        assertTrue("Chapter 2.ink" in names, "Should use header text as filename")
    }

    @Test
    fun `parse does not duplicate ink suffix when header already ends with ink`() {
        val md = """
            # story.ink

            ```ink
            === start ===
            Hello!
            -> END
            ```
        """.trimIndent()
        val result = engine.parse(md)
        assertEquals(1, result.files.size)
        assertEquals("story.ink", result.files[0].name,
            "Should not produce 'story.ink.ink'")
    }

    // ── parse: table extraction ─────────────────────────────────

    @Test
    fun `parse extracts tables from markdown`() {
        val result = engine.parse(singleBlockMd)
        assertEquals(1, result.tables.size, "Should extract 1 table")

        val table = result.tables[0]
        assertEquals("characters", table.name, "Table name should come from header")
        assertEquals(listOf("name", "role", "health"), table.columns,
            "Should extract column headers")
        assertEquals(1, table.rows.size, "Should extract 1 data row")
        assertEquals("Arthur", table.rows[0]["name"])
        assertEquals("knight", table.rows[0]["role"])
        assertEquals("100", table.rows[0]["health"])
    }

    @Test
    fun `parse extracts multi-row tables`() {
        val result = engine.parse(multiBlockMd)
        val itemsTable = result.tables.first { it.name == "items" }
        assertEquals(2, itemsTable.rows.size, "Items table should have 2 rows")
        assertEquals("sword", itemsTable.rows[0]["item"])
        assertEquals("potion", itemsTable.rows[1]["item"])
    }

    @Test
    fun `parse extracts tables with no ink blocks`() {
        val result = engine.parse(tableOnlyMd)
        assertEquals(0, result.files.size, "Should have no ink files")
        assertEquals(1, result.tables.size, "Should extract 1 table")

        val table = result.tables[0]
        assertEquals(2, table.rows.size, "Config table should have 2 rows")
        assertEquals("100", table.rows[0]["Value"])
        assertEquals("50", table.rows[1]["Value"])
    }

    // ── parse: empty / edge cases ───────────────────────────────

    @Test
    fun `parse handles empty markdown`() {
        val result = engine.parse("")
        assertTrue(result.files.isEmpty(), "Empty markdown should produce no files")
        assertTrue(result.tables.isEmpty(), "Empty markdown should produce no tables")
    }

    @Test
    fun `parse handles markdown with no ink blocks and no tables`() {
        val md = """
            # Just a Title

            Some plain text with no code blocks or tables.

            More prose here.
        """.trimIndent()
        val result = engine.parse(md)
        assertTrue(result.files.isEmpty(), "Should produce no ink files")
        assertTrue(result.tables.isEmpty(), "Should produce no tables")
    }

    // ── render: VAR injection from tables ───────────────────────

    @Test
    fun `render produces map of filename to ink source`() {
        val rendered = engine.render(multiBlockMd)
        assertEquals(2, rendered.size, "Should render 2 files")
        assertTrue(rendered.containsKey("characters.ink"))
        assertTrue(rendered.containsKey("items.ink"))
    }

    @Test
    fun `render injects single-row table as flat VAR declarations`() {
        val rendered = engine.render(singleRowTableMd)
        val source = rendered["player.ink"]
        assertNotNull(source, "Should produce player.ink")

        // Single-row table should produce flat VARs
        assertTrue(source.contains("VAR name = \"Frodo\""),
            "Should inject VAR name from table row")
        assertTrue(source.contains("VAR class = \"hobbit\""),
            "Should inject VAR class from table row")
        assertTrue(source.contains("VAR level = 5"),
            "Should inject VAR level as integer (no quotes)")
        assertTrue(source.contains("Auto-generated from table"),
            "Should include auto-generation comment")
    }

    @Test
    fun `render injects multi-row table as LIST declaration`() {
        val rendered = engine.render(multiRowTableMd)
        val source = rendered["inventory.ink"]
        assertNotNull(source, "Should produce inventory.ink")

        // Multi-row table should produce a LIST from the first column
        assertTrue(source.contains("LIST items = sword, potion, shield"),
            "Should generate LIST from first column values: got\n$source")
        assertTrue(source.contains("Table data: 3 rows x 3 columns"),
            "Should include table data summary comment")
    }

    @Test
    fun `render handles markdown with no ink blocks`() {
        val rendered = engine.render(tableOnlyMd)
        assertTrue(rendered.isEmpty(),
            "Markdown with no ink blocks should produce empty render map")
    }

    @Test
    fun `render preserves original ink source alongside injected VARs`() {
        val rendered = engine.render(singleRowTableMd)
        val source = rendered["player.ink"]!!

        // The original ink source should still be present after the injected VARs
        assertTrue(source.contains("=== intro ==="),
            "Should preserve the original knot declaration")
        assertTrue(source.contains("Your journey begins."),
            "Should preserve the original story text")
        assertTrue(source.contains("-> END"),
            "Should preserve the original divert")
    }

    @Test
    fun `render does not inject VARs when table name does not match section`() {
        // The table is under "Config" but the ink block is under "story",
        // so the table should NOT be injected into story.ink.
        val md = """
            # Config

            | setting   | value |
            |-----------|-------|
            | difficulty| hard  |

            # story

            ```ink
            === start ===
            Adventure begins.
            -> END
            ```
        """.trimIndent()
        val rendered = engine.render(md)
        val source = rendered["story.ink"]
        assertNotNull(source, "Should produce story.ink")

        // Table name is "Config", file name is "story.ink" — no match
        assertFalse(source.contains("Auto-generated from table"),
            "Should NOT inject VARs when table name doesn't match file name")
        assertTrue(source.contains("=== start ==="),
            "Should still contain the original ink source")
    }
}
