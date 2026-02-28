// ═══════════════════════════════════════════════════════════════
// MsgpackAssetEventClient.cs — WebSocket+msgpack transport client
// Connects to the Kotlin MCP server's /rsocket endpoint.
// Uses ContractlessStandardResolver to match Jackson's string-key
// msgpack format (McpRouter.kt:500-502).
// ═══════════════════════════════════════════════════════════════

using System;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Ink.Common;
using Ink.Common.Transport;

namespace Ink.Maui
{
    /// <summary>
    /// WebSocket+msgpack transport client for MAUI.
    /// Receives asset events from the Kotlin MCP server.
    /// </summary>
    public class MsgpackAssetEventClient : IAssetEventTransport
    {
        private ClientWebSocket? _ws;
        private CancellationTokenSource? _cts;

        public event Action<InkTagEvent>? OnTagEvent;
        public event Action<AssetLoadRequest>? OnAssetLoad;
        public event Action<string>? OnAssetLoaded;
        public event Action<InventoryChangeEvent>? OnInventoryChange;
        public event Action<VoiceSynthRequest>? OnVoiceSynthesize;
        public event Action<string>? OnVoiceReady;

        public async Task ConnectAsync(string url, CancellationToken ct = default)
        {
            _ws = new ClientWebSocket();
            _cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            await _ws.ConnectAsync(new Uri(url), _cts.Token);
            _ = ReceiveLoop(_cts.Token);
        }

        public async Task SubscribeAsync(string sessionId, string[] channels, CancellationToken ct = default)
        {
            if (_ws?.State != WebSocketState.Open) return;

            // Subscribe message (JSON text frame for initial handshake)
            var msg = $"{{\"type\":\"subscribe\",\"session_id\":\"{sessionId}\",\"channels\":[{string.Join(",", Array.ConvertAll(channels, c => $"\"{c}\""))}]}}";
            var bytes = Encoding.UTF8.GetBytes(msg);
            await _ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, ct);
        }

        public async Task FireAndForgetAsync(string channel, string eventJson, CancellationToken ct = default)
        {
            if (_ws?.State != WebSocketState.Open) return;

            var msg = $"{{\"type\":\"fire_and_forget\",\"channel\":\"{channel}\",\"data\":{eventJson}}}";
            var bytes = Encoding.UTF8.GetBytes(msg);
            await _ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, ct);
        }

        public async Task DisconnectAsync(CancellationToken ct = default)
        {
            _cts?.Cancel();
            if (_ws?.State == WebSocketState.Open)
                await _ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", ct);
            _ws?.Dispose();
        }

        private async Task ReceiveLoop(CancellationToken ct)
        {
            var buffer = new byte[8192];
            while (!ct.IsCancellationRequested && _ws?.State == WebSocketState.Open)
            {
                try
                {
                    var result = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), ct);
                    if (result.MessageType == WebSocketMessageType.Close) break;

                    // TODO: Deserialize msgpack binary frames using MessagePack-CSharp
                    // For now, handle JSON text frames as fallback
                    if (result.MessageType == WebSocketMessageType.Text)
                    {
                        var json = Encoding.UTF8.GetString(buffer, 0, result.Count);
                        DispatchEvent(json);
                    }
                }
                catch (OperationCanceledException) { break; }
                catch { /* log and continue */ }
            }
        }

        private void DispatchEvent(string json)
        {
            // TODO: Parse channel from envelope and dispatch to correct event
            // For now this is a placeholder — full msgpack deserialization
            // will use MessagePack.MessagePackSerializer with ContractlessStandardResolver
        }
    }
}
