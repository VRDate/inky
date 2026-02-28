/**
 * AI MCP Client — Shared interface to LLM tools on the Kotlin MCP server.
 *
 * Wraps the 10 LLM/service MCP tools exposed by McpTools.kt:
 *   - llm_chat, generate_ink, review_ink, translate_ink_hebrew
 *   - generate_compile_play
 *   - list_models, load_model, model_info
 *   - list_services, connect_service
 *
 * Used by:
 *   - ink-js-react-editor (InkAiAdapter — web-based AI assistant panel)
 *   - ink-js-ai (Electron AI assistant — replaces direct OpenAI SDK calls)
 *   - pwa (future: AI features in browser extension)
 */

import { callMcpTool, checkMcpHealth } from "./mcp-client.js";

// ── Types ──────────────────────────────────────────────────────────

/** Chat message in OpenAI-compatible format. */
export interface ChatMessage {
  role: "user" | "assistant" | "system";
  content: string;
}

/** LLM model info from list_models / model_info. */
export interface LlmModelInfo {
  id: string;
  name: string;
  size?: string;
  quantization?: string;
  vramGb?: number;
  loaded?: boolean;
  description?: string;
}

/** LLM service provider from list_services. */
export interface LlmService {
  id: string;
  name: string;
  baseUrl: string;
  defaultModel?: string;
  isLocal: boolean;
  requiresApiKey: boolean;
  description?: string;
}

/** Result of generate_ink. */
export interface GenerateInkResult {
  source: string;
  prompt?: string;
}

/** Result of review_ink. */
export interface ReviewInkResult {
  review: string;
  issues?: Array<{ type: string; message: string; line?: number }>;
  suggestions?: string[];
}

/** Result of generate_compile_play pipeline. */
export interface GenerateCompilePlayResult {
  source: string;
  sessionId: string;
  text: string;
  choices: Array<{ index: number; text: string }>;
  canContinue: boolean;
}

// ── AI MCP Client ──────────────────────────────────────────────────

/** Options for creating the AI MCP client. */
export interface AiMcpClientOptions {
  /** MCP server URL (e.g. "http://localhost:3001"). */
  serverUrl: string;
}

/**
 * AI MCP Client — proxies all AI/LLM operations through the Kotlin MCP server.
 *
 * The MCP server handles backend selection (JLama local, LM Studio, cloud services).
 * This client is a thin typed wrapper over callMcpTool().
 */
export class AiMcpClient {
  private serverUrl: string;

  constructor(options: AiMcpClientOptions) {
    this.serverUrl = options.serverUrl;
  }

  private call<T = unknown>(tool: string, args: Record<string, unknown> = {}): Promise<T> {
    return callMcpTool<T>(this.serverUrl, tool, args);
  }

  // ── Chat ────────────────────────────────────────────────────

  /**
   * Send a chat message to the loaded LLM or connected service.
   * Equivalent to ai/llmClient.js chat() but routed through MCP server.
   */
  async chat(message: string): Promise<string> {
    const result = await this.call<{ response: string }>("llm_chat", { message });
    return result.response;
  }

  // ── Ink Generation ──────────────────────────────────────────

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
    const result = await this.call<{ source: string }>("translate_ink_hebrew", { source });
    return result.source;
  }

  /** Full pipeline: generate ink → compile → start story session. */
  async generateCompilePlay(prompt: string): Promise<GenerateCompilePlayResult> {
    return this.call<GenerateCompilePlayResult>("generate_compile_play", { prompt });
  }

  // ── Fix Errors (composite: review + generate) ──────────────

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
    return {
      fixedCode: codeMatch ? codeMatch[1] : response,
      explanation: response,
    };
  }

  /**
   * Explain ink code or concepts using the LLM.
   */
  async explain(query: string, code?: string): Promise<string> {
    let prompt = query;
    if (code) prompt += `\n\nCode:\n\`\`\`ink\n${code}\n\`\`\``;
    return this.chat(prompt);
  }

  // ── Model Management ────────────────────────────────────────

  /** List available DictaLM GGUF models. */
  async listModels(vramGb?: number): Promise<LlmModelInfo[]> {
    const result = await this.call<{ models: LlmModelInfo[] }>(
      "list_models",
      vramGb != null ? { vram_gb: vramGb } : {}
    );
    return result.models;
  }

  /** Load a DictaLM model by ID. */
  async loadModel(modelId: string): Promise<LlmModelInfo> {
    return this.call<LlmModelInfo>("load_model", { model_id: modelId });
  }

  /** Get info about the currently loaded model. */
  async modelInfo(): Promise<LlmModelInfo> {
    return this.call<LlmModelInfo>("model_info");
  }

  // ── Service Management ──────────────────────────────────────

  /** List available LLM service providers. */
  async listServices(): Promise<LlmService[]> {
    const result = await this.call<{ services: LlmService[] }>("list_services");
    return result.services;
  }

  /** Connect to an external LLM service. */
  async connectService(
    serviceId: string,
    options?: { apiKey?: string; model?: string }
  ): Promise<{ connected: boolean; service: string; model?: string }> {
    return this.call("connect_service", {
      service_id: serviceId,
      ...(options?.apiKey && { api_key: options.apiKey }),
      ...(options?.model && { model: options.model }),
    });
  }

  // ── Health ──────────────────────────────────────────────────

  /** Check if the MCP server is reachable. */
  async isAvailable(): Promise<boolean> {
    return checkMcpHealth(this.serverUrl);
  }
}

/**
 * Create an AI MCP client.
 *
 * @param serverUrl  MCP server base URL (default: "http://localhost:3001")
 */
export function createAiMcpClient(serverUrl = "http://localhost:3001"): AiMcpClient {
  return new AiMcpClient({ serverUrl });
}
