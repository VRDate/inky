/**
 * CodeMirror 6 editor for pure .ink source files.
 *
 * Features: ink syntax highlighting, autocompletion, folding, Yjs collab.
 * Used standalone or embedded in Remirror ```ink code blocks.
 */

import React, { useEffect, useRef } from "react";
import { EditorView } from "@codemirror/view";
import { EditorState } from "@codemirror/state";
import { inkExtension } from "@inky/codemirror-ink";
import { inkYjsExtension } from "@inky/codemirror-ink";
import { useInkEditor } from "./InkEditorProvider";

export interface InkCodeEditorProps {
  /** Initial content (ignored if Yjs is connected) */
  initialContent?: string;
  /** Called when content changes */
  onChange?: (content: string) => void;
  /** CSS class name */
  className?: string;
  /** Whether to enable Yjs collab (requires InkEditorProvider with yjsWsUrl) */
  collaborative?: boolean;
  /** Yjs text field name (default: "ink-source") */
  yjsFieldName?: string;
}

export function InkCodeEditor({
  initialContent = "",
  onChange,
  className = "",
  collaborative = false,
  yjsFieldName = "ink-source",
}: InkCodeEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const { ydoc } = useInkEditor();

  useEffect(() => {
    if (!containerRef.current) return;

    const extensions = [
      inkExtension(),
      EditorView.updateListener.of((update) => {
        if (update.docChanged && onChange) {
          onChange(update.state.doc.toString());
        }
      }),
    ];

    // Add Yjs collab if enabled
    if (collaborative && ydoc) {
      const ytext = ydoc.getText(yjsFieldName);
      extensions.push(inkYjsExtension(ytext));
    }

    const state = EditorState.create({
      doc: collaborative ? undefined : initialContent,
      extensions,
    });

    const view = new EditorView({
      state,
      parent: containerRef.current,
    });

    viewRef.current = view;

    return () => {
      view.destroy();
      viewRef.current = null;
    };
  }, [collaborative, yjsFieldName]);

  return <div ref={containerRef} className={`ink-code-editor ${className}`} />;
}
