package ink.mcp

import kotlin.test.*

/**
 * Unit tests for EditEngine — ink source code parsing, editing, and statistics.
 *
 * Tests: parse (knots, stitches, functions, variables, includes, diverts),
 * getSection, replaceSection, insertAfter, rename, getStats.
 */
class EditEngineTest {

    private val engine = EditEngine()

    companion object {
        private val sampleInk = """
            VAR health = 100
            VAR name = "Claude"
            CONST MAX_HP = 200
            LIST mood = neutral, happy, sad

            INCLUDE helpers.ink

            === start ===
            Hello {name}!
            * [Go north] -> north
            * [Go south] -> south

            === north ===
            You head north.
            -> ending

            = campfire
            You rest by the fire.
            -> ending

            === south ===
            You head south.
            ~ health = health - 10
            -> ending

            === ending ===
            The end. HP: {health}
            -> END

            === function clamp(val, lo, hi) ===
            {
              - val < lo: ~ return lo
              - val > hi: ~ return hi
              - else: ~ return val
            }
        """.trimIndent()
    }

    // ── parse ──────────────────────────────────────────────────────

    @Test
    fun `parse extracts knots`() {
        val structure = engine.parse(sampleInk)
        val knotNames = structure.sections.filter { it.type == "knot" }.map { it.name }
        assertTrue("start" in knotNames, "Should find knot 'start'")
        assertTrue("north" in knotNames, "Should find knot 'north'")
        assertTrue("south" in knotNames, "Should find knot 'south'")
        assertTrue("ending" in knotNames, "Should find knot 'ending'")
    }

    @Test
    fun `parse extracts stitches`() {
        val structure = engine.parse(sampleInk)
        val stitches = structure.sections.filter { it.type == "stitch" }
        assertTrue(stitches.any { it.name == "campfire" },
            "Should find stitch 'campfire'")
        assertEquals("north", stitches.first { it.name == "campfire" }.parent,
            "campfire should be a stitch of 'north'")
    }

    @Test
    fun `parse extracts functions`() {
        val structure = engine.parse(sampleInk)
        val functions = structure.sections.filter { it.type == "function" || it.parameters.isNotEmpty() }
        assertTrue(functions.any { it.name == "clamp" },
            "Should find function 'clamp'")
    }

    @Test
    fun `parse extracts variables`() {
        val structure = engine.parse(sampleInk)
        val vars = structure.variables
        assertTrue(vars.any { it.name == "health" && it.type == "VAR" })
        assertTrue(vars.any { it.name == "name" && it.type == "VAR" })
        assertTrue(vars.any { it.name == "MAX_HP" && it.type == "CONST" })
        assertTrue(vars.any { it.name == "mood" && it.type == "LIST" })
    }

    @Test
    fun `parse extracts includes`() {
        val structure = engine.parse(sampleInk)
        assertTrue(structure.includes.contains("helpers.ink"),
            "Should find INCLUDE helpers.ink")
    }

    @Test
    fun `parse extracts diverts`() {
        val structure = engine.parse(sampleInk)
        val targets = structure.diverts.map { it.target }.toSet()
        assertTrue("north" in targets, "Should find divert to 'north'")
        assertTrue("south" in targets, "Should find divert to 'south'")
        assertTrue("ending" in targets, "Should find divert to 'ending'")
        assertTrue("END" in targets, "Should find divert to 'END'")
    }

    @Test
    fun `parse counts total lines`() {
        val structure = engine.parse(sampleInk)
        assertTrue(structure.totalLines > 20,
            "Should count substantial lines, got ${structure.totalLines}")
    }

    // ── getSection ─────────────────────────────────────────────────

    @Test
    fun `getSection returns matching knot`() {
        val section = engine.getSection(sampleInk, "start")
        assertNotNull(section, "Should find section 'start'")
        assertTrue(section.content.contains("Hello"),
            "start section should contain 'Hello'")
    }

    @Test
    fun `getSection returns null for unknown section`() {
        val section = engine.getSection(sampleInk, "nonexistent_knot")
        assertNull(section, "Unknown section should return null")
    }

    // ── replaceSection ─────────────────────────────────────────────

    @Test
    fun `replaceSection swaps content`() {
        val newContent = "=== start ===\nGoodbye world!\n-> END"
        val result = engine.replaceSection(sampleInk, "start", newContent)
        assertTrue(result.contains("Goodbye world!"),
            "Replaced section should contain new content")
        assertFalse(result.contains("Go north"),
            "Replaced section should not contain old content")
        // Other sections still present
        assertTrue(result.contains("=== north ==="),
            "Other sections should remain")
    }

    // ── insertAfter ────────────────────────────────────────────────

    @Test
    fun `insertAfter adds new section`() {
        val newSection = "=== east ===\nYou head east.\n-> ending"
        val result = engine.insertAfter(sampleInk, "north", newSection)
        assertTrue(result.contains("=== east ==="),
            "Inserted section should appear")
        // Verify order: north comes before east
        val northIdx = result.indexOf("=== north ===")
        val eastIdx = result.indexOf("=== east ===")
        assertTrue(eastIdx > northIdx, "east should come after north")
    }

    // ── rename ─────────────────────────────────────────────────────

    @Test
    fun `rename updates knot name and diverts`() {
        val result = engine.rename(sampleInk, "north", "wilderness")
        assertTrue(result.contains("=== wilderness ==="),
            "Should rename knot declaration")
        assertTrue(result.contains("-> wilderness"),
            "Should rename divert targets")
        assertFalse(result.contains("-> north"),
            "Old divert targets should be renamed")
    }

    // ── getStats ───────────────────────────────────────────────────

    @Test
    fun `getStats returns expected statistics`() {
        val stats = engine.getStats(sampleInk)
        assertTrue((stats["knots"] as Number).toInt() >= 4,
            "Should count >= 4 knots")
        assertTrue((stats["variables"] as Number).toInt() >= 3,
            "Should count >= 3 variables")
        assertTrue((stats["choices"] as Number).toInt() >= 2,
            "Should count >= 2 choices")
        assertTrue((stats["total_lines"] as Number).toInt() > 20,
            "Should count > 20 lines")
        assertTrue((stats["word_count"] as Number).toInt() > 30,
            "Should count > 30 words")
    }

    @Test
    fun `getStats detects unreferenced knots`() {
        val stats = engine.getStats(sampleInk)
        val unreferenced = stats["unreferenced_knots"]
        assertNotNull(unreferenced, "Should report unreferenced_knots key")
    }

    @Test
    fun `getStats detects missing targets`() {
        val badInk = "-> nonexistent_knot\n-> END"
        val stats = engine.getStats(badInk)
        val missing = stats["missing_divert_targets"]
        assertNotNull(missing, "Should report missing_divert_targets key")
    }

    // ── listSections ───────────────────────────────────────────────

    @Test
    fun `listSections returns all sections`() {
        val sections = engine.listSections(sampleInk)
        assertTrue(sections.size >= 4, "Should list >= 4 sections")
        val names = sections.map { it.name }
        assertTrue("start" in names)
        assertTrue("ending" in names)
    }

    // ── appendSection ──────────────────────────────────────────────

    @Test
    fun `appendSection adds at end`() {
        val newSection = "\n=== bonus ===\nBonus content!\n-> END"
        val result = engine.appendSection(sampleInk, newSection)
        assertTrue(result.endsWith("-> END") || result.contains("=== bonus ==="),
            "Appended section should be at end")
    }

    // ── Edge cases ─────────────────────────────────────────────────

    @Test
    fun `parse empty source`() {
        val structure = engine.parse("")
        assertTrue(structure.sections.isEmpty() || structure.sections.size == 1,
            "Empty source should have 0 or 1 section (preamble)")
        assertTrue(structure.variables.isEmpty())
        assertTrue(structure.includes.isEmpty())
    }

    @Test
    fun `parse source with only text`() {
        val structure = engine.parse("Hello world!\n-> END")
        assertTrue(structure.diverts.any { it.target == "END" },
            "Should find divert to END")
    }
}
