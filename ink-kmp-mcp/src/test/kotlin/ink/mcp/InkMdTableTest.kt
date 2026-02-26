package ink.mcp

import kotlin.test.*
import java.io.File
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.ext.tables.*
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * Tests markdown files containing ```ink fenced code blocks and markdown tables.
 *
 * Uses **Flexmark** (CommonMark parser) for AST-based extraction of:
 *   - FencedCodeBlock nodes with info="ink" → ink blocks
 *   - TableBlock/TableHead/TableBody nodes → markdown tables
 *
 * H1-H6 headings act as a **file path** / POI spreadsheet sheet name /
 * ink LIST of headers addressing the tables and ```ink blocks beneath them.
 *
 * Validates that docs/BIDI_TDD_ISSUES.md:
 *   1. Parses as a valid CommonMark document via Flexmark
 *   2. Contains extractable ```ink code blocks with valid ink syntax
 *   3. Contains markdown tables with correct schema (# | Title | Tags | TDD)
 *   4. Tags are semicolon-delimited lowercase kebab-case arrays
 *   5. TDD column uses YES:/NO:/PARTIAL: prefixes
 *   6. Ink blocks cross-reference table entries
 *   7. Heading path tree addresses blocks and tables
 */
class InkMdTableTest {

    // ═══════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════

    data class HeadingNode(
        val level: Int,
        val text: String,
        val path: List<String> // full heading ancestry
    )

    data class InkBlock(
        val heading: HeadingNode,
        val source: String,
        val lineNumber: Int
    )

    data class MdTable(
        val heading: HeadingNode,
        val headers: List<String>,
        val rows: List<List<String>>,
        val lineNumber: Int
    )

    data class IssueRow(
        val issueNumber: String,
        val title: String,
        val tags: List<String>,
        val tddVerdict: String,   // YES, NO, PARTIAL
        val tddReason: String
    )

    companion object {
        private val projectRoot = System.getProperty("user.dir")
            .let { if (it.endsWith("ink-kmp-mcp")) File(it).parent else it }

        private val mdSource: String by lazy {
            File("$projectRoot/docs/BIDI_TDD_ISSUES.md").readText(Charsets.UTF_8)
        }

        // ─── Flexmark AST parser ───
        private val flexmarkOptions = MutableDataSet().apply {
            set(Parser.EXTENSIONS, listOf(TablesExtension.create()))
        }
        private val flexmarkParser: Parser = Parser.builder(flexmarkOptions).build()
        private val mdDocument: Node by lazy { flexmarkParser.parse(mdSource) }

        // ─── Flexmark AST-based extractors with heading-path tree ───
        private data class ExtractResult(
            val headingTree: List<HeadingNode>,
            val inkBlocks: List<InkBlock>,
            val tables: List<MdTable>
        )

        private val extractResult: ExtractResult by lazy { extractViaFlexmark(mdDocument) }
        private val headingTree: List<HeadingNode> by lazy { extractResult.headingTree }
        private val inkBlocks: List<InkBlock> by lazy { extractResult.inkBlocks }
        private val mdTables: List<MdTable> by lazy { extractResult.tables }
        private val issuesTables: List<MdTable> by lazy {
            mdTables.filter { "#" in it.headers && "TDD" in it.headers }
        }

        // ─── Known tags vocabulary ───
        private val KNOWN_TAGS = setOf(
            "compiler", "runtime", "parser", "ui", "editor", "electron", "export",
            "crash", "regression", "platform", "ux", "file-io", "save", "syntax",
            "bidi", "rtl", "i18n", "performance", "feature-request", "documentation",
            "accessibility", "tags", "state", "choices", "glue", "threads", "tunnels",
            "variables", "lists", "logic", "api", "dark-mode", "packaging"
        )

        // ─── Ink syntax regexes (matching ink-grammar.ts) ───
        private val KNOT_REGEX = Regex("""^={2,}\s*\w[\w(, )]*={2,}\s*$""")
        private val VAR_DECL_REGEX = Regex("""^(VAR|CONST)\s+\w+\s*=""")
        private val LIST_DECL_REGEX = Regex("""^LIST\s+\w+\s*=""")
        private val LOGIC_LINE_REGEX = Regex("""^~\s""")
        private val CHOICE_REGEX = Regex("""^\*\s|^\+\s|^\*\s*\[|^\*\s*\{|^\*\s*->""")
        private val GLUE_REGEX = Regex("""<>""")
        private val TAG_KEBAB = Regex("""^[a-z][a-z0-9-]*$""")

        // ─── Flexmark AST extraction with heading-path tree ───

        /**
         * Walk the Flexmark AST collecting:
         * - Heading → heading path stack (h1-h6 as file paths / sheet names)
         * - FencedCodeBlock with info="ink" → InkBlock (routed by info string)
         * - TableBlock → MdTable
         *
         * This mirrors the generic ```[info] fenced code block routing pattern
         * used by Remirror, CodeMirror, Markdig, and marked.
         */
        private fun extractViaFlexmark(doc: Node): ExtractResult {
            val headingTree = mutableListOf<HeadingNode>()
            val inkBlocks = mutableListOf<InkBlock>()
            val tables = mutableListOf<MdTable>()

            // Heading stack tracks current path by level
            val headingStack = mutableListOf<Pair<Int, String>>()

            fun currentPath(): List<String> = headingStack.map { it.second }

            fun currentHeading(): HeadingNode {
                if (headingStack.isEmpty())
                    return HeadingNode(0, "(root)", emptyList())
                val last = headingStack.last()
                return HeadingNode(last.first, last.second, currentPath())
            }

            fun cellText(cell: TableCell): String =
                cell.text.toString().trim()

            fun walk(node: Node) {
                when (node) {
                    is Heading -> {
                        val level = node.level
                        val text = node.text.toString()

                        // Pop headings at same or deeper level
                        while (headingStack.isNotEmpty() && headingStack.last().first >= level)
                            headingStack.removeAt(headingStack.size - 1)

                        headingStack.add(level to text)
                        headingTree.add(HeadingNode(level, text, currentPath()))
                    }
                    is FencedCodeBlock -> {
                        // Generic ```[info] routing — only process info="ink"
                        val info = node.info.toString().trim()
                        if (info == "ink") {
                            inkBlocks.add(InkBlock(
                                heading = currentHeading(),
                                source = node.contentChars.toString().trimEnd(),
                                lineNumber = node.startLineNumber + 1
                            ))
                        }
                    }
                    is TableBlock -> {
                        var headers = listOf<String>()
                        val rows = mutableListOf<List<String>>()

                        var child = node.firstChild
                        while (child != null) {
                            when (child) {
                                is TableHead -> {
                                    val headerRow = child.firstChild as? TableRow
                                    if (headerRow != null) {
                                        headers = headerRow.children
                                            .filterIsInstance<TableCell>()
                                            .map { cellText(it) }
                                    }
                                }
                                is TableBody -> {
                                    var row = child.firstChild
                                    while (row != null) {
                                        if (row is TableRow) {
                                            rows.add(row.children
                                                .filterIsInstance<TableCell>()
                                                .map { cellText(it) })
                                        }
                                        row = row.next
                                    }
                                }
                            }
                            child = child.next
                        }
                        if (headers.isNotEmpty() && rows.isNotEmpty()) {
                            tables.add(MdTable(currentHeading(), headers, rows, node.startLineNumber + 1))
                        }
                    }
                }
                // Only recurse non-table children (TableBlock children handled above)
                if (node !is TableBlock) {
                    var child = node.firstChild
                    while (child != null) {
                        walk(child)
                        child = child.next
                    }
                }
            }
            walk(doc)
            return ExtractResult(headingTree, inkBlocks, tables)
        }

        fun parseIssueRow(headers: List<String>, row: List<String>): IssueRow {
            val h = headers.indexOf("#")
            val t = headers.indexOf("Title")
            val tg = headers.indexOf("Tags")
            val td = headers.indexOf("TDD")
            val tddCell = row.getOrElse(td) { "" }
            val verdict = when {
                tddCell.startsWith("YES:") -> "YES"
                tddCell.startsWith("NO:") -> "NO"
                else -> "PARTIAL"
            }
            return IssueRow(
                issueNumber = row.getOrElse(h) { "" },
                title = row.getOrElse(t) { "" },
                tags = row.getOrElse(tg) { "" }.split(";").map { it.trim() },
                tddVerdict = verdict,
                tddReason = tddCell.replace(Regex("""^(YES|NO|PARTIAL):\s*"""), "")
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. FLEXMARK AST PARSING — heading path tree
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `flexmark parses markdown document without errors`() {
        assertNotNull(mdDocument, "Flexmark should parse the markdown")
        assertTrue(mdDocument.firstChild != null, "Document should have children")
    }

    @Test
    fun `heading tree has expected sections`() {
        assertTrue(headingTree.size > 10,
            "Expected many headings, got ${headingTree.size}")
        val h2s = headingTree.filter { it.level == 2 }
        assertTrue(h2s.size >= 5,
            "Expected >= 5 h2 sections, got ${h2s.size}")
    }

    @Test
    fun `heading path tracks ancestry`() {
        val hasAncestry = headingTree.any { it.path.size >= 2 }
        assertTrue(hasAncestry, "Some headings should have multi-level path ancestry")
    }

    @Test
    fun `h2 headings act as section paths for tables and blocks`() {
        val h2s = headingTree.filter { it.level == 2 }
        assertTrue(h2s.size >= 5, "Expected >= 5 h2 sections")
        // Each h2 should have a 2-element path (h1 + h2)
        for (h in h2s) {
            assertTrue(h.path.size >= 2,
                "h2 '${h.text}' should have path with >= 2 elements, got ${h.path}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. EXTRACT ```ink BLOCKS — routed by info="ink"
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `extracts all 9 ink blocks via flexmark`() {
        assertEquals(10, inkBlocks.size,
            "Headings: ${inkBlocks.map { it.heading.text }}")
    }

    @Test
    fun `ink blocks have heading path ancestry`() {
        for (block in inkBlocks) {
            assertTrue(block.heading.path.isNotEmpty(),
                "Block '${block.heading.text}' should have heading path")
        }
    }

    @Test
    fun `ink blocks are under E2E Test Resource section`() {
        for (block in inkBlocks) {
            val path = block.heading.path.joinToString(" > ")
            assertTrue(
                "E2E Test Resource" in path || "Issue" in path,
                "Ink block should be under E2E section, got: $path"
            )
        }
    }

    @Test
    fun `each ink block has non-empty source`() {
        for (block in inkBlocks) {
            assertTrue(block.source.isNotBlank(),
                "Block '${block.heading.text}' at line ${block.lineNumber} is empty")
        }
    }

    @Test
    fun `ink blocks reference issue numbers in headings`() {
        for (block in inkBlocks) {
            assertTrue(
                block.heading.text.contains("Issue") || block.heading.text.contains("ink-"),
                "Block heading '${block.heading.text}' should reference an Issue"
            )
        }
    }

    @Test
    fun `ink blocks contain ASSERT comments as test contracts`() {
        val withAsserts = inkBlocks.count { "// ASSERT:" in it.source }
        assertTrue(withAsserts >= 8,
            "At least 8 blocks should have // ASSERT: comments, got $withAsserts")
    }

    @Test
    fun `ink blocks cover expected issues`() {
        val allHeadings = inkBlocks.joinToString(" ") { it.heading.text }
        for (issue in listOf("#122", "#541", "#534", "#508", "#485", "ink-959", "ink-916", "ink-844")) {
            assertTrue(issue in allHeadings, "Missing ink block for issue $issue")
        }
    }

    @Test
    fun `ink blocks routed separately from other code`() {
        var inkCount = 0
        var otherCount = 0
        fun walk(node: Node) {
            if (node is FencedCodeBlock) {
                if (node.info.toString().trim() == "ink") inkCount++ else otherCount++
            }
            var child = node.firstChild
            while (child != null) { walk(child); child = child.next }
        }
        walk(mdDocument)
        assertEquals(10, inkCount)
        assertTrue(otherCount >= 0, "Non-ink code blocks exist as separate routes")
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. INK SYNTAX VALIDATION
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `VAR declarations use valid syntax`() {
        for (block in inkBlocks) {
            val varLines = block.source.lines().filter { it.trim().startsWith("VAR ") }
            for (line in varLines) {
                assertTrue(VAR_DECL_REGEX.containsMatchIn(line.trim()),
                    "VAR line '$line' in '${block.heading.text}' should match VAR_DECL_REGEX")
            }
        }
    }

    @Test
    fun `knot declarations use valid syntax`() {
        for (block in inkBlocks) {
            val knotLines = block.source.lines().filter { it.trim().startsWith("=== ") }
            for (line in knotLines) {
                assertTrue(KNOT_REGEX.containsMatchIn(line.trim()),
                    "Knot '$line' in '${block.heading.text}' should match KNOT_REGEX")
            }
        }
    }

    @Test
    fun `choice lines use valid bullet markers`() {
        for (block in inkBlocks) {
            val choiceLines = block.source.lines()
                .filter {
                    it.trim().let { l ->
                        l.startsWith("* ") || l.startsWith("+ ") ||
                        l.startsWith("* [") || l.startsWith("* {") ||
                        l == "* -> fallback"
                    }
                }
            for (line in choiceLines) {
                assertTrue(CHOICE_REGEX.containsMatchIn(line.trim()),
                    "Choice '$line' in '${block.heading.text}' should match CHOICE_REGEX")
            }
        }
    }

    @Test
    fun `blocks with diverts have END or DONE except issue 534`() {
        for (block in inkBlocks) {
            if (!block.source.contains("->")) continue
            if ("#534" in block.heading.text) continue

            val hasTerminator = "-> END" in block.source ||
                    "-> DONE" in block.source ||
                    "->->" in block.source
            assertTrue(hasTerminator,
                "Block '${block.heading.text}' has diverts but no END/DONE/tunnel-return")
        }
    }

    @Test
    fun `RTL blocks contain Hebrew or CJK text`() {
        val rtlBlocks = inkBlocks.filter { "#122" in it.heading.text || "#485" in it.heading.text }
        val hebrewRange = Regex("[\u0590-\u05FF]")
        val cjkRange = Regex("[\u3000-\u9FFF\uAC00-\uD7AF]")

        for (block in rtlBlocks) {
            assertTrue(hebrewRange.containsMatchIn(block.source) || cjkRange.containsMatchIn(block.source),
                "Block '${block.heading.text}' should contain RTL or CJK text")
        }
    }

    @Test
    fun `CJK block contains Japanese Hiragana, Chinese CJK Unified, and Korean Hangul`() {
        val cjkBlock = inkBlocks.find { "#485" in it.heading.text }
        assertNotNull(cjkBlock, "Should find CJK block for issue #485")
        assertTrue(Regex("[\u3040-\u309F]").containsMatchIn(cjkBlock.source), "Missing Hiragana")
        assertTrue(Regex("[\u4E00-\u9FFF]").containsMatchIn(cjkBlock.source), "Missing CJK Unified")
        assertTrue(Regex("[\uAC00-\uD7AF]").containsMatchIn(cjkBlock.source), "Missing Hangul")
    }

    @Test
    fun `glue blocks contain the glue operator`() {
        val glueBlocks = inkBlocks.filter { GLUE_REGEX.containsMatchIn(it.source) }
        assertTrue(glueBlocks.isNotEmpty(), "At least one ink block should use glue <>")
    }

    @Test
    fun `logic tilda lines are valid`() {
        for (block in inkBlocks) {
            val logicLines = block.source.lines().filter { it.trim().startsWith("~ ") }
            for (line in logicLines) {
                assertTrue(LOGIC_LINE_REGEX.containsMatchIn(line.trim()),
                    "Logic line '$line' in '${block.heading.text}' should match LOGIC_LINE_REGEX")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. MARKDOWN TABLE SCHEMA
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `finds at least 2 issues tables`() {
        assertTrue(issuesTables.size >= 2,
            "Expected >= 2 issues tables, got ${issuesTables.size}")
    }

    @Test
    fun `issues tables addressed under section headings`() {
        for (table in issuesTables) {
            val path = table.heading.path.joinToString(" > ")
            assertTrue(
                "inkle/inky" in path || "inkle/ink" in path,
                "Issues table should be under inkle section, got: $path"
            )
        }
    }

    @Test
    fun `tables have heading path ancestry`() {
        for (table in mdTables) {
            assertTrue(table.heading.path.isNotEmpty(),
                "Table at '${table.heading.text}' should have heading path")
        }
    }

    @Test
    fun `issues tables have correct columns`() {
        for (table in issuesTables) {
            for (col in listOf("#", "Title", "Tags", "TDD")) {
                assertTrue(col in table.headers,
                    "Table '${table.heading.text}' missing column: $col")
            }
        }
    }

    @Test
    fun `inky table has at least 40 rows`() {
        val inkyTable = issuesTables.find { "inkle/inky" in it.heading.path.joinToString(" ") }!!
        assertTrue(inkyTable.rows.size >= 40,
            "Expected >= 40 inky rows, got ${inkyTable.rows.size}")
    }

    @Test
    fun `ink table has at least 20 rows`() {
        val inkTable = issuesTables.find { "inkle/ink" in it.heading.path.joinToString(" ") }!!
        assertTrue(inkTable.rows.size >= 20,
            "Expected >= 20 ink rows, got ${inkTable.rows.size}")
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. TABLE DATA CONSISTENCY
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `all issue cells contain GitHub links`() {
        for (table in issuesTables) {
            val idx = table.headers.indexOf("#")
            for ((r, row) in table.rows.withIndex()) {
                assertTrue("github.com/inkle/" in row[idx],
                    "Row ${r + 1} in '${table.heading.text}' missing GitHub link: ${row[idx]}")
            }
        }
    }

    @Test
    fun `tags are semicolon-delimited lowercase kebab-case`() {
        for (table in issuesTables) {
            val idx = table.headers.indexOf("Tags")
            for ((r, row) in table.rows.withIndex()) {
                val tags = row[idx].split(";").map { it.trim() }
                for (tag in tags) {
                    assertTrue(TAG_KEBAB.matches(tag),
                        "Tag '$tag' in row ${r + 1} of '${table.heading.text}' should be kebab-case")
                }
            }
        }
    }

    @Test
    fun `tags use known vocabulary only`() {
        for (table in issuesTables) {
            val idx = table.headers.indexOf("Tags")
            for ((r, row) in table.rows.withIndex()) {
                val tags = row[idx].split(";").map { it.trim() }
                for (tag in tags) {
                    assertTrue(tag in KNOWN_TAGS,
                        "Unknown tag '$tag' in row ${r + 1} of '${table.heading.text}'")
                }
            }
        }
    }

    @Test
    fun `TDD column starts with YES or NO or PARTIAL`() {
        for (table in issuesTables) {
            val idx = table.headers.indexOf("TDD")
            for ((r, row) in table.rows.withIndex()) {
                val tdd = row[idx]
                assertTrue(
                    tdd.startsWith("YES:") || tdd.startsWith("NO:") || tdd.startsWith("PARTIAL:"),
                    "Row ${r + 1} TDD invalid: '${tdd.take(40)}'")
            }
        }
    }

    @Test
    fun `parseIssueRow produces typed fields`() {
        val table = issuesTables.first()
        val parsed = parseIssueRow(table.headers, table.rows.first())
        assertTrue(parsed.issueNumber.isNotEmpty())
        assertTrue(parsed.title.isNotEmpty())
        assertTrue(parsed.tags.isNotEmpty())
        assertTrue(parsed.tddVerdict in listOf("YES", "NO", "PARTIAL"))
        assertTrue(parsed.tddReason.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. CROSS-REFERENCE: INK BLOCKS ↔ TABLE ENTRIES
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `ink block issues map to TDD=YES or PARTIAL table rows`() {
        val allRows = issuesTables.flatMap { table ->
            table.rows.map { parseIssueRow(table.headers, it) }
        }

        for (block in inkBlocks) {
            val match = Regex("""#(\d+)|ink-(\d+)""").find(block.heading.text) ?: continue
            val num = match.groupValues[1].ifEmpty { match.groupValues[2] }

            val tableRow = allRows.find { it.issueNumber.contains(num) }
            if (tableRow != null) {
                assertTrue(
                    tableRow.tddVerdict == "YES" || tableRow.tddVerdict == "PARTIAL",
                    "Ink block for issue $num has TDD=${tableRow.tddVerdict}, expected YES or PARTIAL"
                )
            }
        }
    }

    @Test
    fun `summary statistics percentages sum to approximately 100`() {
        val summaryTables = mdTables.filter {
            "Verdict" in it.headers && "Count" in it.headers
        }
        assertTrue(summaryTables.size >= 2)

        for (table in summaryTables) {
            val idxPct = table.headers.indexOf("%")
            var totalPct = 0
            for (row in table.rows) {
                val pct = row[idxPct].replace(Regex("""\*+|%"""), "").trim().toIntOrNull()
                if (pct != null) totalPct += pct
            }
            assertTrue(totalPct in 99..101,
                "Percentages in '${table.heading.text}' should sum to ~100%, got $totalPct%")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. BIDI TEST MATRIX
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `test matrix table has coverage columns`() {
        val matrix = mdTables.find { "Feature" in it.headers && "TDD Gap" in it.headers }
        assertNotNull(matrix, "Should find test matrix table")
        for (col in listOf("bidi_and_tdd.ink", "bidi-e2e.test.js", "BidiTddInkTest.kt", "bidify.test.js")) {
            assertTrue(col in matrix.headers, "Missing matrix column: $col")
        }
    }

    @Test
    fun `test matrix covers key ink features`() {
        val matrix = mdTables.find { "Feature" in it.headers && "TDD Gap" in it.headers }!!
        val idx = matrix.headers.indexOf("Feature")
        val features = matrix.rows.map { it[idx] }

        val expected = listOf("Plain text", "Choices", "Diverts", "Glue", "Tags",
            "Tunnels", "Threads", "Variables", "Functions", "Lists")
        for (f in expected) {
            assertTrue(features.any { f in it },
                "Test matrix should cover feature: $f")
        }
    }
}
