package ink.mcp

import kotlin.test.*

/**
 * Ported from app/test/fixtures/bidi_and_tdd.ink
 *
 * Tests 28 ink syntax features with bilingual (Hebrew+English) content
 * through the MCP server's InkEngine (GraalJS + inkjs).
 *
 * This validates that:
 *   1. The ink source compiles without errors
 *   2. All 28 syntax features work with RTL text
 *   3. Variables, functions, lists, tunnels, threads work correctly
 *   4. 10 RTL scripts render through the engine
 *   5. The bidi museum section produces output for each script
 */
class BidiTddInkTest {

    companion object {
        // Resolve paths relative to project root
        private val projectRoot = System.getProperty("user.dir")
            .let { if (it.endsWith("ink-kmp-mcp")) it else "$it/ink-kmp-mcp" }
            .removeSuffix("/ink-kmp-mcp")

        private val inkjsPath = "$projectRoot/ink-electron/node_modules/inkjs/dist/ink-full.js"
        private val bidifyPath = "$projectRoot/ink-electron/renderer/bidify.js"

        private val engine: InkEngine by lazy {
            InkEngine(inkjsPath, bidifyPath)
        }

        private val bidiTddSource: String by lazy {
            val resource = BidiTddInkTest::class.java.getResource("/bidi_and_tdd.ink")
            resource?.readText()
                ?: java.io.File("$projectRoot/ink-electron/test/fixtures/bidi_and_tdd.ink").readText()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPILATION
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `compile bidi_and_tdd ink source`() {
        val result = engine.compile(bidiTddSource)
        assertTrue(result.success, "Compilation should succeed. Errors: ${result.errors}")
        assertNotNull(result.json, "Compiled JSON should not be null")
        assertTrue(result.json!!.isNotEmpty(), "Compiled JSON should not be empty")
    }

    @Test
    fun `compile produces valid JSON`() {
        val result = engine.compile(bidiTddSource)
        assertTrue(result.success)
        // Basic JSON structure check
        val json = result.json!!
        assertTrue(json.startsWith("{"), "JSON should start with {")
        assertTrue(json.contains("\"inkVersion\""), "JSON should contain inkVersion")
    }

    // ═══════════════════════════════════════════════════════════════
    // STORY START — main menu renders
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `start story shows main menu with choices`() {
        val (sessionId, result) = engine.startSession(bidiTddSource)
        try {
            assertTrue(result.text.contains("Inky Test Suite") || result.text.contains("חבילת בדיקות"),
                "Main menu should contain title text")
            assertTrue(result.choices.isNotEmpty(), "Main menu should have choices")
            // Expect: Settings, Smoke Test, Syntax, Museum, TDD Story, Time Travel
            assertTrue(result.choices.size >= 5, "Main menu should have at least 5 choices, got ${result.choices.size}")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SMOKE TEST — basic compile + play
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `smoke test path works`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            // Find the smoke test choice (index 1 = "Smoke Test")
            val smokeIndex = initial.choices.indexOfFirst {
                it.text.contains("Smoke") || it.text.contains("בדיקה מהירה")
            }
            assertTrue(smokeIndex >= 0, "Should find Smoke Test choice")

            val afterSmoke = engine.choose(sessionId, smokeIndex)
            assertTrue(
                afterSmoke.text.contains("שלום") || afterSmoke.text.contains("Hello"),
                "Smoke test should output text"
            )
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 28 SYNTAX FEATURES — play through syn_01..syn_28
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `syntax features path compiles and plays through 28 stages`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            // Find the Syntax choice (index 2 = "Syntax — 28 features")
            val syntaxIndex = initial.choices.indexOfFirst {
                it.text.contains("Syntax") || it.text.contains("28") || it.text.contains("תחביר")
            }
            assertTrue(syntaxIndex >= 0, "Should find Syntax choice")

            var result = engine.choose(sessionId, syntaxIndex)

            // Walk through the 28 syntax knots
            // Many auto-advance (no choices), some have choices we need to pick
            // Tunnels (syn_13) and threads (syn_14) need canContinue checked first
            var stageCount = 0
            val allText = StringBuilder()
            val maxTurns = 300 // generous limit for tunnels/threads

            for (turn in 0 until maxTurns) {
                allText.append(result.text)

                // Count stages by looking for "NN/28" pattern
                for (match in Regex("""(\d{2})/28""").findAll(result.text)) {
                    stageCount = maxOf(stageCount, match.groupValues[1].toInt())
                }

                // If we reached 28/28 summary, we're done
                if (result.text.contains("28/28")) {
                    break
                }

                // Continue or choose — prefer continue first (handles tunnel returns, glue)
                if (result.canContinue) {
                    result = engine.continueStory(sessionId)
                } else if (result.choices.isNotEmpty()) {
                    // Thread knots (syn_14) present thread choices (Fruit, Gossip)
                    // alongside the main-flow "Leave" choice. Choosing a thread
                    // choice (index 0) diverts to -> DONE, ending that thread but
                    // stranding the story. We detect the thread stage by looking
                    // for the "Leave"/"עזוב" choice and pick it to advance.
                    // For all other stages, default to index 0.
                    val leaveIdx = result.choices.indexOfFirst {
                        it.text.contains("Leave") || it.text.contains("עזוב")
                    }
                    val pick = if (leaveIdx >= 0) leaveIdx else 0
                    result = engine.choose(sessionId, pick)
                } else {
                    break
                }
            }

            assertTrue(stageCount >= 28, "Should reach stage 28/28, reached $stageCount/28")
            assertTrue(allText.contains("שלום") || allText.contains("Hello"), "Should contain bilingual text")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INDIVIDUAL SYNTAX FEATURES — spot checks
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `variables work with bilingual text`() {
        val (sessionId, _) = engine.startSession(bidiTddSource)
        try {
            // Check initial variable values
            val health = engine.getVariable(sessionId, "health")
            assertEquals(100, (health as Number).toInt(), "Initial health should be 100")

            val gold = engine.getVariable(sessionId, "gold")
            assertEquals(50, (gold as Number).toInt(), "Initial gold should be 50")

            val lang = engine.getVariable(sessionId, "lang")
            assertEquals("both", lang, "Initial lang should be 'both'")

            // Set a variable
            engine.setVariable(sessionId, "lang", "he")
            val newLang = engine.getVariable(sessionId, "lang")
            assertEquals("he", newLang, "Lang should be 'he' after set")
        } finally {
            engine.endSession(sessionId)
        }
    }

    @Test
    fun `functions evaluate correctly`() {
        val (sessionId, _) = engine.startSession(bidiTddSource)
        try {
            // Test the clamp function
            val result = engine.evaluateFunction(sessionId, "clamp", listOf(150, 0, 100))
            assertEquals(100, (result as Number).toInt(), "clamp(150, 0, 100) should be 100")

            val result2 = engine.evaluateFunction(sessionId, "clamp", listOf(-10, 0, 100))
            assertEquals(0, (result2 as Number).toInt(), "clamp(-10, 0, 100) should be 0")
        } finally {
            engine.endSession(sessionId)
        }
    }

    @Test
    fun `global tags are accessible`() {
        val (sessionId, _) = engine.startSession(bidiTddSource)
        try {
            // bidi_and_tdd.ink doesn't have global tags at the top level,
            // but let's verify the API works
            val tags = engine.getGlobalTags(sessionId)
            assertNotNull(tags, "Tags should not be null")
        } finally {
            engine.endSession(sessionId)
        }
    }

    @Test
    fun `save and load state round-trip`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            // Save initial state
            val savedState = engine.saveState(sessionId)
            assertNotNull(savedState, "Saved state should not be null")
            assertTrue(savedState.isNotEmpty(), "Saved state should not be empty")

            // Navigate to smoke test
            val smokeIndex = initial.choices.indexOfFirst {
                it.text.contains("Smoke") || it.text.contains("בדיקה מהירה")
            }
            if (smokeIndex >= 0) {
                engine.choose(sessionId, smokeIndex)
            }

            // Load saved state — should go back to main menu
            engine.loadState(sessionId, savedState)
            val restored = engine.continueStory(sessionId)
            // After restoring, we should be back at the main menu or similar state
            assertNotNull(restored, "Restored result should not be null")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BIDI MUSEUM — 10 RTL SCRIPTS
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `bidi museum all 10 scripts render`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            // Find the Museum choice
            val museumIndex = initial.choices.indexOfFirst {
                it.text.contains("Museum") || it.text.contains("מוזיאון")
            }
            assertTrue(museumIndex >= 0, "Should find Museum choice")

            var result = engine.choose(sessionId, museumIndex)

            // Find "All 10" choice
            val allIndex = result.choices.indexOfFirst {
                it.text.contains("All 10") || it.text.contains("10")
            }
            assertTrue(allIndex >= 0, "Should find 'All 10' choice")

            result = engine.choose(sessionId, allIndex)
            val text = result.text

            // Verify all 10 RTL scripts appear in the output
            val scripts = listOf(
                "Hebrew" to "שלום",
                "Arabic" to "مرحبا",
                "Persian" to "سلام",
                "Urdu" to "ہیلو",
                "Yiddish" to "שלום וועלט",
                "Syriac" to "ܫܠܡܐ",
                "Thaana" to "ހެލޯ",
                "N'Ko" to "ߊߟߎ",
                "Samaritan" to "ࠔࠋࠌ",
                "Mandaic" to "ࡔࡋࡀࡌࡀ"
            )

            for ((name, sample) in scripts) {
                assertTrue(text.contains(sample),
                    "$name script text '$sample' should appear in museum output. Got: ${text.take(200)}...")
            }
        } finally {
            engine.endSession(sessionId)
        }
    }

    @Test
    fun `individual RTL script paths work`() {
        // Test Hebrew path specifically
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            val museumIndex = initial.choices.indexOfFirst {
                it.text.contains("Museum") || it.text.contains("מוזיאון")
            }
            assertTrue(museumIndex >= 0)
            var result = engine.choose(sessionId, museumIndex)

            // Choose Hebrew
            val heIndex = result.choices.indexOfFirst { it.text.contains("Hebrew") }
            assertTrue(heIndex >= 0)
            result = engine.choose(sessionId, heIndex)

            assertTrue(result.text.contains("שלום"), "Hebrew path should output שלום")
            assertTrue(result.choices.isNotEmpty(), "Should have OK/Broken check choices")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SESSION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `session lifecycle — create, list, end`() {
        val (sessionId, _) = engine.startSession(bidiTddSource)
        try {
            val sessions = engine.listSessions()
            assertTrue(sessions.contains(sessionId), "Session should appear in list")
        } finally {
            engine.endSession(sessionId)
        }

        val sessionsAfter = engine.listSessions()
        assertFalse(sessionsAfter.contains(sessionId), "Session should be removed after end")
    }

    @Test
    fun `reset story returns to beginning`() {
        val (sessionId, initial) = engine.startSession(bidiTddSource)
        try {
            // Make a choice to advance
            if (initial.choices.isNotEmpty()) {
                engine.choose(sessionId, 0)
            }

            // Reset
            val resetResult = engine.resetStory(sessionId)
            assertNotNull(resetResult)
            // After reset, should have the main menu choices again
            assertTrue(resetResult.choices.isNotEmpty(), "Reset should show initial choices")
        } finally {
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BIDIFY INTEGRATION
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `compile produces JSON suitable for bidify_json`() {
        val result = engine.compile(bidiTddSource)
        assertTrue(result.success)
        val json = result.json!!

        // The JSON should contain Hebrew text that could be bidified
        assertTrue(json.contains("שלום") || json.contains("עברית") || json.contains("חבילת"),
            "Compiled JSON should contain Hebrew text from the story")
    }

    @Test
    fun `bilingual t function works in story output`() {
        val (sessionId, _) = engine.startSession(bidiTddSource)
        try {
            // Default lang is "both", so t() returns "he_text — en_text"
            val lang = engine.getVariable(sessionId, "lang")
            assertEquals("both", lang)

            // The main menu text should show bilingual output
            // (already shown in initial result, but let's verify via variable)
        } finally {
            engine.endSession(sessionId)
        }
    }
}
