/**
 * MCP JSON-RPC 2.0 Client Utilities
 *
 * Shared MCP response parsing and tool invocation extracted from:
 *   - pwa/popup.js (lines 60-67)
 *   - pwa/sidepanel.js (lines 43-50)
 *   - pwa/background.js (lines 92-104)
 *   - editor/ink-js-react-editor/InkRuntimeAdapter.ts (lines 160-174)
 */

/** Raw MCP JSON-RPC 2.0 response shape. */
export interface McpResponse {
  jsonrpc?: string;
  id?: number;
  result?: {
    content?: Array<{ type: string; text: string }>;
    [key: string]: unknown;
  };
  error?: {
    code?: number;
    message: string;
  };
}

/**
 * Parse an MCP JSON-RPC 2.0 response, extracting the first text content
 * block and deserializing it as JSON.
 *
 * Duplicated pattern:
 *   `resp.result?.content?.[0]?.text → JSON.parse`
 *
 * @param resp  Raw MCP response object
 * @returns     Parsed payload, or the raw response if parsing fails
 */
export function parseMcpResponse<T = unknown>(resp: McpResponse | null | undefined): T {
  if (!resp) throw new Error("Empty MCP response");
  if (resp.error) throw new Error(resp.error.message);

  const text = resp.result?.content?.[0]?.text;
  if (text) {
    return JSON.parse(text) as T;
  }
  // Fallback: return the raw response (e.g. when result has no content array)
  return resp as unknown as T;
}

/**
 * Parse an MCP response without throwing — returns `{ data, error }`.
 * Useful for UI code where errors are displayed rather than caught.
 */
export function parseMcpResponseSafe<T = unknown>(
  resp: McpResponse | null | undefined
): { data: T | null; error: string | null } {
  try {
    return { data: parseMcpResponse<T>(resp), error: null };
  } catch (e: any) {
    return { data: null, error: e.message || String(e) };
  }
}

/**
 * Build a JSON-RPC 2.0 request body for an MCP tool call.
 *
 * @param tool  MCP tool name (e.g. "compile_ink", "start_story", "choose")
 * @param args  Tool arguments
 * @param id    Request ID (defaults to Date.now())
 */
export function buildMcpRequest(
  tool: string,
  args: Record<string, unknown>,
  id?: number
): { body: string; headers: Record<string, string> } {
  return {
    body: JSON.stringify({
      jsonrpc: "2.0",
      id: id ?? Date.now(),
      method: "tools/call",
      params: { name: tool, arguments: args },
    }),
    headers: { "Content-Type": "application/json" },
  };
}

/**
 * Call an MCP tool via HTTP POST and parse the response.
 *
 * Unified from:
 *   - background.js callTool() (lines 92-104)
 *   - InkRuntimeAdapter.ts mcpCall() (lines 160-174)
 *
 * @param serverUrl  Base URL of the MCP server (e.g. "http://localhost:3001")
 * @param tool       MCP tool name
 * @param args       Tool arguments
 * @returns          Parsed response payload
 */
export async function callMcpTool<T = unknown>(
  serverUrl: string,
  tool: string,
  args: Record<string, unknown>
): Promise<T> {
  const { body, headers } = buildMcpRequest(tool, args);
  const resp = await fetch(`${serverUrl}/message`, {
    method: "POST",
    headers,
    body,
  });
  const data: McpResponse = await resp.json();
  return parseMcpResponse<T>(data);
}

/**
 * Check if the MCP server is reachable and healthy.
 *
 * @param serverUrl  Base URL of the MCP server
 * @returns          true if server responds with status "ok"
 */
export async function checkMcpHealth(serverUrl: string): Promise<boolean> {
  try {
    const resp = await fetch(`${serverUrl}/health`);
    const data = await resp.json();
    return data.status === "ok";
  } catch {
    return false;
  }
}
