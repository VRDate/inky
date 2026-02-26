package ink.mcp

import kotlinx.serialization.json.*

/**
 * MCP tool definitions and handlers for the Inky ink compiler.
 * Each tool maps to an InkEngine operation.
 */
class McpTools(private val engine: InkEngine) {

    /** All available MCP tools */
    val tools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "compile_ink",
            description = "Compile ink source code to JSON. Returns compiled JSON or error messages.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") {
                        put("type", "string")
                        put("description", "Ink source code to compile")
                    }
                }
                putJsonArray("required") { add("source") }
            }
        ),
        McpToolInfo(
            name = "start_story",
            description = "Compile ink source and start an interactive story session. Returns session ID, initial text, and available choices.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") {
                        put("type", "string")
                        put("description", "Ink source code")
                    }
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Optional session ID (auto-generated if omitted)")
                    }
                }
                putJsonArray("required") { add("source") }
            }
        ),
        McpToolInfo(
            name = "start_story_json",
            description = "Start an interactive story session from pre-compiled JSON.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("json") {
                        put("type", "string")
                        put("description", "Compiled ink JSON")
                    }
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Optional session ID")
                    }
                }
                putJsonArray("required") { add("json") }
            }
        ),
        McpToolInfo(
            name = "choose",
            description = "Make a choice in an active story session. Returns subsequent text and new choices.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                    putJsonObject("choice_index") {
                        put("type", "integer")
                        put("description", "0-based index of the choice to select")
                    }
                }
                putJsonArray("required") { add("session_id"); add("choice_index") }
            }
        ),
        McpToolInfo(
            name = "continue_story",
            description = "Continue reading the story text in an active session.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "get_variable",
            description = "Get the value of an ink variable in the story.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Variable name")
                    }
                }
                putJsonArray("required") { add("session_id"); add("name") }
            }
        ),
        McpToolInfo(
            name = "set_variable",
            description = "Set the value of an ink variable in the story.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Variable name")
                    }
                    putJsonObject("value") {
                        put("description", "Value to set (string, number, or boolean)")
                    }
                }
                putJsonArray("required") { add("session_id"); add("name"); add("value") }
            }
        ),
        McpToolInfo(
            name = "save_state",
            description = "Save the current story state as JSON for later restoration.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "load_state",
            description = "Load a previously saved story state.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                    putJsonObject("state_json") {
                        put("type", "string")
                        put("description", "Previously saved state JSON")
                    }
                }
                putJsonArray("required") { add("session_id"); add("state_json") }
            }
        ),
        McpToolInfo(
            name = "reset_story",
            description = "Reset the story to its beginning.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "evaluate_function",
            description = "Call an ink function defined in the story.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                    putJsonObject("function_name") {
                        put("type", "string")
                        put("description", "Name of the ink function")
                    }
                    putJsonObject("args") {
                        put("type", "array")
                        put("description", "Arguments to pass to the function")
                    }
                }
                putJsonArray("required") { add("session_id"); add("function_name") }
            }
        ),
        McpToolInfo(
            name = "get_global_tags",
            description = "Get the global tags defined at the top of the ink story.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID")
                    }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "list_sessions",
            description = "List all active story sessions.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
        ),
        McpToolInfo(
            name = "end_session",
            description = "End a story session and free its resources.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") {
                        put("type", "string")
                        put("description", "Story session ID to end")
                    }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "bidify",
            description = "Add Unicode bidi markers (LRI/RLI/PDI) to text for proper RTL display.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Text to bidify")
                    }
                }
                putJsonArray("required") { add("text") }
            }
        ),
        McpToolInfo(
            name = "strip_bidi",
            description = "Remove Unicode bidi markers from text.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Text to strip bidi markers from")
                    }
                }
                putJsonArray("required") { add("text") }
            }
        ),
        McpToolInfo(
            name = "bidify_json",
            description = "Add bidi markers to story text strings in compiled ink JSON.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("json") {
                        put("type", "string")
                        put("description", "Compiled ink JSON to bidify")
                    }
                }
                putJsonArray("required") { add("json") }
            }
        )
    )

    /** Dispatch a tool call to the appropriate handler */
    fun callTool(name: String, arguments: JsonObject?): McpToolResult {
        return try {
            when (name) {
                "compile_ink" -> handleCompile(arguments)
                "start_story" -> handleStartStory(arguments)
                "start_story_json" -> handleStartStoryJson(arguments)
                "choose" -> handleChoose(arguments)
                "continue_story" -> handleContinue(arguments)
                "get_variable" -> handleGetVariable(arguments)
                "set_variable" -> handleSetVariable(arguments)
                "save_state" -> handleSaveState(arguments)
                "load_state" -> handleLoadState(arguments)
                "reset_story" -> handleResetStory(arguments)
                "evaluate_function" -> handleEvaluateFunction(arguments)
                "get_global_tags" -> handleGetGlobalTags(arguments)
                "list_sessions" -> handleListSessions()
                "end_session" -> handleEndSession(arguments)
                "bidify" -> handleBidify(arguments)
                "strip_bidi" -> handleStripBidi(arguments)
                "bidify_json" -> handleBidifyJson(arguments)
                else -> errorResult("Unknown tool: $name")
            }
        } catch (e: Exception) {
            errorResult("${e::class.simpleName}: ${e.message}")
        }
    }

    // -- Tool handlers --

    private fun handleCompile(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val result = engine.compile(source)
        val response = buildJsonObject {
            put("success", result.success)
            if (result.json != null) put("json", result.json)
            if (result.errors.isNotEmpty()) putJsonArray("errors") { result.errors.forEach { add(it) } }
            if (result.warnings.isNotEmpty()) putJsonArray("warnings") { result.warnings.forEach { add(it) } }
        }
        return textResult(response.toString())
    }

    private fun handleStartStory(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val sessionId = args?.get("session_id")?.jsonPrimitive?.contentOrNull
        val (id, result) = engine.startSession(source, sessionId)
        return textResult(continueResultJson(id, result).toString())
    }

    private fun handleStartStoryJson(args: JsonObject?): McpToolResult {
        val json = args.requireString("json")
        val sessionId = args?.get("session_id")?.jsonPrimitive?.contentOrNull
        val (id, result) = engine.startSessionFromJson(json, sessionId)
        return textResult(continueResultJson(id, result).toString())
    }

    private fun handleChoose(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val choiceIndex = args?.get("choice_index")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("choice_index required")
        val result = engine.choose(sessionId, choiceIndex)
        return textResult(continueResultJson(sessionId, result).toString())
    }

    private fun handleContinue(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val result = engine.continueStory(sessionId)
        return textResult(continueResultJson(sessionId, result).toString())
    }

    private fun handleGetVariable(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val name = args.requireString("name")
        val value = engine.getVariable(sessionId, name)
        return textResult(buildJsonObject {
            put("name", name)
            when (value) {
                null -> put("value", JsonNull)
                is Boolean -> put("value", value)
                is Int -> put("value", value)
                is Double -> put("value", value)
                is String -> put("value", value)
                else -> put("value", value.toString())
            }
        }.toString())
    }

    private fun handleSetVariable(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val name = args.requireString("name")
        val valueElement = args?.get("value") ?: throw IllegalArgumentException("value required")
        val value: Any? = when {
            valueElement is JsonNull -> null
            valueElement.jsonPrimitive.isString -> valueElement.jsonPrimitive.content
            valueElement.jsonPrimitive.booleanOrNull != null -> valueElement.jsonPrimitive.boolean
            valueElement.jsonPrimitive.intOrNull != null -> valueElement.jsonPrimitive.int
            valueElement.jsonPrimitive.doubleOrNull != null -> valueElement.jsonPrimitive.double
            else -> valueElement.jsonPrimitive.content
        }
        engine.setVariable(sessionId, name, value)
        return textResult("""{"ok":true,"name":"$name"}""")
    }

    private fun handleSaveState(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val state = engine.saveState(sessionId)
        return textResult(buildJsonObject {
            put("session_id", sessionId)
            put("state_json", state)
        }.toString())
    }

    private fun handleLoadState(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val stateJson = args.requireString("state_json")
        engine.loadState(sessionId, stateJson)
        return textResult("""{"ok":true,"session_id":"$sessionId"}""")
    }

    private fun handleResetStory(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val result = engine.resetStory(sessionId)
        return textResult(continueResultJson(sessionId, result).toString())
    }

    private fun handleEvaluateFunction(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val funcName = args.requireString("function_name")
        val fnArgs = args?.get("args")?.jsonArray?.map { element ->
            when {
                element is JsonNull -> null
                element.jsonPrimitive.isString -> element.jsonPrimitive.content
                element.jsonPrimitive.intOrNull != null -> element.jsonPrimitive.int
                element.jsonPrimitive.doubleOrNull != null -> element.jsonPrimitive.double
                element.jsonPrimitive.booleanOrNull != null -> element.jsonPrimitive.boolean
                else -> element.jsonPrimitive.content
            }
        } ?: emptyList()
        val result = engine.evaluateFunction(sessionId, funcName, fnArgs)
        return textResult(buildJsonObject {
            put("function", funcName)
            when (result) {
                null -> put("result", JsonNull)
                is Boolean -> put("result", result)
                is Int -> put("result", result)
                is Double -> put("result", result)
                is String -> put("result", result)
                else -> put("result", result.toString())
            }
        }.toString())
    }

    private fun handleGetGlobalTags(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        val tags = engine.getGlobalTags(sessionId)
        return textResult(buildJsonObject {
            putJsonArray("tags") { tags.forEach { add(it) } }
        }.toString())
    }

    private fun handleListSessions(): McpToolResult {
        val sessions = engine.listSessions()
        return textResult(buildJsonObject {
            putJsonArray("sessions") { sessions.forEach { add(it) } }
        }.toString())
    }

    private fun handleEndSession(args: JsonObject?): McpToolResult {
        val sessionId = args.requireString("session_id")
        engine.endSession(sessionId)
        return textResult("""{"ok":true,"session_id":"$sessionId"}""")
    }

    private fun handleBidify(args: JsonObject?): McpToolResult {
        val text = args.requireString("text")
        return textResult(buildJsonObject {
            put("result", engine.bidify(text))
        }.toString())
    }

    private fun handleStripBidi(args: JsonObject?): McpToolResult {
        val text = args.requireString("text")
        return textResult(buildJsonObject {
            put("result", engine.stripBidi(text))
        }.toString())
    }

    private fun handleBidifyJson(args: JsonObject?): McpToolResult {
        val json = args.requireString("json")
        return textResult(buildJsonObject {
            put("result", engine.bidifyJson(json))
        }.toString())
    }

    // -- Helpers --

    private fun continueResultJson(sessionId: String, result: InkEngine.ContinueResult) = buildJsonObject {
        put("session_id", sessionId)
        put("text", result.text)
        put("can_continue", result.canContinue)
        putJsonArray("choices") {
            result.choices.forEach { c ->
                addJsonObject {
                    put("index", c.index)
                    put("text", c.text)
                    putJsonArray("tags") { c.tags.forEach { add(it) } }
                }
            }
        }
        putJsonArray("tags") { result.tags.forEach { add(it) } }
    }

    private fun textResult(text: String) = McpToolResult(
        content = listOf(McpContentBlock(text = text))
    )

    private fun errorResult(message: String) = McpToolResult(
        content = listOf(McpContentBlock(text = message)),
        isError = true
    )

    private fun JsonObject?.requireString(key: String): String {
        return this?.get(key)?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("'$key' is required")
    }
}
