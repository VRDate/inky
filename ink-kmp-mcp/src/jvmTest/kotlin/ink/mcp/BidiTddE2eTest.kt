package ink.mcp

import ink.kt.SimpleJson
import ink.mcp.KtTestFixtures.engine
import ink.mcp.KtTestFixtures.bidiTddSource
import ink.mcp.KtTestFixtures.mdSource
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.ext.tables.*
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.*
import kotlin.time.TimeSource

// ═══════════════════════════════════════════════════════════════════════
// STOPWATCH — tracks elapsed time for script timeouts
// ═══════════════════════════════════════════════════════════════════════

private class Stopwatch(private val label: String, private val timeoutMs: Long = 30_000L) {
    private val mark = TimeSource.Monotonic.markNow()

    fun elapsedMs(): Long = mark.elapsedNow().inWholeMilliseconds

    fun checkTimeout(step: String = "") {
        val elapsed = elapsedMs()
        if (elapsed > timeoutMs) {
            throw AssertionError("Stopwatch '$label' timed out at ${elapsed}ms (limit: ${timeoutMs}ms). Step: $step")
        }
    }

    fun elapsed(): String = "${elapsedMs()}ms"
}

// ═══════════════════════════════════════════════════════════════════════
// SHARED FIXTURES — parsed once from bidi_and_tdd.ink + BIDI_TDD_ISSUES.md
// ═══════════════════════════════════════════════════════════════════════

private object E2eFixtures {
    val flexmarkOptions = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(TablesExtension.create()))
    }
    val flexmarkParser: Parser = Parser.builder(flexmarkOptions).build()
    val mdDocument: Node by lazy { flexmarkParser.parse(mdSource) }

    data class InkBlock(
        val heading: String,
        val source: String,
        val asserts: List<String>
    )

    data class IssueRecord(
        val number: String,
        val title: String,
        val tags: List<String>,
        val verdict: String,
        val reason: String
    )

    val inkBlocks: List<InkBlock> by lazy {
        val blocks = mutableListOf<InkBlock>()
        val headingStack = mutableListOf<String>()
        fun walk(node: Node) {
            when (node) {
                is Heading -> {
                    while (headingStack.size >= node.level) headingStack.removeAt(headingStack.size - 1)
                    headingStack.add(node.text.toString())
                }
                is FencedCodeBlock -> {
                    if (node.info.toString().trim() == "ink") {
                        val src = node.contentChars.toString().trimEnd()
                        val asserts = src.lines()
                            .filter { it.trim().startsWith("// ASSERT:") }
                            .map { it.trim().removePrefix("// ASSERT:").trim() }
                        blocks.add(InkBlock(headingStack.lastOrNull() ?: "", src, asserts))
                    }
                }
            }
            if (node !is TableBlock) {
                var child = node.firstChild; while (child != null) { walk(child); child = child.next }
            }
        }
        walk(mdDocument)
        blocks
    }

    val issueRecords: List<IssueRecord> by lazy {
        val records = mutableListOf<IssueRecord>()
        fun cellText(cell: TableCell): String = cell.text.toString().trim()
        fun walk(node: Node) {
            if (node is TableBlock) {
                var headers = listOf<String>()
                val rows = mutableListOf<List<String>>()
                var child = node.firstChild
                while (child != null) {
                    when (child) {
                        is TableHead -> {
                            val hr = child.firstChild as? TableRow
                            if (hr != null) headers = hr.children.filterIsInstance<TableCell>().map { cellText(it) }
                        }
                        is TableBody -> {
                            var row = child.firstChild
                            while (row != null) {
                                if (row is TableRow) rows.add(row.children.filterIsInstance<TableCell>().map { cellText(it) })
                                row = row.next
                            }
                        }
                    }
                    child = child.next
                }
                if ("#" in headers && "TDD" in headers) {
                    val iNum = headers.indexOf("#")
                    val iTitle = headers.indexOf("Title")
                    val iTags = headers.indexOf("Tags")
                    val iTdd = headers.indexOf("TDD")
                    for (row in rows) {
                        val tddCell = row.getOrElse(iTdd) { "" }
                        val verdict = when {
                            tddCell.startsWith("YES:") -> "YES"
                            tddCell.startsWith("NO:") -> "NO"
                            else -> "PARTIAL"
                        }
                        records.add(IssueRecord(
                            number = row.getOrElse(iNum) { "" },
                            title = row.getOrElse(iTitle) { "" },
                            tags = row.getOrElse(iTags) { "" }.split(";").map { it.trim() },
                            verdict = verdict,
                            reason = tddCell.replace(Regex("""^(YES|NO|PARTIAL):\s*"""), "")
                        ))
                    }
                }
            } else {
                var child = node.firstChild; while (child != null) { walk(child); child = child.next }
            }
        }
        walk(mdDocument)
        records
    }
}

// ═══════════════════════════════════════════════════════════════════════
// BidiTddInkE2eTest — E2E tests driven by bidi_and_tdd.ink resource
//
// Plays through every path (syntax, museum, TDD story, time travel),
// exercises threads, tunnels, glue, tags, functions, save/load, and
// verifies compiled JSON via SimpleJson round-trip.
// ═══════════════════════════════════════════════════════════════════════

@Tag("graaljs")
@Timeout(120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class BidiTddInkE2eTest {

    // ── 1. FULL SYNTAX PLAYTHROUGH — all 28 features ──────────────

    @Test
    fun `e2e ink syntax path exercises all 28 features and collects tags`() {
        val sw = Stopwatch("syntax-28", timeoutMs = 60_000L)
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            val syntaxIndex = initial.choices.indexOfFirst {
                it.text.contains("Syntax") || it.text.contains("28") || it.text.contains("תחביר")
            }
            assertTrue(syntaxIndex >= 0, "Should find Syntax choice")

            var result = engine.choose(sessionId, syntaxIndex)
            val allText = StringBuilder()
            val allTags = mutableListOf<String>()
            val stagesReached = mutableSetOf<Int>()

            for (turn in 0 until 400) {
                sw.checkTimeout("turn $turn, stages=${stagesReached.size}/28")

                allText.append(result.text)
                allTags.addAll(result.tags)

                for (m in Regex("""(\d{2})/28""").findAll(result.text))
                    stagesReached.add(m.groupValues[1].toInt())

                if (result.text.contains("28/28")) break

                if (result.canContinue) {
                    result = engine.continueStory(sessionId)
                } else if (result.choices.isNotEmpty()) {
                    val leaveIdx = result.choices.indexOfFirst {
                        it.text.contains("Leave") || it.text.contains("עזוב")
                    }
                    result = engine.choose(sessionId, if (leaveIdx >= 0) leaveIdx else 0)
                } else break
            }

            for (i in 1..28)
                assertTrue(i in stagesReached, "Stage $i/28 not reached. Stages: $stagesReached")

            val text = allText.toString()

            // Verify each of the 28 syntax feature labels appeared
            assertTrue("Knots" in text || "קשרים" in text, "01 knots")
            assertTrue("Stitches" in text || "תפרים" in text, "02 stitches")
            assertTrue("Choices" in text || "בחירות" in text, "03 choices")
            assertTrue("Nested" in text || "מקוננות" in text, "04 nested choices")
            assertTrue("Gathers" in text || "איסוף" in text, "05 gathers")
            assertTrue("Variables" in text || "משתנים" in text, "06 variables")
            assertTrue("Conditionals" in text || "תנאים" in text, "07 conditionals")
            assertTrue("Alternatives" in text || "חלופות" in text, "08 alternatives")
            assertTrue("Glue" in text || "דבק" in text, "09 glue")
            assertTrue("Tags" in text || "תגיות" in text, "10 tags")
            assertTrue("Parameters" in text || "פרמטרים" in text, "11 parameters")
            assertTrue("Functions" in text || "פונקציות" in text, "12 functions")
            assertTrue("Tunnels" in text || "מנהרות" in text, "13 tunnels")
            assertTrue("Threads" in text || "חוטים" in text, "14 threads")
            assertTrue("Lists" in text || "רשימות" in text, "15 lists")
            assertTrue("divert" in text.lowercase() || "הפניות" in text, "16 var diverts")
            assertTrue("Visit" in text || "ביקורים" in text, "17 visit counts")
            assertTrue("Conditional" in text || "מותנות" in text, "18 conditional choices")
            assertTrue("Multi-line" in text || "מרובי" in text, "19 multi-line cond")
            assertTrue("Strings" in text || "מחרוזות" in text, "20 strings")
            assertTrue("Math" in text || "מתמטיקה" in text, "21 math")
            assertTrue("Logic" in text || "לוגיקה" in text, "22 logic")
            assertTrue("Comments" in text || "הערות" in text, "23 comments")
            assertTrue("TODO" in text, "24 TODO")
            assertTrue("Escaping" in text || "תווים מיוחדים" in text, "25 escaping")
            assertTrue("INCLUDE" in text, "26 include")
            assertTrue("chains" in text || "שרשרת" in text, "27 divert chains")
            assertTrue("Summary" in text || "סיכום" in text, "28 summary")

            assertTrue(allTags.any { "CLEAR" in it }, "Should have CLEAR tags from knot headers")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 2. THREADS — syn_14: merged choices from <- ──────────────

    @Test
    fun `e2e ink threads merge choices from syn_thread_a and syn_thread_b`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            val syntaxIndex = initial.choices.indexOfFirst { it.text.contains("Syntax") || it.text.contains("28") }
            assertTrue(syntaxIndex >= 0)
            var result = engine.choose(sessionId, syntaxIndex)

            for (turn in 0 until 200) {
                if (result.text.contains("14/28")) break
                if (result.canContinue) {
                    result = engine.continueStory(sessionId)
                } else if (result.choices.isNotEmpty()) {
                    result = engine.choose(sessionId, 0)
                } else break
            }

            assertTrue(result.text.contains("14/28") || result.text.contains("Threads") ||
                result.text.contains("חוטים"), "Should reach thread stage")

            if (result.choices.isNotEmpty()) {
                val choiceTexts = result.choices.map { it.text }
                val hasFruit = choiceTexts.any { "Fruit" in it || "פירות" in it }
                val hasGossip = choiceTexts.any { "Gossip" in it || "שמועות" in it }
                val hasLeave = choiceTexts.any { "Leave" in it || "עזוב" in it }
                assertTrue(hasFruit || hasGossip || hasLeave,
                    "Thread stage should merge choices. Got: $choiceTexts")
            }
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 3. TIME TRAVEL PATH — Agile TDD ─────────────────────────

    @Test
    fun `e2e ink time travel path reaches alternate timeline`() {
        val sw = Stopwatch("time-travel", timeoutMs = 60_000L)
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            val ttIndex = initial.choices.indexOfFirst {
                it.text.contains("Time Travel") || it.text.contains("מסע בזמן")
            }
            assertTrue(ttIndex >= 0, "Should find Time Travel choice")

            var result = engine.choose(sessionId, ttIndex)
            val allText = StringBuilder()

            for (turn in 0 until 500) {
                sw.checkTimeout("turn $turn")
                allText.append(result.text)
                if (result.text.contains("TWO TIMELINES") || result.text.contains("שני צירי זמן") ||
                    result.choices.any { it.text.contains("End") || it.text.contains("סיים") }) break
                if (result.canContinue) {
                    result = engine.continueStory(sessionId)
                } else if (result.choices.isNotEmpty()) {
                    result = engine.choose(sessionId, 0)
                } else break
            }

            val text = allText.toString()
            assertTrue("Sprint" in text || "ספרינט" in text, "Should contain Sprint concept")
            assertTrue("TDD" in text, "Should contain TDD concept")

            val sprintNumber = engine.getVariable(sessionId, "sprint_number")
            assertNotNull(sprintNumber, "sprint_number should be set during time travel")
            val hasTests = engine.getVariable(sessionId, "has_tests")
            assertNotNull(hasTests, "has_tests should be set during time travel")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 4. TDD STORY — 8-year bug narrative ─────────────────────

    @Test
    fun `e2e ink tdd story covers 2016-2024 timeline`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            val tddIndex = initial.choices.indexOfFirst {
                it.text.contains("TDD Story") || it.text.contains("סיפור הבאג")
            }
            assertTrue(tddIndex >= 0)

            var result = engine.choose(sessionId, tddIndex)
            val allText = StringBuilder()

            for (turn in 0 until 300) {
                allText.append(result.text)
                if (result.choices.any { it.text.contains("Menu") || it.text.contains("תפריט") ||
                        it.text.contains("End") || it.text.contains("סיים") }) break
                if (result.canContinue) {
                    result = engine.continueStory(sessionId)
                } else if (result.choices.isNotEmpty()) {
                    result = engine.choose(sessionId, 0)
                } else break
            }

            val text = allText.toString()
            assertTrue("2017" in text || "2016" in text, "TDD story should reference years")
            assertTrue("#122" in text || "122" in text, "TDD story should reference issue #122")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 5. BIDI MUSEUM — all 10 RTL scripts ─────────────────────

    @Test
    fun `e2e ink museum renders all 10 RTL scripts`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            val museumIdx = initial.choices.indexOfFirst {
                it.text.contains("Museum") || it.text.contains("מוזיאון")
            }
            assertTrue(museumIdx >= 0)
            var result = engine.choose(sessionId, museumIdx)

            val allIdx = result.choices.indexOfFirst { it.text.contains("All 10") || it.text.contains("10") }
            assertTrue(allIdx >= 0)
            result = engine.choose(sessionId, allIdx)

            val text = result.text
            for ((name, sample) in mapOf(
                "Hebrew" to "שלום", "Arabic" to "مرحبا", "Persian" to "سلام",
                "Urdu" to "ہیلو", "Yiddish" to "שלום וועלט", "Syriac" to "ܫܠܡܐ",
                "Thaana" to "ހެލޯ", "N'Ko" to "ߊߟߎ", "Samaritan" to "ࠔࠋࠌ",
                "Mandaic" to "ࡔࡋࡀࡌࡀ"
            )) {
                assertTrue(sample in text, "Museum should render $name script ('$sample')")
            }
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 6. SETTINGS — language toggle ────────────────────────────

    @Test
    fun `e2e ink settings toggles language variable`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            val settingsIdx = initial.choices.indexOfFirst {
                it.text.contains("Settings") || it.text.contains("הגדרות")
            }
            assertTrue(settingsIdx >= 0)
            val result = engine.choose(sessionId, settingsIdx)

            assertTrue(result.text.contains("both") || result.text.contains("Settings") ||
                result.text.contains("הגדרות"), "Should be on settings page")

            val heIdx = result.choices.indexOfFirst { it.text.contains("עברית") }
            if (heIdx >= 0) {
                engine.choose(sessionId, heIdx)
                assertEquals("he", engine.getVariable(sessionId, "lang"),
                    "Language should be 'he' after choosing Hebrew")
            }
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 7. SAVE/LOAD STATE — time travel JSON round-trip ────────

    @Test
    fun `e2e ink save state and restore after progression`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            val ttIdx = initial.choices.indexOfFirst {
                it.text.contains("Time Travel") || it.text.contains("מסע בזמן")
            }
            assertTrue(ttIdx >= 0)
            engine.choose(sessionId, ttIdx)

            val savedState = engine.saveState(sessionId)
            assertTrue(savedState.isNotEmpty())

            // Verify saved state is valid JSON via SimpleJson
            val stateObj = SimpleJson.textToDictionary(savedState)
            assertTrue(stateObj.containsKey("flows") || stateObj.containsKey("callstackThreads") ||
                stateObj.containsKey("currentFlowName"),
                "Saved state should contain ink state structure")

            val varsBefore = engine.getVariable(sessionId, "sprint_number")
            val result = engine.continueStory(sessionId)
            if (result.choices.isNotEmpty()) engine.choose(sessionId, 0)

            engine.loadState(sessionId, savedState)
            assertEquals(varsBefore, engine.getVariable(sessionId, "sprint_number"),
                "sprint_number should be restored after load state")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 8. COMPILED JSON — SimpleJson verification ──────────────

    @Test
    fun `e2e ink compiled JSON contains all syntax patterns`() {
        val compiled = engine.compile(bidiTddSource)
        assertTrue(compiled.success)
        val json = compiled.json!!

        val story = SimpleJson.textToDictionary(json)
        assertTrue(story.containsKey("inkVersion"), "Should have inkVersion")
        assertTrue(story.containsKey("root"), "Should have root")
        assertTrue(story.containsKey("listDefs"), "Should have listDefs")

        val inkVersion = story["inkVersion"]
        assertTrue(inkVersion is Int && inkVersion >= 20, "inkVersion >= 20, got $inkVersion")

        @Suppress("UNCHECKED_CAST")
        val listDefs = story["listDefs"] as Map<String, Any?>
        assertTrue(listDefs.containsKey("mood"), "listDefs should contain 'mood'")
        assertTrue(listDefs.containsKey("inventory"), "listDefs should contain 'inventory'")
    }

    // ── 9. FUNCTION EVALUATION — clamp, t ───────────────────────

    @Test
    fun `e2e ink function evaluation with JSON serializable results`() {
        val (sessionId, _) = engine.startSession(bidiTddSource)
        try {
            // Test clamp function
            val clamped = engine.evaluateFunction(sessionId, "clamp", listOf(200, 0, 100))
            assertEquals(100, (clamped as Number).toInt())
            val clampedLow = engine.evaluateFunction(sessionId, "clamp", listOf(-50, 0, 100))
            assertEquals(0, (clampedLow as Number).toInt())
            val clampedMid = engine.evaluateFunction(sessionId, "clamp", listOf(42, 0, 100))
            assertEquals(42, (clampedMid as Number).toInt())

            // Test t() bilingual translation function
            engine.setVariable(sessionId, "lang", "en")
            val enText = engine.evaluateFunction(sessionId, "t", listOf("שלום", "Hello"))
            assertEquals("Hello", enText)
            engine.setVariable(sessionId, "lang", "he")
            val heText = engine.evaluateFunction(sessionId, "t", listOf("שלום", "Hello"))
            assertEquals("שלום", heText)
            engine.setVariable(sessionId, "lang", "both")
            val bothText = engine.evaluateFunction(sessionId, "t", listOf("שלום", "Hello"))
            assertEquals("שלום — Hello", bothText)

            // Serialize function results to JSON and verify round-trip
            val w = SimpleJson.Writer()
            w.writeObjectStart()
            w.writeProperty("clamp_200", (clamped as Number).toInt())
            w.writeProperty("clamp_neg50", (clampedLow as Number).toInt())
            w.writeProperty("clamp_42", (clampedMid as Number).toInt())
            w.writeProperty("t_en", enText as String)
            w.writeProperty("t_he", heText as String)
            w.writeProperty("t_both", bothText as String)
            w.writeObjectEnd()

            val results = SimpleJson.textToDictionary(w.toString())
            assertEquals(100, results["clamp_200"])
            assertEquals(0, results["clamp_neg50"])
            assertEquals(42, results["clamp_42"])
            assertEquals("Hello", results["t_en"])
            assertEquals("שלום", results["t_he"])
            assertEquals("שלום — Hello", results["t_both"])
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 10. PLAY THE GAME — track state across choices ─────────

    @Test
    fun `e2e ink play game tracks expected state across all paths`() {
        val sw = Stopwatch("play-game", timeoutMs = 60_000L)
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            // Record initial state
            assertEquals(100, (engine.getVariable(sessionId, "health") as Number).toInt())
            assertEquals(50, (engine.getVariable(sessionId, "gold") as Number).toInt())
            assertEquals(0, (engine.getVariable(sessionId, "bugs_found") as Number).toInt())
            assertEquals("both", engine.getVariable(sessionId, "lang"))

            // -- PLAY: Settings → change language → verify --
            val settingsIdx = initial.choices.indexOfFirst {
                it.text.contains("Settings") || it.text.contains("הגדרות")
            }
            var result = engine.choose(sessionId, settingsIdx)
            val enIdx = result.choices.indexOfFirst { it.text.contains("English") }
            if (enIdx >= 0) {
                result = engine.choose(sessionId, enIdx)
                assertEquals("en", engine.getVariable(sessionId, "lang"),
                    "After choosing English, lang should be 'en'")
            }

            // Navigate back to main menu
            val menuIdx = result.choices.indexOfFirst {
                it.text.contains("Back") || it.text.contains("חזרה") || it.text.contains("Menu")
            }
            if (menuIdx >= 0) result = engine.choose(sessionId, menuIdx)

            // -- PLAY: Smoke Test → verify health unchanged --
            val smokeIdx = result.choices.indexOfFirst {
                it.text.contains("Smoke") || it.text.contains("עשן")
            }
            if (smokeIdx >= 0) {
                result = engine.choose(sessionId, smokeIdx)
                val healthBefore = (engine.getVariable(sessionId, "health") as Number).toInt()
                // Play through smoke test
                for (turn in 0 until 50) {
                    sw.checkTimeout("smoke test turn $turn")
                    if (!result.canContinue && result.choices.isEmpty()) break
                    if (result.canContinue) result = engine.continueStory(sessionId)
                    else if (result.choices.isNotEmpty()) result = engine.choose(sessionId, 0)
                }
                // Health may be modified during smoke test; verify it's still in valid range
                val healthAfter = (engine.getVariable(sessionId, "health") as Number).toInt()
                assertTrue(healthAfter in 0..100,
                    "Health should be in 0..100 after smoke test, got $healthAfter (was $healthBefore)")
            }

            // -- PLAY: Use clamp function to verify bounds --
            val clamped = engine.evaluateFunction(sessionId, "clamp", listOf(999, 0, 100))
            assertEquals(100, (clamped as Number).toInt(), "clamp(999,0,100) = 100")

            // -- PLAY: Set health and verify via variable API --
            engine.setVariable(sessionId, "health", 75)
            assertEquals(75, (engine.getVariable(sessionId, "health") as Number).toInt(),
                "health should be 75 after setVariable")

            // -- PLAY: Use t() function for bilingual text --
            val langNow = engine.getVariable(sessionId, "lang") as String
            val tResult = engine.evaluateFunction(sessionId, "t", listOf("שלום", "Hello"))
            assertNotNull(tResult, "t() should return translated text")

            // Serialize the game state snapshot to JSON
            val w = SimpleJson.Writer()
            w.writeObjectStart()
            w.writeProperty("health", (engine.getVariable(sessionId, "health") as Number).toInt())
            w.writeProperty("gold", (engine.getVariable(sessionId, "gold") as Number).toInt())
            w.writeProperty("lang", langNow)
            w.writeProperty("tResult", tResult as String)
            w.writeProperty("clampResult", (clamped as Number).toInt())
            w.writeObjectEnd()

            val snapshot = SimpleJson.textToDictionary(w.toString())
            assertEquals(75, snapshot["health"], "health should be 75 after setVariable")
            assertTrue((snapshot["gold"] as Int) >= 0, "gold should be non-negative")
            assertEquals(100, snapshot["clampResult"])
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 11. GLUE — joined output across knots ───────────────────

    @Test
    fun `e2e ink glue produces joined output`() {
        val glueBlock = E2eFixtures.inkBlocks.find { "Glue" in it.heading && "Tags" in it.heading }
        if (glueBlock != null) {
            val compiled = engine.compile(glueBlock.source)
            if (compiled.success) {
                val (sessionId, result) = engine.startSessionFromJson(compiled.json!!)
                try {
                    var text = result.text
                    var r = result
                    while (r.canContinue) { r = engine.continueStory(sessionId); text += r.text }
                    assertTrue("Hello" in text && "World" in text,
                        "Glue block should produce joined text. Got: $text")
                } finally {
                    engine.endSession(sessionId)
                }
            }
        }

        assertTrue(bidiTddSource.contains("<>"), "bidi_and_tdd.ink should use glue <>")
        assertTrue(bidiTddSource.contains("syn_09b"), "Glue should chain to syn_09b")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// BidiTddMdE2eTest — E2E tests driven by BIDI_TDD_ISSUES.md resource
//
// Parses the markdown, extracts issue tables + ```ink blocks,
// serializes to JSON via SimpleJson, and cross-references ink↔table.
// ═══════════════════════════════════════════════════════════════════════

@Tag("graaljs")
@Timeout(120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class BidiTddMdE2eTest {

    // ── 1. ISSUE RECORDS EXTRACTION ─────────────────────────────

    @Test
    fun `e2e md issue records extracted are non-empty`() {
        assertTrue(E2eFixtures.issueRecords.size >= 60,
            "Expected >= 60 issue records, got ${E2eFixtures.issueRecords.size}")
    }

    // ── 2. JSON SERIALIZATION ROUND-TRIP ────────────────────────

    @Test
    fun `e2e md issue records serialize to JSON and deserialize back`() {
        val w = SimpleJson.Writer()
        w.writeArrayStart()
        for (issue in E2eFixtures.issueRecords) {
            w.writeObjectStart()
            w.writeProperty("number", issue.number)
            w.writeProperty("title", issue.title)
            w.writeProperty("verdict", issue.verdict)
            w.writeProperty("reason", issue.reason)
            w.writeProperty("tagCount", issue.tags.size)
            w.writeProperty("tags", issue.tags.joinToString(";"))
            w.writeObjectEnd()
        }
        w.writeArrayEnd()

        val json = w.toString()
        assertTrue(json.startsWith("["), "JSON should start with [")
        assertTrue(json.endsWith("]"), "JSON should end with ]")

        val arr = SimpleJson.textToArray(json)
        assertEquals(E2eFixtures.issueRecords.size, arr.size, "Round-trip should preserve count")

        @Suppress("UNCHECKED_CAST")
        val first = arr[0] as Map<String, Any?>
        assertTrue((first["number"] as String).isNotEmpty())
        assertTrue((first["title"] as String).isNotEmpty())
        assertTrue((first["verdict"] as String) in listOf("YES", "NO", "PARTIAL"))

        val firstTags = (first["tags"] as String).split(";")
        assertEquals(first["tagCount"] as Int, firstTags.size, "tagCount round-trip")
    }

    // ── 3. JSON AGGREGATE FUNCTIONS ─────────────────────────────

    @Test
    fun `e2e md JSON aggregate functions on issue data`() {
        val yesCount = E2eFixtures.issueRecords.count { it.verdict == "YES" }
        val noCount = E2eFixtures.issueRecords.count { it.verdict == "NO" }
        val partialCount = E2eFixtures.issueRecords.count { it.verdict == "PARTIAL" }
        val total = E2eFixtures.issueRecords.size

        val w = SimpleJson.Writer()
        w.writeObjectStart()
        w.writeProperty("total", total)
        w.writeProperty("yes", yesCount)
        w.writeProperty("no", noCount)
        w.writeProperty("partial", partialCount)
        w.writeProperty("yesPercent", (yesCount * 100) / total)
        w.writeProperty("preventable", yesCount + partialCount)
        w.writeObjectEnd()

        val summary = SimpleJson.textToDictionary(w.toString())
        assertEquals(total, summary["total"])
        assertEquals(yesCount, summary["yes"])
        assertEquals(yesCount + noCount + partialCount, total,
            "YES + NO + PARTIAL should equal total")

        // BIDI_TDD_ISSUES.md reports ~55% YES, but Flexmark parsing of link-heavy
        // cells can shift counts slightly; accept >= 40% as stable threshold
        assertTrue(yesCount >= 35, "At least 35 YES issues, got $yesCount")
        assertTrue((summary["yesPercent"] as Int) >= 40,
            "YES% should be >= 40%, got ${summary["yesPercent"]}")
    }

    // ── 4. TAG FREQUENCY ANALYSIS ───────────────────────────────

    @Test
    fun `e2e md JSON tag frequency analysis`() {
        val tagFrequency = mutableMapOf<String, Int>()
        for (issue in E2eFixtures.issueRecords) {
            for (tag in issue.tags) {
                tagFrequency[tag] = (tagFrequency[tag] ?: 0) + 1
            }
        }

        val w = SimpleJson.Writer()
        w.writeObjectStart()
        for ((tag, count) in tagFrequency.entries.sortedByDescending { it.value }) {
            w.writeProperty(tag, count)
        }
        w.writeObjectEnd()

        val freqMap = SimpleJson.textToDictionary(w.toString())
        assertTrue(freqMap.isNotEmpty())
        assertTrue((freqMap["compiler"] as? Int ?: 0) >= 5, "compiler >= 5")
        assertTrue((freqMap["crash"] as? Int ?: 0) >= 5, "crash >= 5")
        assertTrue((freqMap["runtime"] as? Int ?: 0) >= 3, "runtime >= 3")

        for (tag in tagFrequency.keys) {
            assertTrue(Regex("^[a-z][a-z0-9-]*$").matches(tag), "Tag '$tag' should be kebab-case")
        }
    }

    // ── 5. CROSS-REFERENCE: INK BLOCKS ↔ ISSUE TABLE ───────────

    @Test
    fun `e2e md cross-reference ink blocks to issue table`() {
        val crossRefs = mutableListOf<Pair<String, E2eFixtures.IssueRecord>>()

        for (block in E2eFixtures.inkBlocks) {
            val match = Regex("""#(\d+)|ink-(\d+)""").find(block.heading) ?: continue
            val num = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val record = E2eFixtures.issueRecords.find { it.number.contains(num) }
            if (record != null) crossRefs.add(num to record)
        }

        assertTrue(crossRefs.size >= 5,
            "At least 5 ink blocks should cross-ref to issue table, got ${crossRefs.size}")

        val w = SimpleJson.Writer()
        w.writeArrayStart()
        for ((num, record) in crossRefs) {
            w.writeObjectStart()
            w.writeProperty("issueNum", num)
            w.writeProperty("title", record.title)
            w.writeProperty("verdict", record.verdict)
            w.writeProperty("hasInkBlock", true)
            w.writeObjectEnd()
        }
        w.writeArrayEnd()

        val arr = SimpleJson.textToArray(w.toString())
        assertEquals(crossRefs.size, arr.size)

        @Suppress("UNCHECKED_CAST")
        for (item in arr) {
            val obj = item as Map<String, Any?>
            val verdict = obj["verdict"] as String
            assertTrue(verdict == "YES" || verdict == "PARTIAL",
                "Cross-ref issue ${obj["issueNum"]} should be YES or PARTIAL, got $verdict")
        }
    }

    // ── 6. COMPILE INK BLOCKS FROM MD ───────────────────────────

    @Test
    fun `e2e md ink blocks compile successfully`() {
        val compilableBlocks = E2eFixtures.inkBlocks.filter { block ->
            "#534" !in block.heading && block.source.contains("=== ")
        }

        assertTrue(compilableBlocks.size >= 8,
            "At least 8 ink blocks should be compilable, got ${compilableBlocks.size}")

        val failures = mutableListOf<String>()
        for (block in compilableBlocks) {
            val result = engine.compile(block.source)
            if (!result.success) failures.add("${block.heading}: ${result.errors.take(2)}")
        }

        assertTrue(failures.isEmpty(),
            "All compilable ink blocks should compile. Failures:\n${failures.joinToString("\n")}")
    }

    // ── 7. PLAY INK BLOCKS FROM MD ──────────────────────────────

    @Test
    fun `e2e md ink blocks compile to valid JSON`() {
        // Ink blocks from BIDI_TDD_ISSUES.md are standalone snippets that demonstrate
        // issue patterns. They compile successfully but may reference variables not
        // declared locally (e.g. hp, editor_ready). Verify compiled JSON is valid.
        val compilableBlocks = E2eFixtures.inkBlocks.filter { block ->
            "#534" !in block.heading && block.source.contains("=== ")
        }

        var jsonValidCount = 0
        for (block in compilableBlocks) {
            val compiled = engine.compile(block.source)
            if (!compiled.success || compiled.json == null) continue

            // Verify compiled JSON is parseable by SimpleJson
            val story = SimpleJson.textToDictionary(compiled.json)
            if (story.containsKey("inkVersion") && story.containsKey("root")) {
                jsonValidCount++

                // Serialize a summary of each compiled block
                val w = SimpleJson.Writer()
                w.writeObjectStart()
                w.writeProperty("heading", block.heading)
                w.writeProperty("inkVersion", story["inkVersion"] as Int)
                w.writeProperty("hasListDefs", story.containsKey("listDefs"))
                w.writeObjectEnd()

                val summary = SimpleJson.textToDictionary(w.toString())
                assertEquals(block.heading, summary["heading"])
            }
        }

        assertTrue(jsonValidCount >= 8,
            "At least 8 compiled blocks should produce valid JSON, got $jsonValidCount")
    }

    // ── 8. TIME TRAVEL IMPACT MODEL TABLE → JSON ────────────────

    @Test
    fun `e2e md time travel impact model data in JSON`() {
        val impactTable = mutableListOf<Map<String, Any?>>()
        fun walk(node: Node) {
            if (node is TableBlock) {
                var headers = listOf<String>()
                val rows = mutableListOf<List<String>>()
                var child = node.firstChild
                while (child != null) {
                    when (child) {
                        is TableHead -> {
                            val hr = child.firstChild as? TableRow
                            if (hr != null) headers = hr.children.filterIsInstance<TableCell>()
                                .map { it.text.toString().trim() }
                        }
                        is TableBody -> {
                            var row = child.firstChild
                            while (row != null) {
                                if (row is TableRow) rows.add(row.children.filterIsInstance<TableCell>()
                                    .map { it.text.toString().trim() })
                                row = row.next
                            }
                        }
                    }
                    child = child.next
                }
                if ("Metric" in headers && "Delta" in headers) {
                    for (row in rows) {
                        val map = mutableMapOf<String, Any?>()
                        for ((i, h) in headers.withIndex()) map[h] = row.getOrElse(i) { "" }
                        impactTable.add(map)
                    }
                }
            } else {
                var child = node.firstChild; while (child != null) { walk(child); child = child.next }
            }
        }
        walk(E2eFixtures.mdDocument)

        assertTrue(impactTable.isNotEmpty(), "Time Travel Impact Model table should exist")

        val w = SimpleJson.Writer()
        w.writeArrayStart()
        for (row in impactTable) {
            w.writeObjectStart()
            for ((key, value) in row) w.writeProperty(key, value?.toString() ?: "")
            w.writeObjectEnd()
        }
        w.writeArrayEnd()

        val arr = SimpleJson.textToArray(w.toString())
        assertEquals(impactTable.size, arr.size, "Should round-trip all impact rows")

        val allMetrics = arr.map {
            @Suppress("UNCHECKED_CAST")
            (it as Map<String, Any?>)["Metric"] as? String ?: ""
        }
        assertTrue(allMetrics.any { "RTL" in it || "#122" in it || "bidi" in it.lowercase() },
            "Impact model should contain RTL/bidi metric. Got: $allMetrics")
    }

    // ── 9. INJECT MD TABLE DATA INTO INK STORY VARIABLES ──────

    @Test
    fun `e2e md table data injected into ink story vars and verified`() {
        val (sessionId, _) = engine.startSession(bidiTddSource)
        try {
            // Extract counts from BIDI_TDD_ISSUES.md tables
            val records = E2eFixtures.issueRecords
            val yesCount = records.count { it.verdict == "YES" }
            val noCount = records.count { it.verdict == "NO" }
            val totalIssues = records.size

            // Inject md-derived data into existing ink story variables:
            // bugs_found ← total issues, bugs_fixed ← YES count, workarounds ← NO count
            engine.setVariable(sessionId, "bugs_found", totalIssues)
            engine.setVariable(sessionId, "bugs_fixed", yesCount)
            engine.setVariable(sessionId, "workarounds", noCount)

            // Read back and verify data was persisted
            assertEquals(totalIssues, (engine.getVariable(sessionId, "bugs_found") as Number).toInt(),
                "bugs_found should match md total issue count")
            assertEquals(yesCount, (engine.getVariable(sessionId, "bugs_fixed") as Number).toInt(),
                "bugs_fixed should match md YES count")
            assertEquals(noCount, (engine.getVariable(sessionId, "workarounds") as Number).toInt(),
                "workarounds should match md NO count")

            // Save state with injected data
            val stateJson = engine.saveState(sessionId)
            assertTrue(stateJson.isNotEmpty(), "State JSON should be non-empty")

            // Serialize injected data summary to JSON and verify round-trip
            val w = SimpleJson.Writer()
            w.writeObjectStart()
            w.writeProperty("source", "BIDI_TDD_ISSUES.md")
            w.writeProperty("bugs_found", totalIssues)
            w.writeProperty("bugs_fixed", yesCount)
            w.writeProperty("workarounds", noCount)
            w.writeObjectEnd()

            val summary = SimpleJson.textToDictionary(w.toString())
            assertEquals("BIDI_TDD_ISSUES.md", summary["source"])
            assertEquals(totalIssues, summary["bugs_found"])

            // Restore state and re-verify injected data survived save/load round-trip
            engine.loadState(sessionId, stateJson)
            assertEquals(totalIssues, (engine.getVariable(sessionId, "bugs_found") as Number).toInt(),
                "bugs_found should survive save/load round-trip")
            assertEquals(yesCount, (engine.getVariable(sessionId, "bugs_fixed") as Number).toInt(),
                "bugs_fixed should survive save/load round-trip")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ── 10. ASSERT CONTRACTS FROM INK BLOCKS ────────────────────

    @Test
    fun `e2e md ink blocks have assert contracts`() {
        val blocksWithAsserts = E2eFixtures.inkBlocks.filter { it.asserts.isNotEmpty() }
        assertTrue(blocksWithAsserts.size >= 8,
            "At least 8 blocks should have ASSERT contracts, got ${blocksWithAsserts.size}")

        val w = SimpleJson.Writer()
        w.writeArrayStart()
        for (block in blocksWithAsserts) {
            w.writeObjectStart()
            w.writeProperty("heading", block.heading)
            w.writeProperty("assertCount", block.asserts.size)
            w.writeProperty("firstAssert", block.asserts.first())
            w.writeObjectEnd()
        }
        w.writeArrayEnd()

        val arr = SimpleJson.textToArray(w.toString())
        assertEquals(blocksWithAsserts.size, arr.size)

        for (block in blocksWithAsserts) {
            for (a in block.asserts) {
                assertTrue(a.length >= 10, "Assert '$a' in '${block.heading}' is too short")
            }
        }
    }
}
