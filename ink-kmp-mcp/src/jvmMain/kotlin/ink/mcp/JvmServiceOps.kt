package ink.mcp

import org.slf4j.LoggerFactory

/**
 * JVM adapter implementing [McpServiceOps].
 *
 * Wraps [LlmServiceConfig] (Kotlin object) and [JvmLlmOps].
 * When [connect] is called, a ChatModel is created via LlmServiceConfig
 * and injected into JvmLlmOps as the external LLM.
 */
class JvmServiceOps(
    private val llmOps: JvmLlmOps
) : McpServiceOps {

    private val log = LoggerFactory.getLogger(JvmServiceOps::class.java)

    private var _connectedServiceId: String? = null

    override val connectedServiceId: String? get() = _connectedServiceId

    override fun listServices(): List<Map<String, Any>> {
        return LlmServiceConfig.listServices()
    }

    override fun connect(
        serviceId: String,
        apiKey: String?,
        model: String?,
        baseUrl: String?
    ): Map<String, Any> {
        return try {
            val chatModel = LlmServiceConfig.connect(serviceId, apiKey, model, baseUrl)
            llmOps.externalLlm = chatModel
            _connectedServiceId = serviceId

            val service = LlmServiceConfig.findService(serviceId)
            log.info("Connected to service: {} ({})", serviceId, service?.name ?: "unknown")

            mapOf(
                "ok" to true,
                "service" to serviceId,
                "model" to (model ?: service?.defaultModel ?: "default"),
                "message" to "Connected to ${service?.name ?: serviceId}"
            )
        } catch (e: Exception) {
            log.error("Failed to connect to service {}: {}", serviceId, e.message)
            mapOf(
                "ok" to false,
                "service" to serviceId,
                "model" to (model ?: ""),
                "message" to (e.message ?: "Connection failed")
            )
        }
    }
}
