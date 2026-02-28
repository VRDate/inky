package ink.mcp

import dev.langchain4j.model.chat.ChatModel
import org.slf4j.LoggerFactory

/**
 * JVM adapter implementing [McpLlmOps].
 *
 * Dispatch priority for chat / generateInk / reviewInk / translateToHebrew:
 *   1. externalLlm (set by [JvmServiceOps.connect])
 *   2. camelRoutes.sendToRoute() with llmEngine fallback
 *   3. llmEngine directly
 *   4. error — no LLM backend available
 */
class JvmLlmOps(
    private val llmEngine: LlmEngine? = null,
    private var camelRoutes: CamelRoutes? = null
) : McpLlmOps {

    private val log = LoggerFactory.getLogger(JvmLlmOps::class.java)

    /** External ChatModel set when a service provider is connected. */
    @Volatile
    var externalLlm: ChatModel? = null

    /** Allow CamelRoutes to be refreshed after model load. */
    fun setCamelRoutes(routes: CamelRoutes?) {
        this.camelRoutes = routes
    }

    // ── McpLlmOps ────────────────────────────────────────────

    override fun chat(message: String): String = dispatch(
        routeName = "llm-chat",
        body = message,
        directFn = { llmEngine!!.chat(message) },
        externalFn = { it.chat(message) }
    )

    override fun generateInk(prompt: String): String = dispatch(
        routeName = "llm-generate-ink",
        body = prompt,
        directFn = { llmEngine!!.generateInk(prompt) },
        externalFn = {
            val systemPrompt = """You are an expert ink (inkle's ink) script writer.
Generate valid ink syntax. Use knots (===), stitches (=), choices (*,+),
diverts (->), variables (VAR), conditionals, and other ink features as needed.
Only output the ink code, no explanations."""
            it.chat("$systemPrompt\n\nUser request: $prompt")
        }
    )

    override fun reviewInk(source: String): String = dispatch(
        routeName = "llm-review-ink",
        body = source,
        directFn = { llmEngine!!.reviewInk(source) },
        externalFn = {
            val systemPrompt = """You are an expert ink (inkle's ink) script reviewer.
Analyze the ink code for: syntax errors, logic issues, dead ends,
missing diverts, unused knots, and RTL/bidi concerns.
Provide specific, actionable feedback."""
            it.chat("$systemPrompt\n\nInk code to review:\n$source")
        }
    )

    override fun translateToHebrew(source: String): String = dispatch(
        routeName = "llm-translate-he",
        body = source,
        directFn = { llmEngine!!.translateToHebrew(source) },
        externalFn = {
            val systemPrompt = """You are a Hebrew translator for interactive fiction.
Translate only the story text (dialogue, descriptions) to Hebrew.
Keep all ink syntax (knots, diverts, variables, choices markup) unchanged.
Preserve the ink structure exactly."""
            it.chat("$systemPrompt\n\nInk source:\n$source")
        }
    )

    override fun loadModel(modelId: String): String {
        val engine = llmEngine ?: throw IllegalStateException("No LlmEngine available")
        val model = DictaLmConfig.findModel(modelId)
            ?: throw IllegalArgumentException(
                "Unknown model: $modelId. Available: ${DictaLmConfig.MODELS.map { it.id }}"
            )
        val loaded = engine.loadModel(model)
        camelRoutes?.refreshChatModel()
        return loaded
    }

    override fun loadCustomModel(repo: String): String {
        val engine = llmEngine ?: throw IllegalStateException("No LlmEngine available")
        val loaded = engine.loadCustomModel(repo)
        camelRoutes?.refreshChatModel()
        return loaded
    }

    override fun getModelInfo(): Map<String, Any?> {
        val engine = llmEngine ?: return mapOf(
            "loaded" to (externalLlm != null),
            "model_id" to null,
            "message" to "No local LlmEngine; external LLM ${if (externalLlm != null) "connected" else "not connected"}"
        )
        return engine.getModelInfo()
    }

    // ── Private dispatch logic ───────────────────────────────

    /**
     * Dispatch pattern shared by chat/generateInk/reviewInk/translateToHebrew.
     *
     * Priority:
     *   1. externalLlm
     *   2. camelRoutes then fallback to llmEngine
     *   3. llmEngine directly
     *   4. error
     */
    private inline fun dispatch(
        routeName: String,
        body: Any?,
        crossinline directFn: () -> String,
        crossinline externalFn: (ChatModel) -> String
    ): String {
        // 1. External LLM
        externalLlm?.let { return externalFn(it) }

        // 2. Camel route with llmEngine fallback
        if (llmEngine != null && camelRoutes != null) {
            return try {
                camelRoutes!!.sendToRoute(routeName, body)?.toString()
                    ?: directFn()
            } catch (e: Exception) {
                log.debug("Camel route {} failed, falling back to llmEngine: {}", routeName, e.message)
                directFn()
            }
        }

        // 3. LlmEngine directly
        if (llmEngine != null) {
            return directFn()
        }

        // 4. No backend
        throw IllegalStateException(
            "No LLM backend available. Load a local model or connect to a service."
        )
    }
}
