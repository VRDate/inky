/**
 * @inky/remirror-ink — Remirror (ProseMirror) extension for ink.
 *
 * Provides:
 * - InkExtension: commands (insertKnot, insertChoice, insertDivert, etc.)
 * - Node views: ```ink code blocks rendered by Ace or CodeMirror (configurable)
 * - Schema: inkCodeBlock, inkKnot, inkChoice ProseMirror nodes
 * - Yjs: collaborative editing via y-prosemirror
 */

export { InkExtension } from "./InkExtension";
export {
  type InkBlockEditor,
  type InkNodeViewConfig,
  createCodeMirrorInkNodeView,
} from "./ink-node-view";
export {
  inkCodeBlockSpec,
  inkKnotSpec,
  inkChoiceSpec,
} from "./ink-schema";
export {
  createInkYjsDoc,
  getInkBlockYText,
} from "./ink-yjs";
