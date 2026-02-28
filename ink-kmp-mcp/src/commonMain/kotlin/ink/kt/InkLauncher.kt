package ink.kt

/**
 * KMP ink session launcher — the single entry point for running ink stories
 * on all Kotlin Multiplatform targets.
 *
 * **Default** (`legacy=false`): Uses [ink.kt.Story] (pure Kotlin, works on JVM + JS + Native + WASM).
 * **Legacy** (`legacy=true`): Uses the platform-specific official engine via [InkPlatform]:
 *   - **JVM**: blade-ink Java (`com.bladecoder.ink.runtime.Story`) for runtime,
 *             blade-ink compiler (`com.bladecoder.ink.compiler.Compiler`) for compilation
 *   - **JS** (future): inkjs (`inkjs.Story` via Kotlin/JS interop)
 *   - **Native/WASM** (future): not available (falls back to ink.kt)
 *
 * The session-based API mirrors `InkEngine` (GraalJS wrapper in ink.mcp) so that
 * existing MCP tools and tests can switch from GraalJS to ink.kt with no API change.
 *
 * When the build migrates to `kotlin("multiplatform")` (Phase 7), the [InkPlatform]
 * companion object becomes `expect/actual` for per-target legacy engine binding.
 *
 * Roadmap Phase 12: Ink Engine Selection Framework.
 *
 * @see InkRuntime common interface for ink runtimes
 */
class InkLauncher(val legacy: Boolean = false) {

    // ── Data classes matching InkEngine contract ─────────────────────

    data class CompileResult(
        val success: Boolean,
        val json: String? = null,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    data class ContinueResult(
        val text: String,
        val canContinue: Boolean,
        val choices: List<ChoiceInfo> = emptyList(),
        val tags: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    )

    data class ChoiceInfo(
        val index: Int,
        val text: String,
        val tags: List<String> = emptyList()
    )

    // ── Session storage ─────────────────────────────────────────────

    private val sessions = HashMap<String, StorySession>()
    private var nextSessionId = 0

    private class StorySession(
        val id: String,
        val story: Story,
        val source: String
    )

    // ── Compile ─────────────────────────────────────────────────────

    /** Compile ink source to JSON. Delegates to [InkPlatform] for compiler selection. */
    fun compile(inkSource: String): CompileResult = InkPlatform.compile(inkSource, legacy)

    // ── Session lifecycle ───────────────────────────────────────────

    /** Compile ink source and start a new session, returning (sessionId, initial output). */
    fun startSession(inkSource: String, sessionId: String? = null): Pair<String, ContinueResult> {
        val compiled = compile(inkSource)
        if (!compiled.success || compiled.json == null) {
            return Pair(
                sessionId ?: newSessionId(),
                ContinueResult("", false, errors = compiled.errors)
            )
        }
        return startSessionFromJson(compiled.json, sessionId)
    }

    /** Start a session from pre-compiled JSON. */
    fun startSessionFromJson(json: String, sessionId: String? = null): Pair<String, ContinueResult> {
        val id = sessionId ?: newSessionId()
        val story = InkPlatform.createStory(json, legacy)
        sessions[id] = StorySession(id, story, json)
        val result = doContinue(story)
        return Pair(id, result)
    }

    /** Continue the story until a choice point or end. */
    fun continueStory(sessionId: String): ContinueResult {
        return doContinue(getSession(sessionId).story)
    }

    /** Make a choice and continue. */
    fun choose(sessionId: String, choiceIndex: Int): ContinueResult {
        val session = getSession(sessionId)
        session.story.chooseChoiceIndex(choiceIndex)
        return doContinue(session.story)
    }

    // ── Variables ───────────────────────────────────────────────────

    fun getVariable(sessionId: String, varName: String): Any? =
        getSession(sessionId).story.variablesState?.get(varName)

    fun setVariable(sessionId: String, varName: String, value: Any?) {
        getSession(sessionId).story.variablesState?.set(varName, value)
    }

    // ── State persistence ───────────────────────────────────────────

    fun saveState(sessionId: String): String =
        getSession(sessionId).story.state.toJson()

    fun loadState(sessionId: String, stateJson: String) {
        getSession(sessionId).story.state.loadJson(stateJson)
    }

    fun resetStory(sessionId: String): ContinueResult {
        val session = getSession(sessionId)
        session.story.resetState()
        return doContinue(session.story)
    }

    // ── Functions ───────────────────────────────────────────────────

    fun evaluateFunction(sessionId: String, funcName: String, args: List<Any?> = emptyList()): Any? =
        getSession(sessionId).story.evaluateFunction(funcName, args.toTypedArray())

    // ── Tags ────────────────────────────────────────────────────────

    fun getGlobalTags(sessionId: String): List<String> =
        getSession(sessionId).story.getGlobalTags() ?: emptyList()

    // ── Session management ──────────────────────────────────────────

    fun endSession(sessionId: String) { sessions.remove(sessionId) }
    fun listSessions(): List<String> = sessions.keys.toList()
    fun hasSession(sessionId: String): Boolean = sessions.containsKey(sessionId)

    // ── Internal ────────────────────────────────────────────────────

    private fun getSession(sessionId: String): StorySession =
        sessions[sessionId] ?: throw IllegalArgumentException("Unknown session: $sessionId")

    private fun newSessionId(): String = "session_${nextSessionId++}"

    private fun doContinue(story: Story): ContinueResult {
        val sb = StringBuilder()
        while (story.canContinue()) {
            sb.append(story.continueStory())
        }
        return ContinueResult(
            text = sb.toString(),
            canContinue = story.canContinue(),
            choices = story.currentChoices.map { ChoiceInfo(it.index, it.text, it.tags ?: emptyList()) },
            tags = story.currentTags ?: emptyList()
        )
    }

    companion object {
        /** Create an InkLauncher using ink.kt (default). */
        fun create(): InkLauncher = InkLauncher(legacy = false)
        /** Create an InkLauncher using the legacy platform engine. */
        fun createLegacy(): InkLauncher = InkLauncher(legacy = true)
    }
}

/**
 * Platform-specific story creation and compilation.
 *
 * - **JVM actual**: blade-ink Java for `legacy=true`, ink.kt for `legacy=false`
 * - **JS actual**: ink.kt only (no legacy engine available)
 * - **Native/WASM actual**: ink.kt only (no legacy engine available)
 */
expect object InkPlatform {
    fun createStory(json: String, legacy: Boolean = false): Story
    fun compile(source: String, legacy: Boolean = false): InkLauncher.CompileResult
}
