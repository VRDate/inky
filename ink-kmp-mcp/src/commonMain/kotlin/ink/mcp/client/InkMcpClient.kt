package ink.mcp.client

import ink.mcp.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

/**
 * Multiplatform MCP client for the ink MCP server.
 *
 * Wraps all 81 MCP tools with typed suspend functions. Compiles to JVM, JS, and WASM.
 * Uses Ktor HTTP client under the hood — engine is platform-specific (CIO on JVM, JS fetch on JS/WASM).
 *
 * Usage:
 * ```kotlin
 * val client = InkMcpClient("http://localhost:3001")
 * val result = client.compileInk("Hello -> END")
 * println(result.success) // true
 * ```
 */
class InkMcpClient(
    val serverUrl: String = "http://localhost:3001",
    private val httpClient: HttpClient = defaultClient()
) {
    private val json = mcpJson
    private var requestId = 0

    // ════════════════════════════════════════════════════════════════════
    // TRANSPORT
    // ════════════════════════════════════════════════════════════════════

    /** Call an MCP tool and parse the text content as JSON of type [T]. */
    private suspend inline fun <reified T> call(tool: String, args: JsonObject = buildJsonObject {}): T {
        val text = callText(tool, args)
        return json.decodeFromString<T>(text)
    }

    /** Call an MCP tool and return the raw text content (for non-JSON responses). */
    private suspend fun callText(tool: String, args: JsonObject = buildJsonObject {}): String {
        val request = JsonRpcRequest(
            id = JsonPrimitive(requestId++),
            method = "tools/call",
            params = buildJsonObject {
                put("name", tool)
                put("arguments", args)
            }
        )
        val response: HttpResponse = httpClient.post("$serverUrl/message") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
        }
        val body = response.bodyAsText()
        val rpcResponse = json.decodeFromString<JsonRpcResponse>(body)

        if (rpcResponse.error != null) {
            throw McpException(rpcResponse.error.code, rpcResponse.error.message)
        }

        val result = rpcResponse.result?.jsonObject
            ?: throw McpException(-1, "No result in MCP response")
        val content = result["content"]?.jsonArray
            ?: throw McpException(-1, "No content in MCP response")
        val firstBlock = content.firstOrNull()?.jsonObject
            ?: throw McpException(-1, "Empty content in MCP response")

        val isError = result["isError"]?.jsonPrimitive?.booleanOrNull ?: false
        val text = firstBlock["text"]?.jsonPrimitive?.content
            ?: throw McpException(-1, "No text in MCP content block")

        if (isError) throw McpException(-32000, text)
        return text
    }

    // ════════════════════════════════════════════════════════════════════
    // INK COMPILATION & PLAYBACK (17 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun compileInk(source: String): CompileInkResponse =
        call("compile_ink", buildJsonObject { put("source", source) })

    suspend fun startStory(source: String, sessionId: String? = null): ContinueResponse =
        call("start_story", buildJsonObject {
            put("source", source)
            if (sessionId != null) put("session_id", sessionId)
        })

    suspend fun startStoryJson(compiledJson: String, sessionId: String? = null): ContinueResponse =
        call("start_story_json", buildJsonObject {
            put("json", compiledJson)
            if (sessionId != null) put("session_id", sessionId)
        })

    suspend fun choose(sessionId: String, choiceIndex: Int): ContinueResponse =
        call("choose", buildJsonObject {
            put("session_id", sessionId)
            put("choice_index", choiceIndex)
        })

    suspend fun continueStory(sessionId: String): ContinueResponse =
        call("continue_story", buildJsonObject { put("session_id", sessionId) })

    suspend fun getVariable(sessionId: String, name: String): VariableResponse =
        call("get_variable", buildJsonObject {
            put("session_id", sessionId)
            put("name", name)
        })

    suspend fun setVariable(sessionId: String, name: String, value: JsonElement): SetVariableResponse =
        call("set_variable", buildJsonObject {
            put("session_id", sessionId)
            put("name", name)
            put("value", value)
        })

    suspend fun saveState(sessionId: String): SaveStateResponse =
        call("save_state", buildJsonObject { put("session_id", sessionId) })

    suspend fun loadState(sessionId: String, stateJson: String): LoadStateResponse =
        call("load_state", buildJsonObject {
            put("session_id", sessionId)
            put("state_json", stateJson)
        })

    suspend fun resetStory(sessionId: String): ContinueResponse =
        call("reset_story", buildJsonObject { put("session_id", sessionId) })

    suspend fun evaluateFunction(sessionId: String, functionName: String, args: List<JsonElement> = emptyList()): EvalFunctionResponse =
        call("evaluate_function", buildJsonObject {
            put("session_id", sessionId)
            put("function_name", functionName)
            if (args.isNotEmpty()) put("args", JsonArray(args))
        })

    suspend fun getGlobalTags(sessionId: String): GlobalTagsResponse =
        call("get_global_tags", buildJsonObject { put("session_id", sessionId) })

    suspend fun listSessions(): ListSessionsResponse =
        call("list_sessions")

    suspend fun endSession(sessionId: String): EndSessionResponse =
        call("end_session", buildJsonObject { put("session_id", sessionId) })

    suspend fun bidify(text: String): BidiResponse =
        call("bidify", buildJsonObject { put("text", text) })

    suspend fun stripBidi(text: String): BidiResponse =
        call("strip_bidi", buildJsonObject { put("text", text) })

    suspend fun bidifyJson(jsonStr: String): BidiResponse =
        call("bidify_json", buildJsonObject { put("json", jsonStr) })

    // ════════════════════════════════════════════════════════════════════
    // DEBUG (8 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun startDebug(sessionId: String): JsonObject =
        json.decodeFromString(callText("start_debug", buildJsonObject { put("session_id", sessionId) }))

    suspend fun addBreakpoint(sessionId: String, type: String, target: String): BreakpointResponse =
        call("add_breakpoint", buildJsonObject {
            put("session_id", sessionId)
            put("type", type)
            put("target", target)
        })

    suspend fun removeBreakpoint(sessionId: String, breakpointId: String): RemoveBreakpointResponse =
        call("remove_breakpoint", buildJsonObject {
            put("session_id", sessionId)
            put("breakpoint_id", breakpointId)
        })

    suspend fun debugStep(sessionId: String): DebugStepResponse =
        call("debug_step", buildJsonObject { put("session_id", sessionId) })

    suspend fun debugContinue(sessionId: String, maxSteps: Int = 100): DebugStepResponse =
        call("debug_continue", buildJsonObject {
            put("session_id", sessionId)
            put("max_steps", maxSteps)
        })

    suspend fun addWatch(sessionId: String, variable: String): AddWatchResponse =
        call("add_watch", buildJsonObject {
            put("session_id", sessionId)
            put("variable", variable)
        })

    suspend fun debugInspect(sessionId: String): JsonObject =
        json.decodeFromString(callText("debug_inspect", buildJsonObject { put("session_id", sessionId) }))

    suspend fun debugTrace(sessionId: String, lastN: Int = 50): JsonObject =
        json.decodeFromString(callText("debug_trace", buildJsonObject {
            put("session_id", sessionId)
            put("last_n", lastN)
        }))

    // ════════════════════════════════════════════════════════════════════
    // EDIT (6 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun parseInk(source: String): InkStructureResponse =
        call("parse_ink", buildJsonObject { put("source", source) })

    suspend fun getSection(source: String, sectionName: String): SectionInfo =
        call("get_section", buildJsonObject {
            put("source", source)
            put("section_name", sectionName)
        })

    suspend fun replaceSection(source: String, sectionName: String, newContent: String): EditResponse =
        call("replace_section", buildJsonObject {
            put("source", source)
            put("section_name", sectionName)
            put("new_content", newContent)
        })

    suspend fun insertSection(source: String, afterSection: String, newContent: String): EditResponse =
        call("insert_section", buildJsonObject {
            put("source", source)
            put("after_section", afterSection)
            put("new_content", newContent)
        })

    suspend fun renameSection(source: String, oldName: String, newName: String): EditResponse =
        call("rename_section", buildJsonObject {
            put("source", source)
            put("old_name", oldName)
            put("new_name", newName)
        })

    suspend fun inkStats(source: String): JsonObject =
        json.decodeFromString(callText("ink_stats", buildJsonObject { put("source", source) }))

    // ════════════════════════════════════════════════════════════════════
    // INK+MARKDOWN (3 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun parseInkMd(markdown: String): ParseInkMdResponse =
        call("parse_ink_md", buildJsonObject { put("markdown", markdown) })

    suspend fun renderInkMd(markdown: String): RenderInkMdResponse =
        call("render_ink_md", buildJsonObject { put("markdown", markdown) })

    suspend fun compileInkMd(markdown: String): CompileInkMdResponse =
        call("compile_ink_md", buildJsonObject { put("markdown", markdown) })

    // ════════════════════════════════════════════════════════════════════
    // PLANTUML (5 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun ink2puml(source: String, mode: String = "activity", title: String = "Ink Story Flow"): PumlResponse =
        call("ink2puml", buildJsonObject {
            put("source", source)
            put("mode", mode)
            put("title", title)
        })

    suspend fun ink2svg(source: String, mode: String = "activity", title: String = "Ink Story Flow"): SvgResponse =
        call("ink2svg", buildJsonObject {
            put("source", source)
            put("mode", mode)
            put("title", title)
        })

    suspend fun puml2svg(puml: String): SvgResponse =
        call("puml2svg", buildJsonObject { put("puml", puml) })

    suspend fun inkToc(source: String): TocResponse =
        call("ink_toc", buildJsonObject { put("source", source) })

    suspend fun inkTocPuml(source: String, title: String = "Ink Story TOC"): TocPumlResponse =
        call("ink_toc_puml", buildJsonObject {
            put("source", source)
            put("title", title)
        })

    // ════════════════════════════════════════════════════════════════════
    // LLM (8 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun listModels(vramGb: Int? = null): ListModelsResponse =
        call("list_models", buildJsonObject {
            if (vramGb != null) put("vram_gb", vramGb)
        })

    suspend fun loadModel(modelId: String? = null, customRepo: String? = null): LoadModelResponse =
        call("load_model", buildJsonObject {
            if (modelId != null) put("model_id", modelId)
            if (customRepo != null) put("custom_repo", customRepo)
        })

    suspend fun modelInfo(): JsonObject =
        json.decodeFromString(callText("model_info"))

    suspend fun chat(message: String): String =
        call<ChatResponse>("llm_chat", buildJsonObject { put("message", message) }).response

    suspend fun generateInk(prompt: String): GenerateInkResponse =
        call("generate_ink", buildJsonObject { put("prompt", prompt) })

    suspend fun reviewInk(source: String): ReviewInkResponse =
        call("review_ink", buildJsonObject { put("source", source) })

    suspend fun translateToHebrew(source: String): TranslateInkResponse =
        call("translate_ink_hebrew", buildJsonObject { put("source", source) })

    suspend fun generateCompilePlay(prompt: String): GenerateCompilePlayResponse =
        call("generate_compile_play", buildJsonObject { put("prompt", prompt) })

    // ════════════════════════════════════════════════════════════════════
    // SERVICES (2 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun listServices(): ListServicesResponse =
        call("list_services")

    suspend fun connectService(
        serviceId: String,
        apiKey: String? = null,
        model: String? = null
    ): ConnectServiceResponse =
        call("connect_service", buildJsonObject {
            put("service_id", serviceId)
            if (apiKey != null) put("api_key", apiKey)
            if (model != null) put("model", model)
        })

    // ════════════════════════════════════════════════════════════════════
    // COLLABORATION (2 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun collabStatus(): CollabStatusResponse =
        call("collab_status")

    suspend fun collabInfo(documentId: String): JsonObject =
        json.decodeFromString(callText("collab_info", buildJsonObject { put("document_id", documentId) }))

    // ════════════════════════════════════════════════════════════════════
    // CALENDAR (4 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun createEvent(args: JsonObject): JsonObject =
        json.decodeFromString(callText("create_event", args))

    suspend fun listEvents(args: JsonObject = buildJsonObject {}): ListEventsResponse =
        call("list_events", args)

    suspend fun exportIcs(args: JsonObject = buildJsonObject {}): ExportIcsResponse =
        call("export_ics", args)

    suspend fun importIcs(ics: String): JsonObject =
        json.decodeFromString(callText("import_ics", buildJsonObject { put("ics", ics) }))

    // ════════════════════════════════════════════════════════════════════
    // VCARD / PRINCIPALS (4 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun createPrincipal(args: JsonObject): JsonObject =
        json.decodeFromString(callText("create_principal", args))

    suspend fun listPrincipals(args: JsonObject = buildJsonObject {}): ListPrincipalsResponse =
        call("list_principals", args)

    suspend fun getPrincipal(principalId: String): JsonObject =
        json.decodeFromString(callText("get_principal", buildJsonObject { put("principal_id", principalId) }))

    suspend fun deletePrincipal(principalId: String): JsonObject =
        json.decodeFromString(callText("delete_principal", buildJsonObject { put("principal_id", principalId) }))

    // ════════════════════════════════════════════════════════════════════
    // AUTH (2 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun authStatus(): JsonObject =
        json.decodeFromString(callText("auth_status"))

    suspend fun createLlmCredential(args: JsonObject): JsonObject =
        json.decodeFromString(callText("create_llm_credential", args))

    // ════════════════════════════════════════════════════════════════════
    // WEBDAV (10 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun webdavList(path: String, isShared: Boolean = false): WebDavListResponse =
        call("webdav_list", buildJsonObject {
            put("path", path)
            put("is_shared", isShared)
        })

    suspend fun webdavGet(path: String): JsonObject =
        json.decodeFromString(callText("webdav_get", buildJsonObject { put("path", path) }))

    suspend fun webdavPut(path: String, content: String, contentType: String = "text/plain"): JsonObject =
        json.decodeFromString(callText("webdav_put", buildJsonObject {
            put("path", path)
            put("content", content)
            put("content_type", contentType)
        }))

    suspend fun webdavDelete(path: String): JsonObject =
        json.decodeFromString(callText("webdav_delete", buildJsonObject { put("path", path) }))

    suspend fun webdavMkdir(path: String): JsonObject =
        json.decodeFromString(callText("webdav_mkdir", buildJsonObject { put("path", path) }))

    suspend fun webdavSync(args: JsonObject): JsonObject =
        json.decodeFromString(callText("webdav_sync", args))

    suspend fun webdavBackup(path: String): JsonObject =
        json.decodeFromString(callText("webdav_backup", buildJsonObject { put("path", path) }))

    suspend fun webdavListBackups(path: String): WebDavListBackupsResponse =
        call("webdav_list_backups", buildJsonObject { put("path", path) })

    suspend fun webdavRestore(args: JsonObject): JsonObject =
        json.decodeFromString(callText("webdav_restore", args))

    suspend fun webdavWorkingCopy(args: JsonObject): JsonObject =
        json.decodeFromString(callText("webdav_working_copy", args))

    // ════════════════════════════════════════════════════════════════════
    // ASSETS (10 tools)
    // ════════════════════════════════════════════════════════════════════

    suspend fun resolveEmoji(emoji: String): ResolveEmojiResponse =
        call("resolve_emoji", buildJsonObject { put("emoji", emoji) })

    suspend fun parseAssetTags(text: String): List<ParsedAssetTagResponse> =
        json.decodeFromString(callText("parse_asset_tags", buildJsonObject { put("text", text) }))

    suspend fun generateItems(args: JsonObject): GenerateTableResponse =
        call("generate_items", args)

    suspend fun generateCharacters(args: JsonObject): GenerateTableResponse =
        call("generate_characters", args)

    suspend fun generateStoryMd(args: JsonObject): String =
        callText("generate_story_md", args)

    suspend fun evaluateFormulas(args: JsonObject): List<GenerateTableResponse> =
        json.decodeFromString(callText("evaluate_formulas", args))

    suspend fun listEmojiGroups(args: JsonObject = buildJsonObject {}): ListEmojiGroupsResponse =
        call("list_emoji_groups", args)

    suspend fun resolveUnicodeBlock(block: String): ResolveUnicodeBlockResponse =
        call("resolve_unicode_block", buildJsonObject { put("block", block) })

    suspend fun emitAssetEvent(sessionId: String, text: String): AssetEventResponse =
        call("emit_asset_event", buildJsonObject {
            put("session_id", sessionId)
            put("text", text)
        })

    suspend fun listAssetEvents(args: JsonObject = buildJsonObject {}): ListAssetEventsResponse =
        call("list_asset_events", args)

    // ════════════════════════════════════════════════════════════════════
    // HEALTH
    // ════════════════════════════════════════════════════════════════════

    /** Check if the MCP server is reachable. */
    suspend fun isAvailable(): Boolean {
        return try {
            val resp: HttpResponse = httpClient.get("$serverUrl/health")
            resp.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    /** Close the underlying HTTP client. */
    fun close() {
        httpClient.close()
    }

    companion object {
        fun defaultClient() = HttpClient {
            install(ContentNegotiation) {
                json(mcpJson)
            }
        }
    }
}

/** Exception thrown when an MCP tool returns an error. */
class McpException(val code: Int, override val message: String) : Exception(message)
