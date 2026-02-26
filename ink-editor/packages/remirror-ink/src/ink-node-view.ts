/**
 * ProseMirror node view for ```ink code blocks.
 *
 * Configurable: user selects Ace or CodeMirror 6 in Remirror settings.
 * Both use the shared @inky/ink-language grammar.
 *
 * When CodeMirror is selected, creates an inline CodeMirror 6 editor
 * with inkExtension() from @inky/codemirror-ink.
 *
 * When Ace is selected, creates an inline Ace editor using the existing
 * ace-ink-mode from app/renderer/ace-ink-mode/ace-ink.js.
 */

/** Editor type for ink code blocks */
export type InkBlockEditor = "codemirror" | "ace";

/** Configuration for ink code block node views */
export interface InkNodeViewConfig {
  /** Which editor to use for ```ink blocks */
  editor: InkBlockEditor;
  /** Optional: callback when block content changes */
  onChange?: (content: string) => void;
}

/**
 * Create a CodeMirror 6 node view for a ProseMirror ```ink code block.
 *
 * This creates an EditorView inside the ProseMirror node, using the
 * inkExtension() bundle for syntax highlighting, completion, and folding.
 */
export function createCodeMirrorInkNodeView(config: InkNodeViewConfig) {
  // Dynamic import to avoid bundling both editors when only one is used
  return async (node: any, view: any, getPos: () => number) => {
    if (config.editor === "codemirror") {
      const { EditorView } = await import("@codemirror/view");
      const { EditorState } = await import("@codemirror/state");
      const { inkExtension } = await import("@inky/codemirror-ink");

      const dom = document.createElement("div");
      dom.className = "ink-code-block ink-code-block--codemirror";

      const cmState = EditorState.create({
        doc: node.textContent,
        extensions: [
          inkExtension(),
          EditorView.updateListener.of((update) => {
            if (update.docChanged && config.onChange) {
              config.onChange(update.state.doc.toString());
            }
          }),
        ],
      });

      new EditorView({ state: cmState, parent: dom });
      return { dom };
    }

    // Ace fallback — creates an Ace editor instance
    const dom = document.createElement("div");
    dom.className = "ink-code-block ink-code-block--ace";
    dom.style.minHeight = "100px";

    // Ace editor is expected to be loaded globally (from existing Inky setup)
    if (typeof (globalThis as any).ace !== "undefined") {
      const ace = (globalThis as any).ace;
      const editor = ace.edit(dom);
      editor.session.setMode("ace/mode/ink");
      editor.setValue(node.textContent, -1);
      editor.on("change", () => {
        if (config.onChange) {
          config.onChange(editor.getValue());
        }
      });
    } else {
      dom.textContent = node.textContent;
    }

    return { dom };
  };
}
