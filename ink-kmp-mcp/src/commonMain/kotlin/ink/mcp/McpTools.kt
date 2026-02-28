package ink.mcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * MCP tool definitions and handlers for:
 *   - Ink compilation & playback (17 tools)
 *   - Ink debugging (8 tools)
 *   - Ink section editing (6 tools)
 *   - Ink+Markdown template processing (3 tools)
 *   - Ink→PlantUML diagrams + TOC (5 tools)
 *   - LLM model management (8 tools)
 *   - LLM service providers (2 tools)
 *   - Collaboration (2 tools)
 *   - WebDAV + Backup (10 tools)
 *   - Asset pipeline + faker (10 tools)
 */
class McpTools(
    private val engine: McpInkOps,
    private val llmOps: McpLlmOps? = null,
    private val serviceOps: McpServiceOps? = null,
    private val debugEngine: McpDebugOps? = null,
    private val editEngine: EditEngine = EditEngine(),
    private val colabEngine: McpColabOps? = null,
    private val inkMdEngine: InkMdEngine = InkMdEngine(),
    private val pumlOps: McpPumlOps? = null,
    private val calendarEngine: McpCalendarOps? = null,
    private val vcardEngine: McpVCardOps? = null,
    private val authEngine: McpAuthOps? = null,
    private val webDavEngine: McpWebDavOps? = null,
    private val assetOps: McpAssetOps? = null
) {

    /** All available MCP tools */
    val tools: List<McpToolInfo> by lazy {
        buildList {
            addAll(inkTools)
            if (debugEngine != null) addAll(debugTools)
            addAll(editTools)
            addAll(inkMdTools)
            if (pumlOps != null) addAll(pumlTools)
            if (llmOps != null) addAll(llmTools)
            if (serviceOps != null) addAll(serviceTools)
            if (colabEngine != null) addAll(colabTools)
            if (calendarEngine != null) addAll(calendarTools)
            if (vcardEngine != null) addAll(vcardTools)
            if (authEngine != null) addAll(authTools)
            if (webDavEngine != null) addAll(webDavTools)
            if (assetOps != null) addAll(assetTools)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // INK TOOLS (17)
    // ════════════════════════════════════════════════════════════════════

    private val inkTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "compile_ink",
            description = "Compile ink source code to JSON. Returns compiled JSON or error messages.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code to compile") }
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
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code") }
                    putJsonObject("session_id") { put("type", "string"); put("description", "Optional session ID (auto-generated if omitted)") }
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
                    putJsonObject("json") { put("type", "string"); put("description", "Compiled ink JSON") }
                    putJsonObject("session_id") { put("type", "string"); put("description", "Optional session ID") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("choice_index") { put("type", "integer"); put("description", "0-based index of the choice") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("name") { put("type", "string"); put("description", "Variable name") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("name") { put("type", "string"); put("description", "Variable name") }
                    putJsonObject("value") { put("description", "Value to set (string, number, or boolean)") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("state_json") { put("type", "string"); put("description", "Previously saved state JSON") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("function_name") { put("type", "string"); put("description", "Name of the ink function") }
                    putJsonObject("args") { put("type", "array"); put("description", "Arguments to pass") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
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
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID to end") }
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
                    putJsonObject("text") { put("type", "string"); put("description", "Text to bidify") }
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
                    putJsonObject("text") { put("type", "string"); put("description", "Text to strip bidi markers from") }
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
                    putJsonObject("json") { put("type", "string"); put("description", "Compiled ink JSON to bidify") }
                }
                putJsonArray("required") { add("json") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // DEBUG TOOLS (8)
    // ════════════════════════════════════════════════════════════════════

    private val debugTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "start_debug",
            description = "Start debugging an existing story session. Adds breakpoints, watches, step execution.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID to debug") }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "add_breakpoint",
            description = "Add a breakpoint. Types: 'knot' (break at knot), 'pattern' (regex match on output), 'variable_change' (break when variable changes).",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("type") { put("type", "string"); put("description", "Breakpoint type: knot, stitch, pattern, variable_change") }
                    putJsonObject("target") { put("type", "string"); put("description", "Knot name, regex pattern, or variable name") }
                }
                putJsonArray("required") { add("session_id"); add("type"); add("target") }
            }
        ),
        McpToolInfo(
            name = "remove_breakpoint",
            description = "Remove a breakpoint by ID.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("breakpoint_id") { put("type", "string"); put("description", "Breakpoint ID to remove") }
                }
                putJsonArray("required") { add("session_id"); add("breakpoint_id") }
            }
        ),
        McpToolInfo(
            name = "debug_step",
            description = "Step to the next story output, checking breakpoints and watching variables.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "debug_continue",
            description = "Continue execution until a breakpoint is hit or the story ends.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("max_steps") { put("type", "integer"); put("description", "Max steps before stopping (default 100)") }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "add_watch",
            description = "Add an ink variable to the watch list. Changes are tracked between steps.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("variable") { put("type", "string"); put("description", "Variable name to watch") }
                }
                putJsonArray("required") { add("session_id"); add("variable") }
            }
        ),
        McpToolInfo(
            name = "debug_inspect",
            description = "Inspect the current debug state: step count, watches, breakpoints, recent visits.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                }
                putJsonArray("required") { add("session_id") }
            }
        ),
        McpToolInfo(
            name = "debug_trace",
            description = "Get the execution trace log (list of steps, text, and variable changes).",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Story session ID") }
                    putJsonObject("last_n") { put("type", "integer"); put("description", "Number of recent entries (default 50)") }
                }
                putJsonArray("required") { add("session_id") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // EDIT TOOLS (6)
    // ════════════════════════════════════════════════════════════════════

    private val editTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "parse_ink",
            description = "Parse ink source into sections (knots, stitches, functions), variables, includes, and diverts. Returns full structure.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code to parse") }
                }
                putJsonArray("required") { add("source") }
            }
        ),
        McpToolInfo(
            name = "get_section",
            description = "Get a specific knot, stitch, or function by name with its content.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code") }
                    putJsonObject("section_name") { put("type", "string"); put("description", "Name of the section to retrieve") }
                }
                putJsonArray("required") { add("source"); add("section_name") }
            }
        ),
        McpToolInfo(
            name = "replace_section",
            description = "Replace a knot/stitch/function's content with new content.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Full ink source code") }
                    putJsonObject("section_name") { put("type", "string"); put("description", "Name of section to replace") }
                    putJsonObject("new_content") { put("type", "string"); put("description", "New content for the section") }
                }
                putJsonArray("required") { add("source"); add("section_name"); add("new_content") }
            }
        ),
        McpToolInfo(
            name = "insert_section",
            description = "Insert a new section after an existing one.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Full ink source code") }
                    putJsonObject("after_section") { put("type", "string"); put("description", "Name of section to insert after") }
                    putJsonObject("new_content") { put("type", "string"); put("description", "Ink content to insert") }
                }
                putJsonArray("required") { add("source"); add("after_section"); add("new_content") }
            }
        ),
        McpToolInfo(
            name = "rename_section",
            description = "Rename a knot/stitch and update all diverts that reference it.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Full ink source code") }
                    putJsonObject("old_name") { put("type", "string"); put("description", "Current section name") }
                    putJsonObject("new_name") { put("type", "string"); put("description", "New section name") }
                }
                putJsonArray("required") { add("source"); add("old_name"); add("new_name") }
            }
        ),
        McpToolInfo(
            name = "ink_stats",
            description = "Get ink script statistics: knot/stitch/function counts, dead ends, missing divert targets, word count.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code to analyze") }
                }
                putJsonArray("required") { add("source") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // INK+MARKDOWN TOOLS (3)
    // ════════════════════════════════════════════════════════════════════

    private val inkMdTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "parse_ink_md",
            description = "Parse a Markdown template containing ```ink code blocks and tables. " +
                "H1-H6 headers above code blocks define file names. Tables define variables/items used in ink blocks.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("markdown") { put("type", "string"); put("description", "Markdown content with ```ink blocks and tables") }
                }
                putJsonArray("required") { add("markdown") }
            }
        ),
        McpToolInfo(
            name = "render_ink_md",
            description = "Render a Markdown template: extract ink blocks, resolve table data as variables, " +
                "produce compilable ink files. Returns map of filename -> ink source.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("markdown") { put("type", "string"); put("description", "Markdown template with ```ink blocks") }
                }
                putJsonArray("required") { add("markdown") }
            }
        ),
        McpToolInfo(
            name = "compile_ink_md",
            description = "Parse, render, and compile all ink blocks from a Markdown template. Returns compilation results per file.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("markdown") { put("type", "string"); put("description", "Markdown template with ```ink blocks") }
                }
                putJsonArray("required") { add("markdown") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // PLANTUML DIAGRAM TOOLS (5)
    // ════════════════════════════════════════════════════════════════════

    private val pumlTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "ink2puml",
            description = "Convert ink source to a PlantUML diagram showing story flow (knots, choices, diverts). Returns PlantUML source.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code") }
                    putJsonObject("mode") { put("type", "string"); put("description", "Diagram mode: 'activity' (default) or 'state'") }
                    putJsonObject("title") { put("type", "string"); put("description", "Optional diagram title") }
                }
                putJsonArray("required") { add("source") }
            }
        ),
        McpToolInfo(
            name = "ink2svg",
            description = "Convert ink source to an SVG diagram showing story flow. Uses PlantUML to render.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code") }
                    putJsonObject("mode") { put("type", "string"); put("description", "Diagram mode: 'activity' (default) or 'state'") }
                    putJsonObject("title") { put("type", "string"); put("description", "Optional diagram title") }
                }
                putJsonArray("required") { add("source") }
            }
        ),
        McpToolInfo(
            name = "puml2svg",
            description = "Render raw PlantUML source to SVG. Use for custom diagrams.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("puml") { put("type", "string"); put("description", "PlantUML source code") }
                }
                putJsonArray("required") { add("puml") }
            }
        ),
        McpToolInfo(
            name = "ink_toc",
            description = "Generate a Table of Contents for an ink script with MCP tool links. Designed for LLMs with limited context — gives compact overview with mcp:tool_name references for each section.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code") }
                }
                putJsonArray("required") { add("source") }
            }
        ),
        McpToolInfo(
            name = "ink_toc_puml",
            description = "Generate a PlantUML TOC diagram with MCP tool links embedded in notes. Visual map of the story with clickable MCP references.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code") }
                    putJsonObject("title") { put("type", "string"); put("description", "Optional diagram title") }
                }
                putJsonArray("required") { add("source") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // LLM MODEL TOOLS (8)
    // ════════════════════════════════════════════════════════════════════

    private val llmTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "list_models",
            description = "List all available DictaLM 3.0 GGUF models with size, quantization, and VRAM requirements.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("vram_gb") { put("type", "integer"); put("description", "Available VRAM in GB to filter compatible models") }
                }
            }
        ),
        McpToolInfo(
            name = "load_model",
            description = "Download and load a DictaLM 3.0 GGUF model. Downloads from HuggingFace on first use.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("model_id") { put("type", "string"); put("description", "Model ID from list_models") }
                    putJsonObject("custom_repo") { put("type", "string"); put("description", "HuggingFace repo name for custom model") }
                }
            }
        ),
        McpToolInfo(
            name = "model_info",
            description = "Get information about the currently loaded LLM model.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
        ),
        McpToolInfo(
            name = "llm_chat",
            description = "Send a chat message to the loaded LLM or connected service.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("message") { put("type", "string"); put("description", "Chat message to send") }
                }
                putJsonArray("required") { add("message") }
            }
        ),
        McpToolInfo(
            name = "generate_ink",
            description = "Generate ink interactive fiction code from a natural language description.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("prompt") { put("type", "string"); put("description", "Description of the story/scene to generate") }
                }
                putJsonArray("required") { add("prompt") }
            }
        ),
        McpToolInfo(
            name = "review_ink",
            description = "Review ink code for syntax errors, logic issues, and improvements.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code to review") }
                }
                putJsonArray("required") { add("source") }
            }
        ),
        McpToolInfo(
            name = "translate_ink_hebrew",
            description = "Translate ink story text to Hebrew, preserving ink syntax.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "Ink source code to translate") }
                }
                putJsonArray("required") { add("source") }
            }
        ),
        McpToolInfo(
            name = "generate_compile_play",
            description = "Full pipeline: generate ink from prompt → compile → start session.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("prompt") { put("type", "string"); put("description", "Story description to generate and play") }
                }
                putJsonArray("required") { add("prompt") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // SERVICE TOOLS (2)
    // ════════════════════════════════════════════════════════════════════

    private val serviceTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "list_services",
            description = "List all available LLM service providers (Claude, Gemini, Copilot, Grok, Perplexity, Comet, OpenRouter, Together, Groq, LM Studio, Ollama).",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
        ),
        McpToolInfo(
            name = "connect_service",
            description = "Connect to an external LLM service provider. Once connected, llm_chat/generate_ink/review_ink will use this service.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("service_id") { put("type", "string"); put("description", "Service ID from list_services (e.g. 'claude', 'gemini', 'grok')") }
                    putJsonObject("api_key") { put("type", "string"); put("description", "API key (or set via env var)") }
                    putJsonObject("model") { put("type", "string"); put("description", "Model name override") }
                    putJsonObject("base_url") { put("type", "string"); put("description", "Base URL override") }
                }
                putJsonArray("required") { add("service_id") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // COLLABORATION TOOLS (2)
    // ════════════════════════════════════════════════════════════════════

    private val colabTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "collab_status",
            description = "List active collaboration documents with connected client counts.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
        ),
        McpToolInfo(
            name = "collab_info",
            description = "Get collaboration details for a specific document.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("doc_id") { put("type", "string"); put("description", "Document ID") }
                }
                putJsonArray("required") { add("doc_id") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // CALENDAR TOOLS (4)
    // ════════════════════════════════════════════════════════════════════

    private val calendarTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "create_event",
            description = "Create a story/game event in an ICS calendar. Categories: milestone, session, deadline, quest.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("calendar_id") { put("type", "string"); put("description", "Calendar ID (e.g. story name or doc ID)") }
                    putJsonObject("summary") { put("type", "string"); put("description", "Event title") }
                    putJsonObject("description") { put("type", "string"); put("description", "Event description") }
                    putJsonObject("dt_start") { put("type", "string"); put("description", "Start datetime (ISO-8601)") }
                    putJsonObject("dt_end") { put("type", "string"); put("description", "End datetime (ISO-8601, optional)") }
                    putJsonObject("category") { put("type", "string"); put("description", "Event category: milestone, session, deadline, quest") }
                }
                putJsonArray("required") { add("calendar_id"); add("summary"); add("dt_start") }
            }
        ),
        McpToolInfo(
            name = "list_events",
            description = "List events in a calendar, optionally filtered by category.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("calendar_id") { put("type", "string"); put("description", "Calendar ID") }
                    putJsonObject("category") { put("type", "string"); put("description", "Filter by category (optional)") }
                }
                putJsonArray("required") { add("calendar_id") }
            }
        ),
        McpToolInfo(
            name = "export_ics",
            description = "Export a calendar as ICS (iCalendar) format string.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("calendar_id") { put("type", "string"); put("description", "Calendar ID") }
                }
                putJsonArray("required") { add("calendar_id") }
            }
        ),
        McpToolInfo(
            name = "import_ics",
            description = "Import events from ICS content into a calendar.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("calendar_id") { put("type", "string"); put("description", "Calendar ID") }
                    putJsonObject("ics_content") { put("type", "string"); put("description", "ICS file content to import") }
                }
                putJsonArray("required") { add("calendar_id"); add("ics_content") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // VCARD PRINCIPAL TOOLS (4)
    // ════════════════════════════════════════════════════════════════════

    private val vcardTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "create_principal",
            description = "Create a user or LLM principal with vCard, folder mapping, and optional MCP URI credentials.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("id") { put("type", "string"); put("description", "Principal ID (username or model name)") }
                    putJsonObject("name") { put("type", "string"); put("description", "Display name") }
                    putJsonObject("email") { put("type", "string"); put("description", "Email (for human users)") }
                    putJsonObject("role") { put("type", "string"); put("description", "Role: 'edit' or 'view'") }
                    putJsonObject("is_llm") { put("type", "boolean"); put("description", "True for LLM model principals") }
                    putJsonObject("folder_path") { put("type", "string"); put("description", "Path to ink scripts folder (optional)") }
                }
                putJsonArray("required") { add("id"); add("name"); add("role") }
            }
        ),
        McpToolInfo(
            name = "list_principals",
            description = "List all user and LLM principals.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("is_llm") { put("type", "boolean"); put("description", "Filter: true for LLM only, false for human only") }
                }
            }
        ),
        McpToolInfo(
            name = "get_principal",
            description = "Get full details of a principal including vCard data.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("id") { put("type", "string"); put("description", "Principal ID") }
                }
                putJsonArray("required") { add("id") }
            }
        ),
        McpToolInfo(
            name = "delete_principal",
            description = "Delete a principal and its folder mapping.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("id") { put("type", "string"); put("description", "Principal ID to delete") }
                }
                putJsonArray("required") { add("id") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // AUTH TOOLS (2)
    // ════════════════════════════════════════════════════════════════════

    private val authTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "auth_status",
            description = "Get authentication system status: Keycloak config, LLM credential count, auth mode.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
        ),
        McpToolInfo(
            name = "create_llm_credential",
            description = "Create basicauth credentials for an LLM model. Returns model_name:token pair and MCP URI.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("model_name") { put("type", "string"); put("description", "LLM model name (e.g. 'claude-sonnet', 'gpt-4o')") }
                    putJsonObject("host") { put("type", "string"); put("description", "MCP server host (default: localhost)") }
                    putJsonObject("port") { put("type", "integer"); put("description", "MCP server port (default: 3001)") }
                }
                putJsonArray("required") { add("model_name") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // WEBDAV TOOLS (6)
    // ════════════════════════════════════════════════════════════════════

    private val webDavTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "webdav_list",
            description = "List files and directories at a WebDAV path. domain/user/shared/ is publicly readable, other paths require auth.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "Path under ink-scripts/ (e.g. 'example.com/alice/shared/')") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID for access control (optional for shared paths)") }
                }
                putJsonArray("required") { add("path") }
            }
        ),
        McpToolInfo(
            name = "webdav_get",
            description = "Get file content from a WebDAV path. Shared files are publicly readable.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "File path under ink-scripts/ (e.g. 'example.com/alice/script.ink')") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID for access control") }
                }
                putJsonArray("required") { add("path") }
            }
        ),
        McpToolInfo(
            name = "webdav_put",
            description = "Write file content to a WebDAV path. Requires edit role. Creates parent directories.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "File path under ink-scripts/") }
                    putJsonObject("content") { put("type", "string"); put("description", "File content to write") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID (must have edit role)") }
                }
                putJsonArray("required") { add("path"); add("content"); add("principal_id") }
            }
        ),
        McpToolInfo(
            name = "webdav_delete",
            description = "Delete a file or directory at a WebDAV path. Requires edit role.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "Path to delete") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID (must have edit role)") }
                }
                putJsonArray("required") { add("path"); add("principal_id") }
            }
        ),
        McpToolInfo(
            name = "webdav_mkdir",
            description = "Create a directory at a WebDAV path. Requires edit role.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "Directory path to create") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID (must have edit role)") }
                }
                putJsonArray("required") { add("path"); add("principal_id") }
            }
        ),
        McpToolInfo(
            name = "webdav_sync",
            description = "Sync files from a remote WebDAV server to a local path using Sardine client. Downloads .ink, .puml, .svg files.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("remote_url") { put("type", "string"); put("description", "Remote WebDAV URL to sync from") }
                    putJsonObject("local_path") { put("type", "string"); put("description", "Local path under ink-scripts/ to sync to") }
                    putJsonObject("username") { put("type", "string"); put("description", "Remote WebDAV username (optional)") }
                    putJsonObject("password") { put("type", "string"); put("description", "Remote WebDAV password (optional)") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID for local write access") }
                }
                putJsonArray("required") { add("remote_url"); add("local_path"); add("principal_id") }
            }
        ),
        McpToolInfo(
            name = "webdav_backup",
            description = "Create timestamped backup of master script files (.ink, .puml, .svg). Master = no timestamp (main merged copy). Backup = timestamp prefix.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("script_path") { put("type", "string"); put("description", "Script base path without extension (e.g. 'example.com/alice/script')") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID (must have edit role)") }
                }
                putJsonArray("required") { add("script_path"); add("principal_id") }
            }
        ),
        McpToolInfo(
            name = "webdav_list_backups",
            description = "List timestamped backups for a master file, newest first.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "Master file path (e.g. 'example.com/alice/script.ink')") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID for access control") }
                }
                putJsonArray("required") { add("path") }
            }
        ),
        McpToolInfo(
            name = "webdav_restore",
            description = "Restore a timestamped backup to the master file (main merged copy).",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("backup_path") { put("type", "string"); put("description", "Backup file path (e.g. 'example.com/alice/script/2026-02-26_14-30-00.000000000.ink')") }
                    putJsonObject("master_path") { put("type", "string"); put("description", "Master file path (e.g. 'example.com/alice/script.ink')") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID (must have edit role)") }
                }
                putJsonArray("required") { add("backup_path"); add("master_path"); add("principal_id") }
            }
        ),
        McpToolInfo(
            name = "webdav_working_copy",
            description = "Create an LLM working copy of a user's files. Origin = user's master files. Working copy = LLM's local edit copy. Yjs sync merges back if enabled.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("origin_path") { put("type", "string"); put("description", "Origin directory path (e.g. 'example.com/alice/')") }
                    putJsonObject("model_id") { put("type", "string"); put("description", "LLM model ID for the working copy") }
                    putJsonObject("principal_id") { put("type", "string"); put("description", "Principal ID (the LLM model)") }
                }
                putJsonArray("required") { add("origin_path"); add("model_id"); add("principal_id") }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // ASSET PIPELINE + FAKER TOOLS (6)
    // ════════════════════════════════════════════════════════════════════

    private val assetTools: List<McpToolInfo> = listOf(
        McpToolInfo(
            name = "resolve_emoji",
            description = "Resolve an emoji to its AssetCategory (animset, grip, mesh prefix, audio category).",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("emoji") { put("type", "string"); put("description", "Emoji character (e.g. 🗡️)") }
                }
                putJsonArray("required") { add("emoji") }
            }
        ),
        McpToolInfo(
            name = "parse_asset_tags",
            description = "Parse ink story tags (# mesh:🗡️, # anim:sword_slash, # voice:gandalf_en) into AssetRef list.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "Ink tags from ContinueResult") }
                }
                putJsonArray("required") { add("tags") }
            }
        ),
        McpToolInfo(
            name = "generate_items",
            description = "Generate an items MD table with emoji categories, faker-generated names, and POI formula columns (base_dmg + per_level * level = total_dmg).",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("seed") { put("type", "integer"); put("description", "Random seed for deterministic generation (default: 42)") }
                    putJsonObject("count") { put("type", "integer"); put("description", "Number of items to generate (default: 5)") }
                    putJsonObject("level") { put("type", "integer"); put("description", "Game level for per-level modifiers (default: 1)") }
                    putJsonObject("categories") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "Filter by category names (e.g. sword, potion)") }
                }
            }
        ),
        McpToolInfo(
            name = "generate_characters",
            description = "Generate a DnD characters MD table with faker names, classes, races, and stat formulas (STR, DEX, CON, INT, WIS, CHA, HP).",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("seed") { put("type", "integer"); put("description", "Random seed (default: 42)") }
                    putJsonObject("count") { put("type", "integer"); put("description", "Number of characters (default: 5)") }
                }
            }
        ),
        McpToolInfo(
            name = "generate_story_md",
            description = "Generate a full story Markdown document with characters table, items table, POI formulas, and ink code blocks.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("seed") { put("type", "integer"); put("description", "Random seed (default: 42)") }
                    putJsonObject("level") { put("type", "integer"); put("description", "Game level (default: 1)") }
                    putJsonObject("characters") { put("type", "integer"); put("description", "Number of characters (default: 5)") }
                    putJsonObject("items") { put("type", "integer"); put("description", "Number of items (default: 5)") }
                }
            }
        ),
        McpToolInfo(
            name = "evaluate_formulas",
            description = "Evaluate POI XLSX formulas in MD table cells. Cells starting with '=' are computed using Apache POI (supports arithmetic, SUM, IF, etc.).",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("markdown") { put("type", "string"); put("description", "Markdown content with tables containing formula cells") }
                }
                putJsonArray("required") { add("markdown") }
            }
        ),
        McpToolInfo(
            name = "list_emoji_groups",
            description = "List all Unicode emoji groups and subgroups available in the manifest. Returns group hierarchy from emoji-test.txt and UnicodeData.txt blocks.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("filter") { put("type", "string"); put("description", "Optional substring filter for group/subgroup names") }
                }
            }
        ),
        McpToolInfo(
            name = "resolve_unicode_block",
            description = "Resolve all symbols in a Unicode block (e.g., 'IPA Extensions', 'Currency Symbols') to their categories.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("block") { put("type", "string"); put("description", "Unicode block name (e.g., 'IPA Extensions', 'Smileys & Emotion', 'Currency Symbols')") }
                }
                putJsonArray("required") { add("block") }
            }
        ),
        McpToolInfo(
            name = "emit_asset_event",
            description = "Emit an asset event by processing ink tags through the EmojiAssetManifest and publishing to the AssetEventBus.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Ink story session ID") }
                    putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "Ink tags to process (e.g. '# mesh:🗡️')") }
                    putJsonObject("knot") { put("type", "string"); put("description", "Current knot/stitch path") }
                }
                putJsonArray("required") { add("session_id"); add("tags") }
            }
        ),
        McpToolInfo(
            name = "list_asset_events",
            description = "List recent asset events from the AssetEventBus, optionally filtered by session ID or channel.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("session_id") { put("type", "string"); put("description", "Filter by session ID") }
                    putJsonObject("channel") { put("type", "string"); put("description", "Filter by channel (e.g. 'ink/story/tags', 'ink/asset/load')") }
                    putJsonObject("limit") { put("type", "integer"); put("description", "Max events to return (default: 50)") }
                }
            }
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // DISPATCH
    // ════════════════════════════════════════════════════════════════════

    fun callTool(name: String, arguments: JsonObject?): McpToolResult {
        return try {
            when (name) {
                // Ink tools
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
                // Debug tools
                "start_debug" -> handleStartDebug(arguments)
                "add_breakpoint" -> handleAddBreakpoint(arguments)
                "remove_breakpoint" -> handleRemoveBreakpoint(arguments)
                "debug_step" -> handleDebugStep(arguments)
                "debug_continue" -> handleDebugContinue(arguments)
                "add_watch" -> handleAddWatch(arguments)
                "debug_inspect" -> handleDebugInspect(arguments)
                "debug_trace" -> handleDebugTrace(arguments)
                // Edit tools
                "parse_ink" -> handleParseInk(arguments)
                "get_section" -> handleGetSection(arguments)
                "replace_section" -> handleReplaceSection(arguments)
                "insert_section" -> handleInsertSection(arguments)
                "rename_section" -> handleRenameSection(arguments)
                "ink_stats" -> handleInkStats(arguments)
                // Ink+Markdown tools
                "parse_ink_md" -> handleParseInkMd(arguments)
                "render_ink_md" -> handleRenderInkMd(arguments)
                "compile_ink_md" -> handleCompileInkMd(arguments)
                // PlantUML diagram tools
                "ink2puml" -> handleInk2Puml(arguments)
                "ink2svg" -> handleInk2Svg(arguments)
                "puml2svg" -> handlePuml2Svg(arguments)
                "ink_toc" -> handleInkToc(arguments)
                "ink_toc_puml" -> handleInkTocPuml(arguments)
                // LLM tools
                "list_models" -> handleListModels(arguments)
                "load_model" -> handleLoadModel(arguments)
                "model_info" -> handleModelInfo()
                "llm_chat" -> handleLlmChat(arguments)
                "generate_ink" -> handleGenerateInk(arguments)
                "review_ink" -> handleReviewInk(arguments)
                "translate_ink_hebrew" -> handleTranslateHebrew(arguments)
                "generate_compile_play" -> handleGenerateCompilePlay(arguments)
                // Service tools
                "list_services" -> handleListServices()
                "connect_service" -> handleConnectService(arguments)
                // Collaboration tools
                "collab_status" -> handleCollabStatus()
                "collab_info" -> handleCollabInfo(arguments)
                // Calendar tools
                "create_event" -> handleCreateEvent(arguments)
                "list_events" -> handleListEvents(arguments)
                "export_ics" -> handleExportIcs(arguments)
                "import_ics" -> handleImportIcs(arguments)
                // vCard tools
                "create_principal" -> handleCreatePrincipal(arguments)
                "list_principals" -> handleListPrincipals(arguments)
                "get_principal" -> handleGetPrincipal(arguments)
                "delete_principal" -> handleDeletePrincipal(arguments)
                // Auth tools
                "auth_status" -> handleAuthStatus()
                "create_llm_credential" -> handleCreateLlmCredential(arguments)
                // WebDAV tools
                "webdav_list" -> handleWebDavList(arguments)
                "webdav_get" -> handleWebDavGet(arguments)
                "webdav_put" -> handleWebDavPut(arguments)
                "webdav_delete" -> handleWebDavDelete(arguments)
                "webdav_mkdir" -> handleWebDavMkDir(arguments)
                "webdav_sync" -> handleWebDavSync(arguments)
                "webdav_backup" -> handleWebDavBackup(arguments)
                "webdav_list_backups" -> handleWebDavListBackups(arguments)
                "webdav_restore" -> handleWebDavRestore(arguments)
                "webdav_working_copy" -> handleWebDavWorkingCopy(arguments)
                // Asset pipeline + faker tools
                "resolve_emoji" -> handleResolveEmoji(arguments)
                "parse_asset_tags" -> handleParseAssetTags(arguments)
                "generate_items" -> handleGenerateItems(arguments)
                "generate_characters" -> handleGenerateCharacters(arguments)
                "generate_story_md" -> handleGenerateStoryMd(arguments)
                "evaluate_formulas" -> handleEvaluateFormulas(arguments)
                "list_emoji_groups" -> handleListEmojiGroups(arguments)
                "resolve_unicode_block" -> handleResolveUnicodeBlock(arguments)
                "emit_asset_event" -> handleEmitAssetEvent(arguments)
                "list_asset_events" -> handleListAssetEvents(arguments)
                else -> errorResult("Unknown tool: $name")
            }
        } catch (e: Exception) {
            errorResult("${e::class.simpleName}: ${e.message}")
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // INK HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleCompile(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val result = engine.compile(source)
        return textResult(buildJsonObject {
            put("success", result.success)
            if (result.json != null) put("json", result.json)
            if (result.errors.isNotEmpty()) putJsonArray("errors") { result.errors.forEach { add(it) } }
            if (result.warnings.isNotEmpty()) putJsonArray("warnings") { result.warnings.forEach { add(it) } }
        }.toString())
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
            putAny("value", value)
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
            putAny("result", result)
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
        return textResult(buildJsonObject { put("result", engine.bidify(text)) }.toString())
    }

    private fun handleStripBidi(args: JsonObject?): McpToolResult {
        val text = args.requireString("text")
        return textResult(buildJsonObject { put("result", engine.stripBidi(text)) }.toString())
    }

    private fun handleBidifyJson(args: JsonObject?): McpToolResult {
        val json = args.requireString("json")
        return textResult(buildJsonObject { put("result", engine.bidifyJson(json)) }.toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // DEBUG HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleStartDebug(args: JsonObject?): McpToolResult {
        val dbg = debugEngine ?: return errorResult("Debug engine not configured")
        val sessionId = args.requireString("session_id")
        val info = dbg.startDebug(sessionId)
        return textResult(mapToJson(info).toString())
    }

    private fun handleAddBreakpoint(args: JsonObject?): McpToolResult {
        val dbg = debugEngine ?: return errorResult("Debug engine not configured")
        val sessionId = args.requireString("session_id")
        val type = args.requireString("type")
        val target = args.requireString("target")
        val bp = dbg.addBreakpoint(sessionId, type, target)
        return textResult(buildJsonObject {
            put("id", bp.id)
            put("type", bp.type)
            put("target", bp.target)
            put("enabled", bp.enabled)
        }.toString())
    }

    private fun handleRemoveBreakpoint(args: JsonObject?): McpToolResult {
        val dbg = debugEngine ?: return errorResult("Debug engine not configured")
        val sessionId = args.requireString("session_id")
        val bpId = args.requireString("breakpoint_id")
        val removed = dbg.removeBreakpoint(sessionId, bpId)
        return textResult("""{"ok":$removed,"breakpoint_id":"$bpId"}""")
    }

    private fun handleDebugStep(args: JsonObject?): McpToolResult {
        val dbg = debugEngine ?: return errorResult("Debug engine not configured")
        val sessionId = args.requireString("session_id")
        val result = dbg.step(sessionId)
        return textResult(buildJsonObject {
            put("text", result.text)
            put("can_continue", result.canContinue)
            putJsonArray("choices") {
                result.choices.forEach { c ->
                    addJsonObject { put("index", c.index); put("text", c.text) }
                }
            }
            putJsonArray("tags") { result.tags.forEach { add(it) } }
            put("step_number", result.stepNumber)
            put("is_paused", result.isPaused)
            if (result.hitBreakpoint != null) {
                putJsonObject("hit_breakpoint") {
                    put("id", result.hitBreakpoint.id)
                    put("type", result.hitBreakpoint.type)
                    put("target", result.hitBreakpoint.target)
                }
            }
            if (result.watchChanges.isNotEmpty()) {
                putJsonObject("watch_changes") {
                    result.watchChanges.forEach { (name, pair) ->
                        putJsonObject(name) {
                            putAny("old", pair.first)
                            putAny("new", pair.second)
                        }
                    }
                }
            }
        }.toString())
    }

    private fun handleDebugContinue(args: JsonObject?): McpToolResult {
        val dbg = debugEngine ?: return errorResult("Debug engine not configured")
        val sessionId = args.requireString("session_id")
        val maxSteps = args?.get("max_steps")?.jsonPrimitive?.intOrNull ?: 100
        val result = dbg.continueDebug(sessionId, maxSteps)
        return textResult(buildJsonObject {
            put("text", result.text)
            put("can_continue", result.canContinue)
            putJsonArray("choices") {
                result.choices.forEach { c ->
                    addJsonObject { put("index", c.index); put("text", c.text) }
                }
            }
            put("step_number", result.stepNumber)
            put("is_paused", result.isPaused)
            if (result.hitBreakpoint != null) {
                putJsonObject("hit_breakpoint") {
                    put("id", result.hitBreakpoint.id)
                    put("type", result.hitBreakpoint.type)
                    put("target", result.hitBreakpoint.target)
                }
            }
        }.toString())
    }

    private fun handleAddWatch(args: JsonObject?): McpToolResult {
        val dbg = debugEngine ?: return errorResult("Debug engine not configured")
        val sessionId = args.requireString("session_id")
        val varName = args.requireString("variable")
        val watch = dbg.addWatch(sessionId, varName)
        return textResult(buildJsonObject {
            put("variable", watch.name)
            putAny("current_value", watch.lastValue)
        }.toString())
    }

    private fun handleDebugInspect(args: JsonObject?): McpToolResult {
        val dbg = debugEngine ?: return errorResult("Debug engine not configured")
        val sessionId = args.requireString("session_id")
        val info = dbg.inspect(sessionId)
        return textResult(mapToJson(info).toString())
    }

    private fun handleDebugTrace(args: JsonObject?): McpToolResult {
        val dbg = debugEngine ?: return errorResult("Debug engine not configured")
        val sessionId = args.requireString("session_id")
        val lastN = args?.get("last_n")?.jsonPrimitive?.intOrNull ?: 50
        val trace = dbg.getTrace(sessionId, lastN)
        return textResult(buildJsonObject {
            putJsonArray("trace") {
                trace.forEach { entry -> add(mapToJson(entry)) }
            }
        }.toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // EDIT HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleParseInk(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val structure = editEngine.parse(source)
        return textResult(mcpJson.encodeToString(structure))
    }

    private fun handleGetSection(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val sectionName = args.requireString("section_name")
        val section = editEngine.getSection(source, sectionName)
            ?: return errorResult("Section not found: $sectionName")
        return textResult(mcpJson.encodeToString(section))
    }

    private fun handleReplaceSection(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val sectionName = args.requireString("section_name")
        val newContent = args.requireString("new_content")
        val result = editEngine.replaceSection(source, sectionName, newContent)
        return textResult(mcpJson.encodeToString(EditResponse(ok = true, source = result)))
    }

    private fun handleInsertSection(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val afterSection = args.requireString("after_section")
        val newContent = args.requireString("new_content")
        val result = editEngine.insertAfter(source, afterSection, newContent)
        return textResult(mcpJson.encodeToString(EditResponse(ok = true, source = result)))
    }

    private fun handleRenameSection(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val oldName = args.requireString("old_name")
        val newName = args.requireString("new_name")
        val result = editEngine.rename(source, oldName, newName)
        return textResult(mcpJson.encodeToString(EditResponse(ok = true, source = result)))
    }

    private fun handleInkStats(args: JsonObject?): McpToolResult {
        val source = args.requireString("source")
        val stats = editEngine.getStats(source)
        return textResult(mapToJson(stats).toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // INK+MARKDOWN HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleParseInkMd(args: JsonObject?): McpToolResult {
        val markdown = args.requireString("markdown")
        val result = inkMdEngine.parse(markdown)
        return textResult(buildJsonObject {
            putJsonArray("files") {
                result.files.forEach { f ->
                    addJsonObject {
                        put("name", f.name)
                        put("ink_source", f.inkSource)
                        put("header_level", f.headerLevel)
                    }
                }
            }
            putJsonArray("tables") {
                result.tables.forEach { t ->
                    addJsonObject {
                        put("name", t.name)
                        putJsonArray("columns") { t.columns.forEach { add(it) } }
                        putJsonArray("rows") {
                            t.rows.forEach { row ->
                                addJsonObject { row.forEach { (k, v) -> put(k, v) } }
                            }
                        }
                    }
                }
            }
        }.toString())
    }

    private fun handleRenderInkMd(args: JsonObject?): McpToolResult {
        val markdown = args.requireString("markdown")
        val rendered = inkMdEngine.render(markdown)
        return textResult(buildJsonObject {
            putJsonObject("files") {
                rendered.forEach { (name, source) -> put(name, source) }
            }
        }.toString())
    }

    private fun handleCompileInkMd(args: JsonObject?): McpToolResult {
        val markdown = args.requireString("markdown")
        val rendered = inkMdEngine.render(markdown)
        return textResult(buildJsonObject {
            putJsonArray("results") {
                rendered.forEach { (name, source) ->
                    val compileResult = engine.compile(source)
                    addJsonObject {
                        put("file", name)
                        put("success", compileResult.success)
                        if (compileResult.errors.isNotEmpty()) putJsonArray("errors") { compileResult.errors.forEach { add(it) } }
                        if (compileResult.warnings.isNotEmpty()) putJsonArray("warnings") { compileResult.warnings.forEach { add(it) } }
                    }
                }
            }
        }.toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // PLANTUML HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleInk2Puml(args: JsonObject?): McpToolResult {
        val puml = pumlOps ?: return errorResult("PlantUML engine not configured")
        val source = args.requireString("source")
        val mode = args?.get("mode")?.jsonPrimitive?.contentOrNull ?: "activity"
        val title = args?.get("title")?.jsonPrimitive?.contentOrNull ?: "Ink Story Flow"
        val result = puml.inkToPuml(source, mode, title)
        return textResult(buildJsonObject {
            put("puml", result)
            put("mode", mode)
        }.toString())
    }

    private fun handleInk2Svg(args: JsonObject?): McpToolResult {
        val puml = pumlOps ?: return errorResult("PlantUML engine not configured")
        val source = args.requireString("source")
        val mode = args?.get("mode")?.jsonPrimitive?.contentOrNull ?: "activity"
        val title = args?.get("title")?.jsonPrimitive?.contentOrNull ?: "Ink Story Flow"
        val pumlSrc = puml.inkToPuml(source, mode, title)
        val svg = puml.pumlToSvg(pumlSrc)
        return textResult(buildJsonObject {
            put("svg", svg)
            put("puml", pumlSrc)
            put("mode", mode)
        }.toString())
    }

    private fun handlePuml2Svg(args: JsonObject?): McpToolResult {
        val puml = pumlOps ?: return errorResult("PlantUML engine not configured")
        val pumlSrc = args.requireString("puml")
        val svg = puml.pumlToSvg(pumlSrc)
        return textResult(buildJsonObject {
            put("svg", svg)
        }.toString())
    }

    private fun handleInkToc(args: JsonObject?): McpToolResult {
        val puml = pumlOps ?: return errorResult("PlantUML engine not configured")
        val source = args.requireString("source")
        val toc = puml.generateToc(source)
        return textResult(buildJsonObject {
            put("toc", toc)
        }.toString())
    }

    private fun handleInkTocPuml(args: JsonObject?): McpToolResult {
        val puml = pumlOps ?: return errorResult("PlantUML engine not configured")
        val source = args.requireString("source")
        val title = args?.get("title")?.jsonPrimitive?.contentOrNull ?: "Ink Story TOC"
        val result = puml.generateTocPuml(source, title)
        return textResult(buildJsonObject {
            put("puml", result)
        }.toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // LLM HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleListModels(args: JsonObject?): McpToolResult {
        val vramGb = args?.get("vram_gb")?.jsonPrimitive?.intOrNull
        val models = if (vramGb != null) DictaLmConfig.modelsForVram(vramGb) else DictaLmConfig.MODELS
        val recommended = vramGb?.let { DictaLmConfig.recommendModel(it) }
        return textResult(buildJsonObject {
            putJsonArray("models") {
                models.forEach { m ->
                    addJsonObject {
                        put("id", m.id); put("parameters", m.parameters)
                        put("quantization", m.quantization); put("architecture", m.architecture)
                        put("size_gb", m.sizeGb); put("min_vram_gb", m.minVramGb)
                        put("jlama_compatible", m.jlamaCompatible); put("description", m.description)
                        put("url", m.url)
                    }
                }
            }
            if (recommended != null) put("recommended", recommended.id)
            if (vramGb != null) put("vram_filter_gb", vramGb)
        }.toString())
    }

    private fun handleLoadModel(args: JsonObject?): McpToolResult {
        val llm = llmOps ?: return errorResult("LLM engine not configured")
        val customRepo = args?.get("custom_repo")?.jsonPrimitive?.contentOrNull
        val modelId = args?.get("model_id")?.jsonPrimitive?.contentOrNull

        val loadedId = if (customRepo != null) {
            llm.loadCustomModel(customRepo)
        } else if (modelId != null) {
            val model = DictaLmConfig.findModel(modelId)
                ?: return errorResult("Unknown model: $modelId. Use list_models to see options.")
            if (!model.jlamaCompatible) {
                return errorResult("Model '$modelId' uses ${model.architecture} — NOT JLama compatible. Use Ollama/vLLM.")
            }
            llm.loadModel(modelId)
        } else {
            return errorResult("Provide 'model_id' or 'custom_repo'")
        }

        return textResult(buildJsonObject {
            put("ok", true); put("model_id", loadedId); put("message", "Model loaded and ready")
        }.toString())
    }

    private fun handleModelInfo(): McpToolResult {
        val llm = llmOps ?: return errorResult("LLM engine not configured")
        return textResult(mapToJson(llm.getModelInfo()).toString())
    }

    private fun handleLlmChat(args: JsonObject?): McpToolResult {
        val llm = llmOps ?: return errorResult("No LLM connected. Use connect_service or load_model first.")
        val message = args.requireString("message")
        val response = llm.chat(message)
        return textResult(buildJsonObject { put("response", response) }.toString())
    }

    private fun handleGenerateInk(args: JsonObject?): McpToolResult {
        val llm = llmOps ?: return errorResult("No LLM connected.")
        val prompt = args.requireString("prompt")
        val inkCode = llm.generateInk(prompt)
        val compileResult = try { engine.compile(inkCode) } catch (_: Exception) { null }
        return textResult(buildJsonObject {
            put("ink_source", inkCode)
            put("compiles", compileResult?.success ?: false)
            if (compileResult?.errors?.isNotEmpty() == true) {
                putJsonArray("compile_errors") { compileResult.errors.forEach { add(it) } }
            }
        }.toString())
    }

    private fun handleReviewInk(args: JsonObject?): McpToolResult {
        val llm = llmOps ?: return errorResult("No LLM connected.")
        val source = args.requireString("source")
        val review = llm.reviewInk(source)
        return textResult(buildJsonObject { put("review", review) }.toString())
    }

    private fun handleTranslateHebrew(args: JsonObject?): McpToolResult {
        val llm = llmOps ?: return errorResult("No LLM connected.")
        val source = args.requireString("source")
        val translated = llm.translateToHebrew(source)
        return textResult(buildJsonObject { put("translated_ink", translated) }.toString())
    }

    private fun handleGenerateCompilePlay(args: JsonObject?): McpToolResult {
        val llm = llmOps ?: return errorResult("No LLM connected.")
        val prompt = args.requireString("prompt")
        val inkCode = llm.generateInk(prompt)
        val compileResult = engine.compile(inkCode)
        if (!compileResult.success) {
            return textResult(buildJsonObject {
                put("stage", "compile_failed")
                put("ink_source", inkCode)
                putJsonArray("errors") { compileResult.errors.forEach { add(it) } }
            }.toString())
        }
        val (sessionId, storyResult) = engine.startSession(inkCode)
        return textResult(buildJsonObject {
            put("stage", "playing")
            put("ink_source", inkCode)
            put("session_id", sessionId)
            put("text", storyResult.text)
            put("can_continue", storyResult.canContinue)
            putJsonArray("choices") {
                storyResult.choices.forEach { c ->
                    addJsonObject { put("index", c.index); put("text", c.text) }
                }
            }
        }.toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // SERVICE HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleListServices(): McpToolResult {
        val svc = serviceOps ?: return errorResult("Service management not available")
        val services = svc.listServices()
        return textResult(buildJsonObject {
            putJsonArray("services") {
                services.forEach { s -> add(mapToJson(s)) }
            }
            svc.connectedServiceId?.let { put("connected_service", it) }
        }.toString())
    }

    private fun handleConnectService(args: JsonObject?): McpToolResult {
        val svc = serviceOps ?: return errorResult("Service management not available")
        val serviceId = args.requireString("service_id")
        val apiKey = args?.get("api_key")?.jsonPrimitive?.contentOrNull
        val model = args?.get("model")?.jsonPrimitive?.contentOrNull
        val baseUrl = args?.get("base_url")?.jsonPrimitive?.contentOrNull

        val result = svc.connect(serviceId, apiKey, model, baseUrl)
        return textResult(mapToJson(result).toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // COLLABORATION HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleCollabStatus(): McpToolResult {
        val colab: McpColabOps = colabEngine ?: return errorResult("Collaboration not enabled")
        val docs = colab.listDocuments()
        return textResult(buildJsonObject {
            putJsonArray("documents") {
                docs.forEach { d -> add(mapToJson(d)) }
            }
            put("total_clients", colab.totalClients)
        }.toString())
    }

    private fun handleCollabInfo(args: JsonObject?): McpToolResult {
        val colab: McpColabOps = colabEngine ?: return errorResult("Collaboration not enabled")
        val docId = args.requireString("doc_id")
        val info = colab.getDocumentInfo(docId)
            ?: return errorResult("Document not found: $docId")
        return textResult(mapToJson(info).toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // CALENDAR HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleCreateEvent(args: JsonObject?): McpToolResult {
        val cal = calendarEngine ?: return errorResult("Calendar engine not enabled")
        val result = cal.createEvent(
            calId = args.requireString("calendar_id"),
            summary = args.requireString("summary"),
            description = args?.get("description")?.jsonPrimitive?.contentOrNull,
            dtStart = args.requireString("dt_start"),
            dtEnd = args?.get("dt_end")?.jsonPrimitive?.contentOrNull,
            category = args?.get("category")?.jsonPrimitive?.contentOrNull
        )
        return textResult(mapToJson(result).toString())
    }

    private fun handleListEvents(args: JsonObject?): McpToolResult {
        val cal = calendarEngine ?: return errorResult("Calendar engine not enabled")
        val events = cal.listEvents(
            args.requireString("calendar_id"),
            args?.get("category")?.jsonPrimitive?.contentOrNull
        )
        return textResult(buildJsonObject {
            putJsonArray("events") { events.forEach { add(mapToJson(it)) } }
        }.toString())
    }

    private fun handleExportIcs(args: JsonObject?): McpToolResult {
        val cal = calendarEngine ?: return errorResult("Calendar engine not enabled")
        val ics = cal.exportIcs(args.requireString("calendar_id"))
        return textResult(buildJsonObject { put("ics", ics) }.toString())
    }

    private fun handleImportIcs(args: JsonObject?): McpToolResult {
        val cal = calendarEngine ?: return errorResult("Calendar engine not enabled")
        val result = cal.importIcs(
            args.requireString("calendar_id"),
            args.requireString("ics_content")
        )
        return textResult(mapToJson(result).toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // VCARD HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleCreatePrincipal(args: JsonObject?): McpToolResult {
        val vc = vcardEngine ?: return errorResult("vCard engine not enabled")
        val result = vc.createPrincipal(
            id = args.requireString("id"),
            name = args.requireString("name"),
            email = args?.get("email")?.jsonPrimitive?.contentOrNull,
            role = args.requireString("role"),
            isLlm = args?.get("is_llm")?.jsonPrimitive?.booleanOrNull ?: false,
            folderPath = args?.get("folder_path")?.jsonPrimitive?.contentOrNull
        )
        return textResult(mapToJson(result).toString())
    }

    private fun handleListPrincipals(args: JsonObject?): McpToolResult {
        val vc = vcardEngine ?: return errorResult("vCard engine not enabled")
        val isLlm = args?.get("is_llm")?.jsonPrimitive?.booleanOrNull
        val principals = vc.listPrincipals(isLlm)
        return textResult(buildJsonObject {
            putJsonArray("principals") { principals.forEach { add(mapToJson(it)) } }
        }.toString())
    }

    private fun handleGetPrincipal(args: JsonObject?): McpToolResult {
        val vc = vcardEngine ?: return errorResult("vCard engine not enabled")
        val info = vc.getPrincipal(args.requireString("id"))
            ?: return errorResult("Principal not found")
        return textResult(mapToJson(info).toString())
    }

    private fun handleDeletePrincipal(args: JsonObject?): McpToolResult {
        val vc = vcardEngine ?: return errorResult("vCard engine not enabled")
        val result = vc.deletePrincipal(args.requireString("id"))
        return textResult(mapToJson(result).toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // AUTH HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleAuthStatus(): McpToolResult {
        val auth = authEngine ?: return errorResult("Auth engine not enabled")
        return textResult(mapToJson(auth.getAuthStatus()).toString())
    }

    private fun handleCreateLlmCredential(args: JsonObject?): McpToolResult {
        val auth = authEngine ?: return errorResult("Auth engine not enabled")
        val result = auth.createLlmCredential(
            modelName = args.requireString("model_name"),
            host = args?.get("host")?.jsonPrimitive?.contentOrNull ?: "localhost",
            port = args?.get("port")?.jsonPrimitive?.intOrNull ?: 3001,
            jcard = args?.get("jcard")?.jsonPrimitive?.contentOrNull
        )
        return textResult(mapToJson(result).toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // WEBDAV HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleWebDavList(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val path = args.requireString("path")
        val principalId = args?.get("principal_id")?.jsonPrimitive?.contentOrNull

        if (!dav.canAccess(principalId, path, write = false)) {
            return errorResult("Access denied: $path")
        }

        val files = dav.listFiles(path)
        return textResult(buildJsonObject {
            put("path", path)
            put("is_shared", dav.isSharedPath(path))
            putJsonArray("files") { files.forEach { add(mapToJson(it)) } }
        }.toString())
    }

    private fun handleWebDavGet(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val path = args.requireString("path")
        val principalId = args?.get("principal_id")?.jsonPrimitive?.contentOrNull

        if (!dav.canAccess(principalId, path, write = false)) {
            return errorResult("Access denied: $path")
        }

        val file = dav.getFile(path) ?: return errorResult("Not found: $path")
        return textResult(mapToJson(file).toString())
    }

    private fun handleWebDavPut(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val path = args.requireString("path")
        val content = args.requireString("content")
        val principalId = args.requireString("principal_id")

        if (!dav.canAccess(principalId, path, write = true)) {
            return errorResult("Write access denied: $path")
        }

        val result = dav.putFile(path, content)
        return textResult(mapToJson(result).toString())
    }

    private fun handleWebDavDelete(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val path = args.requireString("path")
        val principalId = args.requireString("principal_id")

        if (!dav.canAccess(principalId, path, write = true)) {
            return errorResult("Delete access denied: $path")
        }

        val result = dav.deleteFile(path)
        return textResult(mapToJson(result).toString())
    }

    private fun handleWebDavMkDir(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val path = args.requireString("path")
        val principalId = args.requireString("principal_id")

        if (!dav.canAccess(principalId, path, write = true)) {
            return errorResult("Mkdir access denied: $path")
        }

        val result = dav.mkDir(path)
        return textResult(mapToJson(result).toString())
    }

    private fun handleWebDavSync(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val remoteUrl = args.requireString("remote_url")
        val localPath = args.requireString("local_path")
        val principalId = args.requireString("principal_id")
        val username = args?.get("username")?.jsonPrimitive?.contentOrNull
        val password = args?.get("password")?.jsonPrimitive?.contentOrNull

        if (!dav.canAccess(principalId, localPath, write = true)) {
            return errorResult("Write access denied for sync target: $localPath")
        }

        val result = dav.syncFromRemote(remoteUrl, localPath, username, password)
        return textResult(mapToJson(result).toString())
    }

    private fun handleWebDavBackup(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val scriptPath = args.requireString("script_path")
        val principalId = args.requireString("principal_id")

        if (!dav.canAccess(principalId, scriptPath, write = true)) {
            return errorResult("Backup access denied: $scriptPath")
        }

        val result = dav.createBackupSet(scriptPath)
        return textResult(mapToJson(result).toString())
    }

    private fun handleWebDavListBackups(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val path = args.requireString("path")
        val principalId = args?.get("principal_id")?.jsonPrimitive?.contentOrNull

        if (!dav.canAccess(principalId, path, write = false)) {
            return errorResult("Access denied: $path")
        }

        val backups = dav.listBackups(path)
        return textResult(buildJsonObject {
            put("path", path)
            putJsonArray("backups") { backups.forEach { add(mapToJson(it)) } }
        }.toString())
    }

    private fun handleWebDavRestore(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val backupPath = args.requireString("backup_path")
        val masterPath = args.requireString("master_path")
        val principalId = args.requireString("principal_id")

        if (!dav.canAccess(principalId, masterPath, write = true)) {
            return errorResult("Restore access denied: $masterPath")
        }

        val result = dav.restoreBackup(backupPath, masterPath)
        return textResult(mapToJson(result).toString())
    }

    private fun handleWebDavWorkingCopy(args: JsonObject?): McpToolResult {
        val dav = webDavEngine ?: return errorResult("WebDAV engine not enabled")
        val originPath = args.requireString("origin_path")
        val modelId = args.requireString("model_id")
        val principalId = args.requireString("principal_id")

        if (!dav.canAccess(principalId, originPath, write = false)) {
            return errorResult("Read access denied for working copy: $originPath")
        }

        val result = dav.createWorkingCopy(originPath, modelId)
        return textResult(mapToJson(result).toString())
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════

    private fun continueResultJson(sessionId: String, result: McpInkOps.ContinueResult) = buildJsonObject {
        put("session_id", sessionId)
        put("text", result.text)
        put("can_continue", result.canContinue)
        putJsonArray("choices") {
            result.choices.forEach { c ->
                addJsonObject {
                    put("index", c.index); put("text", c.text)
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

    /** Put any value into a JsonObjectBuilder */
    private fun JsonObjectBuilder.putAny(key: String, value: Any?) {
        when (value) {
            null -> put(key, JsonNull)
            is Boolean -> put(key, value)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Double -> put(key, value)
            is String -> put(key, value)
            else -> put(key, value.toString())
        }
    }

    /** Convert a Map to JsonObject */
    private fun mapToJson(map: Map<String, Any?>): JsonObject = buildJsonObject {
        map.forEach { (k, v) ->
            when (v) {
                null -> put(k, JsonNull)
                is Boolean -> put(k, v)
                is Int -> put(k, v)
                is Long -> put(k, v)
                is Double -> put(k, v)
                is String -> put(k, v)
                is List<*> -> putJsonArray(k) {
                    v.forEach { item ->
                        when (item) {
                            is String -> add(item)
                            is Int -> add(item)
                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                add(mapToJson(item as Map<String, Any?>))
                            }
                            else -> add(item.toString())
                        }
                    }
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    put(k, mapToJson(v as Map<String, Any?>))
                }
                else -> put(k, v.toString())
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ASSET PIPELINE + FAKER HANDLERS
    // ════════════════════════════════════════════════════════════════════

    private fun handleResolveEmoji(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val emoji = args.requireString("emoji")
        val result = assets.resolveEmoji(emoji) ?: return errorResult("Unknown emoji: $emoji")
        return textResult(mapToJson(result).toString())
    }

    private fun handleParseAssetTags(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val tagsArray = args?.get("tags")?.jsonArray
            ?: return errorResult("Missing required parameter: tags")
        val tags = tagsArray.map { it.jsonPrimitive.content }
        val refs = assets.parseAssetTags(tags)
        return textResult(buildJsonArray {
            for (ref in refs) add(mapToJson(ref))
        }.toString())
    }

    private fun handleGenerateItems(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val result = assets.generateItems(
            seed = args?.get("seed")?.jsonPrimitive?.longOrNull ?: 42L,
            count = args?.get("count")?.jsonPrimitive?.intOrNull ?: 5,
            level = args?.get("level")?.jsonPrimitive?.intOrNull ?: 1,
            categories = args?.get("categories")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        )
        return textResult(mapToJson(result).toString())
    }

    private fun handleGenerateCharacters(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val result = assets.generateCharacters(
            seed = args?.get("seed")?.jsonPrimitive?.longOrNull ?: 42L,
            count = args?.get("count")?.jsonPrimitive?.intOrNull ?: 5
        )
        return textResult(mapToJson(result).toString())
    }

    private fun handleGenerateStoryMd(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val seed = args?.get("seed")?.jsonPrimitive?.longOrNull ?: 42L
        val level = args?.get("level")?.jsonPrimitive?.intOrNull ?: 1
        val charCount = args?.get("characters")?.jsonPrimitive?.intOrNull ?: 5
        val itemCount = args?.get("items")?.jsonPrimitive?.intOrNull ?: 5
        val markdown = assets.generateStoryMd(seed, level, maxOf(charCount, itemCount))
        return textResult(markdown)
    }

    private fun handleEvaluateFormulas(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val markdown = args.requireString("markdown")
        val parsed = inkMdEngine.parse(markdown)
        val evaluatedTables = assets.evaluateFormulas(parsed.tables)
        return textResult(buildJsonArray {
            for (table in evaluatedTables) add(mapToJson(table))
        }.toString())
    }

    private fun handleListEmojiGroups(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val filter = args?.get("filter")?.jsonPrimitive?.contentOrNull
        val result = assets.listEmojiGroups(filter)
        return textResult(mapToJson(result).toString())
    }

    private fun handleResolveUnicodeBlock(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val block = args?.get("block")?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: block")
        val result = assets.resolveUnicodeBlock(block)
        return textResult(mapToJson(result).toString())
    }

    private fun handleEmitAssetEvent(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val sessionId = args?.get("session_id")?.jsonPrimitive?.contentOrNull
            ?: return errorResult("Missing required parameter: session_id")
        val tags = args["tags"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: return errorResult("Missing required parameter: tags")
        val knot = args["knot"]?.jsonPrimitive?.contentOrNull ?: ""
        val result = assets.emitAssetEvent(sessionId, tags, knot)
        return textResult(mapToJson(result).toString())
    }

    private fun handleListAssetEvents(args: JsonObject?): McpToolResult {
        val assets = assetOps ?: return errorResult("Asset engine not configured")
        val sessionId = args?.get("session_id")?.jsonPrimitive?.contentOrNull
        val channel = args?.get("channel")?.jsonPrimitive?.contentOrNull
        val limit = args?.get("limit")?.jsonPrimitive?.intOrNull ?: 50
        val result = assets.listAssetEvents(sessionId, channel, limit)
        return textResult(mapToJson(result).toString())
    }
}
