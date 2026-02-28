/**
 * @inky/common — Shared utilities for the Inky ink editor ecosystem.
 *
 * Modules:
 *   - mcp-client:        MCP JSON-RPC 2.0 response parsing and tool invocation
 *   - ai-mcp-client:     AI/LLM operations via MCP server (chat, generate, review, translate)
 *   - inklecate-runner:   Binary path detection, error parsing, temp dirs
 */

export {
  type McpResponse,
  parseMcpResponse,
  parseMcpResponseSafe,
  buildMcpRequest,
  callMcpTool,
  checkMcpHealth,
} from "./mcp-client.js";

export {
  type InkIssueType,
  type InkIssue,
  type FindInklecateOptions,
  parseInklecateIssue,
  parseInklecateOutput,
  getInklecateBinaryName,
  findInklecatePath,
  getTempCompileDir,
  stripBom,
} from "./inklecate-runner.js";

export {
  type ChatMessage,
  type LlmModelInfo,
  type LlmService,
  type GenerateInkResult,
  type ReviewInkResult,
  type GenerateCompilePlayResult,
  type AiMcpClientOptions,
  AiMcpClient,
  createAiMcpClient,
} from "./ai-mcp-client.js";
