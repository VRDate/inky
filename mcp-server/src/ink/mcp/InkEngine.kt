package ink.mcp

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * GraalJS-powered ink engine. Loads inkjs (compiler + runtime) and bidify.js
 * to provide headless ink compilation, story playback, and bidi text support.
 *
 * Each story session gets its own GraalJS context for isolation.
 */
class InkEngine(private val inkjsPath: String, private val bidifyPath: String? = null) {

    private val log = LoggerFactory.getLogger(InkEngine::class.java)
    private val sessions = ConcurrentHashMap<String, StorySession>()

    // Pre-read JS sources once
    private val inkjsSource: String = File(inkjsPath).readText()
    private val bidifySource: String? = bidifyPath?.let { File(it).readText() }

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
        val tags: List<String> = emptyList()
    )

    data class ChoiceInfo(
        val index: Int,
        val text: String,
        val tags: List<String> = emptyList()
    )

    class StorySession(
        val id: String,
        val context: Context,
        val source: String,
        var stateJson: String? = null
    )

    init {
        log.info("InkEngine initialized: inkjs={}, bidify={}", inkjsPath, bidifyPath ?: "(none)")
        // Verify inkjs loads
        val testCtx = createContext()
        testCtx.close()
        log.info("GraalJS + inkjs verified OK")
    }

    /** Create a fresh GraalJS context with inkjs loaded */
    private fun createContext(): Context {
        val ctx = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .option("js.ecmascript-version", "2022")
            .build()

        // Load inkjs via CommonJS shim
        ctx.eval("js", "var exports = {}; var module = { exports: exports };")
        ctx.eval("js", Source.newBuilder("js", inkjsSource, "ink-full.js").build())
        ctx.eval("js", """
            var inkjs = module.exports;
            if (!inkjs.Compiler) throw new Error('inkjs.Compiler not found');
            if (!inkjs.Story) throw new Error('inkjs.Story not found');
        """.trimIndent())

        // Load bidify if available
        if (bidifySource != null) {
            ctx.eval("js", "var exports = {}; var module = { exports: exports };")
            ctx.eval("js", Source.newBuilder("js", bidifySource, "bidify.js").build())
            ctx.eval("js", "var bidifyModule = module.exports;")
        }

        return ctx
    }

    /** Compile ink source to JSON */
    fun compile(inkSource: String): CompileResult {
        val ctx = createContext()
        try {
            ctx.getBindings("js").putMember("__inkSource", inkSource)

            ctx.eval("js", """
                var __errors = [];
                var __warnings = [];
                var __json = null;
                var __success = false;
                try {
                    var compiler = new inkjs.Compiler(__inkSource);
                    var story = compiler.Compile();
                    if (story) {
                        __json = story.ToJson();
                        __success = true;
                    }
                } catch(e) {
                    __errors.push(e.toString());
                }
            """.trimIndent())

            return CompileResult(
                success = ctx.eval("js", "__success").asBoolean(),
                json = ctx.eval("js", "__json").let { if (it.isNull) null else it.asString() },
                errors = readStringArray(ctx, "__errors"),
                warnings = readStringArray(ctx, "__warnings")
            )
        } finally {
            ctx.close()
        }
    }

    /** Start a new story session from ink source, returns session ID + initial output */
    fun startSession(inkSource: String, sessionId: String? = null): Pair<String, ContinueResult> {
        val id = sessionId ?: UUID.randomUUID().toString().take(8)
        endSession(id)

        val ctx = createContext()
        ctx.getBindings("js").putMember("__inkSource", inkSource)

        ctx.eval("js", """
            var __compileErrors = [];
            var compiler = new inkjs.Compiler(__inkSource);
            var __story = compiler.Compile();
            if (!__story) throw new Error('Compilation failed');
        """.trimIndent())

        sessions[id] = StorySession(id = id, context = ctx, source = inkSource)
        return id to continueStory(id)
    }

    /** Start a session from pre-compiled JSON */
    fun startSessionFromJson(json: String, sessionId: String? = null): Pair<String, ContinueResult> {
        val id = sessionId ?: UUID.randomUUID().toString().take(8)
        endSession(id)

        val ctx = createContext()
        ctx.getBindings("js").putMember("__jsonSource", json)
        ctx.eval("js", "var __story = new inkjs.Story(__jsonSource);")

        sessions[id] = StorySession(id = id, context = ctx, source = json)
        return id to continueStory(id)
    }

    /** Continue the story, collecting all text until a choice or end */
    fun continueStory(sessionId: String): ContinueResult {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        val ctx = session.context

        ctx.eval("js", """
            var __text = '';
            var __tags = [];
            while (__story.canContinue) {
                __text += __story.Continue();
                var t = __story.currentTags;
                if (t && t.length > 0) for (var i = 0; i < t.length; i++) __tags.push(t[i]);
            }
            var __canContinue = __story.canContinue;
            var __choices = [];
            if (__story.currentChoices) {
                for (var i = 0; i < __story.currentChoices.length; i++) {
                    var c = __story.currentChoices[i];
                    __choices.push({ index: c.index, text: c.text, tags: c.tags || [] });
                }
            }
        """.trimIndent())

        return ContinueResult(
            text = ctx.eval("js", "__text").asString(),
            canContinue = ctx.eval("js", "__canContinue").asBoolean(),
            choices = readChoices(ctx),
            tags = readStringArray(ctx, "__tags")
        )
    }

    /** Make a choice in the story */
    fun choose(sessionId: String, choiceIndex: Int): ContinueResult {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        session.context.getBindings("js").putMember("__choiceIdx", choiceIndex)
        session.context.eval("js", "__story.ChooseChoiceIndex(__choiceIdx);")
        return continueStory(sessionId)
    }

    /** Get a story variable */
    fun getVariable(sessionId: String, varName: String): Any? {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        session.context.getBindings("js").putMember("__varName", varName)
        return graalToKotlin(session.context.eval("js", "__story.variablesState[__varName]"))
    }

    /** Set a story variable */
    fun setVariable(sessionId: String, varName: String, value: Any?) {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        session.context.getBindings("js").putMember("__varName", varName)
        session.context.getBindings("js").putMember("__varValue", value)
        session.context.eval("js", "__story.variablesState[__varName] = __varValue;")
    }

    /** Save story state as JSON */
    fun saveState(sessionId: String): String {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        val state = session.context.eval("js", "__story.state.ToJson()").asString()
        session.stateJson = state
        return state
    }

    /** Load story state from JSON */
    fun loadState(sessionId: String, stateJson: String) {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        session.context.getBindings("js").putMember("__stateJson", stateJson)
        session.context.eval("js", "__story.state.LoadJson(__stateJson);")
    }

    /** Reset story to beginning */
    fun resetStory(sessionId: String): ContinueResult {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        session.context.eval("js", "__story.ResetState();")
        return continueStory(sessionId)
    }

    /** Evaluate an ink function */
    fun evaluateFunction(sessionId: String, funcName: String, args: List<Any?> = emptyList()): Any? {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        val ctx = session.context
        ctx.getBindings("js").putMember("__funcName", funcName)
        args.forEachIndexed { i, arg -> ctx.getBindings("js").putMember("__arg$i", arg) }
        val argsJs = args.indices.joinToString(", ") { "__arg$it" }
        return graalToKotlin(ctx.eval("js", "__story.EvaluateFunction(__funcName, [$argsJs])"))
    }

    /** Get global tags */
    fun getGlobalTags(sessionId: String): List<String> {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        return readStringArray(session.context, "__story.globalTags || []")
    }

    /** Bidify text */
    fun bidify(text: String): String {
        if (bidifySource == null) return text
        val ctx = createContext()
        try {
            ctx.getBindings("js").putMember("__text", text)
            return ctx.eval("js", "bidifyModule.bidify(__text)").asString()
        } finally {
            ctx.close()
        }
    }

    /** Strip bidi markers */
    fun stripBidi(text: String): String {
        if (bidifySource == null) return text
        val ctx = createContext()
        try {
            ctx.getBindings("js").putMember("__text", text)
            return ctx.eval("js", "bidifyModule.stripBidi(__text)").asString()
        } finally {
            ctx.close()
        }
    }

    /** Bidify compiled JSON */
    fun bidifyJson(jsonString: String): String {
        if (bidifySource == null) return jsonString
        val ctx = createContext()
        try {
            ctx.getBindings("js").putMember("__json", jsonString)
            return ctx.eval("js", "bidifyModule.bidifyJson(__json)").asString()
        } finally {
            ctx.close()
        }
    }

    /** End a session */
    fun endSession(sessionId: String) {
        sessions.remove(sessionId)?.let { s ->
            try { s.context.close() } catch (_: Exception) {}
        }
    }

    /** List active session IDs */
    fun listSessions(): List<String> = sessions.keys().toList()

    /** Check if session exists */
    fun hasSession(sessionId: String): Boolean = sessions.containsKey(sessionId)

    // -- Helpers --

    private fun readStringArray(ctx: Context, expr: String): List<String> {
        val arr = ctx.eval("js", expr)
        if (!arr.hasArrayElements()) return emptyList()
        return (0 until arr.arraySize).map { arr.getArrayElement(it).asString() }
    }

    private fun readChoices(ctx: Context): List<ChoiceInfo> {
        val arr = ctx.eval("js", "__choices")
        if (!arr.hasArrayElements()) return emptyList()
        return (0 until arr.arraySize).map { i ->
            val c = arr.getArrayElement(i)
            val tags = c.getMember("tags").let { t ->
                if (t.hasArrayElements()) (0 until t.arraySize).map { t.getArrayElement(it).asString() }
                else emptyList()
            }
            ChoiceInfo(
                index = c.getMember("index").asInt(),
                text = c.getMember("text").asString(),
                tags = tags
            )
        }
    }

    private fun graalToKotlin(value: Value): Any? = when {
        value.isNull -> null
        value.isBoolean -> value.asBoolean()
        value.isNumber -> if (value.fitsInInt()) value.asInt() else value.asDouble()
        value.isString -> value.asString()
        else -> value.toString()
    }
}
