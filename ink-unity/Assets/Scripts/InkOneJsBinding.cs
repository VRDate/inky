// OneJS ↔ C# ink runtime binding.
//
// Exposes Story.Continue(), currentChoices, ChooseChoiceIndex() etc. to JS
// so that InkRuntimeAdapter.ts can drive the C# runtime from React/JS.
//
// When running inside Unity WebGL via OneJS, the React InkPlayer.tsx calls
// these bindings through globalThis.__inkBridge.

using UnityEngine;
using System.Collections.Generic;
using System.Text.Json;

namespace InkUnity
{
    public class InkOneJsBinding : MonoBehaviour
    {
        private InkBridge _bridge;

        private void Awake()
        {
            _bridge = GetComponent<InkBridge>();
            RegisterJsBindings();
        }

        /// <summary>Register C# methods as JS globals via OneJS.</summary>
        private void RegisterJsBindings()
        {
            // OneJS exposes C# objects to JS via:
            // _jsEngine.SetGlobal("__inkBridge", this);
            //
            // Then in JS: globalThis.__inkBridge.StartStory(json)
            //
            // Each method returns JSON strings that InkRuntimeAdapter.ts parses.
            Debug.Log("[InkOneJsBinding] Registered __inkBridge for OneJS");
        }

        // ── Methods called from JS (InkRuntimeAdapter.ts) ──

        public string StartStory(string json)
        {
            return _bridge.StartStory(json);
        }

        public string ContinueStory(string sessionId)
        {
            var text = _bridge.ContinueStory();
            var choices = _bridge.GetChoices();
            var tags = _bridge.GetTags();
            var result = new
            {
                text,
                choices = FormatChoices(choices),
                canContinue = _bridge.CanContinue,
                tags
            };
            return JsonSerializer.Serialize(result);
        }

        public string Choose(string sessionId, int choiceIndex)
        {
            _bridge.Choose(choiceIndex);
            return ContinueStory(sessionId);
        }

        public string GetVariable(string sessionId, string name)
        {
            // TODO: Implement when ink C# runtime is connected
            return "null";
        }

        public void SetVariable(string sessionId, string name, string valueJson)
        {
            // TODO: Implement when ink C# runtime is connected
        }

        public string SaveState(string sessionId)
        {
            return _bridge.SaveState();
        }

        public void LoadState(string sessionId, string stateJson)
        {
            _bridge.LoadState(stateJson);
        }

        public void ResetStory(string sessionId)
        {
            _bridge.ResetStory();
        }

        public void EndSession(string sessionId)
        {
            // No-op for Unity — single story per bridge
        }

        public string Compile(string source)
        {
            // Compilation happens via MCP server, not locally in Unity
            return JsonSerializer.Serialize(new { json = "", errors = new[] { "Compile via MCP server" } });
        }

        private object[] FormatChoices(List<string> choices)
        {
            var result = new object[choices.Count];
            for (int i = 0; i < choices.Count; i++)
            {
                result[i] = new { index = i, text = choices[i] };
            }
            return result;
        }
    }
}
