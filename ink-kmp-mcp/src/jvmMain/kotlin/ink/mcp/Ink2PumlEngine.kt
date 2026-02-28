package ink.mcp

import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

/**
 * Ink → PlantUML converter engine.
 *
 * Walks the ink structure (knots, stitches, choices, diverts, variables)
 * and generates PlantUML activity/state diagrams showing the story flow.
 *
 * Two diagram modes:
 *   - activity  — UML activity diagram (default): shows flow with choices as branches
 *   - state     — UML state diagram: shows knots as states with divert transitions
 *
 * Also provides PlantUML → SVG rendering via the PlantUML Java API.
 */
class Ink2PumlEngine(
    private val editEngine: EditEngine = EditEngine()
) : McpPumlOps {

    private val log = LoggerFactory.getLogger(Ink2PumlEngine::class.java)

    enum class DiagramMode { ACTIVITY, STATE }

    // ── McpPumlOps interface override (String mode → DiagramMode) ──

    override fun inkToPuml(source: String, mode: String, title: String): String {
        val diagramMode = when (mode.lowercase()) {
            "state" -> DiagramMode.STATE
            else -> DiagramMode.ACTIVITY
        }
        return inkToPuml(source, diagramMode, title)
    }

    // ── Internal methods (DiagramMode-based) ─────────────────

    /**
     * Convert ink source to PlantUML diagram source.
     *
     * @param inkSource  the ink script to analyze
     * @param mode       diagram type: ACTIVITY or STATE
     * @param title      optional diagram title (defaults to "Ink Story Flow")
     * @param mcpLinks   if true, embed MCP tool URIs in PlantUML notes for LLM navigation
     * @return PlantUML source string
     */
    fun inkToPuml(
        inkSource: String,
        mode: DiagramMode = DiagramMode.ACTIVITY,
        title: String = "Ink Story Flow",
        mcpLinks: Boolean = true
    ): String {
        val structure = editEngine.parse(inkSource)
        return when (mode) {
            DiagramMode.ACTIVITY -> generateActivityDiagram(structure, inkSource, title, mcpLinks)
            DiagramMode.STATE -> generateStateDiagram(structure, inkSource, title, mcpLinks)
        }
    }

    /**
     * Generate a Table of Contents (TOC) for an ink script, mapped to PlantUML
     * and MCP tool links. Designed for LLMs with limited context — the TOC
     * gives a compact overview and lets the LLM fetch specific sections via MCP.
     *
     * Format:
     *   # Story TOC
     *   ## Knots
     *   - knot_name (lines 10-25, 3 choices) → mcp:get_section {section_name: "knot_name"}
     *   ## Variables
     *   - VAR score = 0 → mcp:get_variable {name: "score"}
     *   ## Stats
     *   - 5 knots, 3 stitches, 12 choices → mcp:ink_stats
     *   ## Diagram
     *   - mcp:ink2puml → activity diagram | mcp:ink2svg → SVG
     */
    override fun generateToc(inkSource: String): String {
        val structure = editEngine.parse(inkSource)
        return buildString {
            appendLine("# Ink Story — Table of Contents")
            appendLine()

            // Knots
            val knots = structure.sections.filter { it.type == "knot" }
            if (knots.isNotEmpty()) {
                appendLine("## Knots (${knots.size})")
                for (knot in knots) {
                    val choices = extractChoices(knot.content)
                    val diverts = extractDivertsFromContent(knot.content)
                    val stitches = structure.sections.filter { it.type == "stitch" && it.parent == knot.name }
                    val info = buildList {
                        add("lines ${knot.startLine + 1}-${knot.endLine}")
                        if (choices.isNotEmpty()) add("${choices.size} choices")
                        if (stitches.isNotEmpty()) add("${stitches.size} stitches")
                        if (diverts.isNotEmpty()) add("→ ${diverts.joinToString(", ")}")
                    }
                    appendLine("- **${knot.name}** (${info.joinToString(", ")})")
                    appendLine("  mcp:get_section {\"source\": \"...\", \"section_name\": \"${knot.name}\"}")

                    for (stitch in stitches) {
                        appendLine("  - ${stitch.name} (lines ${stitch.startLine + 1}-${stitch.endLine})")
                        appendLine("    mcp:get_section {\"source\": \"...\", \"section_name\": \"${stitch.name}\"}")
                    }
                }
                appendLine()
            }

            // Functions
            val functions = structure.sections.filter { it.type == "function" }
            if (functions.isNotEmpty()) {
                appendLine("## Functions (${functions.size})")
                for (func in functions) {
                    appendLine("- **${func.name}**(${func.parameters.joinToString(", ")}) — lines ${func.startLine + 1}-${func.endLine}")
                    appendLine("  mcp:get_section {\"source\": \"...\", \"section_name\": \"${func.name}\"}")
                }
                appendLine()
            }

            // Variables
            if (structure.variables.isNotEmpty()) {
                appendLine("## Variables (${structure.variables.size})")
                for (v in structure.variables) {
                    appendLine("- ${v.type} **${v.name}** = ${v.initialValue} (line ${v.line + 1})")
                    appendLine("  mcp:get_variable {\"session_id\": \"...\", \"name\": \"${v.name}\"}")
                }
                appendLine()
            }

            // Includes
            if (structure.includes.isNotEmpty()) {
                appendLine("## Includes (${structure.includes.size})")
                for (inc in structure.includes) {
                    appendLine("- INCLUDE $inc")
                }
                appendLine()
            }

            // Stats summary
            val stitchCount = structure.sections.count { it.type == "stitch" }
            appendLine("""
                ## Stats
                - ${knots.size} knots, $stitchCount stitches, ${functions.size} functions, ${structure.variables.size} variables, ${structure.divertCount} diverts, ${structure.totalLines} lines
                  mcp:ink_stats {"source": "..."}
            """.trimIndent())
            appendLine()

            // Diagram links & Quick actions
            appendLine("""
                ## Diagrams
                - Activity diagram: mcp:ink2puml {"source": "...", "mode": "activity"}
                - State diagram:    mcp:ink2puml {"source": "...", "mode": "state"}
                - SVG rendering:    mcp:ink2svg  {"source": "...", "mode": "activity"}

                ## MCP Quick Actions
                - Parse structure:  mcp:parse_ink      {"source": "..."}
                - Compile & play:   mcp:start_story    {"source": "..."}
                - Review code:      mcp:review_ink     {"source": "..."}
                - Debug session:    mcp:start_story → mcp:start_debug → mcp:debug_step
            """.trimIndent())
        }
    }

    /**
     * Generate a compact PlantUML TOC diagram with MCP links embedded in notes.
     * Designed for LLMs to scan quickly and call the right MCP tool.
     */
    override fun generateTocPuml(inkSource: String, title: String): String {
        val structure = editEngine.parse(inkSource)
        val knots = structure.sections.filter { it.type == "knot" }
        val functions = structure.sections.filter { it.type == "function" }

        return buildString {
            appendLine("""
                @startuml
                !theme plain
                skinparam backgroundColor #FEFEFE
                title $title
                footer TOC with MCP tool links | Ink2PumlEngine
            """.trimIndent())
            appendLine()

            // Map diagram
            appendLine("map \"Story Map\" as storyMap {")
            for (knot in knots) {
                val choices = extractChoices(knot.content)
                val choiceInfo = if (choices.isNotEmpty()) " [${choices.size} choices]" else ""
                appendLine("  ${knot.name} => lines ${knot.startLine + 1}-${knot.endLine}$choiceInfo")
            }
            for (func in functions) {
                appendLine("  ${func.name}() => lines ${func.startLine + 1}-${func.endLine}")
            }
            appendLine("}")
            appendLine()

            // Variables map
            if (structure.variables.isNotEmpty()) {
                appendLine("map \"Variables\" as varsMap {")
                for (v in structure.variables) {
                    appendLine("  ${v.name} => ${v.type} = ${v.initialValue}")
                }
                appendLine("}")
                appendLine()
            }

            // MCP tools note
            appendLine("""
                note right of storyMap
                  **MCP Tools for Navigation**
                  ----
                  get_section: read a knot/stitch by name
                  parse_ink: full structure analysis
                  ink_stats: statistics & dead-end detection
                  ink2puml: activity/state diagram
                  ink2svg: SVG rendering of diagram
                  start_story: compile & play
                  start_debug: debug with breakpoints
                  review_ink: LLM code review
                end note
            """.trimIndent())

            // Knot detail notes with MCP links
            for (knot in knots) {
                val choices = extractChoices(knot.content)
                val diverts = extractDivertsFromContent(knot.content)
                val stitches = structure.sections.filter { it.type == "stitch" && it.parent == knot.name }

                if (choices.isNotEmpty() || stitches.isNotEmpty()) {
                    appendLine()
                    appendLine("note bottom of storyMap")
                    appendLine("  **${knot.name}** (${knot.endLine - knot.startLine} lines)")
                    if (stitches.isNotEmpty()) {
                        appendLine("  Stitches: ${stitches.joinToString(", ") { it.name }}")
                    }
                    if (choices.isNotEmpty()) {
                        appendLine("  Choices:")
                        for (c in choices) {
                            val arrow = if (c.divert != null) " → ${c.divert}" else ""
                            appendLine("    ${if (c.isStickyChoice) "+" else "*"} ${escapePuml(c.text)}$arrow")
                        }
                    }
                    if (diverts.isNotEmpty()) {
                        appendLine("  Diverts: ${diverts.joinToString(", ")}")
                    }
                    appendLine("  ----")
                    appendLine("  mcp:get_section {section_name: \"${knot.name}\"}")
                    appendLine("end note")
                }
            }

            appendLine()
            appendLine("@enduml")
        }
    }

    /**
     * Convert ink source to SVG via PlantUML Java API.
     *
     * @param inkSource  the ink script to convert
     * @param mode       diagram type
     * @param title      optional title
     * @return SVG string
     */
    fun inkToSvg(
        inkSource: String,
        mode: DiagramMode = DiagramMode.ACTIVITY,
        title: String = "Ink Story Flow"
    ): String {
        val puml = inkToPuml(inkSource, mode, title)
        return pumlToSvg(puml)
    }

    /**
     * Render PlantUML source to SVG using the PlantUML Java API.
     *
     * Uses net.sourceforge.plantuml.SourceStringReader.
     */
    override fun pumlToSvg(pumlSource: String): String {
        return try {
            val readerClass = Class.forName("net.sourceforge.plantuml.SourceStringReader")
            val fileFormatClass = Class.forName("net.sourceforge.plantuml.FileFormat")
            val fileFormatOptionClass = Class.forName("net.sourceforge.plantuml.FileFormatOption")

            val svgFormat = fileFormatClass.getField("SVG").get(null)
            val formatOption = fileFormatOptionClass.getConstructor(fileFormatClass).newInstance(svgFormat)

            val reader = readerClass.getConstructor(String::class.java).newInstance(pumlSource)
            val baos = ByteArrayOutputStream()

            val outputImageMethod = readerClass.getMethod(
                "outputImage",
                java.io.OutputStream::class.java,
                fileFormatOptionClass
            )
            outputImageMethod.invoke(reader, baos, formatOption)

            baos.toString("UTF-8")
        } catch (e: ClassNotFoundException) {
            log.warn("PlantUML not on classpath, returning puml source as fallback")
            "<!-- PlantUML not available: ${e.message} -->\n<!--\n$pumlSource\n-->"
        } catch (e: Exception) {
            log.error("PlantUML rendering failed", e)
            "<!-- PlantUML render error: ${e.message} -->\n<!--\n$pumlSource\n-->"
        }
    }

    // ── Activity Diagram ──────────────────────────────────────────────

    private fun generateActivityDiagram(
        structure: InkStructureResponse,
        inkSource: String,
        title: String,
        mcpLinks: Boolean = true
    ): String = buildString {
        appendLine("""
            @startuml
            !theme plain
            skinparam backgroundColor #FEFEFE
            title $title
            footer Generated from ink source by Ink2PumlEngine
        """.trimIndent())
        appendLine()

        // Show variables as notes with MCP links
        if (structure.variables.isNotEmpty()) {
            appendLine("floating note left")
            appendLine("  **Variables**")
            structure.variables.forEach { v ->
                appendLine("  ${v.type} ${v.name} = ${v.initialValue}")
            }
            if (mcpLinks) {
                appendLine("  ----")
                appendLine("  mcp:get_variable {name: \"<var>\"}")
                appendLine("  mcp:set_variable {name: \"<var>\", value: <val>}")
            }
            appendLine("end note")
            appendLine()
        }

        // TOC note with MCP navigation links
        if (mcpLinks) {
            val knots = structure.sections.filter { it.type == "knot" }
            appendLine("floating note right")
            appendLine("  **TOC** (${knots.size} knots, ${structure.variables.size} vars)")
            for (knot in knots) {
                val choices = extractChoices(knot.content)
                val choiceLabel = if (choices.isNotEmpty()) " [${choices.size}ch]" else ""
                appendLine("  · ${knot.name}$choiceLabel → L${knot.startLine + 1}")
            }
            appendLine("""
                  ----
                  mcp:get_section {section_name: "<knot>"}
                  mcp:parse_ink → full structure
                  mcp:ink_stats → dead-end analysis
                end note
            """.trimIndent())
            appendLine()
        }

        appendLine("start")
        appendLine()

        val knots = structure.sections.filter { it.type == "knot" || it.type == "function" }
        val preamble = structure.sections.find { it.type == "preamble" }

        // Parse preamble for initial flow
        if (preamble != null) {
            val preambleChoices = extractChoices(preamble.content)
            val preambleDiverts = extractDivertsFromContent(preamble.content)
            val preambleText = extractNarrativeText(preamble.content)

            if (preambleText.isNotEmpty()) {
                appendLine(":${escapePuml(preambleText.first())};")
            }

            if (preambleChoices.isNotEmpty()) {
                renderChoices(this, preambleChoices)
            } else if (preambleDiverts.isNotEmpty()) {
                preambleDiverts.first().let { target ->
                    if (target != "END" && target != "DONE") {
                        appendLine("-> **$target**;")
                    }
                }
            }
        }

        // Each knot becomes a partition
        for (knot in knots) {
            appendLine()
            if (knot.type == "function") {
                appendLine("|${knot.name}|")
                appendLine(":function **${knot.name}**(${knot.parameters.joinToString(", ")});")
            } else {
                appendLine("partition \"=== ${knot.name} ===\" {")
            }

            // Extract narrative lines, choices, and diverts within this knot
            val lines = knot.content.lines()
            val choices = extractChoices(knot.content)
            val narrativeText = extractNarrativeText(knot.content)
            val knotDiverts = extractDivertsFromContent(knot.content)

            // Render stitches within this knot
            val stitches = structure.sections.filter { it.type == "stitch" && it.parent == knot.name }

            // Narrative text
            for (text in narrativeText.take(2)) {
                appendLine(":${escapePuml(text)};")
            }
            if (narrativeText.size > 2) {
                appendLine(":...;")
            }

            // Stitches
            for (stitch in stitches) {
                appendLine()
                appendLine("group ${stitch.name}")
                val stitchText = extractNarrativeText(stitch.content)
                val stitchChoices = extractChoices(stitch.content)

                for (text in stitchText.take(1)) {
                    appendLine(":${escapePuml(text)};")
                }

                if (stitchChoices.isNotEmpty()) {
                    renderChoices(this, stitchChoices)
                }
                appendLine("end group")
            }

            // Choices (if any at knot level, not in stitches)
            if (choices.isNotEmpty() && stitches.isEmpty()) {
                renderChoices(this, choices)
            }

            // Diverts at the end of the knot
            val terminalDiverts = knotDiverts.filter { d ->
                d != "END" && d != "DONE" && !choices.any { c -> c.divert == d }
            }
            if (terminalDiverts.isNotEmpty()) {
                appendLine("-> **${terminalDiverts.last()}**;")
            }

            // Check for END/DONE
            if (knotDiverts.contains("END") || knotDiverts.contains("DONE")) {
                appendLine("stop")
            }

            if (knot.type != "function") {
                appendLine("}")
            }
        }

        // If no knots, check for a simple linear story
        if (knots.isEmpty() && preamble != null) {
            val allChoices = extractChoices(preamble.content)
            val allDiverts = extractDivertsFromContent(preamble.content)
            if (allChoices.isEmpty() && (allDiverts.contains("END") || allDiverts.contains("DONE") || allDiverts.isEmpty())) {
                appendLine()
                appendLine("stop")
            }
        }

        appendLine()
        appendLine("@enduml")
    }

    // ── State Diagram ─────────────────────────────────────────────────

    private fun generateStateDiagram(
        structure: InkStructureResponse,
        inkSource: String,
        title: String,
        mcpLinks: Boolean = true
    ): String = buildString {
        appendLine("""
            @startuml
            !theme plain
            skinparam backgroundColor #FEFEFE
            title $title
            footer Generated from ink source by Ink2PumlEngine
        """.trimIndent())
        appendLine()

        val knots = structure.sections.filter { it.type == "knot" }
        val functions = structure.sections.filter { it.type == "function" }

        // Variables note
        if (structure.variables.isNotEmpty()) {
            appendLine("note top of start")
            structure.variables.forEach { v ->
                appendLine("  ${v.type} ${v.name} = ${v.initialValue}")
            }
            appendLine("end note")
            appendLine()
        }

        // Define states for each knot
        for (knot in knots) {
            val choices = extractChoices(knot.content)
            val narrativeText = extractNarrativeText(knot.content)
            val stitches = structure.sections.filter { it.type == "stitch" && it.parent == knot.name }

            appendLine("state \"${knot.name}\" as ${sanitizeId(knot.name)} {")
            if (narrativeText.isNotEmpty()) {
                appendLine("  ${sanitizeId(knot.name)} : ${escapePuml(narrativeText.first())}")
            }
            if (choices.isNotEmpty()) {
                appendLine("  ${sanitizeId(knot.name)} : Choices: ${choices.size}")
            }

            // Stitches as sub-states
            for (stitch in stitches) {
                appendLine("  state \"${stitch.name}\" as ${sanitizeId(knot.name + "_" + stitch.name)}")
            }
            appendLine("}")
            appendLine()
        }

        // Functions shown as stereotyped states
        for (func in functions) {
            appendLine("state \"${func.name}()\" as ${sanitizeId(func.name)} <<function>>")
        }
        appendLine()

        // Initial transition — check preamble diverts
        val preamble = structure.sections.find { it.type == "preamble" }
        val initialDiverts = if (preamble != null) extractDivertsFromContent(preamble.content) else emptyList()

        if (initialDiverts.isNotEmpty()) {
            val firstTarget = initialDiverts.first()
            if (firstTarget != "END" && firstTarget != "DONE") {
                appendLine("[*] --> ${sanitizeId(firstTarget)}")
            }
        } else if (knots.isNotEmpty()) {
            appendLine("[*] --> ${sanitizeId(knots.first().name)}")
        }

        // Transitions from diverts
        for (section in structure.sections) {
            if (section.type == "preamble") continue

            val sectionId = if (section.type == "stitch" && section.parent != null) {
                sanitizeId(section.parent + "_" + section.name)
            } else {
                sanitizeId(section.name)
            }

            val diverts = extractDivertsFromContent(section.content)
            val choices = extractChoices(section.content)

            // Choice-driven transitions
            for (choice in choices) {
                if (choice.divert != null) {
                    val targetId = if (choice.divert == "END" || choice.divert == "DONE") {
                        "[*]"
                    } else {
                        sanitizeId(choice.divert)
                    }
                    appendLine("$sectionId --> $targetId : ${escapePuml(choice.text)}")
                }
            }

            // Non-choice diverts
            val choiceDiverts = choices.mapNotNull { it.divert }.toSet()
            for (divert in diverts) {
                if (divert in choiceDiverts) continue
                val targetId = if (divert == "END" || divert == "DONE") "[*]" else sanitizeId(divert)
                appendLine("$sectionId --> $targetId")
            }
        }

        // MCP navigation legend
        if (mcpLinks) {
            appendLine()
            appendLine("""
                legend right
                  **MCP Tool Links**
                  |= Action |= Tool Call |
            """.trimIndent())
            for (k in knots) {
                appendLine("  | Read ${k.name} | mcp:get_section {section_name: \"${k.name}\"} |")
            }
            appendLine("""
                  | Full parse | mcp:parse_ink |
                  | Statistics | mcp:ink_stats |
                  | Play story | mcp:start_story |
                endlegend
            """.trimIndent())
        }

        appendLine()
        appendLine("@enduml")
    }

    // ── Parsing helpers ───────────────────────────────────────────────

    data class InkChoice(
        val text: String,
        val divert: String?,
        val isStickyChoice: Boolean  // + vs *
    )

    private fun extractChoices(content: String): List<InkChoice> {
        val choices = mutableListOf<InkChoice>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            val choiceMatch = Regex("""^([*+])\s*(?:\[([^\]]*)\])?(.*)$""").find(trimmed) ?: continue
            val isSticky = choiceMatch.groupValues[1] == "+"
            val bracketText = choiceMatch.groupValues[2].trim()
            var restText = choiceMatch.groupValues[3].trim()

            // Extract divert from the choice line
            val divertMatch = Regex("""->\s*(\w+)\s*$""").find(restText)
            val divert = divertMatch?.groupValues?.get(1)
            if (divertMatch != null) {
                restText = restText.substring(0, divertMatch.range.first).trim()
            }

            val displayText = when {
                bracketText.isNotEmpty() -> bracketText
                restText.isNotEmpty() -> restText
                else -> "choice"
            }

            choices.add(InkChoice(displayText, divert, isSticky))
        }
        return choices
    }

    private fun extractDivertsFromContent(content: String): List<String> {
        val diverts = mutableListOf<String>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            // Skip choice lines — those are handled separately
            if (trimmed.startsWith("*") || trimmed.startsWith("+")) continue
            // Skip knot/stitch headers
            if (trimmed.startsWith("===") || trimmed.startsWith("= ")) continue

            val matches = Regex("""->\s*(\w+)""").findAll(trimmed)
            for (m in matches) {
                diverts.add(m.groupValues[1])
            }
        }
        return diverts
    }

    private fun extractNarrativeText(content: String): List<String> {
        val texts = mutableListOf<String>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("===") || trimmed.startsWith("= ")) continue
            if (trimmed.startsWith("*") || trimmed.startsWith("+")) continue
            if (trimmed.startsWith("->")) continue
            if (trimmed.startsWith("//")) continue
            if (trimmed.startsWith("~")) continue
            if (trimmed.startsWith("{")) continue
            if (trimmed.startsWith("VAR") || trimmed.startsWith("CONST") || trimmed.startsWith("LIST") || trimmed.startsWith("TEMP")) continue
            if (trimmed.startsWith("INCLUDE")) continue
            if (trimmed.startsWith("#")) continue  // tags
            texts.add(trimmed)
        }
        return texts
    }

    private fun renderChoices(sb: StringBuilder, choices: List<InkChoice>) {
        if (choices.isEmpty()) return

        sb.appendLine("switch (Choice)")
        for (choice in choices) {
            val marker = if (choice.isStickyChoice) "+" else "*"
            sb.appendLine("case ( $marker ${escapePuml(choice.text)} )")
            if (choice.divert != null) {
                if (choice.divert == "END" || choice.divert == "DONE") {
                    sb.appendLine("  stop")
                } else {
                    sb.appendLine("  -> **${choice.divert}**;")
                }
            }
        }
        sb.appendLine("endswitch")
    }

    private fun escapePuml(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .take(80) // truncate long lines for readability
    }

    private fun sanitizeId(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }
}
