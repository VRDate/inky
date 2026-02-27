/**
 * Remirror-only Markdown editor with embedded ```ink code blocks.
 *
 * The primary Edit mode panel. Ink code blocks are rendered by
 * CodeMirror or Ace (configurable in settings). Yjs-synced.
 */

import React from "react";
import { Remirror, useRemirror } from "@remirror/react";
import { InkExtension, type InkBlockEditor } from "@inky/remirror-ink";

export interface InkRemirrorEditorProps {
  /** Initial Markdown content */
  initialContent?: string;
  /** Called when content changes */
  onChange?: (markdown: string) => void;
  /** Which editor for ```ink blocks: "codemirror" or "ace" */
  inkBlockEditor?: InkBlockEditor;
  /** CSS class name */
  className?: string;
}

export function InkRemirrorEditor({
  initialContent = "",
  onChange,
  inkBlockEditor = "codemirror",
  className = "",
}: InkRemirrorEditorProps) {
  const { manager, state } = useRemirror({
    extensions: () => [new InkExtension()],
    content: initialContent,
    stringHandler: "markdown",
  });

  return (
    <div className={`ink-remirror-editor ${className}`}>
      <Remirror
        manager={manager}
        initialContent={state}
        onChange={({ helpers }) => {
          if (onChange) {
            onChange(helpers.getMarkdown());
          }
        }}
      />
    </div>
  );
}
