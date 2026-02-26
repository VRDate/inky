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
import io.ktor.serialization.kotlinx.json.*
import io.ktor.sse.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("ink.mcp.McpRouter")

/**
 * MCP transport session — holds a channel for sending SSE events back to the client.
 */
class McpSession(val id: String) {
    val events = Channel<ServerSentEvent>(Channel.BUFFERED)
}

/**
 * Start the Ktor MCP server.
 */
fun startServer(port: Int, inkjsPath: String, bidifyPath: String?) {
    val engine = InkEngine(inkjsPath, bidifyPath)
    val tools = McpTools(engine)
    val mcpSessions = ConcurrentHashMap<String, McpSession>()

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
                log.error("Unhandled exception", cause)
                call.respondText(
                    """{"error":"${cause.message?.replace("\"", "\\\"") ?: "unknown"}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }
        install(SSE)

        routing {
            // Health check
            get("/health") {
                call.respondText("""{"status":"ok","version":"0.1.0"}""", ContentType.Application.Json)
            }

            // MCP SSE endpoint — client connects here to receive events
            sse("/sse") {
                val sessionId = UUID.randomUUID().toString()
                val session = McpSession(sessionId)
                mcpSessions[sessionId] = session

                // Send the endpoint URL as the first event
                send(ServerSentEvent(
                    data = "/message?sessionId=$sessionId",
                    event = "endpoint"
                ))

                log.info("SSE session started: $sessionId")

                try {
                    // Forward events from the channel to the SSE stream
                    for (event in session.events) {
                        send(event)
                    }
                } finally {
                    mcpSessions.remove(sessionId)
                    log.info("SSE session ended: $sessionId")
                }
            }

            // MCP message endpoint — client sends JSON-RPC here
            post("/message") {
                val sessionId = call.request.queryParameters["sessionId"]
                val session = sessionId?.let { mcpSessions[it] }

                val body = call.receiveText()
                log.debug("MCP request: $body")

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

                // Send response via SSE if session exists, otherwise inline
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

            // Direct REST API (non-MCP, for simpler integrations)
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
        }
    }.start(wait = true)
}

/** Handle a JSON-RPC request per MCP protocol */
private fun handleRpcRequest(request: JsonRpcRequest, tools: McpTools): JsonRpcResponse {
    return when (request.method) {
        "initialize" -> {
            val result = McpInitializeResult(
                serverInfo = McpServerInfo(name = "inky-mcp", version = "0.1.0")
            )
            JsonRpcResponse(
                id = request.id,
                result = mcpJson.encodeToJsonElement(McpInitializeResult.serializer(), result)
            )
        }

        "initialized" -> {
            // Notification, no response needed but we send one anyway for consistency
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
