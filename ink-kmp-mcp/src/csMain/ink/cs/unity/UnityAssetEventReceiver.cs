// ═══════════════════════════════════════════════════════════════
// UnityAssetEventReceiver.cs — Unity asset event consumer
// Wraps IAssetEventTransport (Ink.Common) with Unity-specific:
//   - Main-thread event queue (Update pump)
//   - Resources.Load prefab loading
//   - Inspector-configurable emoji→prefab mapping
//
// Replaces monolithic InkAssetEventReceiver.cs by reusing
// Ink.Common DTOs and transport contracts.
// ═══════════════════════════════════════════════════════════════

#if UNITY_ENGINE
using System;
using System.Collections.Generic;
using UnityEngine;
using Ink.Common;
using Ink.Common.Transport;

namespace Ink.Unity
{
    /// <summary>
    /// Unity MonoBehaviour that receives asset events via
    /// <see cref="IAssetEventTransport"/> and loads prefabs.
    /// </summary>
    public class UnityAssetEventReceiver : MonoBehaviour
    {
        [Header("Connection")]
        [Tooltip("WebSocket URL (leave empty for in-process OneJS mode)")]
        public string rsocketUrl = "";

        [Tooltip("Session ID to filter events")]
        public string sessionId = "";

        [Header("Asset Mapping")]
        public EmojiPrefabMapping[] emojiPrefabs = Array.Empty<EmojiPrefabMapping>();

        // ── Events ──
        public event Action<InkTagEvent>? OnTagEvent;
        public event Action<AssetLoadRequest>? OnAssetLoadRequest;
        public event Action<InventoryChangeEvent>? OnInventoryChange;
        public event Action<VoiceSynthRequest>? OnVoiceSynthesize;

        private readonly Queue<Action> _mainThreadQueue = new Queue<Action>();
        private readonly object _lock = new object();

        private void Update()
        {
            lock (_lock)
            {
                while (_mainThreadQueue.Count > 0)
                    _mainThreadQueue.Dequeue()?.Invoke();
            }
        }

        /// <summary>Queue an action for main-thread execution.</summary>
        public void EnqueueMainThread(Action action)
        {
            lock (_lock) { _mainThreadQueue.Enqueue(action); }
        }

        /// <summary>Process a tag event (dispatches to main thread).</summary>
        public void HandleTagEvent(InkTagEvent evt)
        {
            if (!string.IsNullOrEmpty(sessionId) && evt.SessionId != sessionId) return;
            EnqueueMainThread(() => OnTagEvent?.Invoke(evt));
        }

        /// <summary>Process an asset load request.</summary>
        public void HandleAssetLoad(AssetLoadRequest req)
        {
            if (!string.IsNullOrEmpty(sessionId) && req.SessionId != sessionId) return;
            EnqueueMainThread(() =>
            {
                OnAssetLoadRequest?.Invoke(req);
                LoadPrefab(req.Asset?.Emoji ?? "");
            });
        }

        private void LoadPrefab(string emoji)
        {
            var path = ResolvePrefabPath(emoji);
            if (string.IsNullOrEmpty(path)) return;

            var prefab = Resources.Load<GameObject>(path);
            if (prefab != null) Instantiate(prefab);
        }

        private string? ResolvePrefabPath(string emoji)
        {
            foreach (var m in emojiPrefabs)
                if (m.Emoji == emoji) return m.PrefabPath;
            return null;
        }
    }
}
#endif
