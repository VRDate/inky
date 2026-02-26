package ink.mcp

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import org.slf4j.LoggerFactory

/**
 * LM Studio integration — connects to an external LM Studio server
 * via its OpenAI-compatible API endpoint.
 *
 * LM Studio exposes models at http://localhost:1234/v1 by default.
 * Any model loaded in LM Studio (including DictaLM GGUF) is accessible.
 *
 * @see <a href="https://lmstudio.ai/">LM Studio</a>
 */
class LmStudioEngine(
    private val baseUrl: String = "http://localhost:1234/v1",
    private val modelName: String? = null
) {
    private val log = LoggerFactory.getLogger(LmStudioEngine::class.java)

    @Volatile
    private var chatModel: ChatModel? = null

    init {
        connect()
    }

    /** Connect to LM Studio's OpenAI-compatible API */
    fun connect(url: String = baseUrl, model: String? = modelName) {
        log.info("Connecting to LM Studio at {}", url)

        val builder = OpenAiChatModel.builder()
            .baseUrl(url)
            .apiKey("lm-studio") // LM Studio doesn't require a real API key

        if (model != null) {
            builder.modelName(model)
        }

        chatModel = builder.build()
        log.info("Connected to LM Studio (model: {})", model ?: "default")
    }

    /** Send a chat message */
    fun chat(message: String): String {
        val model = chatModel ?: throw IllegalStateException("Not connected to LM Studio")
        return model.chat(message)
    }

    /** Generate ink code */
    fun generateInk(prompt: String): String {
        val systemPrompt = """You are an expert ink (inkle's ink) script writer.
Generate valid ink syntax. Use knots (===), stitches (=), choices (*,+),
diverts (->), variables (VAR), conditionals, and other ink features.
Only output the ink code, no explanations."""
        return chat("$systemPrompt\n\nUser request: $prompt")
    }

    /** Review ink code */
    fun reviewInk(inkSource: String): String {
        val systemPrompt = """You are an expert ink script reviewer.
Analyze for: syntax errors, dead ends, missing diverts, unused knots, RTL/bidi issues.
Provide specific, actionable feedback."""
        return chat("$systemPrompt\n\nInk code:\n$inkSource")
    }

    /** Translate to Hebrew */
    fun translateToHebrew(inkSource: String): String {
        val systemPrompt = """Translate only story text to Hebrew.
Keep all ink syntax unchanged. Preserve structure exactly."""
        return chat("$systemPrompt\n\nInk source:\n$inkSource")
    }

    /** Get the ChatModel for Camel integration */
    fun getChatModel(): ChatModel? = chatModel

    /** Check connection */
    fun isConnected(): Boolean = chatModel != null

    /** Get connection info */
    fun getInfo(): Map<String, Any?> = mapOf(
        "connected" to isConnected(),
        "base_url" to baseUrl,
        "model" to modelName
    )
}
