/**
 * Yjs collaborative editing binding for CodeMirror 6 + ink.
 *
 * Connects to the existing ColabEngine HocusPocus-compatible WebSocket
 * at /collab/{docId} (implemented in mcp-server/src/ink/mcp/ColabEngine.kt).
 *
 * Uses y-codemirror.next for the CodeMirror ↔ Yjs integration.
 */

import { type Extension } from "@codemirror/state";
import { yCollab } from "y-codemirror.next";
import * as Y from "yjs";

/**
 * Create a Yjs collaborative editing extension for CodeMirror.
 *
 * @param ytext - The Y.Text instance to bind to (from a shared Yjs doc)
 * @param awareness - Optional awareness instance for cursor sharing
 * @returns CodeMirror extension array
 */
export function inkYjsExtension(
  ytext: Y.Text,
  awareness?: unknown
): Extension {
  return yCollab(ytext, awareness as any);
}
