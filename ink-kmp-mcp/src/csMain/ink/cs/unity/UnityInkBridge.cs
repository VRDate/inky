// ═══════════════════════════════════════════════════════════════
// UnityInkBridge.cs — Unity MonoBehaviour wrapper over IInkRuntime
// Delegates to InkRuntimeService (Ink.Common) for all ink logic.
// Adds Unity-specific: OneJS bridge registration, prefab loading,
// main-thread event pumping.
//
// This replaces the monolithic InkOneJsBinding.cs by separating
// platform-agnostic ink logic (Ink.Common) from Unity glue.
// ═══════════════════════════════════════════════════════════════

#if UNITY_ENGINE
using UnityEngine;
using Ink.Common;

namespace Ink.Unity
{
    /// <summary>
    /// Unity MonoBehaviour that wraps <see cref="IInkRuntime"/> and registers
    /// itself as <c>__inkBridge</c> in the OneJS JavaScript context.
    /// </summary>
    public class UnityInkBridge : MonoBehaviour
    {
        private readonly IInkRuntime _runtime = new InkRuntimeService();

        // ── OneJS bridge methods (JSON in/out for JS interop) ──

        public string Compile(string source)
            => JsonUtility.ToJson(_runtime.Compile(source));

        public string StartStory(string json)
            => _runtime.StartStory(json);

        public string ContinueStory(string sessionId)
            => JsonUtility.ToJson(_runtime.ContinueStory(sessionId));

        public string Choose(string sessionId, int choiceIndex)
            => JsonUtility.ToJson(_runtime.Choose(sessionId, choiceIndex));

        public string GetVariable(string sessionId, string name)
            => _runtime.GetVariable(sessionId, name);

        public void SetVariable(string sessionId, string name, string value)
            => _runtime.SetVariable(sessionId, name, value);

        public string SaveState(string sessionId)
            => _runtime.SaveState(sessionId);

        public void LoadState(string sessionId, string stateJson)
            => _runtime.LoadState(sessionId, stateJson);

        public void ResetStory(string sessionId)
            => _runtime.ResetStory(sessionId);

        public void EndSession(string sessionId)
            => _runtime.EndSession(sessionId);

        public string GetAssetEvents(string sessionId)
            => JsonUtility.ToJson(_runtime.GetAssetEvents(sessionId));
    }
}
#endif
