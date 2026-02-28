// ═══════════════════════════════════════════════════════════════
// IInkRuntime.cs — Common 11-method ink runtime contract
// Shared across MAUI and Unity (no platform dependencies).
// Extracted from InkOneJsBinding.cs MonoBehaviour.
// ═══════════════════════════════════════════════════════════════

namespace Ink.Common
{
    /// <summary>
    /// Platform-agnostic ink runtime contract.
    /// Unity implements via MonoBehaviour, MAUI via DI service.
    /// Mirrors the 11-method contract from <c>createOneJsAdapter()</c>.
    /// </summary>
    public interface IInkRuntime
    {
        // ── Compile ──────────────────────────────────────────────
        CompileResult Compile(string source);

        // ── Story lifecycle ──────────────────────────────────────
        string StartStory(string json);
        StoryStateDto ContinueStory(string sessionId);
        StoryStateDto Choose(string sessionId, int choiceIndex);

        // ── Variables ────────────────────────────────────────────
        string GetVariable(string sessionId, string name);
        void SetVariable(string sessionId, string name, string value);

        // ── State persistence ────────────────────────────────────
        string SaveState(string sessionId);
        void LoadState(string sessionId, string stateJson);
        void ResetStory(string sessionId);
        void EndSession(string sessionId);

        // ── Asset events ─────────────────────────────────────────
        AssetEventRefDto[] GetAssetEvents(string sessionId);
    }
}
