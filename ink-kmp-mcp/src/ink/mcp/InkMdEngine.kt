package ink.mcp

import org.slf4j.LoggerFactory

/**
 * Ink+Markdown template engine.
 *
 * Processes Markdown documents that contain ```ink code blocks and tables.
 * H1-H6 headers above ```ink blocks define output file names.
 * Markdown tables define variables/items used in ink code blocks.
 *
 * This enables:
 *   - LLMs to write ink stories in familiar Markdown format
 *   - Remirror/CodeMirror MD editing with embedded ink blocks
 *   - Tables as data sources (items, characters, locations) for ink variables
 *   - Yjs collaboration on both MD prose and ink code blocks
 *
 * Example Markdown template:
 * ```
 * # characters.ink
 *
 * | name    | role     | health |
 * |---------|----------|--------|
 * | Arthur  | knight   | 100    |
 * | Merlin  | wizard   | 80     |
 *
 * ```ink
 * VAR player_name = "Arthur"
 * VAR player_role = "knight"
 * VAR player_health = 100
 *
 * === start ===
 * You are {player_name}, a {player_role}.
 * -> END
 * ```
 *
 * # items.ink
 *
 * | item    | price | effect       |
 * |---------|-------|--------------|
 * | sword   | 50    | damage +10   |
 * | potion  | 25    | health +20   |
 *
 * ```ink
 * LIST items = sword, potion
 * -> END
 * ```
 * ```
 */
class InkMdEngine {

    private val log = LoggerFactory.getLogger(InkMdEngine::class.java)

    data class MdTable(
        val name: String,
        val columns: List<String>,
        val rows: List<Map<String, String>>
    )

    data class InkFile(
        val name: String,
        val inkSource: String,
        val headerLevel: Int
    )

    data class ParseResult(
        val files: List<InkFile>,
        val tables: List<MdTable>
    )

    /** Parse a Markdown document into ink files and data tables */
    fun parse(markdown: String): ParseResult {
        val lines = markdown.lines()
        val files = mutableListOf<InkFile>()
        val tables = mutableListOf<MdTable>()

        var currentHeader: String? = null
        var currentHeaderLevel = 0
        var inInkBlock = false
        var inkLines = mutableListOf<String>()
        var tableLines = mutableListOf<String>()
        var tableName: String? = null

        for (line in lines) {
            val trimmed = line.trim()

            // Detect headers: # ... or ## ... etc.
            val headerMatch = Regex("""^(#{1,6})\s+(.+)$""").find(trimmed)
            if (headerMatch != null && !inInkBlock) {
                // Flush any pending table
                if (tableLines.isNotEmpty()) {
                    parseTable(tableName ?: "data", tableLines)?.let { tables.add(it) }
                    tableLines.clear()
                }

                currentHeaderLevel = headerMatch.groupValues[1].length
                currentHeader = headerMatch.groupValues[2].trim()
                tableName = currentHeader
                continue
            }

            // Detect ink code fence start
            if (trimmed == "```ink" && !inInkBlock) {
                inInkBlock = true
                inkLines = mutableListOf()
                continue
            }

            // Detect code fence end
            if (trimmed == "```" && inInkBlock) {
                inInkBlock = false
                val fileName = currentHeader ?: "story_${files.size + 1}.ink"
                val normalizedName = if (fileName.endsWith(".ink")) fileName else "$fileName.ink"
                files.add(InkFile(
                    name = normalizedName,
                    inkSource = inkLines.joinToString("\n"),
                    headerLevel = currentHeaderLevel
                ))
                inkLines.clear()
                continue
            }

            // Collect ink code
            if (inInkBlock) {
                inkLines.add(line)
                continue
            }

            // Detect table rows (lines starting with |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                tableLines.add(trimmed)
                continue
            }

            // Flush table if we hit a non-table line
            if (tableLines.isNotEmpty() && !trimmed.startsWith("|")) {
                parseTable(tableName ?: "data", tableLines)?.let { tables.add(it) }
                tableLines.clear()
            }
        }

        // Flush remaining table
        if (tableLines.isNotEmpty()) {
            parseTable(tableName ?: "data", tableLines)?.let { tables.add(it) }
        }

        return ParseResult(files = files, tables = tables)
    }

    /** Render: extract ink files and inject table data as VAR declarations */
    fun render(markdown: String): Map<String, String> {
        val parsed = parse(markdown)
        val result = mutableMapOf<String, String>()

        // Build lookup: table name -> table data
        val tablesByName = parsed.tables.associateBy { it.name }

        for (file in parsed.files) {
            var source = file.inkSource

            // Look for table references in the ink source and inject VARs
            // Convention: tables named the same as the section header provide data
            val baseName = file.name.removeSuffix(".ink")
            val table = tablesByName[baseName] ?: tablesByName[file.name]

            if (table != null) {
                val varDecls = generateVarDeclarations(table)
                source = varDecls + "\n" + source
            }

            result[file.name] = source
        }

        return result
    }

    /** Generate ink VAR declarations from a table */
    private fun generateVarDeclarations(table: MdTable): String {
        if (table.rows.isEmpty()) return ""

        val lines = mutableListOf<String>()
        lines.add("// Auto-generated from table: ${table.name}")

        // If single row, generate flat VARs
        if (table.rows.size == 1) {
            val row = table.rows[0]
            for ((col, value) in row) {
                val varName = col.lowercase().replace(Regex("""\s+"""), "_")
                val formattedValue = formatValue(value)
                lines.add("VAR $varName = $formattedValue")
            }
        } else {
            // Multiple rows: generate LIST + index
            val firstName = table.columns.firstOrNull() ?: "item"
            val listItems = table.rows.mapNotNull { it[firstName]?.lowercase()?.replace(" ", "_") }
            if (listItems.isNotEmpty()) {
                lines.add("LIST ${firstName.lowercase()}s = ${listItems.joinToString(", ")}")
            }

            // Also generate lookup functions or comments with data
            lines.add("// Table data: ${table.rows.size} rows x ${table.columns.size} columns")
            for ((i, row) in table.rows.withIndex()) {
                val values = row.entries.joinToString(", ") { "${it.key}=${it.value}" }
                lines.add("// [$i] $values")
            }
        }

        return lines.joinToString("\n")
    }

    /** Format a value for ink VAR declaration */
    private fun formatValue(value: String): String {
        // Try as int
        value.toIntOrNull()?.let { return it.toString() }
        // Try as float
        value.toDoubleOrNull()?.let { return it.toString() }
        // Boolean
        if (value.lowercase() in listOf("true", "false")) return value.lowercase()
        // String
        return "\"$value\""
    }

    /** Parse markdown table lines into MdTable */
    private fun parseTable(name: String, lines: List<String>): MdTable? {
        if (lines.size < 2) return null

        // First line: header
        val headerLine = lines[0]
        val columns = headerLine.trim('|').split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (columns.isEmpty()) return null

        // Second line might be separator (---|---|---)
        val dataStartIndex = if (lines.size > 1 && lines[1].contains("---")) 2 else 1

        // Data rows
        val rows = mutableListOf<Map<String, String>>()
        for (i in dataStartIndex until lines.size) {
            val cells = lines[i].trim('|').split("|").map { it.trim() }
            if (cells.size >= columns.size) {
                val row = mutableMapOf<String, String>()
                for ((j, col) in columns.withIndex()) {
                    row[col] = cells.getOrElse(j) { "" }
                }
                rows.add(row)
            }
        }

        return MdTable(name = name, columns = columns, rows = rows)
    }
}
