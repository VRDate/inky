/**
 * Yjs collaborative editing binding for Remirror + ink.
 *
 * Uses y-prosemirror to sync the Markdown document. Shares the same
 * Yjs doc as CodeMirror ink blocks (nested Yjs XML fragments).
 *
 * Connects to existing ColabEngine HocusPocus WebSocket at /collab/{docId}
 * (mcp-server/src/ink/mcp/ColabEngine.kt).
 */

import * as Y from "yjs";

/**
 * Create a Yjs document and XML fragment for Remirror collaboration.
 *
 * The Yjs doc contains:
 * - "prosemirror" XML fragment (for Remirror/ProseMirror)
 * - "ink-blocks" map (for individual ink code block Y.Text instances)
 *
 * @param docId - Document ID matching the ColabEngine /collab/{docId} endpoint
 * @returns { ydoc, xmlFragment, inkBlocks }
 */
export function createInkYjsDoc(docId: string) {
  const ydoc = new Y.Doc();
  const xmlFragment = ydoc.getXmlFragment("prosemirror");
  const inkBlocks = ydoc.getMap("ink-blocks");

  return { ydoc, xmlFragment, inkBlocks, docId };
}

/**
 * Get or create a Y.Text instance for a specific ink code block.
 *
 * Each ```ink block in the Markdown has its own Y.Text for collaborative
 * editing. The Y.Text is stored in the "ink-blocks" map by block ID.
 *
 * @param inkBlocks - Y.Map from createInkYjsDoc()
 * @param blockId - Unique ID for the ink code block
 * @returns Y.Text instance for the block
 */
export function getInkBlockYText(inkBlocks: Y.Map<Y.Text>, blockId: string): Y.Text {
  let ytext = inkBlocks.get(blockId);
  if (!ytext) {
    ytext = new Y.Text();
    inkBlocks.set(blockId, ytext);
  }
  return ytext;
}
