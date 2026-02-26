/**
 * Full readonly story player (Play mode).
 *
 * Text, choices, variable watch, rewind.
 * Uses inkjs (browser) or C# ink runtime via OneJS binding (Unity WebGL).
 */

import React, { useState, useCallback } from "react";
import { useInkEditor } from "./InkEditorProvider";

export interface InkPlayerProps {
  className?: string;
}

interface StoryLine {
  type: "text" | "choice-made";
  content: string;
  tags?: string[];
}

export function InkPlayer({ className = "" }: InkPlayerProps) {
  const { storyState, makeChoice, continueStory, errors } = useInkEditor();
  const [history, setHistory] = useState<StoryLine[]>([]);

  const handleChoice = useCallback(async (index: number) => {
    if (!storyState) return;
    const choiceText = storyState.choices[index]?.text || `Choice ${index}`;
    setHistory((prev) => [
      ...prev,
      { type: "text", content: storyState.text, tags: storyState.tags },
      { type: "choice-made", content: choiceText },
    ]);
    await makeChoice(index);
  }, [storyState, makeChoice]);

  return (
    <div className={`ink-player ${className}`}>
      <div className="ink-player__header">
        <span className="ink-player__title">Play Mode</span>
      </div>

      {errors.length > 0 && (
        <div className="ink-player__errors">
          {errors.map((err, i) => (
            <div key={i} className="ink-player__error">{err}</div>
          ))}
        </div>
      )}

      <div className="ink-player__scroll">
        {/* History */}
        {history.map((line, i) => (
          <div key={i} className={`ink-player__line ink-player__line--${line.type}`}>
            {line.type === "choice-made" ? (
              <span className="ink-player__choice-made">&gt; {line.content}</span>
            ) : (
              <>
                <span dangerouslySetInnerHTML={{ __html: line.content.replace(/\n/g, "<br>") }} />
                {line.tags && line.tags.length > 0 && (
                  <span className="ink-player__tags">
                    {line.tags.map((t, j) => <span key={j} className="ink-player__tag">#{t}</span>)}
                  </span>
                )}
              </>
            )}
          </div>
        ))}

        {/* Current state */}
        {storyState && (
          <div className="ink-player__current">
            <div
              className="ink-player__text"
              dangerouslySetInnerHTML={{ __html: storyState.text.replace(/\n/g, "<br>") }}
            />

            {storyState.choices.length > 0 && (
              <div className="ink-player__choices">
                {storyState.choices.map((choice) => (
                  <button
                    key={choice.index}
                    className="ink-player__choice-button"
                    onClick={() => handleChoice(choice.index)}
                  >
                    {choice.text}
                  </button>
                ))}
              </div>
            )}

            {!storyState.canContinue && storyState.choices.length === 0 && (
              <div className="ink-player__end">— The End —</div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
