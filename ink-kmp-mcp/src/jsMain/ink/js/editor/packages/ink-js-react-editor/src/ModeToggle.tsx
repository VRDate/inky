/**
 * Edit / Play mode toggle (Ctrl+P).
 *
 * Edit = authoring (Remirror MD editor + live preview)
 * Play = readonly story playback
 *
 * Mirrors Unity's Enter/Exit Play Mode concept.
 */

import React, { useEffect } from "react";
import { useInkEditor, type EditorMode } from "./InkEditorProvider";

export interface ModeToggleProps {
  className?: string;
}

export function ModeToggle({ className = "" }: ModeToggleProps) {
  const { mode, setMode } = useInkEditor();

  // Ctrl+P keyboard shortcut
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key === "p") {
        e.preventDefault();
        setMode(mode === "edit" ? "play" : "edit");
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [mode, setMode]);

  return (
    <div className={`ink-mode-toggle ${className}`}>
      <button
        className={`ink-mode-toggle__btn ${mode === "edit" ? "ink-mode-toggle__btn--active" : ""}`}
        onClick={() => setMode("edit")}
        title="Edit Mode (Ctrl+P)"
      >
        Edit
      </button>
      <button
        className={`ink-mode-toggle__btn ${mode === "play" ? "ink-mode-toggle__btn--active" : ""}`}
        onClick={() => setMode("play")}
        title="Play Mode (Ctrl+P)"
      >
        Play
      </button>
    </div>
  );
}
