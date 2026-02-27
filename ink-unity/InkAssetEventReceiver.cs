// ═══════════════════════════════════════════════════════════════
// InkAssetEventReceiver.cs — Unity C# asset event consumer
// Receives events from AssetEventBus (in-process via OneJS or
// over WebSocket /rsocket endpoint) and triggers asset loading.
//
// AsyncAPI contract: docs/asyncapi/ink-asset-events.yaml
// ═══════════════════════════════════════════════════════════════

using System;
using System.Collections.Generic;
using UnityEngine;

namespace Inky.Unity
{
    /// <summary>
    /// Unity MonoBehaviour that receives asset events from the Inky event bus.
    /// Two modes:
    ///   1. In-process (OneJS): <c>ProcessEvent(json)</c> called from JS
    ///   2. WebSocket: connects to <c>ws://host:port/rsocket</c>
    /// </summary>
    public class InkAssetEventReceiver : MonoBehaviour
    {
        [Header("Connection")]
        [Tooltip("WebSocket URL (leave empty for in-process OneJS mode)")]
        public string rsocketUrl = "";

        [Tooltip("Session ID to filter events")]
        public string sessionId = "";

        [Header("Asset Mapping")]
        [Tooltip("Maps emoji → prefab path (e.g., 🗡️ → Prefabs/Weapons/Sword)")]
        public EmojiPrefabMapping[] emojiPrefabs = Array.Empty<EmojiPrefabMapping>();

        // ── Events (C# delegates for Unity scripts) ──

        /// <summary>Fired when story tags contain asset references.</summary>
        public event Action<InkTagEvent> OnTagEvent;

        /// <summary>Fired when an asset load is requested.</summary>
        public event Action<AssetLoadRequest> OnAssetLoadRequest;

        /// <summary>Fired when inventory changes (equip/unequip/add/remove).</summary>
        public event Action<InventoryChangeEvent> OnInventoryChange;

        /// <summary>Fired when voice synthesis is requested.</summary>
        public event Action<VoiceSynthRequest> OnVoiceSynthesize;

        // ── Internal state ──

        private readonly Dictionary<string, GameObject> _loadedAssets = new();
        private readonly Queue<string> _pendingEvents = new();
        private readonly object _lock = new();

        // ── Unity lifecycle ──

        private void Update()
        {
            // Process events on main thread (WebSocket messages arrive on background threads)
            lock (_lock)
            {
                while (_pendingEvents.Count > 0)
                {
                    var json = _pendingEvents.Dequeue();
                    ProcessEventInternal(json);
                }
            }
        }

        private void OnDestroy()
        {
            foreach (var go in _loadedAssets.Values)
            {
                if (go != null) Destroy(go);
            }
            _loadedAssets.Clear();
        }

        // ── Public API (called from OneJS or WebSocket handler) ──

        /// <summary>
        /// Process a single asset event (JSON string).
        /// Thread-safe: queues for main-thread processing.
        /// Called by OneJS <c>__inkBridge.ProcessAssetEvent(json)</c>
        /// or by WebSocket message handler.
        /// </summary>
        public void ProcessEvent(string eventJson)
        {
            lock (_lock)
            {
                _pendingEvents.Enqueue(eventJson);
            }
        }

        /// <summary>
        /// Confirm that an asset has been loaded (client → server).
        /// </summary>
        public void ConfirmAssetLoaded(string assetId, string meshPath)
        {
            // TODO: Send via WebSocket or OneJS bridge
            Debug.Log($"[InkAssetEvent] Asset loaded: {assetId} at {meshPath}");
        }

        // ── Internal event processing ──

        private void ProcessEventInternal(string json)
        {
            try
            {
                var wrapper = JsonUtility.FromJson<EventWrapper>(json);

                switch (wrapper.channel)
                {
                    case "ink/story/tags":
                        var tagEvent = JsonUtility.FromJson<InkTagEvent>(wrapper.@event);
                        if (!MatchesSession(tagEvent.session_id)) return;
                        OnTagEvent?.Invoke(tagEvent);
                        break;

                    case "ink/asset/load":
                        var loadReq = JsonUtility.FromJson<AssetLoadRequest>(wrapper.@event);
                        if (!MatchesSession(loadReq.session_id)) return;
                        OnAssetLoadRequest?.Invoke(loadReq);
                        LoadAsset(loadReq);
                        break;

                    case "ink/inventory/change":
                        var invChange = JsonUtility.FromJson<InventoryChangeEvent>(wrapper.@event);
                        if (!MatchesSession(invChange.session_id)) return;
                        OnInventoryChange?.Invoke(invChange);
                        HandleInventoryChange(invChange);
                        break;

                    case "ink/voice/synthesize":
                        var voiceReq = JsonUtility.FromJson<VoiceSynthRequest>(wrapper.@event);
                        if (!MatchesSession(voiceReq.session_id)) return;
                        OnVoiceSynthesize?.Invoke(voiceReq);
                        break;
                }
            }
            catch (Exception ex)
            {
                Debug.LogWarning($"[InkAssetEvent] Failed to process event: {ex.Message}");
            }
        }

        private bool MatchesSession(string eventSessionId)
        {
            return string.IsNullOrEmpty(sessionId) || sessionId == eventSessionId;
        }

        private void LoadAsset(AssetLoadRequest request)
        {
            var emoji = request.asset?.emoji ?? "";
            var prefabPath = ResolvePrefabPath(emoji);
            if (string.IsNullOrEmpty(prefabPath))
            {
                Debug.LogWarning($"[InkAssetEvent] No prefab mapped for emoji: {emoji}");
                return;
            }

            // Load AssetBundle or Resources prefab
            var prefab = Resources.Load<GameObject>(prefabPath);
            if (prefab == null)
            {
                Debug.LogWarning($"[InkAssetEvent] Prefab not found: {prefabPath}");
                return;
            }

            var instance = Instantiate(prefab);
            var assetId = $"{emoji}_{Time.frameCount}";
            _loadedAssets[assetId] = instance;

            Debug.Log($"[InkAssetEvent] Loaded: {emoji} → {prefabPath}");
            ConfirmAssetLoaded(assetId, prefabPath);
        }

        private void HandleInventoryChange(InventoryChangeEvent change)
        {
            switch (change.action)
            {
                case "equip":
                    Debug.Log($"[InkAssetEvent] Equip: {change.emoji} ({change.item_name})");
                    // TODO: Attach to character hand bone, play equip animation
                    break;

                case "unequip":
                    Debug.Log($"[InkAssetEvent] Unequip: {change.emoji} ({change.item_name})");
                    // TODO: Detach from character, play unequip animation
                    break;
            }
        }

        private string ResolvePrefabPath(string emoji)
        {
            foreach (var mapping in emojiPrefabs)
            {
                if (mapping.emoji == emoji) return mapping.prefabPath;
            }
            return null;
        }

        // ── Serializable DTOs ──

        [Serializable]
        private struct EventWrapper
        {
            public string channel;
            [SerializeField] public string @event;
            public long timestamp;
        }

        [Serializable]
        public struct InkTagEvent
        {
            public string session_id;
            public string knot;
            public string[] tags;
            public AssetRefDto[] resolved_assets;
            public long timestamp;
        }

        [Serializable]
        public struct AssetLoadRequest
        {
            public string session_id;
            public AssetRefDto asset;
            public string priority;
            public long timestamp;
        }

        [Serializable]
        public struct InventoryChangeEvent
        {
            public string session_id;
            public string action;
            public string emoji;
            public string item_name;
            public AssetRefDto asset;
            public long timestamp;
        }

        [Serializable]
        public struct VoiceSynthRequest
        {
            public string session_id;
            public string text;
            public VoiceRefDto voice_ref;
            public long timestamp;
        }

        [Serializable]
        public struct AssetRefDto
        {
            public string emoji;
            public string category_name;
            public string category_type;
            public string mesh_path;
            public string anim_set_id;
            public VoiceRefDto voice_ref;
        }

        [Serializable]
        public struct VoiceRefDto
        {
            public string character_id;
            public string language;
            public string flac_path;
        }

        [Serializable]
        public struct EmojiPrefabMapping
        {
            public string emoji;
            public string prefabPath;
            public string animSetOverride;
        }
    }
}
