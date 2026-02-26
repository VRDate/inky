/**
 * React context providing:
 * - Ink runtime adapter (inkjs / MCP / OneJS)
 * - Yjs collaborative document provider
 * - Active story session
 * - Compile status
 * - Edit/Play mode state
 */

import React, { createContext, useContext, useState, useCallback, useRef, useEffect, type ReactNode } from "react";
import {
  type InkRuntimeAdapter,
  type InkStoryState,
  createRuntimeAdapter,
} from "./InkRuntimeAdapter";
import * as Y from "yjs";
import { WebsocketProvider } from "y-websocket";

export type EditorMode = "edit" | "play";

export interface InkEditorContextValue {
  /** Current mode: edit (authoring + preview) or play (readonly) */
  mode: EditorMode;
  /** Toggle between edit and play mode */
  setMode: (mode: EditorMode) => void;
  /** The ink runtime adapter */
  runtime: InkRuntimeAdapter;
  /** Current story state (for preview/play) */
  storyState: InkStoryState | null;
  /** Active session ID */
  sessionId: string | null;
  /** Compile ink source and start a preview session */
  compileAndPreview: (source: string) => Promise<void>;
  /** Make a choice in the current session */
  makeChoice: (index: number) => Promise<void>;
  /** Continue the story */
  continueStory: () => Promise<void>;
  /** Compilation errors */
  errors: string[];
  /** Whether currently compiling */
  isCompiling: boolean;
  /** Yjs document */
  ydoc: Y.Doc;
  /** Yjs WebSocket provider (connected to ColabEngine) */
  yjsProvider: WebsocketProvider | null;
}

const InkEditorContext = createContext<InkEditorContextValue | null>(null);

export interface InkEditorProviderProps {
  children: ReactNode;
  /** MCP server URL (e.g., "http://localhost:3001"). If omitted, uses local inkjs. */
  mcpServerUrl?: string;
  /** Yjs WebSocket URL for ColabEngine (e.g., "ws://localhost:3001/collab") */
  yjsWsUrl?: string;
  /** Document ID for Yjs collaboration */
  docId?: string;
}

export function InkEditorProvider({ children, mcpServerUrl, yjsWsUrl, docId }: InkEditorProviderProps) {
  const [mode, setMode] = useState<EditorMode>("edit");
  const [storyState, setStoryState] = useState<InkStoryState | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [isCompiling, setIsCompiling] = useState(false);

  const runtimeRef = useRef(createRuntimeAdapter(mcpServerUrl));
  const ydocRef = useRef(new Y.Doc());
  const [yjsProvider, setYjsProvider] = useState<WebsocketProvider | null>(null);

  // Connect Yjs provider to ColabEngine WebSocket
  useEffect(() => {
    if (yjsWsUrl && docId) {
      const provider = new WebsocketProvider(yjsWsUrl, docId, ydocRef.current);
      setYjsProvider(provider);
      return () => {
        provider.destroy();
      };
    }
  }, [yjsWsUrl, docId]);

  const compileAndPreview = useCallback(async (source: string) => {
    setIsCompiling(true);
    setErrors([]);
    try {
      const result = await runtimeRef.current.compile(source);
      if (result.errors.length > 0) {
        setErrors(result.errors);
        setIsCompiling(false);
        return;
      }
      // End previous session
      if (sessionId) {
        await runtimeRef.current.endSession(sessionId).catch(() => {});
      }
      const newSessionId = await runtimeRef.current.startStory(result.json);
      setSessionId(newSessionId);
      const state = await runtimeRef.current.continueStory(newSessionId);
      setStoryState(state);
    } catch (e: any) {
      setErrors([e.message || String(e)]);
    }
    setIsCompiling(false);
  }, [sessionId]);

  const makeChoice = useCallback(async (index: number) => {
    if (!sessionId) return;
    const state = await runtimeRef.current.choose(sessionId, index);
    setStoryState(state);
  }, [sessionId]);

  const continueStory = useCallback(async () => {
    if (!sessionId) return;
    const state = await runtimeRef.current.continueStory(sessionId);
    setStoryState(state);
  }, [sessionId]);

  return (
    <InkEditorContext.Provider
      value={{
        mode,
        setMode,
        runtime: runtimeRef.current,
        storyState,
        sessionId,
        compileAndPreview,
        makeChoice,
        continueStory,
        errors,
        isCompiling,
        ydoc: ydocRef.current,
        yjsProvider,
      }}
    >
      {children}
    </InkEditorContext.Provider>
  );
}

export function useInkEditor() {
  const ctx = useContext(InkEditorContext);
  if (!ctx) throw new Error("useInkEditor must be used within <InkEditorProvider>");
  return ctx;
}
