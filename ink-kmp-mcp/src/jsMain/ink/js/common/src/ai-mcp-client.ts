/**
 * Ink MCP Client — Typed wrapper for all 81 MCP tools on the Kotlin MCP server.
 *
 * Tool groups (matching McpTools.kt):
 *   - Ink compilation & playback (17 tools)
 *   - Ink debugging (8 tools)
 *   - Ink section editing (6 tools)
 *   - Ink+Markdown templates (3 tools)
 *   - PlantUML diagrams + TOC (5 tools)
 *   - LLM model management (8 tools)
 *   - LLM service providers (2 tools)
 *   - Collaboration (2 tools)
 *   - Calendar (4 tools)
 *   - vCard principals (4 tools)
 *   - Auth (2 tools)
 *   - WebDAV + Backup (10 tools)
 *   - Asset pipeline + faker (10 tools)
 *
 * Response types match the JSON shapes from McpTools.kt handlers (snake_case keys).
 *
 * Used by:
 *   - ink-js-react-editor (InkAiAdapter — web-based AI assistant panel)
 *   - ink-js-ai (Electron AI assistant — replaces direct OpenAI SDK calls)
 *   - pwa (future: AI features in browser extension)
 */

import { callMcpTool, checkMcpHealth, buildMcpRequest, type McpResponse } from "./mcp-client.js";

// ══════════════════════════════════════════════════════════════════
// SHARED TYPES
// ══════════════════════════════════════════════════════════════════

/** Choice in an ink story. */
export interface InkChoice {
  index: number;
  text: string;
  tags: string[];
}

/** Result of story continuation (start_story, choose, continue_story, reset_story). */
export interface ContinueResult {
  session_id: string;
  text: string;
  can_continue: boolean;
  choices: InkChoice[];
  tags: string[];
}

// ══════════════════════════════════════════════════════════════════
// INK COMPILATION & PLAYBACK TYPES (17 tools)
// ══════════════════════════════════════════════════════════════════

/** Result of compile_ink. */
export interface CompileResult {
  success: boolean;
  json?: string;
  errors?: string[];
  warnings?: string[];
}

/** Result of get_variable. */
export interface VariableResult {
  name: string;
  value: unknown;
}

/** Result of save_state. */
export interface SaveStateResult {
  session_id: string;
  state_json: string;
}

/** Result of evaluate_function. */
export interface EvalFunctionResult {
  function: string;
  result: unknown;
}

// ══════════════════════════════════════════════════════════════════
// INK DEBUGGING TYPES (8 tools)
// ══════════════════════════════════════════════════════════════════

/** Breakpoint info. */
export interface Breakpoint {
  id: string;
  type: string;
  target: string;
  enabled: boolean;
}

/** Result of debug_step / debug_continue. */
export interface DebugStepResult {
  text: string;
  can_continue: boolean;
  choices: Array<{ index: number; text: string }>;
  tags?: string[];
  step_number: number;
  is_paused: boolean;
  hit_breakpoint?: { id: string; type: string; target: string };
  watch_changes?: Record<string, { old: unknown; new: unknown }>;
}

/** Result of add_watch. */
export interface WatchResult {
  variable: string;
  current_value: unknown;
}

// ══════════════════════════════════════════════════════════════════
// INK SECTION EDITING TYPES (6 tools)
// ══════════════════════════════════════════════════════════════════

/** Parsed ink section (knot/stitch/function/preamble). */
export interface InkSection {
  name: string;
  type: string;
  start_line: number;
  end_line: number;
  line_count?: number;
  content?: string;
  parent?: string;
  parameters?: string[];
}

/** Parsed ink variable declaration. */
export interface InkVariable {
  name: string;
  type: string;
  initial_value: string;
  line: number;
}

/** Full parsed ink structure from parse_ink. */
export interface InkStructure {
  sections: InkSection[];
  variables: InkVariable[];
  includes: string[];
  total_lines: number;
  divert_count: number;
}

/** Result of ink_stats. */
export interface InkStats {
  total_lines: number;
  word_count: number;
  knots: number;
  stitches: number;
  functions: number;
  variables: number;
  includes: number;
  choices: number;
  diverts: number;
  unreferenced_knots: string[];
  missing_divert_targets: string[];
  [key: string]: unknown;
}

/** Result of replace_section / insert_section / rename_section. */
export interface EditResult {
  ok: boolean;
  source: string;
}

// ══════════════════════════════════════════════════════════════════
// INK+MARKDOWN TYPES (3 tools)
// ══════════════════════════════════════════════════════════════════

/** Markdown table from parse_ink_md. */
export interface MdTable {
  name: string;
  columns: string[];
  rows: Array<Record<string, string>>;
}

/** Parsed ink file from markdown. */
export interface InkFile {
  name: string;
  ink_source: string;
  header_level: number;
}

/** Result of parse_ink_md. */
export interface ParseInkMdResult {
  files: InkFile[];
  tables: MdTable[];
}

/** Result of compile_ink_md. */
export interface CompileInkMdResult {
  results: Array<{
    file: string;
    success: boolean;
    errors?: string[];
    warnings?: string[];
  }>;
}

// ══════════════════════════════════════════════════════════════════
// PLANTUML TYPES (5 tools)
// ══════════════════════════════════════════════════════════════════

/** Result of ink2puml. */
export interface PumlResult {
  puml: string;
  mode: string;
}

/** Result of ink2svg. */
export interface SvgResult {
  svg: string;
  puml: string;
  mode: string;
}

// ══════════════════════════════════════════════════════════════════
// LLM MODEL TYPES (8 tools)
// ══════════════════════════════════════════════════════════════════

/** Chat message in OpenAI-compatible format. */
export interface ChatMessage {
  role: "user" | "assistant" | "system";
  content: string;
}

/** LLM model info from list_models (matches DictaLmConfig.GgufModel). */
export interface LlmModelInfo {
  id: string;
  parameters: string;
  quantization: string;
  architecture: string;
  size_gb: number;
  min_vram_gb: number;
  jlama_compatible: boolean;
  description: string;
  url: string;
}

/** Result of list_models. */
export interface ListModelsResult {
  models: LlmModelInfo[];
  recommended?: string;
  vram_filter_gb?: number;
}

/** Result of load_model. */
export interface LoadModelResult {
  ok: boolean;
  model_id: string;
  message: string;
}

/** Result of generate_ink. */
export interface GenerateInkResult {
  ink_source: string;
  compiles: boolean;
  compile_errors?: string[];
}

/** Result of review_ink. */
export interface ReviewInkResult {
  review: string;
}

/** Result of generate_compile_play (success). */
export interface GenerateCompilePlayResult {
  stage: "playing" | "compile_failed";
  ink_source: string;
  session_id?: string;
  text?: string;
  can_continue?: boolean;
  choices?: Array<{ index: number; text: string }>;
  errors?: string[];
}

// ══════════════════════════════════════════════════════════════════
// LLM SERVICE TYPES (2 tools)
// ══════════════════════════════════════════════════════════════════

/** LLM service provider from list_services (matches LlmServiceConfig.ServiceDef). */
export interface LlmService {
  id: string;
  name: string;
  base_url: string;
  default_model: string;
  api_key_env: string;
  requires_api_key: boolean;
  is_local: boolean;
  description: string;
  doc_url?: string;
}

/** Result of list_services. */
export interface ListServicesResult {
  services: LlmService[];
  connected_service?: string;
}

/** Result of connect_service. */
export interface ConnectServiceResult {
  ok: boolean;
  service: string;
  model: string;
  message: string;
}

// ══════════════════════════════════════════════════════════════════
// COLLABORATION TYPES (2 tools)
// ══════════════════════════════════════════════════════════════════

/** Result of collab_status. */
export interface CollabStatusResult {
  documents: Array<Record<string, unknown>>;
  total_clients: number;
}

// ══════════════════════════════════════════════════════════════════
// CALENDAR TYPES (4 tools)
// ══════════════════════════════════════════════════════════════════

/** Calendar event input for create_event. */
export interface CalendarEventInput {
  summary: string;
  dt_start: string;
  description?: string;
  dt_end?: string;
  category?: string;
}

// ══════════════════════════════════════════════════════════════════
// VCARD / PRINCIPAL TYPES (4 tools)
// ══════════════════════════════════════════════════════════════════

/** Input for create_principal. */
export interface PrincipalInput {
  id: string;
  name: string;
  role: string;
  email?: string;
  is_llm?: boolean;
  folder_path?: string;
}

// ══════════════════════════════════════════════════════════════════
// WEBDAV TYPES (10 tools)
// ══════════════════════════════════════════════════════════════════

/** WebDAV file entry from webdav_list. */
export interface WebDavFile {
  name: string;
  path: string;
  is_directory: boolean;
  size: number;
  last_modified: string;
  content_type?: string;
}

/** WebDAV file content from webdav_get. */
export interface WebDavFileContent {
  name: string;
  path: string;
  size: number;
  content_type: string;
  content: string;
  last_modified: string;
}

/** WebDAV backup entry from webdav_list_backups. */
export interface WebDavBackup {
  name: string;
  path: string;
  size: number;
  timestamp: string;
  extension: string;
  last_modified: string;
}

// ══════════════════════════════════════════════════════════════════
// ASSET PIPELINE TYPES (10 tools)
// ══════════════════════════════════════════════════════════════════

/** Asset category from resolve_emoji. */
export interface AssetCategory {
  name: string;
  type: string;
  animSet: string;
  gripType: string;
  meshPrefix: string;
  audioCategory: string;
}

/** Resolved asset reference from resolve_emoji. */
export interface AssetRef {
  emoji: string;
  category: AssetCategory;
  meshPath: string;
  animSetId: string;
}

/** Voice reference from parse_asset_tags. */
export interface VoiceRef {
  characterId: string;
  language: string;
  flacPath: string;
}

/** Parsed asset tag from parse_asset_tags. */
export interface ParsedAssetTag {
  emoji: string;
  category: string;
  meshPath: string;
  animSetId: string;
  voiceRef?: VoiceRef;
}

/** Unicode block entry from resolve_unicode_block. */
export interface UnicodeEntry {
  emoji: string;
  name: string;
  type: string;
  unicodeGroup: string;
  unicodeSubgroup: string;
  codePoints: string;
  isGameAsset: boolean;
  generalCategory?: string;
  animSet?: string;
  meshPrefix?: string;
}

/** Result of emit_asset_event. */
export interface EmitAssetEventResult {
  session_id: string;
  tags_processed: number;
  assets_resolved: number;
  assets: Array<{
    emoji: string;
    category: string;
    type: string;
    mesh_path: string;
    anim_set: string;
    voice_ref?: { character_id: string; language: string; flac_path: string };
  }>;
  channels_published: string[];
}

// ══════════════════════════════════════════════════════════════════
// CLIENT OPTIONS
// ══════════════════════════════════════════════════════════════════

/** Options for creating the MCP client. */
export interface AiMcpClientOptions {
  /** MCP server URL (e.g. "http://localhost:3001"). */
  serverUrl: string;
}

// ══════════════════════════════════════════════════════════════════
// INK MCP CLIENT
// ══════════════════════════════════════════════════════════════════

/**
 * Ink MCP Client — typed wrapper for all MCP tools on the Kotlin MCP server.
 *
 * The MCP server handles backend selection (JLama local, LM Studio, cloud services)
 * and coordinates all engines (ink, debug, edit, LLM, WebDAV, asset pipeline, etc.).
 * This client is a thin typed layer over callMcpTool().
 */
export class InkMcpClient {
  private serverUrl: string;

  constructor(options: AiMcpClientOptions) {
    this.serverUrl = options.serverUrl;
  }

  /** Call an MCP tool and parse the JSON response. */
  private call<T = unknown>(tool: string, args: Record<string, unknown> = {}): Promise<T> {
    return callMcpTool<T>(this.serverUrl, tool, args);
  }

  /** Call an MCP tool and return the raw text (for non-JSON responses like generate_story_md). */
  private async callText(tool: string, args: Record<string, unknown> = {}): Promise<string> {
    const { body, headers } = buildMcpRequest(tool, args);
    const resp = await fetch(`${this.serverUrl}/message`, { method: "POST", headers, body });
    const data = (await resp.json()) as McpResponse;
    if (data.error) throw new Error(data.error.message);
    return data.result?.content?.[0]?.text ?? "";
  }

  // ════════════════════════════════════════════════════════════════
  // INK COMPILATION & PLAYBACK (17 tools) — McpTools.kt:66-260
  // ════════════════════════════════════════════════════════════════

  /** Compile ink source code to JSON. */
  async compileInk(source: string): Promise<CompileResult> {
    return this.call<CompileResult>("compile_ink", { source });
  }

  /** Compile ink source and start an interactive story session. */
  async startStory(source: string, sessionId?: string): Promise<ContinueResult> {
    return this.call<ContinueResult>("start_story", {
      source,
      ...(sessionId && { session_id: sessionId }),
    });
  }

  /** Start an interactive story session from pre-compiled JSON. */
  async startStoryJson(json: string, sessionId?: string): Promise<ContinueResult> {
    return this.call<ContinueResult>("start_story_json", {
      json,
      ...(sessionId && { session_id: sessionId }),
    });
  }

  /** Make a choice in an active story session. */
  async choose(sessionId: string, choiceIndex: number): Promise<ContinueResult> {
    return this.call<ContinueResult>("choose", { session_id: sessionId, choice_index: choiceIndex });
  }

  /** Continue reading the story text in an active session. */
  async continueStory(sessionId: string): Promise<ContinueResult> {
    return this.call<ContinueResult>("continue_story", { session_id: sessionId });
  }

  /** Get the value of an ink variable. */
  async getVariable(sessionId: string, name: string): Promise<VariableResult> {
    return this.call<VariableResult>("get_variable", { session_id: sessionId, name });
  }

  /** Set the value of an ink variable. */
  async setVariable(sessionId: string, name: string, value: unknown): Promise<{ ok: boolean; name: string }> {
    return this.call("set_variable", { session_id: sessionId, name, value });
  }

  /** Save the current story state as JSON. */
  async saveState(sessionId: string): Promise<SaveStateResult> {
    return this.call<SaveStateResult>("save_state", { session_id: sessionId });
  }

  /** Load a previously saved story state. */
  async loadState(sessionId: string, stateJson: string): Promise<{ ok: boolean; session_id: string }> {
    return this.call("load_state", { session_id: sessionId, state_json: stateJson });
  }

  /** Reset the story to its beginning. */
  async resetStory(sessionId: string): Promise<ContinueResult> {
    return this.call<ContinueResult>("reset_story", { session_id: sessionId });
  }

  /** Call an ink function defined in the story. */
  async evaluateFunction(sessionId: string, functionName: string, args?: unknown[]): Promise<EvalFunctionResult> {
    return this.call<EvalFunctionResult>("evaluate_function", {
      session_id: sessionId,
      function_name: functionName,
      ...(args && { args }),
    });
  }

  /** Get the global tags defined at the top of the ink story. */
  async getGlobalTags(sessionId: string): Promise<string[]> {
    const result = await this.call<{ tags: string[] }>("get_global_tags", { session_id: sessionId });
    return result.tags;
  }

  /** List all active story sessions. */
  async listSessions(): Promise<string[]> {
    const result = await this.call<{ sessions: string[] }>("list_sessions");
    return result.sessions;
  }

  /** End a story session and free its resources. */
  async endSession(sessionId: string): Promise<{ ok: boolean; session_id: string }> {
    return this.call("end_session", { session_id: sessionId });
  }

  /** Add Unicode bidi markers (LRI/RLI/PDI) to text. */
  async bidify(text: string): Promise<string> {
    return (await this.call<{ result: string }>("bidify", { text })).result;
  }

  /** Remove Unicode bidi markers from text. */
  async stripBidi(text: string): Promise<string> {
    return (await this.call<{ result: string }>("strip_bidi", { text })).result;
  }

  /** Add bidi markers to story text strings in compiled ink JSON. */
  async bidifyJson(json: string): Promise<string> {
    return (await this.call<{ result: string }>("bidify_json", { json })).result;
  }

  // ════════════════════════════════════════════════════════════════
  // INK DEBUGGING (8 tools) — McpTools.kt:266-361
  // ════════════════════════════════════════════════════════════════

  /** Start debugging an existing story session. */
  async startDebug(sessionId: string): Promise<Record<string, unknown>> {
    return this.call("start_debug", { session_id: sessionId });
  }

  /** Add a breakpoint (type: 'knot', 'stitch', 'pattern', 'variable_change'). */
  async addBreakpoint(sessionId: string, type: string, target: string): Promise<Breakpoint> {
    return this.call<Breakpoint>("add_breakpoint", { session_id: sessionId, type, target });
  }

  /** Remove a breakpoint by ID. */
  async removeBreakpoint(sessionId: string, breakpointId: string): Promise<{ ok: boolean; breakpoint_id: string }> {
    return this.call("remove_breakpoint", { session_id: sessionId, breakpoint_id: breakpointId });
  }

  /** Step to the next story output, checking breakpoints and watches. */
  async debugStep(sessionId: string): Promise<DebugStepResult> {
    return this.call<DebugStepResult>("debug_step", { session_id: sessionId });
  }

  /** Continue execution until a breakpoint is hit or the story ends. */
  async debugContinue(sessionId: string, maxSteps?: number): Promise<DebugStepResult> {
    return this.call<DebugStepResult>("debug_continue", {
      session_id: sessionId,
      ...(maxSteps != null && { max_steps: maxSteps }),
    });
  }

  /** Add an ink variable to the watch list. */
  async addWatch(sessionId: string, variable: string): Promise<WatchResult> {
    return this.call<WatchResult>("add_watch", { session_id: sessionId, variable });
  }

  /** Inspect the current debug state. */
  async debugInspect(sessionId: string): Promise<Record<string, unknown>> {
    return this.call("debug_inspect", { session_id: sessionId });
  }

  /** Get the execution trace log. */
  async debugTrace(sessionId: string, lastN?: number): Promise<{ trace: Array<Record<string, unknown>> }> {
    return this.call("debug_trace", {
      session_id: sessionId,
      ...(lastN != null && { last_n: lastN }),
    });
  }

  // ════════════════════════════════════════════════════════════════
  // INK SECTION EDITING (6 tools) — McpTools.kt:367-441
  // ════════════════════════════════════════════════════════════════

  /** Parse ink source into sections, variables, includes, and diverts. */
  async parseInk(source: string): Promise<InkStructure> {
    return this.call<InkStructure>("parse_ink", { source });
  }

  /** Get a specific knot, stitch, or function by name. */
  async getSection(source: string, sectionName: string): Promise<InkSection> {
    return this.call<InkSection>("get_section", { source, section_name: sectionName });
  }

  /** Replace a knot/stitch/function's content. */
  async replaceSection(source: string, sectionName: string, newContent: string): Promise<EditResult> {
    return this.call<EditResult>("replace_section", { source, section_name: sectionName, new_content: newContent });
  }

  /** Insert a new section after an existing one. */
  async insertSection(source: string, afterSection: string, newContent: string): Promise<EditResult> {
    return this.call<EditResult>("insert_section", { source, after_section: afterSection, new_content: newContent });
  }

  /** Rename a knot/stitch and update all diverts that reference it. */
  async renameSection(source: string, oldName: string, newName: string): Promise<EditResult> {
    return this.call<EditResult>("rename_section", { source, old_name: oldName, new_name: newName });
  }

  /** Get ink script statistics (knots, stitches, dead ends, word count, etc.). */
  async inkStats(source: string): Promise<InkStats> {
    return this.call<InkStats>("ink_stats", { source });
  }

  // ════════════════════════════════════════════════════════════════
  // INK+MARKDOWN TEMPLATES (3 tools) — McpTools.kt:447-483
  // ════════════════════════════════════════════════════════════════

  /** Parse a Markdown template containing ```ink code blocks and tables. */
  async parseInkMd(markdown: string): Promise<ParseInkMdResult> {
    return this.call<ParseInkMdResult>("parse_ink_md", { markdown });
  }

  /** Render a Markdown template: extract ink blocks, resolve table data. */
  async renderInkMd(markdown: string): Promise<{ files: Record<string, string> }> {
    return this.call("render_ink_md", { markdown });
  }

  /** Parse, render, and compile all ink blocks from a Markdown template. */
  async compileInkMd(markdown: string): Promise<CompileInkMdResult> {
    return this.call<CompileInkMdResult>("compile_ink_md", { markdown });
  }

  // ════════════════════════════════════════════════════════════════
  // PLANTUML DIAGRAMS + TOC (5 tools) — McpTools.kt:489-550
  // ════════════════════════════════════════════════════════════════

  /** Convert ink source to a PlantUML diagram. */
  async ink2puml(source: string, mode?: "activity" | "state", title?: string): Promise<PumlResult> {
    return this.call<PumlResult>("ink2puml", { source, ...(mode && { mode }), ...(title && { title }) });
  }

  /** Convert ink source to an SVG diagram. */
  async ink2svg(source: string, mode?: "activity" | "state", title?: string): Promise<SvgResult> {
    return this.call<SvgResult>("ink2svg", { source, ...(mode && { mode }), ...(title && { title }) });
  }

  /** Render raw PlantUML source to SVG. */
  async puml2svg(puml: string): Promise<{ svg: string }> {
    return this.call("puml2svg", { puml });
  }

  /** Generate a Table of Contents for an ink script with MCP tool links. */
  async inkToc(source: string): Promise<{ toc: string }> {
    return this.call("ink_toc", { source });
  }

  /** Generate a PlantUML TOC diagram with MCP tool links in notes. */
  async inkTocPuml(source: string, title?: string): Promise<{ puml: string }> {
    return this.call("ink_toc_puml", { source, ...(title && { title }) });
  }

  // ════════════════════════════════════════════════════════════════
  // LLM MODEL MANAGEMENT (8 tools) — McpTools.kt:556-641
  // ════════════════════════════════════════════════════════════════

  /** List available DictaLM 3.0 GGUF models. */
  async listModels(vramGb?: number): Promise<ListModelsResult> {
    return this.call<ListModelsResult>("list_models", vramGb != null ? { vram_gb: vramGb } : {});
  }

  /** Download and load a DictaLM 3.0 GGUF model. */
  async loadModel(modelId: string): Promise<LoadModelResult> {
    return this.call<LoadModelResult>("load_model", { model_id: modelId });
  }

  /** Load a custom model from a HuggingFace repo. */
  async loadCustomModel(customRepo: string): Promise<LoadModelResult> {
    return this.call<LoadModelResult>("load_model", { custom_repo: customRepo });
  }

  /** Get information about the currently loaded LLM model. */
  async modelInfo(): Promise<Record<string, unknown>> {
    return this.call("model_info");
  }

  /** Send a chat message to the loaded LLM or connected service. */
  async chat(message: string): Promise<string> {
    return (await this.call<{ response: string }>("llm_chat", { message })).response;
  }

  /** Generate ink code from a natural language prompt. */
  async generateInk(prompt: string): Promise<GenerateInkResult> {
    return this.call<GenerateInkResult>("generate_ink", { prompt });
  }

  /** Review ink source for issues and suggestions. */
  async reviewInk(source: string): Promise<ReviewInkResult> {
    return this.call<ReviewInkResult>("review_ink", { source });
  }

  /** Translate ink story text to Hebrew, preserving ink syntax. */
  async translateToHebrew(source: string): Promise<string> {
    return (await this.call<{ translated_ink: string }>("translate_ink_hebrew", { source })).translated_ink;
  }

  /** Full pipeline: generate ink → compile → start story session. */
  async generateCompilePlay(prompt: string): Promise<GenerateCompilePlayResult> {
    return this.call<GenerateCompilePlayResult>("generate_compile_play", { prompt });
  }

  // ── Composite helpers (client-side logic, not MCP tools) ───────

  /**
   * Fix ink compilation errors using the LLM.
   * Sends the code and errors as context to llm_chat.
   */
  async fixErrors(code: string, errors: Array<{ line: number; message: string }>): Promise<{
    fixedCode: string;
    explanation: string;
  }> {
    const errorSummary = errors.map((e, i) => `${i + 1}. Line ${e.line}: ${e.message}`).join("\n");
    const prompt =
      `Fix these ink compilation errors:\n${errorSummary}\n\nCode:\n\`\`\`ink\n${code}\n\`\`\`\n\n` +
      `Provide:\n1. Fixed code in a \`\`\`ink code block\n2. Brief explanation of changes`;
    const response = await this.chat(prompt);
    const codeMatch = response.match(/```(?:ink)?\n([\s\S]*?)\n```/);
    return { fixedCode: codeMatch ? codeMatch[1] : response, explanation: response };
  }

  /** Explain ink code or concepts using the LLM. */
  async explain(query: string, code?: string): Promise<string> {
    let prompt = query;
    if (code) prompt += `\n\nCode:\n\`\`\`ink\n${code}\n\`\`\``;
    return this.chat(prompt);
  }

  // ════════════════════════════════════════════════════════════════
  // LLM SERVICE PROVIDERS (2 tools) — McpTools.kt:647-670
  // ════════════════════════════════════════════════════════════════

  /** List available LLM service providers. */
  async listServices(): Promise<ListServicesResult> {
    return this.call<ListServicesResult>("list_services");
  }

  /** Connect to an external LLM service provider. */
  async connectService(
    serviceId: string,
    options?: { apiKey?: string; model?: string; baseUrl?: string }
  ): Promise<ConnectServiceResult> {
    return this.call<ConnectServiceResult>("connect_service", {
      service_id: serviceId,
      ...(options?.apiKey && { api_key: options.apiKey }),
      ...(options?.model && { model: options.model }),
      ...(options?.baseUrl && { base_url: options.baseUrl }),
    });
  }

  // ════════════════════════════════════════════════════════════════
  // COLLABORATION (2 tools) — McpTools.kt:676-696
  // ════════════════════════════════════════════════════════════════

  /** List active collaboration documents with connected client counts. */
  async collabStatus(): Promise<CollabStatusResult> {
    return this.call<CollabStatusResult>("collab_status");
  }

  /** Get collaboration details for a specific document. */
  async collabInfo(docId: string): Promise<Record<string, unknown>> {
    return this.call("collab_info", { doc_id: docId });
  }

  // ════════════════════════════════════════════════════════════════
  // CALENDAR (4 tools) — McpTools.kt:702-754
  // ════════════════════════════════════════════════════════════════

  /** Create a story/game event in an ICS calendar. */
  async createEvent(calendarId: string, event: CalendarEventInput): Promise<Record<string, unknown>> {
    return this.call("create_event", { calendar_id: calendarId, ...event });
  }

  /** List events in a calendar, optionally filtered by category. */
  async listEvents(calendarId: string, category?: string): Promise<{ events: Array<Record<string, unknown>> }> {
    return this.call("list_events", { calendar_id: calendarId, ...(category && { category }) });
  }

  /** Export a calendar as ICS (iCalendar) format string. */
  async exportIcs(calendarId: string): Promise<string> {
    return (await this.call<{ ics: string }>("export_ics", { calendar_id: calendarId })).ics;
  }

  /** Import events from ICS content into a calendar. */
  async importIcs(calendarId: string, icsContent: string): Promise<Record<string, unknown>> {
    return this.call("import_ics", { calendar_id: calendarId, ics_content: icsContent });
  }

  // ════════════════════════════════════════════════════════════════
  // VCARD PRINCIPALS (4 tools) — McpTools.kt:760-809
  // ════════════════════════════════════════════════════════════════

  /** Create a user or LLM principal with vCard, folder mapping, and optional MCP URI credentials. */
  async createPrincipal(principal: PrincipalInput): Promise<Record<string, unknown>> {
    return this.call("create_principal", { ...principal });
  }

  /** List all user and LLM principals. */
  async listPrincipals(isLlm?: boolean): Promise<{ principals: Array<Record<string, unknown>> }> {
    return this.call("list_principals", isLlm != null ? { is_llm: isLlm } : {});
  }

  /** Get full details of a principal including vCard data. */
  async getPrincipal(id: string): Promise<Record<string, unknown>> {
    return this.call("get_principal", { id });
  }

  /** Delete a principal and its folder mapping. */
  async deletePrincipal(id: string): Promise<Record<string, unknown>> {
    return this.call("delete_principal", { id });
  }

  // ════════════════════════════════════════════════════════════════
  // AUTH (2 tools) — McpTools.kt:815-837
  // ════════════════════════════════════════════════════════════════

  /** Get authentication system status. */
  async authStatus(): Promise<Record<string, unknown>> {
    return this.call("auth_status");
  }

  /** Create basicauth credentials for an LLM model. */
  async createLlmCredential(
    modelName: string,
    options?: { host?: string; port?: number }
  ): Promise<{ model_name: string; token: string; mcp_uri: string; basic_auth: string }> {
    return this.call("create_llm_credential", {
      model_name: modelName,
      ...(options?.host && { host: options.host }),
      ...(options?.port != null && { port: options.port }),
    });
  }

  // ════════════════════════════════════════════════════════════════
  // WEBDAV + BACKUP (10 tools) — McpTools.kt:843-970
  // ════════════════════════════════════════════════════════════════

  /** List files and directories at a WebDAV path. */
  async webdavList(path: string, principalId?: string): Promise<{ path: string; is_shared: boolean; files: WebDavFile[] }> {
    return this.call("webdav_list", { path, ...(principalId && { principal_id: principalId }) });
  }

  /** Get file content from a WebDAV path. */
  async webdavGet(path: string, principalId?: string): Promise<WebDavFileContent> {
    return this.call<WebDavFileContent>("webdav_get", { path, ...(principalId && { principal_id: principalId }) });
  }

  /** Write file content to a WebDAV path. */
  async webdavPut(path: string, content: string, principalId: string): Promise<{ ok: boolean; path: string; size: number; content_type: string }> {
    return this.call("webdav_put", { path, content, principal_id: principalId });
  }

  /** Delete a file or directory at a WebDAV path. */
  async webdavDelete(path: string, principalId: string): Promise<{ ok: boolean; path: string }> {
    return this.call("webdav_delete", { path, principal_id: principalId });
  }

  /** Create a directory at a WebDAV path. */
  async webdavMkdir(path: string, principalId: string): Promise<{ ok: boolean; path: string; message: string }> {
    return this.call("webdav_mkdir", { path, principal_id: principalId });
  }

  /** Sync files from a remote WebDAV server to a local path. */
  async webdavSync(
    remoteUrl: string,
    localPath: string,
    principalId: string,
    credentials?: { username?: string; password?: string }
  ): Promise<Record<string, unknown>> {
    return this.call("webdav_sync", {
      remote_url: remoteUrl,
      local_path: localPath,
      principal_id: principalId,
      ...(credentials?.username && { username: credentials.username }),
      ...(credentials?.password && { password: credentials.password }),
    });
  }

  /** Create timestamped backup of master script files (.ink, .puml, .svg). */
  async webdavBackup(scriptPath: string, principalId: string): Promise<Record<string, unknown>> {
    return this.call("webdav_backup", { script_path: scriptPath, principal_id: principalId });
  }

  /** List timestamped backups for a master file, newest first. */
  async webdavListBackups(path: string, principalId?: string): Promise<{ path: string; backups: WebDavBackup[] }> {
    return this.call("webdav_list_backups", { path, ...(principalId && { principal_id: principalId }) });
  }

  /** Restore a timestamped backup to the master file. */
  async webdavRestore(backupPath: string, masterPath: string, principalId: string): Promise<{ ok: boolean; backup: string; master: string; size: number }> {
    return this.call("webdav_restore", { backup_path: backupPath, master_path: masterPath, principal_id: principalId });
  }

  /** Create an LLM working copy of a user's files. */
  async webdavWorkingCopy(originPath: string, modelId: string, principalId: string): Promise<Record<string, unknown>> {
    return this.call("webdav_working_copy", { origin_path: originPath, model_id: modelId, principal_id: principalId });
  }

  // ════════════════════════════════════════════════════════════════
  // ASSET PIPELINE + FAKER (10 tools) — McpTools.kt:976-1093
  // ════════════════════════════════════════════════════════════════

  /** Resolve an emoji to its AssetCategory. */
  async resolveEmoji(emoji: string): Promise<AssetRef> {
    return this.call<AssetRef>("resolve_emoji", { emoji });
  }

  /** Parse ink story tags into AssetRef list. */
  async parseAssetTags(tags: string[]): Promise<ParsedAssetTag[]> {
    return this.call<ParsedAssetTag[]>("parse_asset_tags", { tags });
  }

  /** Generate an items MD table with faker names and POI formulas. */
  async generateItems(options?: {
    seed?: number;
    count?: number;
    level?: number;
    categories?: string[];
  }): Promise<MdTable> {
    return this.call<MdTable>("generate_items", { ...options });
  }

  /** Generate a DnD characters MD table with faker names and stat formulas. */
  async generateCharacters(options?: { seed?: number; count?: number }): Promise<MdTable> {
    return this.call<MdTable>("generate_characters", { ...options });
  }

  /** Generate a full story Markdown document with characters, items, and ink blocks. */
  async generateStoryMd(options?: {
    seed?: number;
    level?: number;
    characters?: number;
    items?: number;
  }): Promise<string> {
    return this.callText("generate_story_md", { ...options });
  }

  /** Evaluate POI XLSX formulas in MD table cells. */
  async evaluateFormulas(markdown: string): Promise<MdTable[]> {
    return this.call<MdTable[]>("evaluate_formulas", { markdown });
  }

  /** List all Unicode emoji groups and subgroups. */
  async listEmojiGroups(filter?: string): Promise<{ totalGroups: number; groups: Record<string, string[]> }> {
    return this.call("list_emoji_groups", filter ? { filter } : {});
  }

  /** Resolve all symbols in a Unicode block to their categories. */
  async resolveUnicodeBlock(block: string): Promise<{ block: string; count: number; entries: UnicodeEntry[] }> {
    return this.call("resolve_unicode_block", { block });
  }

  /** Emit an asset event by processing ink tags through the EmojiAssetManifest. */
  async emitAssetEvent(sessionId: string, tags: string[], knot?: string): Promise<EmitAssetEventResult> {
    return this.call<EmitAssetEventResult>("emit_asset_event", {
      session_id: sessionId,
      tags,
      ...(knot && { knot }),
    });
  }

  /** List recent asset events, optionally filtered by session ID or channel. */
  async listAssetEvents(options?: {
    session_id?: string;
    channel?: string;
    limit?: number;
  }): Promise<Record<string, unknown>> {
    return this.call("list_asset_events", { ...options });
  }

  // ════════════════════════════════════════════════════════════════
  // HEALTH
  // ════════════════════════════════════════════════════════════════

  /** Check if the MCP server is reachable. */
  async isAvailable(): Promise<boolean> {
    return checkMcpHealth(this.serverUrl);
  }
}

// ══════════════════════════════════════════════════════════════════
// BACKWARD-COMPATIBLE ALIASES
// ══════════════════════════════════════════════════════════════════

/** @deprecated Use InkMcpClient. */
export const AiMcpClient = InkMcpClient;

/** Create an Ink MCP client. */
export function createInkMcpClient(serverUrl = "http://localhost:3001"): InkMcpClient {
  return new InkMcpClient({ serverUrl });
}

/** @deprecated Use createInkMcpClient. */
export const createAiMcpClient = createInkMcpClient;
