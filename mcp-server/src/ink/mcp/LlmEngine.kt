package ink.mcp

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.jlama.JlamaChatModel
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * LLM engine backed by JLama for local GGUF model inference.
 * Downloads models from HuggingFace on first use and caches them locally.
 */
class LlmEngine(private val modelCachePath: Path = Path.of(System.getProperty("user.home"), ".jlama")) {

    private val log = LoggerFactory.getLogger(LlmEngine::class.java)

    @Volatile
    private var chatModel: ChatLanguageModel? = null

    @Volatile
    private var currentModelId: String? = null

    /** Load a DictaLM GGUF model by ID */
    fun loadModel(modelId: String): String {
        val model = DictaLmConfig.findModel(modelId)
            ?: throw IllegalArgumentException(
                "Unknown model: $modelId. Available: ${DictaLmConfig.MODELS.map { it.id }}"
            )
        return loadModel(model)
    }

    /** Load a GGUF model by config */
    fun loadModel(model: GgufModel): String {
        log.info("Loading model: {} ({} {} ~{}GB)", model.id, model.parameters, model.quantization, model.sizeGb)
        log.info("  HuggingFace: {}", model.huggingFaceRepo)
        log.info("  File: {}", model.fileName)
        log.info("  Cache: {}", modelCachePath)

        chatModel = JlamaChatModel.builder()
            .modelName(model.huggingFaceRepo)
            .modelCachePath(modelCachePath)
            .build()

        currentModelId = model.id
        log.info("Model loaded: {}", model.id)
        return model.id
    }

    /** Load a model by HuggingFace repo name directly */
    fun loadCustomModel(huggingFaceRepo: String): String {
        log.info("Loading custom model: {}", huggingFaceRepo)

        chatModel = JlamaChatModel.builder()
            .modelName(huggingFaceRepo)
            .modelCachePath(modelCachePath)
            .build()

        currentModelId = huggingFaceRepo
        log.info("Custom model loaded: {}", huggingFaceRepo)
        return huggingFaceRepo
    }

    /** Send a chat message to the loaded model */
    fun chat(message: String): String {
        val model = chatModel ?: throw IllegalStateException("No model loaded. Call load_model first.")
        return model.generate(message)
    }

    /** Generate ink code from a prompt */
    fun generateInk(prompt: String): String {
        val systemPrompt = """You are an expert ink (inkle's ink) script writer.
Generate valid ink syntax. Use knots (===), stitches (=), choices (*,+),
diverts (->), variables (VAR), conditionals, and other ink features as needed.
Only output the ink code, no explanations."""

        val fullPrompt = "$systemPrompt\n\nUser request: $prompt"
        return chat(fullPrompt)
    }

    /** Review ink code and suggest improvements */
    fun reviewInk(inkSource: String): String {
        val systemPrompt = """You are an expert ink (inkle's ink) script reviewer.
Analyze the ink code for: syntax errors, logic issues, dead ends,
missing diverts, unused knots, and RTL/bidi concerns.
Provide specific, actionable feedback."""

        val fullPrompt = "$systemPrompt\n\nInk code to review:\n$inkSource"
        return chat(fullPrompt)
    }

    /** Translate ink story text to Hebrew (using DictaLM's Hebrew capabilities) */
    fun translateToHebrew(inkSource: String): String {
        val systemPrompt = """You are a Hebrew translator for interactive fiction.
Translate only the story text (dialogue, descriptions) to Hebrew.
Keep all ink syntax (knots, diverts, variables, choices markup) unchanged.
Preserve the ink structure exactly."""

        val fullPrompt = "$systemPrompt\n\nInk source:\n$inkSource"
        return chat(fullPrompt)
    }

    /** Get the currently loaded model info */
    fun getModelInfo(): Map<String, Any?> {
        val modelId = currentModelId
        val model = modelId?.let { DictaLmConfig.findModel(it) }
        return mapOf(
            "loaded" to (chatModel != null),
            "model_id" to modelId,
            "parameters" to model?.parameters,
            "quantization" to model?.quantization,
            "size_gb" to model?.sizeGb,
            "description" to model?.description,
            "cache_path" to modelCachePath.toString()
        )
    }

    /** Check if a model is loaded */
    fun isLoaded(): Boolean = chatModel != null

    /** Get the underlying ChatLanguageModel for Camel integration */
    fun getChatModel(): ChatLanguageModel? = chatModel
}
