/**
 * Browser client for the Inky asset event pipeline.
 *
 * Connects to the Ktor /rsocket WebSocket endpoint and streams
 * asset events (msgpack binary frames) from the AssetEventBus.
 *
 * Protocol (AsyncAPI contract: ink-asset-events.yaml):
 *   Client → Server (msgpack):
 *     { type: "subscribe", session_id: "...", channels: [...] }
 *     { type: "fire_and_forget", channel: "...", data: {...} }
 *   Server → Client (msgpack):
 *     { channel: "ink/story/tags", event: {...}, timestamp: 123456 }
 *
 * Falls back to JSON text frames if @msgpack/msgpack is not available.
 */

import { encode, decode } from "@msgpack/msgpack";

// ── Channel constants (match AsyncAPI contract) ──

export const CHANNEL_STORY_TAGS = "ink/story/tags";
export const CHANNEL_ASSET_LOAD = "ink/asset/load";
export const CHANNEL_ASSET_LOADED = "ink/asset/loaded";
export const CHANNEL_INVENTORY_CHANGE = "ink/inventory/change";
export const CHANNEL_VOICE_SYNTHESIZE = "ink/voice/synthesize";
export const CHANNEL_VOICE_READY = "ink/voice/ready";

export const ALL_CHANNELS = [
  CHANNEL_STORY_TAGS,
  CHANNEL_ASSET_LOAD,
  CHANNEL_ASSET_LOADED,
  CHANNEL_INVENTORY_CHANGE,
  CHANNEL_VOICE_SYNTHESIZE,
  CHANNEL_VOICE_READY,
] as const;

export type AssetEventChannel = (typeof ALL_CHANNELS)[number];

// ── Event types ──

export interface AssetRef {
  emoji: string;
  category_name: string;
  category_type: string;
  mesh_path: string;
  anim_set_id: string;
  voice_ref?: VoiceRef;
}

export interface VoiceRef {
  character_id: string;
  language: string;
  flac_path: string;
}

export interface InkTagEvent {
  session_id: string;
  knot: string;
  tags: string[];
  resolved_assets: AssetRef[];
  timestamp: number;
}

export interface AssetLoadRequest {
  session_id: string;
  asset: AssetRef;
  priority: "immediate" | "preload" | "lazy";
  timestamp: number;
}

export interface InventoryChangeEvent {
  session_id: string;
  action: "equip" | "unequip" | "add" | "remove" | "use" | "drop";
  emoji: string;
  item_name: string;
  asset?: AssetRef;
  timestamp: number;
}

export interface VoiceSynthRequest {
  session_id: string;
  text: string;
  voice_ref: VoiceRef;
  timestamp: number;
}

export type AssetEvent =
  | InkTagEvent
  | AssetLoadRequest
  | InventoryChangeEvent
  | VoiceSynthRequest;

export interface AssetEventMessage {
  channel: AssetEventChannel;
  event: AssetEvent;
  timestamp: number;
  replay?: boolean;
}

// ── Callback types ──

export type AssetEventCallback = (message: AssetEventMessage) => void;

// ── Client ──

export interface AssetEventClientOptions {
  /** WebSocket URL (default: ws://localhost:8080/rsocket) */
  url?: string;
  /** Session ID to subscribe to (empty = all sessions) */
  sessionId?: string;
  /** Channels to subscribe to (default: all) */
  channels?: AssetEventChannel[];
  /** Called when connection opens */
  onOpen?: () => void;
  /** Called when connection closes */
  onClose?: (code: number, reason: string) => void;
  /** Called on error */
  onError?: (error: Event) => void;
  /** Auto-reconnect on disconnect (default: true) */
  autoReconnect?: boolean;
  /** Reconnect delay in ms (default: 2000) */
  reconnectDelay?: number;
}

export class AssetEventClient {
  private ws: WebSocket | null = null;
  private listeners = new Map<string, Set<AssetEventCallback>>();
  private globalListeners = new Set<AssetEventCallback>();
  private options: Required<AssetEventClientOptions>;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private _connected = false;

  constructor(options: AssetEventClientOptions = {}) {
    this.options = {
      url: options.url ?? "ws://localhost:8080/rsocket",
      sessionId: options.sessionId ?? "",
      channels: options.channels ?? [...ALL_CHANNELS],
      onOpen: options.onOpen ?? (() => {}),
      onClose: options.onClose ?? (() => {}),
      onError: options.onError ?? (() => {}),
      autoReconnect: options.autoReconnect ?? true,
      reconnectDelay: options.reconnectDelay ?? 2000,
    };
  }

  get connected(): boolean {
    return this._connected;
  }

  /** Connect to the asset event WebSocket. */
  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) return;

    this.ws = new WebSocket(this.options.url);
    this.ws.binaryType = "arraybuffer";

    this.ws.onopen = () => {
      this._connected = true;
      this.options.onOpen();
      this.sendSubscribe();
    };

    this.ws.onmessage = (event: MessageEvent) => {
      if (event.data instanceof ArrayBuffer) {
        this.handleBinaryMessage(new Uint8Array(event.data));
      } else if (typeof event.data === "string") {
        this.handleTextMessage(event.data);
      }
    };

    this.ws.onclose = (event: CloseEvent) => {
      this._connected = false;
      this.options.onClose(event.code, event.reason);
      if (this.options.autoReconnect) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (event: Event) => {
      this.options.onError(event);
    };
  }

  /** Disconnect from the WebSocket. */
  disconnect(): void {
    this.options.autoReconnect = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
    this.ws = null;
    this._connected = false;
  }

  /** Subscribe to events on a specific channel. Returns unsubscribe function. */
  on(channel: AssetEventChannel, callback: AssetEventCallback): () => void {
    if (!this.listeners.has(channel)) {
      this.listeners.set(channel, new Set());
    }
    this.listeners.get(channel)!.add(callback);
    return () => this.listeners.get(channel)?.delete(callback);
  }

  /** Subscribe to all events across all channels. Returns unsubscribe function. */
  onAll(callback: AssetEventCallback): () => void {
    this.globalListeners.add(callback);
    return () => this.globalListeners.delete(callback);
  }

  /** Fire-and-forget: confirm asset loaded (client → server). */
  confirmAssetLoaded(sessionId: string, assetId: string, meshPath: string): void {
    this.sendBinary({
      type: "fire_and_forget",
      channel: CHANNEL_ASSET_LOADED,
      data: {
        session_id: sessionId,
        asset_id: assetId,
        mesh_path: meshPath,
        timestamp: Date.now(),
      },
    });
  }

  /** Fire-and-forget: confirm voice synthesis ready (client → server). */
  confirmVoiceReady(sessionId: string, audioUrl: string, durationMs: number): void {
    this.sendBinary({
      type: "fire_and_forget",
      channel: CHANNEL_VOICE_READY,
      data: {
        session_id: sessionId,
        audio_url: audioUrl,
        duration_ms: durationMs,
        timestamp: Date.now(),
      },
    });
  }

  /** Request recent events for a channel. */
  requestRecent(channel: AssetEventChannel, limit = 50): void {
    this.sendBinary({
      type: "request",
      channel,
      limit,
    });
  }

  // ── Private methods ──

  private sendSubscribe(): void {
    this.sendBinary({
      type: "subscribe",
      session_id: this.options.sessionId,
      channels: this.options.channels,
    });
  }

  private sendBinary(data: unknown): void {
    if (this.ws?.readyState !== WebSocket.OPEN) return;
    const bytes = encode(data);
    this.ws.send(bytes);
  }

  private handleBinaryMessage(data: Uint8Array): void {
    try {
      const msg = decode(data) as AssetEventMessage;
      this.dispatchEvent(msg);
    } catch {
      // Silently ignore malformed msgpack
    }
  }

  private handleTextMessage(data: string): void {
    try {
      const msg = JSON.parse(data) as AssetEventMessage;
      this.dispatchEvent(msg);
    } catch {
      // Silently ignore malformed JSON
    }
  }

  private dispatchEvent(msg: AssetEventMessage): void {
    // Channel-specific listeners
    const channelListeners = this.listeners.get(msg.channel);
    if (channelListeners) {
      for (const cb of channelListeners) {
        try {
          cb(msg);
        } catch {
          // Ignore callback errors
        }
      }
    }

    // Global listeners
    for (const cb of this.globalListeners) {
      try {
        cb(msg);
      } catch {
        // Ignore callback errors
      }
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, this.options.reconnectDelay);
  }
}

/** Create and connect an asset event client. */
export function createAssetEventClient(
  options?: AssetEventClientOptions
): AssetEventClient {
  const client = new AssetEventClient(options);
  client.connect();
  return client;
}
