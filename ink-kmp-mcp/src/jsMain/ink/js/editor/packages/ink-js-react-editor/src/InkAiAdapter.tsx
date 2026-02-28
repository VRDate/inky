/**
 * AI Adapter for the React ink editor.
 *
 * Provides AI/LLM capabilities (chat, generate, review, fix, explain, translate)
 * via the Kotlin MCP server. Uses @inky/common/ai-mcp-client under the hood.
 *
 * Usage:
 *   <InkAiProvider serverUrl="http://localhost:3001">
 *     <YourEditor />
 *   </InkAiProvider>
 *
 *   // In any child component:
 *   const ai = useInkAi();
 *   const result = await ai.generateInk("A detective story in a train");
 *   console.log(result.ink_source); // generated ink code
 */

import React, { createContext, useContext, useMemo, useState, useCallback } from "react";
import {
  InkMcpClient,
  createInkMcpClient,
  type LlmModelInfo,
  type LlmService,
  type LoadModelResult,
  type GenerateInkResult,
  type ReviewInkResult,
  type GenerateCompilePlayResult,
  type ListModelsResult,
  type ListServicesResult,
  type ConnectServiceResult,
} from "@inky/common/ai-mcp-client";

// ── Types ──────────────────────────────────────────────────────────

export interface InkAiState {
  /** Whether the MCP server is reachable. */
  available: boolean;
  /** Currently loaded model ID (null if none loaded). */
  modelId: string | null;
  /** Connected service ID (null if using local JLama). */
  serviceId: string | null;
  /** Whether an AI operation is in progress. */
  loading: boolean;
  /** Last error message (null if no error). */
  error: string | null;
}

export interface InkAiActions {
  // ── Chat ──
  /** Send a chat message and get a response. */
  chat(message: string): Promise<string>;

  // ── Ink Operations ──
  /** Generate ink code from a natural language prompt. */
  generateInk(prompt: string): Promise<GenerateInkResult>;
  /** Review ink source for issues and suggestions. */
  reviewInk(source: string): Promise<ReviewInkResult>;
  /** Translate ink to Hebrew preserving syntax. */
  translateToHebrew(source: string): Promise<string>;
  /** Full pipeline: generate → compile → start story. */
  generateCompilePlay(prompt: string): Promise<GenerateCompilePlayResult>;
  /** Fix ink compilation errors using AI. */
  fixErrors(code: string, errors: Array<{ line: number; message: string }>): Promise<{
    fixedCode: string;
    explanation: string;
  }>;
  /** Explain ink code or concepts. */
  explain(query: string, code?: string): Promise<string>;

  // ── Model Management ──
  /** List available models. */
  listModels(vramGb?: number): Promise<ListModelsResult>;
  /** Load a model by ID. */
  loadModel(modelId: string): Promise<LoadModelResult>;
  /** Get info about currently loaded model. */
  modelInfo(): Promise<Record<string, unknown>>;

  // ── Service Management ──
  /** List available LLM service providers. */
  listServices(): Promise<ListServicesResult>;
  /** Connect to an external LLM service. */
  connectService(serviceId: string, options?: { apiKey?: string; model?: string }): Promise<ConnectServiceResult>;

  // ── Health ──
  /** Refresh availability status. */
  checkAvailability(): Promise<boolean>;
}

export type InkAiContext = InkAiState & InkAiActions;

// ── Context ────────────────────────────────────────────────────────

const AiContext = createContext<InkAiContext | null>(null);

// ── Provider ───────────────────────────────────────────────────────

export interface InkAiProviderProps {
  /** MCP server URL. */
  serverUrl?: string;
  children: React.ReactNode;
}

export function InkAiProvider({ serverUrl = "http://localhost:3001", children }: InkAiProviderProps) {
  const client = useMemo(() => createInkMcpClient(serverUrl), [serverUrl]);

  const [state, setState] = useState<InkAiState>({
    available: false,
    modelId: null,
    serviceId: null,
    loading: false,
    error: null,
  });

  /** Wrap an async operation with loading/error state management. */
  const withLoading = useCallback(
    async <T,>(fn: () => Promise<T>): Promise<T> => {
      setState((s) => ({ ...s, loading: true, error: null }));
      try {
        const result = await fn();
        setState((s) => ({ ...s, loading: false }));
        return result;
      } catch (e: any) {
        const error = e.message || String(e);
        setState((s) => ({ ...s, loading: false, error }));
        throw e;
      }
    },
    []
  );

  const actions: InkAiActions = useMemo(
    () => ({
      chat: (message) => withLoading(() => client.chat(message)),
      generateInk: (prompt) => withLoading(() => client.generateInk(prompt)),
      reviewInk: (source) => withLoading(() => client.reviewInk(source)),
      translateToHebrew: (source) => withLoading(() => client.translateToHebrew(source)),
      generateCompilePlay: (prompt) => withLoading(() => client.generateCompilePlay(prompt)),
      fixErrors: (code, errors) => withLoading(() => client.fixErrors(code, errors)),
      explain: (query, code) => withLoading(() => client.explain(query, code)),

      listModels: (vramGb) => withLoading(() => client.listModels(vramGb)),
      loadModel: async (modelId) => {
        const result = await withLoading(() => client.loadModel(modelId));
        setState((s) => ({ ...s, modelId: result.model_id }));
        return result;
      },
      modelInfo: async () => {
        const info = await withLoading(() => client.modelInfo());
        setState((s) => ({ ...s, modelId: (info as any).model_id ?? null }));
        return info;
      },

      listServices: () => withLoading(() => client.listServices()),
      connectService: async (serviceId, options) => {
        const result = await withLoading(() => client.connectService(serviceId, options));
        setState((s) => ({ ...s, serviceId }));
        return result;
      },

      checkAvailability: async () => {
        const available = await client.isAvailable();
        setState((s) => ({ ...s, available }));
        return available;
      },
    }),
    [client, withLoading]
  );

  const ctx: InkAiContext = { ...state, ...actions };

  return <AiContext.Provider value={ctx}>{children}</AiContext.Provider>;
}

// ── Hook ───────────────────────────────────────────────────────────

/**
 * Access AI capabilities from any component inside <InkAiProvider>.
 *
 * @throws Error if used outside of InkAiProvider.
 */
export function useInkAi(): InkAiContext {
  const ctx = useContext(AiContext);
  if (!ctx) throw new Error("useInkAi() must be used within <InkAiProvider>");
  return ctx;
}
