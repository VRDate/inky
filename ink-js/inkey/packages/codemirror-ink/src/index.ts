/**
 * @inky/codemirror-ink — CodeMirror 6 extension for ink interactive fiction.
 *
 * Provides syntax highlighting, autocompletion, code folding, and Yjs
 * collaborative editing for .ink files.
 *
 * Usage:
 *   import { inkExtension } from "@inky/codemirror-ink";
 *   const view = new EditorView({ extensions: [inkExtension()] });
 */

import { type Extension } from "@codemirror/state";
import { inkLanguage } from "./ink-language";
import { inkSyntaxHighlighting } from "./ink-highlight";
import { inkCompletion } from "./ink-complete";
import { inkFolding } from "./ink-fold";

export { inkLanguage, inkStreamParser } from "./ink-language";
export { inkHighlightStyle, inkSyntaxHighlighting } from "./ink-highlight";
export { inkCompletion } from "./ink-complete";
export { inkFolding } from "./ink-fold";
export { inkYjsExtension } from "./ink-yjs";

/**
 * Bundle all ink extensions for CodeMirror 6.
 *
 * Includes: language mode, syntax highlighting, autocompletion, folding.
 * Yjs is NOT included by default — use inkYjsExtension() separately.
 */
export function inkExtension(): Extension {
  return [
    inkLanguage,
    inkSyntaxHighlighting,
    inkCompletion,
    inkFolding,
  ];
}
