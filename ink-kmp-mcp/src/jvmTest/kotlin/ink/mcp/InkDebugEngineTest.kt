package ink.mcp

import ink.mcp.KtTestFixtures.engine
import ink.mcp.KtTestFixtures.bidiTddSource
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.*

/**
 * Tests for InkDebugEngine — debugging capabilities layered on InkEngine.
 *
 * Covers: session lifecycle (start/end/isDebugging), breakpoints (add/list/remove),
 * variable watches (add/remove/get), step execution, continueDebug, inspect, trace,
 * and full debug lifecycle integration.
 *
 * Each test starts a fresh InkEngine story session, then layers debug on top.
 * Uses [KtTestFixtures] for the shared engine and bidi_and_tdd.ink fixture.
 */
@Tag("graaljs")
@Timeout(120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class InkDebugEngineTest {

    private val debugEngine = InkDebugEngine(engine)

    /** Start a story session and return its ID. Caller must endSession in finally. */
    private fun startTestSession(): String {
        val (sessionId, _) = engine.startSession(bidiTddSource)
        return sessionId
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. startDebug / isDebugging
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `startDebug initializes debug session`() {
        val sessionId = startTestSession()
        try {
            val result = debugEngine.startDebug(sessionId)
            assertNotNull(result)
            assertEquals(sessionId, result["session_id"])
            assertEquals(true, result["debugging"])
            assertTrue(debugEngine.isDebugging(sessionId))
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    @Test
    fun `startDebug throws for non-existent session`() {
        assertFailsWith<IllegalStateException> {
            debugEngine.startDebug("non-existent-session")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. addBreakpoint / listBreakpoints
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `addBreakpoint and listBreakpoints`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)

            val bp1 = debugEngine.addBreakpoint(sessionId, "pattern", "Hello")
            assertNotNull(bp1.id)
            assertEquals("pattern", bp1.type)
            assertEquals("Hello", bp1.target)
            assertTrue(bp1.enabled)

            val bp2 = debugEngine.addBreakpoint(sessionId, "knot", "start")
            assertNotNull(bp2.id)
            assertNotEquals(bp1.id, bp2.id)

            val breakpoints = debugEngine.listBreakpoints(sessionId)
            assertEquals(2, breakpoints.size)
            assertEquals("pattern", breakpoints[0].type)
            assertEquals("knot", breakpoints[1].type)
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. removeBreakpoint
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `removeBreakpoint removes existing breakpoint`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)

            val bp = debugEngine.addBreakpoint(sessionId, "pattern", "test")
            assertEquals(1, debugEngine.listBreakpoints(sessionId).size)

            val removed = debugEngine.removeBreakpoint(sessionId, bp.id)
            assertTrue(removed)
            assertEquals(0, debugEngine.listBreakpoints(sessionId).size)

            // Removing again returns false
            val removedAgain = debugEngine.removeBreakpoint(sessionId, bp.id)
            assertFalse(removedAgain)
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. addWatch / removeWatch / getWatches
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `addWatch and getWatches track variable values`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)

            val watch = debugEngine.addWatch(sessionId, "health")
            assertEquals("health", watch.name)
            // health is initialized to 100 in bidi_and_tdd.ink
            assertEquals(100, (watch.lastValue as Number).toInt())

            val watches = debugEngine.getWatches(sessionId)
            assertTrue(watches.containsKey("health"))
            assertEquals(100, (watches["health"] as Number).toInt())
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    @Test
    fun `removeWatch removes watched variable`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)

            debugEngine.addWatch(sessionId, "health")
            assertTrue(debugEngine.getWatches(sessionId).containsKey("health"))

            val removed = debugEngine.removeWatch(sessionId, "health")
            assertTrue(removed)
            assertFalse(debugEngine.getWatches(sessionId).containsKey("health"))

            // Removing again returns false
            val removedAgain = debugEngine.removeWatch(sessionId, "health")
            assertFalse(removedAgain)
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. step — execute one step
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `step advances story by one output`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)

            // startSession already consumed text until the first choice point.
            // Choose first option to advance past the main menu.
            engine.choose(sessionId, 0)

            val result = debugEngine.step(sessionId)
            assertNotNull(result)
            assertTrue(result.stepNumber >= 1)
            assertNotNull(result.text)
            // The result should have either more content, choices, or end
            assertTrue(result.text.isNotEmpty() || result.choices.isNotEmpty() || !result.canContinue,
                "Step should produce text, choices, or indicate story end")
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. continueDebug — run until end or breakpoint
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `continueDebug runs until choices or end`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)

            // Choose first option to move past main menu
            engine.choose(sessionId, 0)

            val result = debugEngine.continueDebug(sessionId, maxSteps = 50)
            assertNotNull(result)
            assertTrue(result.stepNumber >= 1)
            // Should stop at choices or end of story
            assertTrue(result.choices.isNotEmpty() || !result.canContinue,
                "continueDebug should stop at choices or story end")
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. inspect — inspect current debug state
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `inspect returns debug session state`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)
            debugEngine.addBreakpoint(sessionId, "pattern", "test")
            debugEngine.addWatch(sessionId, "health")

            val state = debugEngine.inspect(sessionId)
            assertNotNull(state)
            assertEquals(sessionId, state["session_id"])
            assertEquals(0, state["total_steps"])
            assertEquals(false, state["is_paused"])
            assertEquals(1, state["breakpoints"])
            assertTrue(state["watches"] is Map<*, *>)
            assertEquals(0, state["visit_log_size"])
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. getTrace — execution trace
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getTrace returns visit log after stepping`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)

            // Choose first option to move past main menu
            engine.choose(sessionId, 0)

            // Take a few steps
            debugEngine.step(sessionId)
            debugEngine.step(sessionId)

            val trace = debugEngine.getTrace(sessionId)
            assertTrue(trace.isNotEmpty(), "Trace should have entries after stepping")
            assertTrue(trace.size >= 2, "Trace should have at least 2 entries, got ${trace.size}")

            // Each trace entry should have required fields
            val entry = trace.first()
            assertTrue(entry.containsKey("step"))
            assertTrue(entry.containsKey("text"))
            assertTrue(entry.containsKey("vars_changed"))
            assertTrue(entry.containsKey("timestamp"))
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    @Test
    fun `getTrace respects lastN parameter`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)

            engine.choose(sessionId, 0)
            debugEngine.step(sessionId)
            debugEngine.step(sessionId)
            debugEngine.step(sessionId)

            val fullTrace = debugEngine.getTrace(sessionId, lastN = 50)
            val limitedTrace = debugEngine.getTrace(sessionId, lastN = 1)

            assertTrue(fullTrace.size >= limitedTrace.size)
            assertEquals(1, limitedTrace.size)
        } finally {
            debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. endDebug / isDebugging
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `endDebug ends debug session but keeps story alive`() {
        val sessionId = startTestSession()
        try {
            debugEngine.startDebug(sessionId)
            assertTrue(debugEngine.isDebugging(sessionId))

            debugEngine.endDebug(sessionId)
            assertFalse(debugEngine.isDebugging(sessionId))

            // Story session should still be active
            assertTrue(engine.hasSession(sessionId))
        } finally {
            if (debugEngine.isDebugging(sessionId)) debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }

    @Test
    fun `isDebugging returns false for unknown session`() {
        assertFalse(debugEngine.isDebugging("unknown-session"))
    }

    // ═══════════════════════════════════════════════════════════════
    // 10. Full lifecycle integration
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `full lifecycle -- start, breakpoint, watch, step, inspect, trace, end`() {
        val (sessionId, startResult) = engine.startSession(bidiTddSource)
        try {
            // Start debug
            val debugResult = debugEngine.startDebug(sessionId)
            assertEquals(true, debugResult["debugging"])
            assertTrue(debugEngine.isDebugging(sessionId))

            // Add breakpoint and watches
            val bp = debugEngine.addBreakpoint(sessionId, "pattern", "שלום|Hello")
            assertNotNull(bp.id)
            debugEngine.addWatch(sessionId, "health")
            debugEngine.addWatch(sessionId, "gold")

            // Verify initial inspect
            val preState = debugEngine.inspect(sessionId)
            assertEquals(1, preState["breakpoints"])
            val preWatches = preState["watches"] as Map<*, *>
            assertTrue(preWatches.containsKey("health"))
            assertTrue(preWatches.containsKey("gold"))

            // startSession already continued to the first choice point.
            // Choose the first available option to advance the story.
            assertTrue(startResult.choices.isNotEmpty(), "Story should present choices at start")
            engine.choose(sessionId, 0)

            // Step through
            val stepResult = debugEngine.step(sessionId)
            assertNotNull(stepResult)
            assertTrue(stepResult.stepNumber >= 1)

            // Inspect after stepping
            val postState = debugEngine.inspect(sessionId)
            val totalSteps = postState["total_steps"] as Int
            assertTrue(totalSteps >= 1)
            assertTrue((postState["visit_log_size"] as Int) >= 1)

            // Check trace
            val trace = debugEngine.getTrace(sessionId)
            assertTrue(trace.isNotEmpty())

            // End debug — story remains active
            debugEngine.endDebug(sessionId)
            assertFalse(debugEngine.isDebugging(sessionId))
            assertTrue(engine.hasSession(sessionId))
        } finally {
            if (debugEngine.isDebugging(sessionId)) debugEngine.endDebug(sessionId)
            engine.endSession(sessionId)
        }
    }
}
