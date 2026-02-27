/**
 * Abstraction layer over ink runtimes.
 *
 * Detects environment and routes story operations to the right backend:
 * - Browser: uses inkjs directly (no server needed)
 * - Unity OneJS: calls InkOneJsBinding.cs (C# Ink.Runtime.Story)
 * - MCP server: proxies to Kotlin MCP server via HTTP/SSE
 */

export interface InkChoice {
  index: number;
  text: string;
}

export interface InkStoryState {
  text: string;
  choices: InkChoice[];
  canContinue: boolean;
  tags: string[];
  /** Resolved asset events from the tag pipeline (when asset event engine is connected). */
  assetEvents?: AssetEventRef[];
}

/** Asset reference resolved from an ink tag via EmojiAssetManifest. */
export interface AssetEventRef {
  emoji: string;
  category: string;
  type: string;
  meshPath: string;
  animSetId: string;
}

export interface InkRuntimeAdapter {
  /** Compile ink source to JSON */
  compile(source: string): Promise<{ json: string; errors: string[] }>;
  /** Start a story session from compiled JSON */
  startStory(json: string): Promise<string>; // returns sessionId
  /** Continue the story */
  continueStory(sessionId: string): Promise<InkStoryState>;
  /** Make a choice */
  choose(sessionId: string, choiceIndex: number): Promise<InkStoryState>;
  /** Get a variable value */
  getVariable(sessionId: string, name: string): Promise<unknown>;
  /** Set a variable value */
  setVariable(sessionId: string, name: string, value: unknown): Promise<void>;
  /** Save story state */
  saveState(sessionId: string): Promise<string>;
  /** Load story state */
  loadState(sessionId: string, stateJson: string): Promise<void>;
  /** Reset story to beginning */
  resetStory(sessionId: string): Promise<void>;
  /** End session */
  endSession(sessionId: string): Promise<void>;
  /** Subscribe to asset events for a session. Returns unsubscribe function. */
  onAssetEvent?(sessionId: string, callback: (events: AssetEventRef[]) => void): () => void;
}

/** Detect the current runtime environment */
export type RuntimeEnvironment = "browser" | "unity-onejs" | "mcp-server";

export function detectEnvironment(): RuntimeEnvironment {
  // Check for OneJS global (Unity WebGL via OneJS)
  if (typeof (globalThis as any).__oneJS !== "undefined") {
    return "unity-onejs";
  }
  return "browser";
}

/**
 * Create an inkjs-based runtime adapter (browser environment).
 * Uses inkjs npm package directly — no server needed.
 */
export function createInkJsAdapter(): InkRuntimeAdapter {
  const sessions = new Map<string, any>();
  let nextId = 1;

  return {
    async compile(source: string) {
      const { Compiler } = await import("inkjs/full" as any);
      try {
        const compiler = new Compiler(source);
        const story = compiler.Compile();
        return { json: story.ToJson(), errors: [] };
      } catch (e: any) {
        return { json: "", errors: [e.message || String(e)] };
      }
    },

    async startStory(json: string) {
      const { Story } = await import("inkjs" as any);
      const story = new Story(json);
      const id = `inkjs-${nextId++}`;
      sessions.set(id, story);
      return id;
    },

    async continueStory(sessionId: string) {
      const story = sessions.get(sessionId);
      if (!story) throw new Error(`Session not found: ${sessionId}`);
      let text = "";
      while (story.canContinue) {
        text += story.Continue();
      }
      return {
        text,
        choices: story.currentChoices.map((c: any, i: number) => ({ index: i, text: c.text })),
        canContinue: story.canContinue,
        tags: story.currentTags || [],
      };
    },

    async choose(sessionId: string, choiceIndex: number) {
      const story = sessions.get(sessionId);
      if (!story) throw new Error(`Session not found: ${sessionId}`);
      story.ChooseChoiceIndex(choiceIndex);
      return this.continueStory(sessionId);
    },

    async getVariable(sessionId: string, name: string) {
      const story = sessions.get(sessionId);
      if (!story) throw new Error(`Session not found: ${sessionId}`);
      return story.variablesState[name];
    },

    async setVariable(sessionId: string, name: string, value: unknown) {
      const story = sessions.get(sessionId);
      if (!story) throw new Error(`Session not found: ${sessionId}`);
      story.variablesState[name] = value;
    },

    async saveState(sessionId: string) {
      const story = sessions.get(sessionId);
      if (!story) throw new Error(`Session not found: ${sessionId}`);
      return story.state.toJson();
    },

    async loadState(sessionId: string, stateJson: string) {
      const story = sessions.get(sessionId);
      if (!story) throw new Error(`Session not found: ${sessionId}`);
      story.state.LoadJson(stateJson);
    },

    async resetStory(sessionId: string) {
      const story = sessions.get(sessionId);
      if (!story) throw new Error(`Session not found: ${sessionId}`);
      story.ResetState();
    },

    async endSession(sessionId: string) {
      sessions.delete(sessionId);
    },
  };
}

/**
 * Create an MCP server-backed runtime adapter.
 * Proxies all operations to the Kotlin MCP server via HTTP.
 */
export function createMcpAdapter(serverUrl: string): InkRuntimeAdapter {
  async function mcpCall(tool: string, args: Record<string, unknown>) {
    const resp = await fetch(`${serverUrl}/message`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        jsonrpc: "2.0",
        id: Date.now(),
        method: "tools/call",
        params: { name: tool, arguments: args },
      }),
    });
    const data = await resp.json();
    if (data.error) throw new Error(data.error.message);
    return JSON.parse(data.result?.content?.[0]?.text || "{}");
  }

  return {
    async compile(source: string) {
      return mcpCall("compile_ink", { source });
    },
    async startStory(json: string) {
      const result = await mcpCall("start_story_json", { json });
      return result.sessionId;
    },
    async continueStory(sessionId: string) {
      return mcpCall("continue_story", { sessionId });
    },
    async choose(sessionId: string, choiceIndex: number) {
      return mcpCall("choose", { sessionId, choiceIndex });
    },
    async getVariable(sessionId: string, name: string) {
      const result = await mcpCall("get_variable", { sessionId, name });
      return result.value;
    },
    async setVariable(sessionId: string, name: string, value: unknown) {
      await mcpCall("set_variable", { sessionId, name, value });
    },
    async saveState(sessionId: string) {
      const result = await mcpCall("save_state", { sessionId });
      return result.state;
    },
    async loadState(sessionId: string, stateJson: string) {
      await mcpCall("load_state", { sessionId, state: stateJson });
    },
    async resetStory(sessionId: string) {
      await mcpCall("reset_story", { sessionId });
    },
    async endSession(sessionId: string) {
      await mcpCall("end_session", { sessionId });
    },
  };
}

/**
 * Create a Unity OneJS-backed runtime adapter.
 * Calls InkOneJsBinding.cs through OneJS global bindings.
 */
export function createOneJsAdapter(): InkRuntimeAdapter {
  const bridge = (globalThis as any).__inkBridge;
  if (!bridge) throw new Error("InkOneJsBinding not available — are you running inside Unity OneJS?");

  return {
    async compile(source: string) {
      return JSON.parse(bridge.Compile(source));
    },
    async startStory(json: string) {
      return bridge.StartStory(json);
    },
    async continueStory(sessionId: string) {
      return JSON.parse(bridge.ContinueStory(sessionId));
    },
    async choose(sessionId: string, choiceIndex: number) {
      return JSON.parse(bridge.Choose(sessionId, choiceIndex));
    },
    async getVariable(sessionId: string, name: string) {
      return JSON.parse(bridge.GetVariable(sessionId, name));
    },
    async setVariable(sessionId: string, name: string, value: unknown) {
      bridge.SetVariable(sessionId, name, JSON.stringify(value));
    },
    async saveState(sessionId: string) {
      return bridge.SaveState(sessionId);
    },
    async loadState(sessionId: string, stateJson: string) {
      bridge.LoadState(sessionId, stateJson);
    },
    async resetStory(sessionId: string) {
      bridge.ResetStory(sessionId);
    },
    async endSession(sessionId: string) {
      bridge.EndSession(sessionId);
    },
    onAssetEvent(sessionId: string, callback: (events: AssetEventRef[]) => void) {
      // OneJS in-process: call GetAssetEvents (11th method on __inkBridge)
      if (typeof bridge.GetAssetEvents === "function") {
        const json = bridge.GetAssetEvents(sessionId);
        const events: AssetEventRef[] = JSON.parse(json);
        if (events.length > 0) callback(events);
      }
      // No persistent subscription in OneJS — caller polls via continueStory()
      return () => {};
    },
  };
}

/** Create the appropriate runtime adapter for the current environment */
export function createRuntimeAdapter(mcpServerUrl?: string): InkRuntimeAdapter {
  const env = detectEnvironment();
  switch (env) {
    case "unity-onejs":
      return createOneJsAdapter();
    case "mcp-server":
    case "browser":
      return mcpServerUrl ? createMcpAdapter(mcpServerUrl) : createInkJsAdapter();
  }
}
