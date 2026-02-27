// ═══════════════════════════════════════════════════════════════
// InkOneJsBinding.cs — Unity C# bridge for OneJS ↔ ink runtime
// Exposes the 11 methods that createOneJsAdapter() in
// InkRuntimeAdapter.ts calls via globalThis.__inkBridge
// ═══════════════════════════════════════════════════════════════

using System;
using System.Collections.Generic;
using Ink.Runtime;
using UnityEngine;

namespace Inky.Unity
{
    /// <summary>
    /// Unity MonoBehaviour that registers itself as <c>__inkBridge</c> in the
    /// OneJS JavaScript context, implementing the 11-method contract expected
    /// by <c>createOneJsAdapter()</c> in <c>InkRuntimeAdapter.ts</c>.
    /// </summary>
    public class InkOneJsBinding : MonoBehaviour
    {
        private readonly Dictionary<string, Story> _sessions = new();
        private int _nextSessionId;

        // ── Compile ──────────────────────────────────────────────

        /// <summary>
        /// Compiles ink source to JSON.
        /// Returns JSON string: <c>{ "json": "...", "errors": [] }</c>
        /// </summary>
        public string Compile(string source)
        {
            try
            {
                var compiler = new Ink.Compiler(source);
                var story = compiler.Compile();
                var json = story.ToJson();
                var errors = compiler.errors ?? new List<string>();
                return JsonUtility.ToJson(new CompileResult
                {
                    json = json,
                    errors = errors.ToArray()
                });
            }
            catch (Exception ex)
            {
                return JsonUtility.ToJson(new CompileResult
                {
                    json = "",
                    errors = new[] { ex.Message }
                });
            }
        }

        // ── Story lifecycle ──────────────────────────────────────

        /// <summary>
        /// Creates a Story from compiled JSON and returns a session ID.
        /// </summary>
        public string StartStory(string json)
        {
            var story = new Story(json);
            var sessionId = $"session_{_nextSessionId++}";
            _sessions[sessionId] = story;
            return sessionId;
        }

        /// <summary>
        /// Continues the story and returns the current state as JSON.
        /// </summary>
        public string ContinueStory(string sessionId)
        {
            var story = GetStory(sessionId);
            var text = "";
            while (story.canContinue)
            {
                text += story.Continue();
            }
            return SerializeState(story, text);
        }

        /// <summary>
        /// Makes a choice and returns the resulting state as JSON.
        /// </summary>
        public string Choose(string sessionId, int choiceIndex)
        {
            var story = GetStory(sessionId);
            story.ChooseChoiceIndex(choiceIndex);
            return ContinueStory(sessionId);
        }

        // ── Variables ────────────────────────────────────────────

        /// <summary>
        /// Gets a variable value as JSON string.
        /// </summary>
        public string GetVariable(string sessionId, string name)
        {
            var story = GetStory(sessionId);
            var value = story.variablesState[name];
            return JsonUtility.ToJson(new VariableWrapper { value = value?.ToString() ?? "null" });
        }

        /// <summary>
        /// Sets a variable from a JSON-stringified value.
        /// </summary>
        public void SetVariable(string sessionId, string name, string value)
        {
            var story = GetStory(sessionId);
            // Parse the JSON value — could be string, int, float, bool
            if (int.TryParse(value, out var intVal))
                story.variablesState[name] = intVal;
            else if (float.TryParse(value, out var floatVal))
                story.variablesState[name] = floatVal;
            else if (bool.TryParse(value, out var boolVal))
                story.variablesState[name] = boolVal;
            else
                story.variablesState[name] = value.Trim('"');
        }

        // ── State persistence ────────────────────────────────────

        /// <summary>
        /// Saves the current story state as a JSON string.
        /// </summary>
        public string SaveState(string sessionId)
        {
            var story = GetStory(sessionId);
            return story.state.ToJson();
        }

        /// <summary>
        /// Loads a previously saved state JSON into the story.
        /// </summary>
        public void LoadState(string sessionId, string stateJson)
        {
            var story = GetStory(sessionId);
            story.state.LoadJson(stateJson);
        }

        /// <summary>
        /// Resets the story to the beginning.
        /// </summary>
        public void ResetStory(string sessionId)
        {
            var story = GetStory(sessionId);
            story.ResetState();
        }

        /// <summary>
        /// Ends and removes a session.
        /// </summary>
        public void EndSession(string sessionId)
        {
            _sessions.Remove(sessionId);
        }

        // ── Asset events (11th method) ─────────────────────────────

        /// <summary>
        /// Parses the current tags through a basic emoji→asset mapping
        /// and returns a JSON array of asset event references.
        /// Called by OneJS <c>bridge.GetAssetEvents(sessionId)</c>.
        /// </summary>
        public string GetAssetEvents(string sessionId)
        {
            var story = GetStory(sessionId);
            var tags = story.currentTags ?? new List<string>();
            var events = new List<AssetEventRefDto>();

            foreach (var tag in tags)
            {
                // Parse tags in format: "mesh:🗡️", "anim:sword_slash", "voice:gandalf_en"
                var trimmed = tag.TrimStart('#', ' ');
                var colonIdx = trimmed.IndexOf(':');
                if (colonIdx < 0) continue;

                var key = trimmed.Substring(0, colonIdx).Trim();
                var value = trimmed.Substring(colonIdx + 1).Trim();

                if (key is "mesh" or "anim" or "voice")
                {
                    events.Add(new AssetEventRefDto
                    {
                        emoji = value,
                        category = key,
                        type = key switch
                        {
                            "mesh" => "mesh",
                            "anim" => "animation",
                            "voice" => "voice",
                            _ => "unknown"
                        },
                        meshPath = key == "mesh" ? value : "",
                        animSetId = key == "anim" ? value : ""
                    });
                }
            }

            return JsonUtility.ToJson(new AssetEventRefArrayWrapper { items = events.ToArray() });
        }

        // ── Internal helpers ─────────────────────────────────────

        private Story GetStory(string sessionId)
        {
            if (!_sessions.TryGetValue(sessionId, out var story))
                throw new ArgumentException($"Unknown session: {sessionId}");
            return story;
        }

        private static string SerializeState(Story story, string text)
        {
            var choices = new ChoiceDto[story.currentChoices.Count];
            for (var i = 0; i < story.currentChoices.Count; i++)
            {
                choices[i] = new ChoiceDto
                {
                    index = story.currentChoices[i].index,
                    text = story.currentChoices[i].text
                };
            }

            var tags = story.currentTags?.ToArray() ?? Array.Empty<string>();

            return JsonUtility.ToJson(new StoryStateDto
            {
                text = text,
                choices = choices,
                canContinue = story.canContinue,
                tags = tags
            });
        }

        // ── DTOs for JSON serialization ──────────────────────────

        [Serializable]
        private struct CompileResult
        {
            public string json;
            public string[] errors;
        }

        [Serializable]
        private struct VariableWrapper
        {
            public string value;
        }

        [Serializable]
        private struct ChoiceDto
        {
            public int index;
            public string text;
        }

        [Serializable]
        private struct StoryStateDto
        {
            public string text;
            public ChoiceDto[] choices;
            public bool canContinue;
            public string[] tags;
        }

        [Serializable]
        private struct AssetEventRefDto
        {
            public string emoji;
            public string category;
            public string type;
            public string meshPath;
            public string animSetId;
        }

        [Serializable]
        private struct AssetEventRefArrayWrapper
        {
            public AssetEventRefDto[] items;
        }
    }
}
