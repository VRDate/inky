/**
 * Remirror Markdown editor with embedded ```ink code blocks.
 *
 * The primary Edit mode panel. Ink code blocks are rendered by
 * CodeMirror or Ace (configurable in settings). Yjs-synced.
 */

import React from "react";
import { Remirror, useRemirror } from "@remirror/react";
import { InkExtension, type InkBlockEditor } from "@inky/remirror-ink";

export interface InkProseEditorProps {
  /** Initial Markdown content */
  initialContent?: string;
  /** Called when content changes */
  onChange?: (markdown: string) => void;
  /** Which editor for ```ink blocks: "codemirror" or "ace" */
  inkBlockEditor?: InkBlockEditor;
  /** CSS class name */
  className?: string;
}

export function InkProseEditor({
  initialContent = "",
  onChange,
  inkBlockEditor = "codemirror",
  className = "",
}: InkProseEditorProps) {
  const { manager, state } = useRemirror({
    extensions: () => [new InkExtension()],
    content: initialContent,
    stringHandler: "markdown",
  });

  return (
    <div className={`ink-prose-editor ${className}`}>
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
