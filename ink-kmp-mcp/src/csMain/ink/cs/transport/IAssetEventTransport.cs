// ═══════════════════════════════════════════════════════════════
// IAssetEventTransport.cs — Transport contract for asset events
// Shared across MAUI (WebSocket+msgpack) and Unity (in-process).
// Matches the 6 channels from AssetEventBus.kt.
// ═══════════════════════════════════════════════════════════════

using System;
using System.Threading;
using System.Threading.Tasks;

namespace Ink.Common.Transport
{
    /// <summary>
    /// Transport contract for receiving asset events from the ink MCP server.
    /// MAUI uses WebSocket+msgpack, Unity uses in-process OneJS bridge.
    /// </summary>
    public interface IAssetEventTransport
    {
        /// <summary>Connect to the event bus.</summary>
        Task ConnectAsync(string url, CancellationToken ct = default);

        /// <summary>Subscribe to one or more channels.</summary>
        Task SubscribeAsync(string sessionId, string[] channels, CancellationToken ct = default);

        /// <summary>Send a fire-and-forget event to a channel.</summary>
        Task FireAndForgetAsync(string channel, string eventJson, CancellationToken ct = default);

        /// <summary>Disconnect from the event bus.</summary>
        Task DisconnectAsync(CancellationToken ct = default);

        /// <summary>Fired when a tag event is received.</summary>
        event Action<InkTagEvent> OnTagEvent;

        /// <summary>Fired when an asset load is requested.</summary>
        event Action<AssetLoadRequest> OnAssetLoad;

        /// <summary>Fired when an asset has been loaded.</summary>
        event Action<string> OnAssetLoaded;

        /// <summary>Fired when inventory changes.</summary>
        event Action<InventoryChangeEvent> OnInventoryChange;

        /// <summary>Fired when voice synthesis is requested.</summary>
        event Action<VoiceSynthRequest> OnVoiceSynthesize;

        /// <summary>Fired when voice is ready.</summary>
        event Action<string> OnVoiceReady;
    }

    /// <summary>
    /// The 6 AsyncAPI channels from AssetEventBus.kt.
    /// </summary>
    public static class AssetEventChannels
    {
        public const string StoryTags = "ink/story/tags";
        public const string AssetLoad = "ink/asset/load";
        public const string AssetLoaded = "ink/asset/loaded";
        public const string InventoryChange = "ink/inventory/change";
        public const string VoiceSynthesize = "ink/voice/synthesize";
        public const string VoiceReady = "ink/voice/ready";

        public static readonly string[] All = new[]
        {
            StoryTags, AssetLoad, AssetLoaded,
            InventoryChange, VoiceSynthesize, VoiceReady
        };
    }
}
