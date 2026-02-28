/**
 * MCP JSON-RPC 2.0 helpers for browser extension (no npm modules).
 *
 * Canonical source: @inky/common/src/mcp-client.ts
 * This file is a browser-native subset for use in Chrome/Edge/Firefox extensions
 * that cannot use npm modules directly.
 */

/**
 * Parse an MCP JSON-RPC 2.0 response, extracting the first text content
 * block and deserializing it as JSON.
 *
 * @param {object} resp  Raw MCP response
 * @returns {object}     Parsed payload, or raw response on parse failure
 */
export function parseMcpResponse(resp) {
  if (!resp) throw new Error('Empty MCP response');
  if (resp.error) throw new Error(resp.error.message);

  const text = resp.result?.content?.[0]?.text;
  if (text) return JSON.parse(text);
  return resp;
}

/**
 * Parse an MCP response without throwing — returns { data, error }.
 *
 * @param {object} resp  Raw MCP response
 * @returns {{ data: object|null, error: string|null }}
 */
export function parseMcpResponseSafe(resp) {
  try {
    return { data: parseMcpResponse(resp), error: null };
  } catch (e) {
    return { data: null, error: e.message || String(e) };
  }
}
