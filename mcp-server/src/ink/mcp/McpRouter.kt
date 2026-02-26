package ink.mcp

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.sse.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val mcpLog = LoggerFactory.getLogger("ink.mcp.McpRouter")

/**
 * MCP transport session — holds a channel for sending SSE events back to the client.
 */
class McpSession(val id: String) {
    val events = Channel<ServerSentEvent>(Channel.BUFFERED)
}

/**
 * Start the Ktor MCP server with ink engine, LLM backends, collaboration, and debug.
 *
 * Modes:
 *   "mcp"      — Full MCP server (all features)
 *   "jlama"    — MCP server with local JLama inference
 *   "lmstudio" — MCP server using external LM Studio
 *   "pwa"      — Ink-only server (no LLM)
 */
fun startServer(
    port: Int,
    inkjsPath: String,
    bidifyPath: String?,
    enableLlm: Boolean = true,
    modelCachePath: String? = null,
    mode: String = "mcp",
    autoLoadModel: String? = null,
    lmStudioUrl: String? = null,
    lmStudioModel: String? = null
) {
    val inkEngine = InkEngine(inkjsPath, bidifyPath)

    // Initialize LLM engine based on mode
    val llmEngine = if (enableLlm) LlmEngine(
        modelCachePath = java.nio.file.Path.of(modelCachePath ?: System.getProperty("user.home") + "/.jlama")
    ) else null

    // Initialize LM Studio engine if mode is lmstudio
    val lmStudioEngine = if (mode == "lmstudio" && lmStudioUrl != null) {
        LmStudioEngine(baseUrl = lmStudioUrl, modelName = lmStudioModel)
    } else null

    val camelRoutes = if (enableLlm && llmEngine != null) CamelRoutes(inkEngine, llmEngine) else null

    // Initialize collaboration engine
    val colabEngine = ColabEngine()

    // Initialize edit + puml engines (shared)
    val editEngine = InkEditEngine()
    val ink2PumlEngine = Ink2PumlEngine(editEngine)

    // Initialize tools with all engines
    val tools = McpTools(
        engine = inkEngine,
        llmEngine = llmEngine,
        camelRoutes = camelRoutes,
        debugEngine = InkDebugEngine(inkEngine),
        editEngine = editEngine,
        colabEngine = colabEngine,
        inkMdEngine = InkMdEngine(),
        ink2PumlEngine = ink2PumlEngine
    )
    val mcpSessions = ConcurrentHashMap<String, McpSession>()

    // Auto-load model if specified
    if (autoLoadModel != null && llmEngine != null) {
        mcpLog.info("Auto-loading model: $autoLoadModel")
        llmEngine.loadModel(autoLoadModel)
        camelRoutes?.refreshChatModel()
    }

    // Start Camel routes
    camelRoutes?.start()

    mcpLog.info("Server mode: $mode")

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(mcpJson)
        }
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Accept)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                mcpLog.error("Unhandled exception", cause)
                call.respondText(
                    """{"error":"${cause.message?.replace("\"", "\\\"") ?: "unknown"}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }
        install(SSE)
        install(WebSockets)

        routing {
            // Health check
            get("/health") {
                call.respondText(
                    """{"status":"ok","version":"0.3.0","mode":"$mode","tools":${tools.tools.size}}""",
                    ContentType.Application.Json
                )
            }

            // MCP SSE endpoint — client connects here to receive events
            sse("/sse") {
                val sessionId = UUID.randomUUID().toString()
                val session = McpSession(sessionId)
                mcpSessions[sessionId] = session

                send(ServerSentEvent(
                    data = "/message?sessionId=$sessionId",
                    event = "endpoint"
                ))

                mcpLog.info("SSE session started: $sessionId")

                try {
                    for (event in session.events) {
                        send(event)
                    }
                } finally {
                    mcpSessions.remove(sessionId)
                    mcpLog.info("SSE session ended: $sessionId")
                }
            }

            // MCP message endpoint — client sends JSON-RPC here
            post("/message") {
                val sessionId = call.request.queryParameters["sessionId"]
                val session = sessionId?.let { mcpSessions[it] }

                val body = call.receiveText()
                mcpLog.debug("MCP request: $body")

                val request = try {
                    mcpJson.decodeFromString<JsonRpcRequest>(body)
                } catch (e: Exception) {
                    call.respondText(
                        mcpJson.encodeToString(JsonRpcResponse.serializer(), JsonRpcResponse(
                            error = JsonRpcError(-32700, "Parse error: ${e.message}")
                        )),
                        ContentType.Application.Json
                    )
                    return@post
                }

                val response = handleRpcRequest(request, tools)

                if (session != null) {
                    session.events.send(ServerSentEvent(
                        data = mcpJson.encodeToString(JsonRpcResponse.serializer(), response),
                        event = "message"
                    ))
                    call.respond(HttpStatusCode.Accepted)
                } else {
                    call.respondText(
                        mcpJson.encodeToString(JsonRpcResponse.serializer(), response),
                        ContentType.Application.Json
                    )
                }
            }

            // ── Yjs Collaboration WebSocket ──
            installColabRoutes(colabEngine)

            // Collaboration status REST endpoint
            get("/api/collab") {
                val result = tools.callTool("collab_status", null)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // ── Direct REST API (non-MCP) ──
            post("/api/compile") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("compile_ink", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/start") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("start_story", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/choose") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("choose", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/variable") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val toolName = if (body.containsKey("value")) "set_variable" else "get_variable"
                val result = tools.callTool(toolName, body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/bidify") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("bidify", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            get("/api/sessions") {
                val result = tools.callTool("list_sessions", null)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            get("/api/services") {
                val result = tools.callTool("list_services", null)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // Debug REST endpoints
            post("/api/debug/start") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("start_debug", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/debug/step") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("debug_step", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // Ink+Markdown REST endpoints
            post("/api/ink-md/parse") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("parse_ink_md", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/ink-md/render") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("render_ink_md", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/ink-md/compile") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("compile_ink_md", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // Edit/parse REST endpoints
            post("/api/parse") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("parse_ink", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/stats") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("ink_stats", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // PlantUML diagram REST endpoints
            post("/api/ink2puml") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("ink2puml", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/ink2svg") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("ink2svg", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/puml2svg") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("puml2svg", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/ink-toc") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("ink_toc", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/ink-toc-puml") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("ink_toc_puml", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }
        }
    }.start(wait = true)
}

/** Handle a JSON-RPC request per MCP protocol */
private fun handleRpcRequest(request: JsonRpcRequest, tools: McpTools): JsonRpcResponse {
    return when (request.method) {
        "initialize" -> {
            val result = McpInitializeResult(
                serverInfo = McpServerInfo(name = "inky-mcp", version = "0.3.0")
            )
            JsonRpcResponse(
                id = request.id,
                result = mcpJson.encodeToJsonElement(McpInitializeResult.serializer(), result)
            )
        }

        "initialized" -> {
            JsonRpcResponse(id = request.id, result = buildJsonObject {})
        }

        "tools/list" -> {
            val result = McpToolsListResult(tools = tools.tools)
            JsonRpcResponse(
                id = request.id,
                result = mcpJson.encodeToJsonElement(McpToolsListResult.serializer(), result)
            )
        }

        "tools/call" -> {
            val params = request.params
            val toolName = params?.get("name")?.jsonPrimitive?.contentOrNull
                ?: return JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(-32602, "Missing tool name")
                )
            val arguments = params["arguments"]?.jsonObject

            val result = tools.callTool(toolName, arguments)
            JsonRpcResponse(
                id = request.id,
                result = mcpJson.encodeToJsonElement(McpToolResult.serializer(), result)
            )
        }

        "ping" -> {
            JsonRpcResponse(id = request.id, result = buildJsonObject {})
        }

        else -> {
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(-32601, "Method not found: ${request.method}")
            )
        }
    }
}
