// ═══════════════════════════════════════════════════════════════
// IUnityBridge.cs — Cross-platform Unity UAAL bridge contract
// Android/iOS: native UAAL (AAR/.framework) via UnitySendMessage
// Windows/macOS: WebSocket fallback to standalone Unity build
// ═══════════════════════════════════════════════════════════════

using System;
using System.Threading.Tasks;

namespace Ink.Maui.Services
{
    /// <summary>
    /// Cross-platform bridge for communicating with Unity as a Library.
    /// Implemented per-platform: Android (AAR), iOS (.framework),
    /// Windows/macOS (WebSocket to standalone Unity).
    /// </summary>
    public interface IUnityBridge
    {
        /// <summary>Is the Unity runtime currently loaded and running?</summary>
        bool IsLoaded { get; }

        /// <summary>Show the Unity rendering surface.</summary>
        void ShowUnity();

        /// <summary>Hide/pause the Unity rendering surface.</summary>
        void HideUnity();

        /// <summary>Unload Unity completely (frees memory).</summary>
        void UnloadUnity();

        /// <summary>
        /// Send a message to a Unity GameObject method.
        /// Maps to UnitySendMessage(gameObject, method, message) on mobile,
        /// or WebSocket JSON on desktop.
        /// </summary>
        void SendMessage(string gameObject, string method, string message);

        /// <summary>Fired when Unity sends a message back to the host.</summary>
        event Action<string> OnMessageReceived;
    }

    /// <summary>
    /// Well-known Unity GameObject names and method names for ink integration.
    /// Matches the GameObjects in the ink-unity UAAL scene.
    /// </summary>
    public static class UnityInkTargets
    {
        /// <summary>GameObject: InkAssetEventReceiver (from ink-unity/)</summary>
        public const string AssetEventReceiver = "InkAssetEventReceiver";

        /// <summary>Method: ProcessEvent(string eventJson) — thread-safe queue</summary>
        public const string ProcessEvent = "ProcessEvent";

        /// <summary>Method: ConfirmAssetLoaded(string assetId)</summary>
        public const string ConfirmAssetLoaded = "ConfirmAssetLoaded";

        /// <summary>GameObject: InkOneJsBinding (from ink-unity/)</summary>
        public const string InkBridge = "InkOneJsBinding";
    }
}
