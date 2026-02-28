package ink.mcp

import org.slf4j.LoggerFactory

/**
 * SillyTavern integration configuration.
 *
 * SillyTavern is an LLM frontend that supports multiple backends (LM Studio,
 * Ollama, OpenAI, etc.) and can be extended with custom extensions.
 *
 * Integration approach:
 *   1. SillyTavern connects to LM Studio / Ollama for LLM inference
 *   2. Inky MCP server provides ink tools via HTTP REST or MCP SSE
 *   3. SillyTavern Extension fetches ink story state from MCP server
 *   4. Ink stories map to SillyTavern scenarios/world info
 *
 * SillyTavern API:
 *   - Default: http://localhost:8000
 *   - Uses OpenAI-compatible API format to connect to backends
 *   - Extensions load from: SillyTavern/public/scripts/extensions/third-party/
 *
 * Character Card mapping:
 *   - Ink story → SillyTavern character card
 *   - Knots → World info entries (lore)
 *   - Choices → Injected as system messages
 *   - Variables → Tracked in chat metadata
 *
 * @see <a href="https://docs.sillytavern.app/">SillyTavern Docs</a>
 */
object SillyTavernConfig {

    private val log = LoggerFactory.getLogger(SillyTavernConfig::class.java)

    data class StCharacterCard(
        val name: String,
        val description: String,
        val personality: String,
        val scenario: String,
        val firstMessage: String,
        val mesExample: String = "",
        val systemPrompt: String = "",
        val creatorNotes: String = "",
        val tags: List<String> = emptyList()
    )

    /**
     * Convert an ink story to a SillyTavern character card.
     *
     * Maps ink structure to character card fields:
     *   - Global tags → description and personality
     *   - First knot text → first_message
     *   - Story choices → scenario (as interactive fiction prompt)
     *   - VAR declarations → system prompt context
     */
    fun inkToCharacterCard(
        storyName: String,
        inkSource: String,
        inkEngine: InkEngine? = null
    ): StCharacterCard {
        val editEngine = EditEngine()
        val structure = editEngine.parse(inkSource)

        // Extract metadata from global tags
        val globalVars = structure.variables.map { "${it.name} = ${it.initialValue}" }
        val knots = structure.sections.filter { it.type == "knot" }.map { it.name }

        // Try to compile and get initial text
        var firstText = "Welcome to $storyName. This is an interactive fiction story powered by ink."
        var choices = listOf<String>()

        if (inkEngine != null) {
            try {
                val (sessionId, result) = inkEngine.startSession(inkSource)
                firstText = result.text.ifEmpty { firstText }
                choices = result.choices.map { it.text }
                inkEngine.endSession(sessionId)
            } catch (_: Exception) {}
        }

        val description = buildString {
            appendLine("An interactive fiction story written in ink scripting language.")
            if (knots.isNotEmpty()) {
                appendLine("Story sections: ${knots.joinToString(", ")}")
            }
            if (globalVars.isNotEmpty()) {
                appendLine("Variables: ${globalVars.joinToString(", ")}")
            }
        }

        val scenario = buildString {
            appendLine("This is an interactive fiction game. Present the story text to the user.")
            appendLine("When choices are available, present them as numbered options.")
            appendLine("The user responds by choosing a number or describing their action.")
            if (choices.isNotEmpty()) {
                appendLine("\nCurrent choices:")
                choices.forEachIndexed { i, c -> appendLine("${i + 1}. $c") }
            }
        }

        val systemPrompt = buildString {
            appendLine("You are narrating an interactive fiction story. The story is written in ink format.")
            appendLine("Present the story text naturally, offer choices when available.")
            appendLine("Track the story state and respond appropriately to user choices.")
            if (globalVars.isNotEmpty()) {
                appendLine("\nStory variables: ${globalVars.joinToString("; ")}")
            }
        }

        return StCharacterCard(
            name = storyName,
            description = description.trim(),
            personality = "Interactive fiction narrator. Atmospheric, engaging, responsive to choices.",
            scenario = scenario.trim(),
            firstMessage = firstText.trim(),
            systemPrompt = systemPrompt.trim(),
            creatorNotes = "Generated from ink source via Inky MCP Server",
            tags = listOf("ink", "interactive-fiction", "game") + knots.take(5)
        )
    }

    /**
     * Generate a SillyTavern extension manifest for ink integration.
     */
    fun extensionManifest(): Map<String, Any> = mapOf(
        "display_name" to "Inky Interactive Fiction",
        "loading_order" to 1,
        "requires" to emptyList<String>(),
        "optional" to listOf("translate"),
        "js" to "index.js",
        "css" to "style.css",
        "author" to "Inky MCP",
        "version" to "0.2.0",
        "homePage" to "https://github.com/inkle/inky",
        "auto_update" to true
    )

    /**
     * Connection presets for SillyTavern + LM Studio + Inky MCP.
     */
    fun connectionPresets(): Map<String, Any> = mapOf(
        "silly_tavern" to mapOf(
            "url" to "http://localhost:8000",
            "description" to "SillyTavern default port"
        ),
        "lm_studio" to mapOf(
            "url" to "http://localhost:1234/v1",
            "api_type" to "openai",
            "description" to "LM Studio OpenAI-compatible API"
        ),
        "inky_mcp" to mapOf(
            "url" to "http://localhost:3001",
            "api_type" to "mcp",
            "sse_endpoint" to "/sse",
            "message_endpoint" to "/message",
            "rest_api" to "/api",
            "collab_ws" to "ws://localhost:3001/collab",
            "description" to "Inky MCP server — ink compilation, playback, debug, collab"
        ),
        "ollama" to mapOf(
            "url" to "http://localhost:11434/v1",
            "api_type" to "openai",
            "description" to "Ollama local model server"
        )
    )
}
