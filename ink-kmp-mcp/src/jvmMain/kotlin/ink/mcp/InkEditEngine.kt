package ink.mcp

import org.slf4j.LoggerFactory

/**
 * Ink script section editor — provides tools for structured manipulation
 * of ink scripts at the knot/stitch/function level.
 *
 * Parses ink source into a section tree and allows:
 *   - List all knots, stitches, functions, variables
 *   - Get/replace individual sections by name
 *   - Insert new sections at specified positions
 *   - Rename knots/stitches (updates all diverts)
 *   - Extract statistics and structure analysis
 */
class InkEditEngine {

    private val log = LoggerFactory.getLogger(InkEditEngine::class.java)

    data class InkSection(
        val name: String,
        val type: String,       // "knot", "stitch", "function", "preamble"
        val startLine: Int,     // 0-based
        val endLine: Int,       // exclusive
        val content: String,
        val parent: String? = null, // parent knot for stitches
        val parameters: List<String> = emptyList() // for functions
    )

    data class InkVariable(
        val name: String,
        val type: String,       // "VAR", "CONST", "LIST", "TEMP"
        val initialValue: String,
        val line: Int
    )

    data class InkStructure(
        val sections: List<InkSection>,
        val variables: List<InkVariable>,
        val includes: List<String>,
        val diverts: List<DivertRef>,
        val totalLines: Int
    )

    data class DivertRef(
        val target: String,
        val line: Int,
        val column: Int
    )

    /** Parse ink source into a structured section tree */
    fun parse(source: String): InkStructure {
        val lines = source.lines()
        val sections = mutableListOf<InkSection>()
        val variables = mutableListOf<InkVariable>()
        val includes = mutableListOf<String>()
        val diverts = mutableListOf<DivertRef>()

        var currentKnot: String? = null
        var sectionStart = 0
        var sectionName = "__preamble"
        var sectionType = "preamble"
        var sectionParent: String? = null
        var sectionParams: List<String> = emptyList()

        for ((lineNum, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // Detect knots: === knot_name ===
            val knotMatch = Regex("""^===\s*(\w+)\s*(?:\((.*?)\))?\s*={0,3}\s*$""").find(trimmed)
            if (knotMatch != null) {
                // Close previous section
                if (lineNum > sectionStart) {
                    sections.add(InkSection(
                        name = sectionName,
                        type = sectionType,
                        startLine = sectionStart,
                        endLine = lineNum,
                        content = lines.subList(sectionStart, lineNum).joinToString("\n"),
                        parent = sectionParent,
                        parameters = sectionParams
                    ))
                }

                val name = knotMatch.groupValues[1]
                val params = knotMatch.groupValues[2]
                val isFunction = params.isNotEmpty() || trimmed.contains("function")

                currentKnot = name
                sectionStart = lineNum
                sectionName = name
                sectionType = if (isFunction) "function" else "knot"
                sectionParent = null
                sectionParams = if (params.isNotEmpty()) params.split(",").map { it.trim() } else emptyList()
                continue
            }

            // Detect stitches: = stitch_name
            val stitchMatch = Regex("""^=\s+(\w+)\s*(?:\((.*?)\))?\s*$""").find(trimmed)
            if (stitchMatch != null && currentKnot != null) {
                // Close previous section
                if (lineNum > sectionStart) {
                    sections.add(InkSection(
                        name = sectionName,
                        type = sectionType,
                        startLine = sectionStart,
                        endLine = lineNum,
                        content = lines.subList(sectionStart, lineNum).joinToString("\n"),
                        parent = sectionParent,
                        parameters = sectionParams
                    ))
                }

                sectionStart = lineNum
                sectionName = stitchMatch.groupValues[1]
                sectionType = "stitch"
                sectionParent = currentKnot
                sectionParams = emptyList()
                continue
            }

            // Detect function definitions: === function func_name(params) ===
            val funcMatch = Regex("""^===\s*function\s+(\w+)\s*\((.*?)\)\s*={0,3}\s*$""").find(trimmed)
            if (funcMatch != null) {
                if (lineNum > sectionStart) {
                    sections.add(InkSection(
                        name = sectionName,
                        type = sectionType,
                        startLine = sectionStart,
                        endLine = lineNum,
                        content = lines.subList(sectionStart, lineNum).joinToString("\n"),
                        parent = sectionParent,
                        parameters = sectionParams
                    ))
                }

                currentKnot = null
                sectionStart = lineNum
                sectionName = funcMatch.groupValues[1]
                sectionType = "function"
                sectionParent = null
                sectionParams = funcMatch.groupValues[2].split(",").map { it.trim() }
                continue
            }

            // Detect variables: VAR name = value / CONST / LIST / TEMP
            val varMatch = Regex("""^(VAR|CONST|LIST|TEMP)\s+(\w+)\s*=\s*(.+)$""").find(trimmed)
            if (varMatch != null) {
                variables.add(InkVariable(
                    name = varMatch.groupValues[2],
                    type = varMatch.groupValues[1],
                    initialValue = varMatch.groupValues[3].trim(),
                    line = lineNum
                ))
            }

            // Detect includes: INCLUDE file.ink
            val includeMatch = Regex("""^INCLUDE\s+(.+)$""").find(trimmed)
            if (includeMatch != null) {
                includes.add(includeMatch.groupValues[1].trim())
            }

            // Detect diverts: -> target
            val divertMatches = Regex("""->(\s*)(\w+)""").findAll(line)
            for (m in divertMatches) {
                diverts.add(DivertRef(
                    target = m.groupValues[2],
                    line = lineNum,
                    column = m.range.first
                ))
            }
        }

        // Close final section
        if (lines.size > sectionStart) {
            sections.add(InkSection(
                name = sectionName,
                type = sectionType,
                startLine = sectionStart,
                endLine = lines.size,
                content = lines.subList(sectionStart, lines.size).joinToString("\n"),
                parent = sectionParent,
                parameters = sectionParams
            ))
        }

        return InkStructure(
            sections = sections,
            variables = variables,
            includes = includes,
            diverts = diverts,
            totalLines = lines.size
        )
    }

    /** Get a specific section by name */
    fun getSection(source: String, sectionName: String): InkSection? {
        return parse(source).sections.find { it.name == sectionName }
    }

    /** Replace a section's content */
    fun replaceSection(source: String, sectionName: String, newContent: String): String {
        val structure = parse(source)
        val section = structure.sections.find { it.name == sectionName }
            ?: throw IllegalArgumentException("Section not found: $sectionName")

        val lines = source.lines().toMutableList()
        val newLines = newContent.lines()

        // Replace the section's line range
        for (i in (section.endLine - 1) downTo section.startLine) {
            lines.removeAt(i)
        }
        lines.addAll(section.startLine, newLines)

        return lines.joinToString("\n")
    }

    /** Insert a new section after an existing section */
    fun insertAfter(source: String, afterSection: String, newContent: String): String {
        val structure = parse(source)
        val section = structure.sections.find { it.name == afterSection }
            ?: throw IllegalArgumentException("Section not found: $afterSection")

        val lines = source.lines().toMutableList()
        val newLines = listOf("") + newContent.lines()  // blank line separator
        lines.addAll(section.endLine, newLines)

        return lines.joinToString("\n")
    }

    /** Insert a new section at the end of the script */
    fun appendSection(source: String, newContent: String): String {
        return source.trimEnd() + "\n\n" + newContent + "\n"
    }

    /** Rename a knot/stitch and update all diverts that reference it */
    fun rename(source: String, oldName: String, newName: String): String {
        var result = source

        // Rename the knot/stitch header
        result = result.replace(Regex("""(===\s*)$oldName(\s*===)"""), "$1$newName$2")
        result = result.replace(Regex("""(===\s*)$oldName(\s*\()"""), "$1$newName$2")
        result = result.replace(Regex("""(=\s+)$oldName(\s*)$""", RegexOption.MULTILINE), "$1$newName$2")

        // Update all diverts -> oldName to -> newName
        result = result.replace(Regex("""(->\s*)$oldName\b"""), "$1$newName")

        // Update all divert targets in choices
        result = result.replace(Regex("""(->\s*)$oldName(\s*$)""", RegexOption.MULTILINE), "$1$newName$2")

        return result
    }

    /** Get statistics about the ink script */
    fun getStats(source: String): Map<String, Any> {
        val structure = parse(source)
        val lines = source.lines()

        val choiceCount = lines.count { it.trim().startsWith("*") || it.trim().startsWith("+") }
        val divertCount = structure.diverts.size
        val wordCount = source.split(Regex("""\s+""")).size

        // Find dead ends (knots with no outgoing diverts)
        val knotNames = structure.sections.filter { it.type == "knot" }.map { it.name }.toSet()
        val divertTargets = structure.diverts.map { it.target }.toSet()
        val unreferencedKnots = knotNames - divertTargets - setOf("__preamble")

        // Find missing targets (diverts to nonexistent knots)
        val allSectionNames = structure.sections.map { it.name }.toSet() + setOf("END", "DONE")
        val missingTargets = structure.diverts.filter { it.target !in allSectionNames }.map { it.target }.toSet()

        return mapOf(
            "total_lines" to structure.totalLines,
            "word_count" to wordCount,
            "knots" to structure.sections.count { it.type == "knot" },
            "stitches" to structure.sections.count { it.type == "stitch" },
            "functions" to structure.sections.count { it.type == "function" },
            "variables" to structure.variables.size,
            "includes" to structure.includes.size,
            "choices" to choiceCount,
            "diverts" to divertCount,
            "unreferenced_knots" to unreferencedKnots.toList(),
            "missing_divert_targets" to missingTargets.toList()
        )
    }

    /** List all section names with their types */
    fun listSections(source: String): List<Map<String, Any?>> {
        return parse(source).sections.map { s ->
            mapOf(
                "name" to s.name,
                "type" to s.type,
                "start_line" to s.startLine,
                "end_line" to s.endLine,
                "parent" to s.parent,
                "line_count" to (s.endLine - s.startLine)
            )
        }
    }
}
