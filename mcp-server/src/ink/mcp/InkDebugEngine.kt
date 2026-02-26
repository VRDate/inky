package ink.mcp

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Ink script debugging engine.
 *
 * Provides debugging capabilities for ink stories:
 *   - Breakpoints on knots, stitches, and line patterns
 *   - Step-by-step execution (step-over continues to next output)
 *   - Variable watch list with change detection
 *   - Story state inspection (visit counts, turn index, path)
 *   - Flow profiling (which knots were visited, choice stats)
 *
 * Works on top of InkEngine story sessions — each debug session wraps
 * an existing story session and adds instrumentation.
 */
class InkDebugEngine(private val inkEngine: InkEngine) {

    private val log = LoggerFactory.getLogger(InkDebugEngine::class.java)
    private val debugSessions = ConcurrentHashMap<String, DebugSession>()

    data class Breakpoint(
        val id: String,
        val type: String,      // "knot", "stitch", "pattern", "variable_change"
        val target: String,    // knot name, stitch path, regex pattern, or variable name
        val enabled: Boolean = true
    )

    data class WatchVariable(
        val name: String,
        var lastValue: Any? = null,
        var changeCount: Int = 0
    )

    data class DebugSession(
        val sessionId: String,
        val breakpoints: MutableList<Breakpoint> = mutableListOf(),
        val watches: MutableMap<String, WatchVariable> = mutableMapOf(),
        val visitLog: MutableList<VisitEntry> = mutableListOf(),
        var stepping: Boolean = false,
        var stepCount: Int = 0,
        var totalSteps: Int = 0,
        var isPaused: Boolean = false,
        var lastOutput: String = ""
    )

    data class VisitEntry(
        val step: Int,
        val text: String,
        val choicesMade: Int = 0,
        val variablesChanged: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    )

    data class StepResult(
        val text: String,
        val canContinue: Boolean,
        val choices: List<InkEngine.ChoiceInfo>,
        val tags: List<String>,
        val hitBreakpoint: Breakpoint? = null,
        val watchChanges: Map<String, Pair<Any?, Any?>> = emptyMap(),
        val stepNumber: Int,
        val isPaused: Boolean
    )

    /** Start debugging an existing story session */
    fun startDebug(sessionId: String): Map<String, Any> {
        if (!inkEngine.hasSession(sessionId)) {
            throw IllegalStateException("No story session: $sessionId")
        }

        val debug = DebugSession(sessionId = sessionId)
        debugSessions[sessionId] = debug
        log.info("Debug session started for: {}", sessionId)

        return mapOf(
            "session_id" to sessionId,
            "debugging" to true,
            "message" to "Debug session started. Use add_breakpoint, add_watch, step, continue_debug."
        )
    }

    /** Add a breakpoint */
    fun addBreakpoint(sessionId: String, type: String, target: String): Breakpoint {
        val debug = requireDebug(sessionId)
        val bp = Breakpoint(
            id = "bp_${debug.breakpoints.size + 1}",
            type = type,
            target = target
        )
        debug.breakpoints.add(bp)
        log.info("Breakpoint added: {} {} -> {}", bp.id, type, target)
        return bp
    }

    /** Remove a breakpoint */
    fun removeBreakpoint(sessionId: String, breakpointId: String): Boolean {
        val debug = requireDebug(sessionId)
        return debug.breakpoints.removeIf { it.id == breakpointId }
    }

    /** List breakpoints */
    fun listBreakpoints(sessionId: String): List<Breakpoint> {
        return requireDebug(sessionId).breakpoints.toList()
    }

    /** Add a variable to the watch list */
    fun addWatch(sessionId: String, varName: String): WatchVariable {
        val debug = requireDebug(sessionId)
        val currentValue = try {
            inkEngine.getVariable(sessionId, varName)
        } catch (_: Exception) { null }

        val watch = WatchVariable(name = varName, lastValue = currentValue)
        debug.watches[varName] = watch
        return watch
    }

    /** Remove a watch */
    fun removeWatch(sessionId: String, varName: String): Boolean {
        val debug = requireDebug(sessionId)
        return debug.watches.remove(varName) != null
    }

    /** Get all watch values */
    fun getWatches(sessionId: String): Map<String, Any?> {
        val debug = requireDebug(sessionId)
        val result = mutableMapOf<String, Any?>()
        for ((name, watch) in debug.watches) {
            val current = try {
                inkEngine.getVariable(sessionId, name)
            } catch (_: Exception) { null }
            watch.lastValue = current
            result[name] = current
        }
        return result
    }

    /** Step: continue story until next output, checking breakpoints and watches */
    fun step(sessionId: String): StepResult {
        val debug = requireDebug(sessionId)

        // Snapshot watch values before step
        val watchBefore = debug.watches.mapValues { (name, _) ->
            try { inkEngine.getVariable(sessionId, name) } catch (_: Exception) { null }
        }

        // Continue story
        val result = inkEngine.continueStory(sessionId)
        debug.totalSteps++
        debug.lastOutput = result.text

        // Check watch changes
        val watchChanges = mutableMapOf<String, Pair<Any?, Any?>>()
        for ((name, watch) in debug.watches) {
            val newValue = try {
                inkEngine.getVariable(sessionId, name)
            } catch (_: Exception) { null }
            val oldValue = watchBefore[name]
            if (newValue != oldValue) {
                watchChanges[name] = oldValue to newValue
                watch.lastValue = newValue
                watch.changeCount++
            }
        }

        // Check breakpoints
        val hitBreakpoint = checkBreakpoints(debug, result.text, watchChanges)
        if (hitBreakpoint != null) {
            debug.isPaused = true
        }

        // Log visit
        debug.visitLog.add(VisitEntry(
            step = debug.totalSteps,
            text = result.text.take(100),
            variablesChanged = watchChanges.keys.toList()
        ))

        return StepResult(
            text = result.text,
            canContinue = result.canContinue,
            choices = result.choices,
            tags = result.tags,
            hitBreakpoint = hitBreakpoint,
            watchChanges = watchChanges,
            stepNumber = debug.totalSteps,
            isPaused = debug.isPaused
        )
    }

    /** Continue until a breakpoint is hit or story ends */
    fun continueDebug(sessionId: String, maxSteps: Int = 100): StepResult {
        val debug = requireDebug(sessionId)
        debug.isPaused = false

        var lastResult: StepResult? = null
        var stepsRemaining = maxSteps

        while (stepsRemaining > 0) {
            lastResult = step(sessionId)
            stepsRemaining--

            if (lastResult.hitBreakpoint != null || !lastResult.canContinue || lastResult.choices.isNotEmpty()) {
                break
            }
        }

        return lastResult ?: StepResult(
            text = "",
            canContinue = false,
            choices = emptyList(),
            tags = emptyList(),
            stepNumber = debug.totalSteps,
            isPaused = false
        )
    }

    /** Inspect the current story state */
    fun inspect(sessionId: String): Map<String, Any?> {
        val debug = requireDebug(sessionId)

        return mapOf(
            "session_id" to sessionId,
            "total_steps" to debug.totalSteps,
            "is_paused" to debug.isPaused,
            "breakpoints" to debug.breakpoints.size,
            "watches" to getWatches(sessionId),
            "last_output" to debug.lastOutput.take(200),
            "visit_log_size" to debug.visitLog.size,
            "recent_visits" to debug.visitLog.takeLast(10).map { v ->
                mapOf(
                    "step" to v.step,
                    "text" to v.text,
                    "vars_changed" to v.variablesChanged
                )
            }
        )
    }

    /** Get the full visit log / execution trace */
    fun getTrace(sessionId: String, lastN: Int = 50): List<Map<String, Any?>> {
        val debug = requireDebug(sessionId)
        return debug.visitLog.takeLast(lastN).map { v ->
            mapOf(
                "step" to v.step,
                "text" to v.text,
                "vars_changed" to v.variablesChanged,
                "timestamp" to v.timestamp
            )
        }
    }

    /** End debug session (keeps story session alive) */
    fun endDebug(sessionId: String) {
        debugSessions.remove(sessionId)
        log.info("Debug session ended: {}", sessionId)
    }

    /** Check if debug session exists */
    fun isDebugging(sessionId: String): Boolean = debugSessions.containsKey(sessionId)

    // -- Private helpers --

    private fun requireDebug(sessionId: String): DebugSession {
        return debugSessions[sessionId]
            ?: throw IllegalStateException("No debug session for: $sessionId. Call start_debug first.")
    }

    private fun checkBreakpoints(
        debug: DebugSession,
        text: String,
        watchChanges: Map<String, Pair<Any?, Any?>>
    ): Breakpoint? {
        for (bp in debug.breakpoints) {
            if (!bp.enabled) continue

            val hit = when (bp.type) {
                "pattern" -> {
                    try {
                        Regex(bp.target).containsMatchIn(text)
                    } catch (_: Exception) {
                        text.contains(bp.target)
                    }
                }
                "variable_change" -> {
                    watchChanges.containsKey(bp.target)
                }
                "knot" -> {
                    // Check if story text starts with a knot marker or tag
                    text.contains("=== ${bp.target}") || text.trimStart().startsWith(bp.target)
                }
                "stitch" -> {
                    text.contains("= ${bp.target}")
                }
                else -> false
            }

            if (hit) return bp
        }
        return null
    }
}
