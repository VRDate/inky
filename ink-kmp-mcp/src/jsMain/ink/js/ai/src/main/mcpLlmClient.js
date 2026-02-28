/**
 * MCP-backed LLM Client
 *
 * Drop-in replacement for LLMClient (llmClient.js) that routes all
 * AI operations through the Kotlin MCP server instead of calling
 * OpenAI-compatible APIs directly.
 *
 * Uses @inky/common/ai-mcp-client under the hood.
 *
 * Benefits over direct OpenAI:
 *   - Server manages model lifecycle (JLama, LM Studio, cloud services)
 *   - Hebrew-optimized DictaLM models available via JLama
 *   - 11 cloud providers pre-configured in LlmServiceConfig
 *   - Camel route pipelines (generate → compile → play)
 *   - Shared model state across all connected clients
 */

const { AiMcpClient } = require('@inky/common');

class McpLlmClient {
  /**
   * @param {string} serverUrl - MCP server URL (default: http://localhost:3001)
   */
  constructor(serverUrl = 'http://localhost:3001') {
    this.client = new AiMcpClient({ serverUrl });
    this.serverUrl = serverUrl;
  }

  /**
   * Send a chat completion request (mirrors LLMClient.chat).
   * @param {Array<{role: string, content: string}>} messages - Conversation history
   * @param {Object} _options - Ignored (model selection is server-side)
   * @returns {Promise<string>} AI response
   */
  async chat(messages, _options = {}) {
    // MCP llm_chat takes a single message string.
    // For multi-turn, concatenate the conversation.
    const lastUserMsg = messages.filter(m => m.role === 'user').pop();
    if (!lastUserMsg) throw new Error('No user message in conversation');

    // Include system prompt and context in the message
    const systemMsgs = messages.filter(m => m.role === 'system');
    const contextMsgs = messages.filter(m => m.role !== 'system' && m !== lastUserMsg);

    let fullMessage = '';
    if (systemMsgs.length > 0) {
      fullMessage += systemMsgs.map(m => m.content).join('\n') + '\n\n';
    }
    if (contextMsgs.length > 0) {
      fullMessage += contextMsgs.map(m => `${m.role}: ${m.content}`).join('\n') + '\n\n';
    }
    fullMessage += lastUserMsg.content;

    return await this.client.chat(fullMessage);
  }

  /**
   * Stream chat completion (falls back to non-streaming via MCP).
   * @param {Array} messages - Conversation history
   * @param {Function} onChunk - Callback for each chunk
   * @param {Object} options - Override options
   * @returns {Promise<string>} Complete response
   */
  async chatStream(messages, onChunk, options = {}) {
    // MCP server doesn't support streaming yet — deliver full response as single chunk
    const response = await this.chat(messages, options);
    onChunk(response);
    return response;
  }

  /**
   * Generate ink code from prompt (uses MCP generate_ink tool).
   * @param {string} prompt - User request
   * @param {string} _context - Ignored (server handles context)
   * @returns {Promise<string>} Generated ink code
   */
  async generateInkCode(prompt, _context = '') {
    const result = await this.client.generateInk(prompt);
    return result.source;
  }

  /**
   * Fix ink errors using AI (uses MCP llm_chat with error context).
   * @param {string} code - Current ink code
   * @param {Array} errors - Compilation errors
   * @param {string} _documentation - Ignored (server has its own context)
   * @returns {Promise<Object>} { fixedCode, explanation }
   */
  async fixErrors(code, errors, _documentation = '') {
    return await this.client.fixErrors(code, errors);
  }

  /**
   * Explain ink code or concepts (uses MCP llm_chat).
   * @param {string} query - What to explain
   * @param {string} code - Code context (optional)
   * @param {string} _documentation - Ignored
   * @returns {Promise<string>} Explanation
   */
  async explain(query, code = '', _documentation = '') {
    return await this.client.explain(query, code || undefined);
  }

  /**
   * General chat about ink (uses MCP llm_chat).
   * @param {Array} conversationHistory - Full conversation
   * @param {string} _documentation - Ignored
   * @returns {Promise<string>} Response
   */
  async chatAboutInk(conversationHistory, _documentation = '') {
    return await this.chat(conversationHistory);
  }

  /**
   * Review ink source code (uses MCP review_ink tool).
   * @param {string} source - Ink source code
   * @returns {Promise<Object>} Review result with issues and suggestions
   */
  async reviewInk(source) {
    return await this.client.reviewInk(source);
  }

  /**
   * Translate ink to Hebrew (uses MCP translate_ink_hebrew tool).
   * @param {string} source - Ink source code
   * @returns {Promise<string>} Translated source
   */
  async translateToHebrew(source) {
    return await this.client.translateToHebrew(source);
  }

  /**
   * Full pipeline: generate → compile → play (uses MCP generate_compile_play).
   * @param {string} prompt - Story description
   * @returns {Promise<Object>} { source, sessionId, text, choices, canContinue }
   */
  async generateCompilePlay(prompt) {
    return await this.client.generateCompilePlay(prompt);
  }

  // ── Model & Service Management ────────────────────────────────

  /** List available models on the MCP server. */
  async listModels(vramGb) {
    return await this.client.listModels(vramGb);
  }

  /** Load a model on the MCP server. */
  async loadModel(modelId) {
    return await this.client.loadModel(modelId);
  }

  /** Get info about the loaded model. */
  async modelInfo() {
    return await this.client.modelInfo();
  }

  /** List available LLM service providers. */
  async listServices() {
    return await this.client.listServices();
  }

  /** Connect to a cloud LLM service. */
  async connectService(serviceId, options) {
    return await this.client.connectService(serviceId, options);
  }

  // ── Health & Config ───────────────────────────────────────────

  /**
   * Check if the MCP server is available.
   * @returns {Promise<boolean>}
   */
  async isAvailable() {
    return await this.client.isAvailable();
  }

  /**
   * Get client configuration info.
   * @returns {Object}
   */
  getConfig() {
    return {
      baseURL: this.serverUrl,
      model: 'mcp-server-managed',
      temperature: null,
      maxTokens: null,
      backend: 'mcp',
    };
  }
}

module.exports = McpLlmClient;
