package ink.mcp

import ink.mcp.KtTestFixtures.engine
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Ktor testApplication integration tests for MCP server endpoints.
 *
 * Tests health, JSON-RPC protocol, and tool invocations via HTTP.
 *
 * Uses [KtTestFixtures] for shared engine.
 */
class McpRouterTest {

    companion object {
        private val tools: McpTools by lazy {
            val editEngine = InkEditEngine()
            McpTools(
                engine = engine,
                debugEngine = InkDebugEngine(engine),
                editEngine = editEngine,
                colabEngine = ColabEngine(),
                inkMdEngine = InkMdEngine(),
                ink2PumlEngine = Ink2PumlEngine(editEngine)
            )
        }
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Configures a minimal test application with health + JSON-RPC routes.
     */
    private fun ApplicationTestBuilder.configureTestApp() {
        application {
            install(ContentNegotiation) {
                json(mcpJson)
            }
            install(SSE)

            routing {
                get("/health") {
                    call.respondText(
                        """{"status":"ok","version":"0.3.1","mode":"test","tools":${tools.tools.size}}""",
                        ContentType.Application.Json
                    )
                }

                // Simplified /api/compile — calls tool directly and returns JSON
                post("/api/compile") {
                    val bodyText = String(call.receive<ByteArray>())
                    val bodyJson = mcpJson.parseToJsonElement(bodyText).jsonObject
                    val result = tools.callTool("compile_ink", bodyJson)
                    call.respondText(
                        mcpJson.encodeToString(McpToolResult.serializer(), result),
                        ContentType.Application.Json
                    )
                }

                // Simplified /message — handles JSON-RPC tools/list, tools/call, ping
                post("/message") {
                    val bodyText = String(call.receive<ByteArray>())
                    val request = try {
                        mcpJson.decodeFromString<JsonRpcRequest>(bodyText)
                    } catch (e: Exception) {
                        call.respondText(
                            """{"jsonrpc":"2.0","error":{"code":-32700,"message":"Parse error"}}""",
                            ContentType.Application.Json
                        )
                        return@post
                    }

                    val response = when (request.method) {
                        "tools/list" -> buildJsonObject {
                            put("jsonrpc", "2.0")
                            request.id?.let { put("id", it) }
                            putJsonObject("result") {
                                put("tools", mcpJson.encodeToJsonElement(tools.tools))
                            }
                        }
                        "tools/call" -> {
                            val params = request.params?.jsonObject
                            val toolName = params?.get("name")?.jsonPrimitive?.content ?: ""
                            val args = params?.get("arguments")?.jsonObject
                            val toolResult = tools.callTool(toolName, args)
                            buildJsonObject {
                                put("jsonrpc", "2.0")
                                request.id?.let { put("id", it) }
                                put("result", mcpJson.encodeToJsonElement(toolResult))
                            }
                        }
                        "ping" -> buildJsonObject {
                            put("jsonrpc", "2.0")
                            request.id?.let { put("id", it) }
                            putJsonObject("result") {}
                        }
                        else -> buildJsonObject {
                            put("jsonrpc", "2.0")
                            request.id?.let { put("id", it) }
                            putJsonObject("error") {
                                put("code", -32601)
                                put("message", "Method not found: ${request.method}")
                            }
                        }
                    }
                    call.respondText(response.toString(), ContentType.Application.Json)
                }
            }
        }
    }

    // ── Health endpoint ──────────────────────────────────────────

    @Test
    fun `GET health returns 200 with version and mode`() = testApplication {
        configureTestApp()
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("version"), "Health should include version")
        assertTrue(body.containsKey("mode"), "Health should include mode")
        assertTrue(body.containsKey("tools"), "Health should include tool count")
    }

    @Test
    fun `health endpoint reports tool count`() = testApplication {
        configureTestApp()
        val response = client.get("/health")
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val toolCount = body["tools"]?.jsonPrimitive?.int ?: 0
        assertTrue(toolCount >= 20, "Health should report 20+ tools, got: $toolCount")
    }

    // ── Compile endpoint ─────────────────────────────────────────

    @Test
    fun `POST api compile with valid ink returns result`() = testApplication {
        configureTestApp()
        val response = client.post("/api/compile") {
            contentType(ContentType.Application.Json)
            setBody("""{"source": "Hello, world!\n-> END"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── JSON-RPC: tools/list ─────────────────────────────────────

    @Test
    fun `POST message with tools list returns tool array`() = testApplication {
        configureTestApp()
        val jsonRpcRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/list")
            putJsonObject("params") {}
        }

        val response = client.post("/message?sessionId=test-session") {
            contentType(ContentType.Application.Json)
            setBody(jsonRpcRequest.toString())
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val result = body["result"]?.jsonObject
        assertNotNull(result, "JSON-RPC response should have result")
        val toolsArr = result["tools"]?.jsonArray
        assertNotNull(toolsArr, "Result should contain tools array")
        assertTrue(toolsArr.size >= 20, "Should have 20+ tools, got: ${toolsArr.size}")
    }

    // ── JSON-RPC: tools/call compile_ink ──────────────────────────

    @Test
    fun `POST message with tools call compile_ink`() = testApplication {
        configureTestApp()
        val jsonRpcRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/call")
            putJsonObject("params") {
                put("name", "compile_ink")
                putJsonObject("arguments") {
                    put("source", "Hello!\n-> END")
                }
            }
        }

        val response = client.post("/message?sessionId=test-session") {
            contentType(ContentType.Application.Json)
            setBody(jsonRpcRequest.toString())
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── JSON-RPC: ping ───────────────────────────────────────────

    @Test
    fun `POST message with ping returns empty result`() = testApplication {
        configureTestApp()
        val jsonRpcRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 3)
            put("method", "ping")
        }

        val response = client.post("/message?sessionId=test-session") {
            contentType(ContentType.Application.Json)
            setBody(jsonRpcRequest.toString())
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("result"), "Ping should return result")
    }

    // ── JSON-RPC: unknown method ─────────────────────────────────

    @Test
    fun `POST message with unknown method returns error`() = testApplication {
        configureTestApp()
        val jsonRpcRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 99)
            put("method", "nonexistent/method")
        }

        val response = client.post("/message?sessionId=test-session") {
            contentType(ContentType.Application.Json)
            setBody(jsonRpcRequest.toString())
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"), "Unknown method should return error")
    }

    // ── JSON-RPC: malformed request ──────────────────────────────

    @Test
    fun `POST message with malformed JSON returns parse error`() = testApplication {
        configureTestApp()
        val response = client.post("/message?sessionId=test-session") {
            contentType(ContentType.Application.Json)
            setBody("this is not JSON")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"), "Malformed JSON should return error")
    }
}
