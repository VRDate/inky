package ink.mcp

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import org.slf4j.LoggerFactory

/**
 * Pre-configured LLM service definitions for popular providers.
 *
 * Each service config provides the OpenAI-compatible base URL, default model,
 * and connection parameters. All providers that expose an OpenAI-compatible
 * API can be used with LangChain4j's OpenAiChatModel.
 *
 * Supported services:
 *   - Claude (Anthropic via OpenAI-compat proxy)
 *   - Gemini (Google AI Studio)
 *   - Copilot (GitHub Models)
 *   - Grok (xAI)
 *   - Perplexity (Perplexity AI)
 *   - Comet (CometML)
 *   - LM Studio (local)
 *   - Ollama (local)
 *   - OpenRouter (aggregator)
 *   - Together AI
 *   - Groq (fast inference)
 */
object LlmServiceConfig {

    private val log = LoggerFactory.getLogger(LlmServiceConfig::class.java)

    data class ServiceDef(
        val id: String,
        val name: String,
        val baseUrl: String,
        val defaultModel: String,
        val apiKeyEnv: String,
        val description: String,
        val requiresApiKey: Boolean = true,
        val isLocal: Boolean = false,
        val docUrl: String = ""
    )

    val SERVICES: List<ServiceDef> = listOf(
        ServiceDef(
            id = "claude",
            name = "Claude (Anthropic)",
            baseUrl = "https://api.anthropic.com/v1",
            defaultModel = "claude-sonnet-4-20250514",
            apiKeyEnv = "ANTHROPIC_API_KEY",
            description = "Anthropic Claude — best for nuanced narrative and ink code generation",
            docUrl = "https://docs.anthropic.com/en/api"
        ),
        ServiceDef(
            id = "gemini",
            name = "Gemini (Google)",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            defaultModel = "gemini-2.0-flash",
            apiKeyEnv = "GOOGLE_API_KEY",
            description = "Google Gemini — fast, multimodal, good for story analysis",
            docUrl = "https://ai.google.dev/gemini-api/docs"
        ),
        ServiceDef(
            id = "copilot",
            name = "GitHub Copilot",
            baseUrl = "https://models.inference.ai.azure.com",
            defaultModel = "gpt-4o",
            apiKeyEnv = "GITHUB_TOKEN",
            description = "GitHub Models — free tier available with GitHub account",
            docUrl = "https://docs.github.com/en/github-models"
        ),
        ServiceDef(
            id = "grok",
            name = "Grok (xAI)",
            baseUrl = "https://api.x.ai/v1",
            defaultModel = "grok-3",
            apiKeyEnv = "XAI_API_KEY",
            description = "xAI Grok — creative and unconventional story generation",
            docUrl = "https://docs.x.ai/api"
        ),
        ServiceDef(
            id = "perplexity",
            name = "Perplexity AI",
            baseUrl = "https://api.perplexity.ai",
            defaultModel = "sonar-pro",
            apiKeyEnv = "PERPLEXITY_API_KEY",
            description = "Perplexity — grounded in web knowledge, good for research-based stories",
            docUrl = "https://docs.perplexity.ai"
        ),
        ServiceDef(
            id = "comet",
            name = "Comet Opik",
            baseUrl = "https://api.cloud.opik.com/v1",
            defaultModel = "opik-default",
            apiKeyEnv = "COMET_API_KEY",
            description = "CometML Opik — LLM observability and evaluation platform",
            docUrl = "https://www.comet.com/docs/opik"
        ),
        ServiceDef(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "anthropic/claude-sonnet-4-20250514",
            apiKeyEnv = "OPENROUTER_API_KEY",
            description = "OpenRouter — aggregator with 100+ models, unified API",
            docUrl = "https://openrouter.ai/docs"
        ),
        ServiceDef(
            id = "together",
            name = "Together AI",
            baseUrl = "https://api.together.xyz/v1",
            defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            apiKeyEnv = "TOGETHER_API_KEY",
            description = "Together AI — fast open-source model hosting",
            docUrl = "https://docs.together.ai"
        ),
        ServiceDef(
            id = "groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            apiKeyEnv = "GROQ_API_KEY",
            description = "Groq — ultra-fast inference on LPU hardware",
            docUrl = "https://console.groq.com/docs"
        ),
        ServiceDef(
            id = "lmstudio",
            name = "LM Studio",
            baseUrl = "http://localhost:1234/v1",
            defaultModel = "local-model",
            apiKeyEnv = "LMSTUDIO_API_KEY",
            description = "LM Studio — local model server, no API key needed",
            requiresApiKey = false,
            isLocal = true,
            docUrl = "https://lmstudio.ai/docs"
        ),
        ServiceDef(
            id = "ollama",
            name = "Ollama",
            baseUrl = "http://localhost:11434/v1",
            defaultModel = "llama3.3",
            apiKeyEnv = "OLLAMA_API_KEY",
            description = "Ollama — local model runner, supports GGUF and Nemotron models",
            requiresApiKey = false,
            isLocal = true,
            docUrl = "https://ollama.com/docs"
        )
    )

    /** Find a service by ID */
    fun findService(serviceId: String): ServiceDef? {
        return SERVICES.find { it.id == serviceId }
    }

    /** Connect to a service and return a ChatModel */
    fun connect(
        serviceId: String,
        apiKey: String? = null,
        modelName: String? = null,
        baseUrl: String? = null
    ): ChatModel {
        val service = findService(serviceId)
            ?: throw IllegalArgumentException(
                "Unknown service: $serviceId. Available: ${SERVICES.map { it.id }}"
            )

        val resolvedApiKey = apiKey
            ?: System.getenv(service.apiKeyEnv)
            ?: if (service.requiresApiKey) throw IllegalStateException(
                "API key required for ${service.name}. Set ${service.apiKeyEnv} env var or pass api_key."
            ) else "not-needed"

        val resolvedUrl = baseUrl ?: service.baseUrl
        val resolvedModel = modelName ?: service.defaultModel

        log.info("Connecting to {} at {} with model {}", service.name, resolvedUrl, resolvedModel)

        return OpenAiChatModel.builder()
            .baseUrl(resolvedUrl)
            .apiKey(resolvedApiKey)
            .modelName(resolvedModel)
            .build()
    }

    /** List all available services */
    fun listServices(): List<Map<String, Any>> {
        return SERVICES.map { s ->
            mapOf(
                "id" to s.id,
                "name" to s.name,
                "base_url" to s.baseUrl,
                "default_model" to s.defaultModel,
                "api_key_env" to s.apiKeyEnv,
                "requires_api_key" to s.requiresApiKey,
                "is_local" to s.isLocal,
                "description" to s.description,
                "doc_url" to s.docUrl
            )
        }
    }
}
