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
import io.ktor.server.http.content.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.server.auth.*
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

    // Initialize auth, calendar, vcard, webdav engines
    val authEngine = InkAuthEngine()
    val calendarEngine = InkCalendarEngine()
    val vcardEngine = InkVCardEngine(authEngine)
    val webDavEngine = InkWebDavEngine(vcardEngine)

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
        ink2PumlEngine = ink2PumlEngine,
        calendarEngine = calendarEngine,
        vcardEngine = vcardEngine,
        authEngine = authEngine,
        webDavEngine = webDavEngine
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

        // Install auth plugins when Keycloak is configured
        if (authEngine.isConfigured()) {
            authEngine.installAuth(this)
            mcpLog.info("Auth enabled: Keycloak={}, LLM basic=enabled", authEngine.keycloakRealmUrl)
        } else {
            mcpLog.info("Auth disabled: KEYCLOAK_REALM_URL not set (open access)")
        }

        routing {
            // ── Static files: React ink-editor app (served at /) ──
            // Built React app is copied to resources/static/ by Gradle
            staticResources("/static", "static")

            // Health check
            get("/health") {
                call.respondText(
                    """{"status":"ok","version":"0.3.1","mode":"$mode","tools":${tools.tools.size}}""",
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
            installColabRoutes(colabEngine, authEngine)

            // Collaboration status REST endpoint
            get("/api/collab") {
                val result = tools.callTool("collab_status", null)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // ── Calendar REST API ──
            post("/api/calendar/event") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("create_event", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            get("/api/calendar/{calendarId}/events") {
                val calendarId = call.parameters["calendarId"] ?: "default"
                val category = call.request.queryParameters["category"]
                val args = buildJsonObject {
                    put("calendar_id", calendarId)
                    category?.let { put("category", it) }
                }
                val result = tools.callTool("list_events", args)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            get("/api/calendar/{calendarId}/export") {
                val calendarId = call.parameters["calendarId"] ?: "default"
                val result = tools.callTool("export_ics", buildJsonObject {
                    put("calendar_id", calendarId)
                })
                call.respondText(result.content.first().text, ContentType("text", "calendar"))
            }

            post("/api/calendar/import") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("import_ics", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // ── Principal REST API ──
            post("/api/principals") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("create_principal", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            get("/api/principals") {
                val isLlm = call.request.queryParameters["is_llm"]
                val args = isLlm?.let {
                    buildJsonObject { put("is_llm", it.toBoolean()) }
                }
                val result = tools.callTool("list_principals", args)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            get("/api/principals/{id}") {
                val id = call.parameters["id"] ?: ""
                val result = tools.callTool("get_principal", buildJsonObject {
                    put("id", id)
                })
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            delete("/api/principals/{id}") {
                val id = call.parameters["id"] ?: ""
                val result = tools.callTool("delete_principal", buildJsonObject {
                    put("id", id)
                })
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // ── Auth REST API ──
            get("/api/auth/status") {
                val result = tools.callTool("auth_status", null)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            post("/api/auth/llm-credential") {
                val body = mcpJson.parseToJsonElement(call.receiveText()).jsonObject
                val result = tools.callTool("create_llm_credential", body)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // ── WebDAV REST API ──
            // GET — list directory or get file content
            get("/dav/{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val principalId = call.request.queryParameters["principal_id"]
                val args = buildJsonObject {
                    put("path", path)
                    principalId?.let { put("principal_id", it) }
                }
                val file = java.io.File("./ink-scripts", path)
                if (file.isDirectory) {
                    val result = tools.callTool("webdav_list", args)
                    call.respondText(result.content.first().text, ContentType.Application.Json)
                } else {
                    val result = tools.callTool("webdav_get", args)
                    if (result.isError == true) {
                        call.respondText(result.content.first().text, ContentType.Application.Json, HttpStatusCode.Forbidden)
                    } else {
                        call.respondText(result.content.first().text, ContentType.Application.Json)
                    }
                }
            }

            // PUT — write file
            put("/dav/{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val principalId = call.request.queryParameters["principal_id"] ?: ""
                val content = call.receiveText()
                val args = buildJsonObject {
                    put("path", path)
                    put("content", content)
                    put("principal_id", principalId)
                }
                val result = tools.callTool("webdav_put", args)
                val status = if (result.isError == true) HttpStatusCode.Forbidden else HttpStatusCode.Created
                call.respondText(result.content.first().text, ContentType.Application.Json, status)
            }

            // DELETE — delete file
            delete("/dav/{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val principalId = call.request.queryParameters["principal_id"] ?: ""
                val args = buildJsonObject {
                    put("path", path)
                    put("principal_id", principalId)
                }
                val result = tools.callTool("webdav_delete", args)
                call.respondText(result.content.first().text, ContentType.Application.Json)
            }

            // POST — create directory (MKCOL)
            post("/dav/{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val principalId = call.request.queryParameters["principal_id"] ?: ""
                val args = buildJsonObject {
                    put("path", path)
                    put("principal_id", principalId)
                }
                val result = tools.callTool("webdav_mkdir", args)
                val status = if (result.isError == true) HttpStatusCode.Forbidden else HttpStatusCode.Created
                call.respondText(result.content.first().text, ContentType.Application.Json, status)
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
                serverInfo = McpServerInfo(name = "inky-mcp", version = "0.3.1")
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
