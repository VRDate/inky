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
 * Validates that docs/BIDI_TDD_ISSUES.md:
 *   1. Parses as a valid CommonMark document via Flexmark
 *   2. Contains extractable ```ink code blocks with valid ink syntax
 *   3. Contains markdown tables with correct schema (# | Title | Tags | TDD)
 *   4. Tags are semicolon-delimited lowercase kebab-case arrays
 *   5. TDD column uses YES:/NO:/PARTIAL: prefixes
 *   6. Ink blocks cross-reference table entries
 *   7. Summary statistics are internally consistent
 */
class InkMdTableTest {

    // ═══════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════

    data class InkBlock(
        val heading: String,
        val source: String,
        val lineNumber: Int
    )

    data class MdTable(
        val heading: String,
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
            .let { if (it.endsWith("mcp-server")) File(it).parent else it }

        private val mdSource: String by lazy {
            File("$projectRoot/docs/BIDI_TDD_ISSUES.md").readText(Charsets.UTF_8)
        }

        // ─── Flexmark AST parser ───
        private val flexmarkOptions = MutableDataSet().apply {
            set(Parser.EXTENSIONS, listOf(TablesExtension.create()))
        }
        private val flexmarkParser: Parser = Parser.builder(flexmarkOptions).build()
        private val mdDocument: Node by lazy { flexmarkParser.parse(mdSource) }

        // ─── Flexmark-based extractors ───
        private val flexmarkInkBlocks: List<InkBlock> by lazy { extractInkBlocksViaFlexmark(mdDocument) }
        private val flexmarkTables: List<MdTable> by lazy { extractTablesViaFlexmark(mdDocument) }

        // ─── Regex-based extractors (cross-validation) ───
        private val inkBlocks: List<InkBlock> by lazy { extractInkBlocks(mdSource) }
        private val mdTables: List<MdTable> by lazy { extractMdTables(mdSource) }
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
        private val COMMENT_REGEX = Regex("""^//""")
        private val DIVERT_REGEX = Regex("""->""")
        private val GLUE_REGEX = Regex("""<>""")
        private val TAG_REGEX = Regex("""\s#\s""")
        private val TAG_KEBAB = Regex("""^[a-z][a-z0-9-]*$""")

        // ─── Parsers ───

        fun extractInkBlocks(md: String): List<InkBlock> {
            val blocks = mutableListOf<InkBlock>()
            val lines = md.lines()
            var inBlock = false
            var heading = ""
            var blockLines = mutableListOf<String>()
            var lineNum = 0

            for ((i, line) in lines.withIndex()) {
                val hm = Regex("""^#{1,4}\s+(.+)""").find(line)
                if (hm != null) heading = hm.groupValues[1].trim()

                if (!inBlock && line.trim() == "```ink") {
                    inBlock = true
                    blockLines = mutableListOf()
                    lineNum = i + 1
                } else if (inBlock && line.trim() == "```") {
                    inBlock = false
                    blocks.add(InkBlock(heading, blockLines.joinToString("\n"), lineNum))
                } else if (inBlock) {
                    blockLines.add(line)
                }
            }
            return blocks
        }

        fun extractMdTables(md: String): List<MdTable> {
            val tables = mutableListOf<MdTable>()
            val lines = md.lines()
            var heading = ""
            var i = 0

            while (i < lines.size) {
                val hm = Regex("""^#{1,4}\s+(.+)""").find(lines[i])
                if (hm != null) heading = hm.groupValues[1].trim()

                if (lines[i].trim().startsWith("|") && i + 1 < lines.size) {
                    val sep = lines[i + 1]
                    if (sep.trim().matches(Regex("""\|[\s\-:|]+\|.*"""))) {
                        val headers = parseRow(lines[i])
                        val rows = mutableListOf<List<String>>()
                        var j = i + 2
                        while (j < lines.size && lines[j].trim().startsWith("|")) {
                            rows.add(parseRow(lines[j]))
                            j++
                        }
                        if (rows.isNotEmpty()) {
                            tables.add(MdTable(heading, headers, rows, i + 1))
                        }
                        i = j
                        continue
                    }
                }
                i++
            }
            return tables
        }

        fun parseRow(line: String): List<String> =
            line.split("|").map { it.trim() }.filter { it.isNotEmpty() }

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

        // ─── Flexmark AST-based extractors ───

        /** Walk the AST collecting FencedCodeBlock with info="ink" */
        fun extractInkBlocksViaFlexmark(doc: Node): List<InkBlock> {
            val blocks = mutableListOf<InkBlock>()
            var lastHeading = ""

            fun walk(node: Node) {
                when (node) {
                    is Heading -> lastHeading = node.text.toString()
                    is FencedCodeBlock -> {
                        val info = node.info.toString().trim()
                        if (info == "ink") {
                            blocks.add(InkBlock(
                                heading = lastHeading,
                                source = node.contentChars.toString().trimEnd(),
                                lineNumber = node.startLineNumber + 1
                            ))
                        }
                    }
                }
                var child = node.firstChild
                while (child != null) {
                    walk(child)
                    child = child.next
                }
            }
            walk(doc)
            return blocks
        }

        /** Walk the AST collecting TableBlock nodes */
        fun extractTablesViaFlexmark(doc: Node): List<MdTable> {
            val tables = mutableListOf<MdTable>()
            var lastHeading = ""

            fun cellText(cell: TableCell): String =
                cell.text.toString().trim()

            fun walk(node: Node) {
                when (node) {
                    is Heading -> lastHeading = node.text.toString()
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
                            tables.add(MdTable(lastHeading, headers, rows, node.startLineNumber + 1))
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
            return tables
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FLEXMARK AST PARSING — markdown document structure
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `flexmark parses markdown document without errors`() {
        assertNotNull(mdDocument, "Flexmark should parse the markdown")
        assertTrue(mdDocument.firstChild != null, "Document should have children")
    }

    @Test
    fun `flexmark finds all 9 ink fenced code blocks`() {
        assertEquals(9, flexmarkInkBlocks.size,
            "Flexmark ink blocks: ${flexmarkInkBlocks.map { it.heading }}")
    }

    @Test
    fun `flexmark ink blocks match regex ink blocks`() {
        assertEquals(inkBlocks.size, flexmarkInkBlocks.size,
            "Flexmark and regex extractors should find the same number of ink blocks")
        for (i in inkBlocks.indices) {
            assertEquals(inkBlocks[i].heading, flexmarkInkBlocks[i].heading,
                "Block $i heading should match")
        }
    }

    @Test
    fun `flexmark extracts tables as TableBlock AST nodes`() {
        assertTrue(flexmarkTables.isNotEmpty(),
            "Flexmark should find tables in the markdown")
    }

    @Test
    fun `flexmark tables match regex tables in count`() {
        // Flexmark might merge or split differently, but counts should be close
        assertTrue(flexmarkTables.size >= mdTables.size - 2,
            "Flexmark found ${flexmarkTables.size} tables, regex found ${mdTables.size}")
    }

    @Test
    fun `flexmark issues tables have correct columns`() {
        val fIssuesTables = flexmarkTables.filter {
            "#" in it.headers && "TDD" in it.headers
        }
        assertTrue(fIssuesTables.size >= 2,
            "Flexmark should find >= 2 issues tables, got ${fIssuesTables.size}")
        for (table in fIssuesTables) {
            for (col in listOf("#", "Title", "Tags", "TDD")) {
                assertTrue(col in table.headers,
                    "Flexmark table '${table.heading}' missing column: $col")
            }
        }
    }

    @Test
    fun `flexmark ink blocks contain valid ink source`() {
        for (block in flexmarkInkBlocks) {
            assertTrue(block.source.isNotBlank(),
                "Flexmark block '${block.heading}' has empty source")
            // Every ink block should have at least one ink construct
            val hasInkSyntax = block.source.contains("===") ||
                    block.source.contains("->") ||
                    block.source.contains("VAR ") ||
                    block.source.contains("* [")
            assertTrue(hasInkSyntax,
                "Flexmark block '${block.heading}' has no recognizable ink syntax")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INK BLOCK EXTRACTION (regex-based, cross-validates flexmark)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `extracts all 9 ink blocks from markdown`() {
        assertEquals(9, inkBlocks.size,
            "Headings: ${inkBlocks.map { it.heading }}")
    }

    @Test
    fun `each ink block has non-empty source`() {
        for (block in inkBlocks) {
            assertTrue(block.source.isNotBlank(),
                "Block '${block.heading}' at line ${block.lineNumber} is empty")
        }
    }

    @Test
    fun `ink blocks reference issue numbers in headings`() {
        for (block in inkBlocks) {
            assertTrue(
                block.heading.contains("Issue") || block.heading.contains("ink-"),
                "Block heading '${block.heading}' should reference an Issue"
            )
        }
    }

    @Test
    fun `ink blocks contain ASSERT comments as test contracts`() {
        val withAsserts = inkBlocks.count { "// ASSERT:" in it.source }
        assertTrue(withAsserts >= 8,
            "At least 8 blocks should have // ASSERT: comments, got $withAsserts")
    }

    // ═══════════════════════════════════════════════════════════════
    // INK SYNTAX VALIDATION
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `VAR declarations use valid syntax`() {
        for (block in inkBlocks) {
            val varLines = block.source.lines().filter { it.trim().startsWith("VAR ") }
            for (line in varLines) {
                assertTrue(VAR_DECL_REGEX.containsMatchIn(line.trim()),
                    "VAR line '$line' in '${block.heading}' should match VAR_DECL_REGEX")
            }
        }
    }

    @Test
    fun `knot declarations use valid syntax`() {
        for (block in inkBlocks) {
            val knotLines = block.source.lines().filter { it.trim().startsWith("=== ") }
            for (line in knotLines) {
                assertTrue(KNOT_REGEX.containsMatchIn(line.trim()),
                    "Knot '$line' in '${block.heading}' should match KNOT_REGEX")
            }
        }
    }

    @Test
    fun `choice lines use valid bullet markers`() {
        for (block in inkBlocks) {
            val choiceLines = block.source.lines()
                .filter { it.trim().let { l -> l.startsWith("* ") || l.startsWith("+ ") || l.startsWith("* [") || l.startsWith("* {") || l == "* -> fallback" } }
            for (line in choiceLines) {
                assertTrue(CHOICE_REGEX.containsMatchIn(line.trim()),
                    "Choice '$line' in '${block.heading}' should match CHOICE_REGEX")
            }
        }
    }

    @Test
    fun `blocks with diverts have END or DONE except issue 534`() {
        for (block in inkBlocks) {
            if (!block.source.contains("->")) continue
            if ("#534" in block.heading) continue  // intentionally missing END

            val hasTerminator = "-> END" in block.source ||
                    "-> DONE" in block.source ||
                    "->->" in block.source
            assertTrue(hasTerminator,
                "Block '${block.heading}' has diverts but no END/DONE/tunnel-return")
        }
    }

    @Test
    fun `RTL blocks contain Hebrew or CJK text`() {
        val rtlBlocks = inkBlocks.filter { "#122" in it.heading || "#485" in it.heading }
        val hebrewRange = Regex("[\u0590-\u05FF]")
        val cjkRange = Regex("[\u3000-\u9FFF\uAC00-\uD7AF]")

        for (block in rtlBlocks) {
            assertTrue(hebrewRange.containsMatchIn(block.source) || cjkRange.containsMatchIn(block.source),
                "Block '${block.heading}' should contain RTL or CJK text")
        }
    }

    @Test
    fun `CJK block contains Japanese Hiragana, Chinese CJK Unified, and Korean Hangul`() {
        val cjkBlock = inkBlocks.find { "#485" in it.heading }
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
                    "Logic line '$line' in '${block.heading}' should match LOGIC_LINE_REGEX")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MARKDOWN TABLE SCHEMA
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `finds at least 2 issues tables`() {
        assertTrue(issuesTables.size >= 2,
            "Expected >= 2 issues tables, got ${issuesTables.size}")
    }

    @Test
    fun `inky issues table has required columns`() {
        val inkyTable = issuesTables.find { "inkle/inky" in it.heading }
        assertNotNull(inkyTable)
        for (col in listOf("#", "Title", "Tags", "TDD")) {
            assertTrue(col in inkyTable.headers, "Missing column: $col")
        }
    }

    @Test
    fun `ink issues table has required columns`() {
        val inkTable = issuesTables.find { "inkle/ink" in it.heading }
        assertNotNull(inkTable)
        for (col in listOf("#", "Title", "Tags", "TDD")) {
            assertTrue(col in inkTable.headers, "Missing column: $col")
        }
    }

    @Test
    fun `inky table has at least 40 rows`() {
        val inkyTable = issuesTables.find { "inkle/inky" in it.heading }!!
        assertTrue(inkyTable.rows.size >= 40,
            "Expected >= 40 inky rows, got ${inkyTable.rows.size}")
    }

    @Test
    fun `ink table has at least 20 rows`() {
        val inkTable = issuesTables.find { "inkle/ink" in it.heading }!!
        assertTrue(inkTable.rows.size >= 20,
            "Expected >= 20 ink rows, got ${inkTable.rows.size}")
    }

    @Test
    fun `all issue cells contain GitHub links`() {
        for (table in issuesTables) {
            val idx = table.headers.indexOf("#")
            for ((r, row) in table.rows.withIndex()) {
                assertTrue("github.com/inkle/" in row[idx],
                    "Row ${r + 1} in '${table.heading}' missing GitHub link: ${row[idx]}")
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
                        "Tag '$tag' in row ${r + 1} of '${table.heading}' should be kebab-case")
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
                        "Unknown tag '$tag' in row ${r + 1} of '${table.heading}'")
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
    // CROSS-REFERENCE: INK BLOCKS ↔ TABLE ENTRIES
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `ink block issues map to TDD=YES or PARTIAL table rows`() {
        val allRows = issuesTables.flatMap { table ->
            table.rows.map { parseIssueRow(table.headers, it) }
        }

        for (block in inkBlocks) {
            val match = Regex("""#(\d+)|ink-(\d+)""").find(block.heading) ?: continue
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
                "Percentages in '${table.heading}' should sum to ~100%, got $totalPct%")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BIDI TEST MATRIX
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
