/**
 * @inky/common — Shared utilities for the Inky ink editor ecosystem.
 *
 * Modules:
 *   - mcp-client:        MCP JSON-RPC 2.0 response parsing and tool invocation
 *   - ai-mcp-client:     Typed wrapper for all 81 MCP tools (ink, debug, edit, LLM, WebDAV, assets, etc.)
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
  // Shared types
  type InkChoice,
  type ContinueResult,
  // Ink compilation & playback
  type CompileResult,
  type VariableResult,
  type SaveStateResult,
  type EvalFunctionResult,
  // Ink debugging
  type Breakpoint,
  type DebugStepResult,
  type WatchResult,
  // Ink section editing
  type InkSection,
  type InkVariable,
  type InkStructure,
  type InkStats,
  type EditResult,
  // Ink+Markdown
  type MdTable,
  type InkFile,
  type ParseInkMdResult,
  type CompileInkMdResult,
  // PlantUML
  type PumlResult,
  type SvgResult,
  // LLM models
  type ChatMessage,
  type LlmModelInfo,
  type ListModelsResult,
  type LoadModelResult,
  type GenerateInkResult,
  type ReviewInkResult,
  type GenerateCompilePlayResult,
  // LLM services
  type LlmService,
  type ListServicesResult,
  type ConnectServiceResult,
  // Collaboration
  type CollabStatusResult,
  // Calendar
  type CalendarEventInput,
  // vCard
  type PrincipalInput,
  // WebDAV
  type WebDavFile,
  type WebDavFileContent,
  type WebDavBackup,
  // Assets
  type AssetCategory,
  type AssetRef,
  type VoiceRef,
  type ParsedAssetTag,
  type UnicodeEntry,
  type EmitAssetEventResult,
  // Client
  type AiMcpClientOptions,
  InkMcpClient,
  createInkMcpClient,
  AiMcpClient,
  createAiMcpClient,
} from "./ai-mcp-client.js";
