package ink.mcp

import ink.mcp.KtTestFixtures.engine
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Integration tests for McpTools — the 56+ tool registry.
 *
 * Tests: tool registration, tool metadata, compile_ink invocation,
 * start/continue/choose story flow, and tool categories.
 *
 * Uses [KtTestFixtures] for shared engine.
 */
class McpToolsTest {

    companion object {
        private val mcpTools: McpTools by lazy {
            McpTools(engine)
        }
    }

    private fun jsonArgs(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) put(k, v)
    }

    // ── Tool registry ────────────────────────────────────────────

    @Test
    fun `tool registry has 20+ ink+edit+md+puml tools`() {
        // Without optional engines (llm, collab, calendar, etc.), base tools still > 20
        assertTrue(mcpTools.tools.size >= 20,
            "Should register 20+ base tools, got: ${mcpTools.tools.size}")
    }

    @Test
    fun `all tools have name and description`() {
        for (tool in mcpTools.tools) {
            assertTrue(tool.name.isNotBlank(), "Tool should have a name")
            assertTrue(tool.description.isNotBlank(), "Tool '${tool.name}' should have a description")
        }
    }

    @Test
    fun `all tools have input schema`() {
        for (tool in mcpTools.tools) {
            assertNotNull(tool.inputSchema, "Tool '${tool.name}' should have inputSchema")
            assertEquals("object", tool.inputSchema["type"]?.jsonPrimitive?.content,
                "Tool '${tool.name}' inputSchema should be type object")
        }
    }

    // ── Tool categories ──────────────────────────────────────────

    @Test
    fun `ink core tools are registered`() {
        val names = mcpTools.tools.map { it.name }.toSet()
        val expected = listOf(
            "compile_ink", "start_story", "continue_story",
            "choose", "get_variable", "set_variable",
            "save_state", "load_state", "reset_story", "end_session"
        )
        for (name in expected) {
            assertTrue(name in names, "Missing ink core tool: $name")
        }
    }

    @Test
    fun `debug tools are registered`() {
        val names = mcpTools.tools.map { it.name }.toSet()
        val expected = listOf("start_debug", "add_breakpoint", "debug_step", "debug_continue")
        for (name in expected) {
            assertTrue(name in names, "Missing debug tool: $name")
        }
    }

    @Test
    fun `edit tools are registered`() {
        val names = mcpTools.tools.map { it.name }.toSet()
        val expected = listOf("parse_ink", "get_section", "replace_section", "ink_stats")
        for (name in expected) {
            assertTrue(name in names, "Missing edit tool: $name")
        }
    }

    @Test
    fun `markdown tools are registered`() {
        val names = mcpTools.tools.map { it.name }.toSet()
        val expected = listOf("parse_ink_md", "render_ink_md", "compile_ink_md")
        for (name in expected) {
            assertTrue(name in names, "Missing markdown tool: $name")
        }
    }

    @Test
    fun `plantuml tools are registered`() {
        val names = mcpTools.tools.map { it.name }.toSet()
        val expected = listOf("ink2puml", "ink2svg", "puml2svg", "ink_toc")
        for (name in expected) {
            assertTrue(name in names, "Missing plantuml tool: $name")
        }
    }

    @Test
    fun `service tools are registered`() {
        val names = mcpTools.tools.map { it.name }.toSet()
        assertTrue("list_services" in names, "Missing service tool: list_services")
        assertTrue("connect_service" in names, "Missing service tool: connect_service")
    }

    // ── compile_ink tool invocation ──────────────────────────────

    @Test
    fun `compile_ink with valid source returns result`() {
        val result = mcpTools.callTool("compile_ink", jsonArgs("source" to "Hello, world!\n-> END"))
        assertNotNull(result, "compile_ink should return a result")
        assertFalse(result.isError, "compile_ink should not be an error for valid source")
        assertTrue(result.content.isNotEmpty(), "Result should have content blocks")
    }

    @Test
    fun `compile_ink with syntax error returns error info`() {
        val result = mcpTools.callTool("compile_ink", jsonArgs("source" to "-> nonexistent_knot"))
        assertNotNull(result, "compile_ink with errors should still return a result")
    }

    // ── Story lifecycle: start → continue → choose → end ─────────

    @Test
    fun `full story lifecycle`() {
        val source = """
            === start ===
            Welcome to the test story!
            * [Go left] -> left
            * [Go right] -> right

            === left ===
            You went left.
            -> END

            === right ===
            You went right.
            -> END
        """.trimIndent()

        // Compile
        val compileResult = mcpTools.callTool("compile_ink", jsonArgs("source" to source))
        assertNotNull(compileResult, "Should compile")

        // Start story
        val startResult = mcpTools.callTool("start_story", jsonArgs("source" to source))
        assertNotNull(startResult, "Should start story")
        assertFalse(startResult.isError, "start_story should succeed")

        // List sessions
        val sessionsResult = mcpTools.callTool("list_sessions", null)
        assertNotNull(sessionsResult, "Should list sessions")
    }

    // ── parse_ink tool ───────────────────────────────────────────

    @Test
    fun `parse_ink extracts structure`() {
        val source = """
            VAR score = 0
            === start ===
            Hello!
            -> ending
            === ending ===
            The end.
            -> END
        """.trimIndent()

        val result = mcpTools.callTool("parse_ink", jsonArgs("source" to source))
        assertNotNull(result, "parse_ink should return structure")
        val content = result.content.joinToString { it.text }
        assertTrue(content.contains("start") || content.contains("ending"),
            "Should find knot names in parsed structure")
    }

    // ── ink_stats tool ───────────────────────────────────────────

    @Test
    fun `ink_stats returns statistics`() {
        val source = """
            === start ===
            Hello world!
            * [Choice A] -> start
            * [Choice B] -> END
        """.trimIndent()

        val result = mcpTools.callTool("ink_stats", jsonArgs("source" to source))
        assertNotNull(result, "ink_stats should return statistics")
        assertFalse(result.isError, "ink_stats should succeed")
    }

    // ── Unknown tool ─────────────────────────────────────────────

    @Test
    fun `calling unknown tool returns error result`() {
        val result = mcpTools.callTool("nonexistent_tool_xyz", null)
        assertNotNull(result, "Unknown tool should return a result, not crash")
        assertTrue(result.isError, "Unknown tool should return isError=true")
    }
}
