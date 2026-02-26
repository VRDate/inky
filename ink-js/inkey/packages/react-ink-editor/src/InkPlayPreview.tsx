/**
 * Live play preview panel (Edit mode right side).
 *
 * Auto-recompiles on editor change, shows story text + choices.
 * Uses the runtime adapter from InkEditorProvider (inkjs / MCP / OneJS).
 */

import React from "react";
import { useInkEditor } from "./InkEditorProvider";

export interface InkPlayPreviewProps {
  className?: string;
}

export function InkPlayPreview({ className = "" }: InkPlayPreviewProps) {
  const { storyState, makeChoice, errors, isCompiling } = useInkEditor();

  return (
    <div className={`ink-play-preview ${className}`}>
      <div className="ink-play-preview__header">
        <span className="ink-play-preview__title">Preview</span>
        {isCompiling && <span className="ink-play-preview__status">Compiling...</span>}
        {errors.length > 0 && (
          <span className="ink-play-preview__status ink-play-preview__status--error">
            {errors.length} error{errors.length > 1 ? "s" : ""}
          </span>
        )}
      </div>

      {errors.length > 0 && (
        <div className="ink-play-preview__errors">
          {errors.map((err, i) => (
            <div key={i} className="ink-play-preview__error">{err}</div>
          ))}
        </div>
      )}

      {storyState && (
        <div className="ink-play-preview__story">
          <div
            className="ink-play-preview__text"
            dangerouslySetInnerHTML={{ __html: storyState.text.replace(/\n/g, "<br>") }}
          />

          {storyState.tags.length > 0 && (
            <div className="ink-play-preview__tags">
              {storyState.tags.map((tag, i) => (
                <span key={i} className="ink-play-preview__tag">#{tag}</span>
              ))}
            </div>
          )}

          {storyState.choices.length > 0 && (
            <div className="ink-play-preview__choices">
              {storyState.choices.map((choice) => (
                <button
                  key={choice.index}
                  className="ink-play-preview__choice"
                  onClick={() => makeChoice(choice.index)}
                >
                  {choice.text}
                </button>
              ))}
            </div>
          )}

          {!storyState.canContinue && storyState.choices.length === 0 && (
            <div className="ink-play-preview__end">— End —</div>
          )}
        </div>
      )}

      {!storyState && !isCompiling && errors.length === 0 && (
        <div className="ink-play-preview__empty">
          Edit ink source to see a live preview here.
        </div>
      )}
    </div>
  );
}
