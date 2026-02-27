package ink.mcp

import kotlin.test.*

/**
 * Unit tests for Ink2PumlEngine — ink-to-PlantUML conversion, TOC generation,
 * and SVG rendering.
 *
 * Tests: inkToPuml (activity + state modes), generateToc, generateTocPuml,
 * inkToSvg, pumlToSvg.
 */
class Ink2PumlEngineTest {

    private val engine = Ink2PumlEngine()

    companion object {
        private val sampleInk = """
            === start ===
            Hello!
            * [Go left] -> left
            * [Go right] -> right
            === left ===
            You went left.
            -> END
            === right ===
            You went right.
            -> END
        """.trimIndent()
    }

    // ── inkToPuml — ACTIVITY mode ─────────────────────────────────

    @Test
    fun `inkToPuml generates valid plantuml activity diagram`() {
        val puml = engine.inkToPuml(sampleInk)
        assertTrue(puml.contains("@startuml"), "Should start with @startuml")
        assertTrue(puml.contains("@enduml"), "Should end with @enduml")
        assertTrue(puml.contains("Ink Story Flow"), "Should contain default title")
        assertTrue(puml.contains("start"), "Should reference the start knot")
    }

    @Test
    fun `inkToPuml activity diagram contains choices and knot structure`() {
        val puml = engine.inkToPuml(sampleInk, mode = Ink2PumlEngine.DiagramMode.ACTIVITY)
        // Choices should appear in the diagram
        assertTrue(puml.contains("Go left") || puml.contains("Go right"),
            "Activity diagram should contain choice text")
        // Knot partitions
        assertTrue(puml.contains("left"), "Should reference 'left' knot")
        assertTrue(puml.contains("right"), "Should reference 'right' knot")
        // The diagram should contain a stop for END diverts
        assertTrue(puml.contains("stop"), "Should have stop for -> END")
    }

    @Test
    fun `inkToPuml respects custom title`() {
        val puml = engine.inkToPuml(sampleInk, title = "My Custom Title")
        assertTrue(puml.contains("My Custom Title"),
            "Should contain the custom title")
    }

    // ── inkToPuml — STATE mode ────────────────────────────────────

    @Test
    fun `inkToPuml state mode produces state diagram`() {
        val puml = engine.inkToPuml(sampleInk, mode = Ink2PumlEngine.DiagramMode.STATE)
        assertTrue(puml.contains("@startuml"), "Should start with @startuml")
        assertTrue(puml.contains("@enduml"), "Should end with @enduml")
        // State diagrams define states with the 'state' keyword
        assertTrue(puml.contains("state"), "State diagram should contain 'state' keyword")
        // Should have transitions using -->
        assertTrue(puml.contains("-->"), "State diagram should contain transitions")
        // Knots should appear as states
        assertTrue(puml.contains("start"), "Should have 'start' state")
        assertTrue(puml.contains("left"), "Should have 'left' state")
        assertTrue(puml.contains("right"), "Should have 'right' state")
    }

    @Test
    fun `inkToPuml state mode includes choice labels on transitions`() {
        val puml = engine.inkToPuml(sampleInk, mode = Ink2PumlEngine.DiagramMode.STATE)
        // Choice text should appear as transition labels
        assertTrue(puml.contains("Go left") || puml.contains("Go right"),
            "State diagram should label transitions with choice text")
    }

    // ── generateToc ───────────────────────────────────────────────

    @Test
    fun `generateToc produces table of contents with knots`() {
        val toc = engine.generateToc(sampleInk)
        assertTrue(toc.contains("# Ink Story"), "TOC should have a heading")
        assertTrue(toc.contains("## Knots"), "TOC should list knots section")
        assertTrue(toc.contains("start"), "TOC should mention 'start' knot")
        assertTrue(toc.contains("left"), "TOC should mention 'left' knot")
        assertTrue(toc.contains("right"), "TOC should mention 'right' knot")
        // Stats section
        assertTrue(toc.contains("## Stats"), "TOC should include stats section")
        // MCP tool references
        assertTrue(toc.contains("mcp:get_section"), "TOC should include MCP get_section links")
        // Diagram links
        assertTrue(toc.contains("## Diagrams"), "TOC should include diagrams section")
        assertTrue(toc.contains("mcp:ink2puml"), "TOC should reference ink2puml tool")
    }

    // ── generateTocPuml ───────────────────────────────────────────

    @Test
    fun `generateTocPuml produces valid plantuml TOC diagram`() {
        val puml = engine.generateTocPuml(sampleInk)
        assertTrue(puml.contains("@startuml"), "Should start with @startuml")
        assertTrue(puml.contains("@enduml"), "Should end with @enduml")
        assertTrue(puml.contains("Ink Story TOC"), "Should contain default TOC title")
        // Story map
        assertTrue(puml.contains("map \"Story Map\""), "Should contain a story map")
        assertTrue(puml.contains("start"), "Story map should list 'start' knot")
        assertTrue(puml.contains("left"), "Story map should list 'left' knot")
        assertTrue(puml.contains("right"), "Story map should list 'right' knot")
        // MCP tools note
        assertTrue(puml.contains("MCP Tools for Navigation"),
            "Should contain MCP navigation note")
    }

    @Test
    fun `generateTocPuml respects custom title`() {
        val puml = engine.generateTocPuml(sampleInk, title = "Custom TOC")
        assertTrue(puml.contains("Custom TOC"),
            "Should contain the custom TOC title")
    }

    // ── inkToSvg ──────────────────────────────────────────────────

    @Test
    fun `inkToSvg generates SVG output or plantuml fallback`() {
        val svg = engine.inkToSvg(sampleInk)
        // If PlantUML is on the classpath, we get SVG; otherwise a comment fallback
        val isSvg = svg.contains("<svg") || svg.contains("<?xml")
        val isFallback = svg.contains("<!--") && svg.contains("@startuml")
        assertTrue(isSvg || isFallback,
            "Should produce SVG or a comment-wrapped PlantUML fallback, got: ${svg.take(200)}")
    }

    // ── pumlToSvg ─────────────────────────────────────────────────

    @Test
    fun `pumlToSvg renders plantuml to SVG or returns fallback`() {
        val simplePuml = """
            @startuml
            start
            :Hello;
            stop
            @enduml
        """.trimIndent()
        val svg = engine.pumlToSvg(simplePuml)
        val isSvg = svg.contains("<svg") || svg.contains("<?xml")
        val isFallback = svg.contains("<!--") && svg.contains("@startuml")
        assertTrue(isSvg || isFallback,
            "Should produce SVG or a comment-wrapped PlantUML fallback, got: ${svg.take(200)}")
    }
}
