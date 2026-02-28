// ═══════════════════════════════════════════════════════════════
// UnityEventBridge.cs — Subscribes to IAssetEventTransport,
// serializes Ink.Common DTOs to JSON, and forwards to Unity
// via IUnityBridge.SendMessage → UnitySendMessage on mobile
// or WebSocket on desktop.
//
// This is the glue between the Kotlin MCP server event bus
// and the Unity InkAssetEventReceiver.ProcessEvent().
// ═══════════════════════════════════════════════════════════════

using System;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Ink.Common;
using Ink.Common.Transport;

namespace Ink.Maui.Services
{
    /// <summary>
    /// Bridges asset events from <see cref="IAssetEventTransport"/>
    /// (WebSocket+msgpack from Kotlin MCP) into Unity via
    /// <see cref="IUnityBridge"/> (UnitySendMessage on mobile,
    /// WebSocket on desktop).
    /// </summary>
    public class UnityEventBridge : IDisposable
    {
        private readonly IAssetEventTransport _transport;
        private readonly IUnityBridge _unity;
        private readonly JsonSerializerOptions _jsonOptions;

        public UnityEventBridge(IAssetEventTransport transport, IUnityBridge unity)
        {
            _transport = transport;
            _unity = unity;
            _jsonOptions = new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower
            };

            // Wire up transport events → Unity forwarding
            _transport.OnTagEvent += ForwardTagEvent;
            _transport.OnAssetLoad += ForwardAssetLoad;
            _transport.OnInventoryChange += ForwardInventoryChange;
            _transport.OnVoiceSynthesize += ForwardVoiceSynthesize;
        }

        /// <summary>Connect to the MCP server and subscribe to all channels.</summary>
        public async Task ConnectAsync(string mcpUrl, string sessionId, CancellationToken ct = default)
        {
            await _transport.ConnectAsync(mcpUrl, ct);
            await _transport.SubscribeAsync(sessionId, AssetEventChannels.All, ct);
        }

        private void ForwardTagEvent(InkTagEvent evt)
        {
            var json = WrapEvent(AssetEventChannels.StoryTags, evt);
            _unity.SendMessage(
                UnityInkTargets.AssetEventReceiver,
                UnityInkTargets.ProcessEvent,
                json);
        }

        private void ForwardAssetLoad(AssetLoadRequest req)
        {
            var json = WrapEvent(AssetEventChannels.AssetLoad, req);
            _unity.SendMessage(
                UnityInkTargets.AssetEventReceiver,
                UnityInkTargets.ProcessEvent,
                json);
        }

        private void ForwardInventoryChange(InventoryChangeEvent evt)
        {
            var json = WrapEvent(AssetEventChannels.InventoryChange, evt);
            _unity.SendMessage(
                UnityInkTargets.AssetEventReceiver,
                UnityInkTargets.ProcessEvent,
                json);
        }

        private void ForwardVoiceSynthesize(VoiceSynthRequest req)
        {
            var json = WrapEvent(AssetEventChannels.VoiceSynthesize, req);
            _unity.SendMessage(
                UnityInkTargets.AssetEventReceiver,
                UnityInkTargets.ProcessEvent,
                json);
        }

        /// <summary>
        /// Wraps an event DTO in the envelope format expected by
        /// InkAssetEventReceiver.ProcessEventInternal:
        /// { "channel": "ink/story/tags", "event": "{...}", "timestamp": 123 }
        /// </summary>
        private string WrapEvent<T>(string channel, T evt)
        {
            var eventJson = JsonSerializer.Serialize(evt, _jsonOptions);
            var envelope = new
            {
                channel,
                @event = eventJson,
                timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };
            return JsonSerializer.Serialize(envelope, _jsonOptions);
        }

        public void Dispose()
        {
            _transport.OnTagEvent -= ForwardTagEvent;
            _transport.OnAssetLoad -= ForwardAssetLoad;
            _transport.OnInventoryChange -= ForwardInventoryChange;
            _transport.OnVoiceSynthesize -= ForwardVoiceSynthesize;
        }
    }
}
