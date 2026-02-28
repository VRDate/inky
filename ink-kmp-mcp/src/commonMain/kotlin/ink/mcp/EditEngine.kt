package ink.mcp

import ink.kt.*
import kotlinx.serialization.json.Json

/**
 * Ink script section editor — wraps [InkParser] AST for structured editing.
 *
 * Uses [Container] from ink.kt as section nodes (InkSection = Container).
 * Produces @Serializable response types ([SectionInfo], [VariableInfo],
 * [InkStructureResponse]) shared by server handlers and [ink.mcp.client.InkMcpClient].
 *
 * Provides:
 *   - List all knots, stitches, functions, variables
 *   - Get/replace individual sections by name
 *   - Insert new sections at specified positions
 *   - Rename knots/stitches (updates all diverts)
 *   - Extract statistics and structure analysis
 */
class EditEngine {

    /** Minimal [StoryWrapper] for parse-only usage (no runtime features). */
    private class EditStoryWrapper(private val source: String) : StoryWrapper {
        override fun getFileContent(fileId: String): String = source
        override fun getStoryObject(objId: String): Any = Unit
        override fun getInterrupt(s: String): StoryInterrupt =
            throw UnsupportedOperationException("Not available in edit mode")
        override fun resolveTag(t: String) {}
        override fun logDebug(m: String) {}
        override fun logError(m: String) {}
        override fun logException(e: Exception) {}
    }

    /**
     * Parse ink source into a structured section tree using [InkParser].
     *
     * Variables and includes are extracted from source text since [InkParser]
     * doesn't expose them as metadata. Sections come from the AST containers
     * ([Knot], [Stitch]).
     */
    fun parse(source: String): InkStructureResponse {
        val lines = source.lines()

        // Extract includes and variables from source text
        val includes = mutableListOf<String>()
        val variables = mutableListOf<VariableInfo>()
        val diverts = mutableListOf<DivertInfo>()

        for ((lineNum, line) in lines.withIndex()) {
            val trimmed = line.trim()

            val includeMatch = INCLUDE_RE.find(trimmed)
            if (includeMatch != null) {
                includes.add(includeMatch.groupValues[1].trim())
            }

            val varMatch = VAR_RE.find(trimmed)
            if (varMatch != null) {
                variables.add(VariableInfo(
                    name = varMatch.groupValues[2],
                    type = varMatch.groupValues[1],
                    initialValue = varMatch.groupValues[3].trim(),
                    line = lineNum
                ))
            }

            // Collect divert targets
            val divertMatches = DIVERT_RE.findAll(line)
            for (m in divertMatches) {
                diverts.add(DivertInfo(target = m.groupValues[2], line = lineNum))
            }
        }

        // Parse with InkParser for section structure
        val sections = mutableListOf<SectionInfo>()
        var divertCount = diverts.size

        try {
            // Strip INCLUDE lines to avoid file resolution during edit parsing
            val cleanSource = lines
                .mapIndexed { i, l -> if (l.trim().startsWith("INCLUDE ")) "" else l }
                .joinToString("\n")
            val wrapper = EditStoryWrapper(cleanSource)
            val story = InkParser.parse(cleanSource, wrapper, "edit.ink")

            // Walk AST: collect containers (Knot/Stitch) sorted by line number
            val containers = mutableListOf<Container>()
            for ((_, obj) in story.content) {
                when (obj) {
                    is Knot -> containers.add(obj)
                    is Stitch -> containers.add(obj)
                }
            }
            containers.sortBy { it.lineNumber }

            // Convert containers → SectionInfo
            for ((i, container) in containers.withIndex()) {
                val startLine = container.lineNumber - 1  // InkParser is 1-based → 0-based
                val endLine = if (i < containers.size - 1)
                    containers[i + 1].lineNumber - 1
                else
                    lines.size

                val name = when (container) {
                    is Stitch -> container.id.substringAfterLast('.')
                    else -> container.id
                }

                val type = when {
                    container is Knot && container.isFunction -> "function"
                    container is Knot -> "knot"
                    container is Stitch -> "stitch"
                    else -> "preamble"
                }

                val parent = when (container) {
                    is Stitch -> {
                        var p = container.parent
                        while (p != null && p !is Knot) p = p.parent
                        (p as? Knot)?.id
                    }
                    else -> null
                }

                val parameters = when (container) {
                    is ParameterizedContainer -> container.parameters
                    else -> emptyList()
                }

                val content = lines.subList(startLine, endLine.coerceAtMost(lines.size))
                    .joinToString("\n")

                sections.add(SectionInfo(
                    name = name,
                    type = type,
                    startLine = startLine,
                    endLine = endLine,
                    lineCount = endLine - startLine,
                    content = content,
                    parent = parent,
                    parameters = parameters
                ))
            }
        } catch (_: Exception) {
            // InkParser may fail on partial/invalid source or source without knots.
            // Variables, includes, and divert count are still available from text scan.
        }

        return InkStructureResponse(
            sections = sections,
            variables = variables,
            includes = includes,
            totalLines = lines.size,
            divertCount = divertCount,
            diverts = diverts
        )
    }

    /** Get a specific section by name. */
    fun getSection(source: String, sectionName: String): SectionInfo? {
        return parse(source).sections.find { it.name == sectionName }
    }

    /** Replace a section's content by name. */
    fun replaceSection(source: String, sectionName: String, newContent: String): String {
        val section = getSection(source, sectionName)
            ?: throw IllegalArgumentException("Section not found: $sectionName")

        val lines = source.lines().toMutableList()
        val newLines = newContent.lines()

        for (i in (section.endLine - 1) downTo section.startLine) {
            lines.removeAt(i)
        }
        lines.addAll(section.startLine, newLines)

        return lines.joinToString("\n")
    }

    /** Insert a new section after an existing section. */
    fun insertAfter(source: String, afterSection: String, newContent: String): String {
        val section = getSection(source, afterSection)
            ?: throw IllegalArgumentException("Section not found: $afterSection")

        val lines = source.lines().toMutableList()
        val newLines = listOf("") + newContent.lines()
        lines.addAll(section.endLine, newLines)

        return lines.joinToString("\n")
    }

    /** Insert a new section at the end of the script. */
    fun appendSection(source: String, newContent: String): String {
        return source.trimEnd() + "\n\n" + newContent + "\n"
    }

    /** Rename a knot/stitch and update all diverts that reference it. */
    fun rename(source: String, oldName: String, newName: String): String {
        var result = source
        result = result.replace(Regex("""(===\s*)$oldName(\s*===)"""), "$1$newName$2")
        result = result.replace(Regex("""(===\s*)$oldName(\s*\()"""), "$1$newName$2")
        result = result.replace(Regex("""(=\s+)$oldName(\s*)$""", RegexOption.MULTILINE), "$1$newName$2")
        result = result.replace(Regex("""(->\s*)$oldName\b"""), "$1$newName")
        result = result.replace(Regex("""(->\s*)$oldName(\s*$)""", RegexOption.MULTILINE), "$1$newName$2")
        return result
    }

    /** Get statistics about the ink script. */
    fun getStats(source: String): Map<String, Any> {
        val structure = parse(source)
        val lines = source.lines()

        val choiceCount = lines.count { it.trim().let { t -> t.startsWith("*") || t.startsWith("+") } }
        val wordCount = source.split(Regex("""\s+""")).size

        val divertTargets = structure.diverts.map { it.target }.toSet()
        val knotNames = structure.sections.filter { it.type == "knot" }.map { it.name }.toSet()
        val unreferencedKnots = knotNames - divertTargets - setOf("__preamble")
        val allSectionNames = structure.sections.map { it.name }.toSet() + setOf("END", "DONE")
        val missingTargets = divertTargets.filter { it !in allSectionNames }.toSet()

        return mapOf(
            "total_lines" to structure.totalLines,
            "word_count" to wordCount,
            "knots" to structure.sections.count { it.type == "knot" },
            "stitches" to structure.sections.count { it.type == "stitch" },
            "functions" to structure.sections.count { it.type == "function" },
            "variables" to structure.variables.size,
            "includes" to structure.includes.size,
            "choices" to choiceCount,
            "diverts" to structure.divertCount,
            "unreferenced_knots" to unreferencedKnots.toList(),
            "missing_divert_targets" to missingTargets.toList()
        )
    }

    /** List all section names with their types. */
    fun listSections(source: String): List<SectionInfo> =
        parse(source).sections

    companion object {
        private val INCLUDE_RE = Regex("""^INCLUDE\s+(.+)$""")
        private val VAR_RE = Regex("""^(VAR|CONST|LIST|TEMP)\s+(\w+)\s*=\s*(.+)$""")
        private val DIVERT_RE = Regex("""->(\s*)(\w+)""")
    }
}
