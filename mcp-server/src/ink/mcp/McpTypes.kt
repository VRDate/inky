package ink.mcp

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * MCP (Model Context Protocol) JSON-RPC types.
 * Implements the 2024-11-05 spec for tool serving via SSE + stdio transport.
 */

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class McpServerInfo(
    val name: String,
    val version: String
)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String = "2024-11-05",
    val capabilities: McpCapabilities = McpCapabilities(),
    val serverInfo: McpServerInfo
)

@Serializable
data class McpCapabilities(
    val tools: McpToolCapability = McpToolCapability()
)

@Serializable
data class McpToolCapability(
    val listChanged: Boolean = false
)

@Serializable
data class McpToolInfo(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

@Serializable
data class McpToolsListResult(
    val tools: List<McpToolInfo>
)

@Serializable
data class McpContentBlock(
    val type: String = "text",
    val text: String
)

@Serializable
data class McpToolResult(
    val content: List<McpContentBlock>,
    val isError: Boolean = false
)

val mcpJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}
