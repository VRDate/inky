// ═══════════════════════════════════════════════════════════════
// InkRuntimeService.cs — Platform-agnostic IInkRuntime impl
// Uses Ink.Runtime.Story + Ink.Compiler (netstandard2.0).
// Thread-safe: SemaphoreSlim(1,1) for compilation (same as
// InkStorySession.cs and InkOneJsBinding.cs patterns).
// ═══════════════════════════════════════════════════════════════

using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;
using Ink;
using Ink.Runtime;

namespace Ink.Common
{
    /// <summary>
    /// Common ink runtime — implements <see cref="IInkRuntime"/> without
    /// any Unity or MAUI dependency. Works on any .NET platform.
    /// </summary>
    public class InkRuntimeService : IInkRuntime
    {
        private static readonly SemaphoreSlim CompileSemaphore = new SemaphoreSlim(1, 1);
        private readonly Dictionary<string, Story> _sessions = new Dictionary<string, Story>();
        private int _nextSessionId;

        // ── Compile ──────────────────────────────────────────────

        public CompileResult Compile(string source)
        {
            var errors = new List<string>();
            CompileSemaphore.Wait();
            try
            {
                var compiler = new Compiler(source, new Compiler.Options
                {
                    errorHandler = (message, type) =>
                    {
                        if (type == ErrorType.Error)
                            errors.Add(message);
                    }
                });
                var story = compiler.Compile();
                return new CompileResult
                {
                    Json = story?.ToJson() ?? "",
                    Errors = errors.ToArray()
                };
            }
            catch (Exception ex)
            {
                errors.Add(ex.Message);
                return new CompileResult { Json = "", Errors = errors.ToArray() };
            }
            finally
            {
                CompileSemaphore.Release();
            }
        }

        // ── Story lifecycle ──────────────────────────────────────

        public string StartStory(string json)
        {
            var story = new Story(json);
            var sessionId = $"session_{Interlocked.Increment(ref _nextSessionId)}";
            lock (_sessions) { _sessions[sessionId] = story; }
            return sessionId;
        }

        public StoryStateDto ContinueStory(string sessionId)
        {
            var story = GetStory(sessionId);
            var sb = new StringBuilder();
            while (story.canContinue)
                sb.Append(story.Continue());
            return ToDto(story, sb.ToString());
        }

        public StoryStateDto Choose(string sessionId, int choiceIndex)
        {
            var story = GetStory(sessionId);
            story.ChooseChoiceIndex(choiceIndex);
            return ContinueStory(sessionId);
        }

        // ── Variables ────────────────────────────────────────────

        public string GetVariable(string sessionId, string name)
        {
            var story = GetStory(sessionId);
            var value = story.variablesState[name];
            return value?.ToString() ?? "null";
        }

        public void SetVariable(string sessionId, string name, string value)
        {
            var story = GetStory(sessionId);
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

        public string SaveState(string sessionId)
        {
            var story = GetStory(sessionId);
            return story.state.ToJson();
        }

        public void LoadState(string sessionId, string stateJson)
        {
            var story = GetStory(sessionId);
            story.state.LoadJson(stateJson);
        }

        public void ResetStory(string sessionId)
        {
            var story = GetStory(sessionId);
            story.ResetState();
        }

        public void EndSession(string sessionId)
        {
            lock (_sessions) { _sessions.Remove(sessionId); }
        }

        // ── Asset events ─────────────────────────────────────────

        public AssetEventRefDto[] GetAssetEvents(string sessionId)
        {
            var story = GetStory(sessionId);
            var tags = story.currentTags ?? new List<string>();
            var events = new List<AssetEventRefDto>();

            foreach (var tag in tags)
            {
                var trimmed = tag.TrimStart('#', ' ');
                var colonIdx = trimmed.IndexOf(':');
                if (colonIdx < 0) continue;

                var key = trimmed.Substring(0, colonIdx).Trim();
                var val = trimmed.Substring(colonIdx + 1).Trim();

                if (key == "mesh" || key == "anim" || key == "voice")
                {
                    events.Add(new AssetEventRefDto
                    {
                        Emoji = val,
                        Category = key,
                        Type = key == "mesh" ? "mesh" : key == "anim" ? "animation" : "voice",
                        MeshPath = key == "mesh" ? val : "",
                        AnimSetId = key == "anim" ? val : ""
                    });
                }
            }

            return events.ToArray();
        }

        // ── Internal ─────────────────────────────────────────────

        private Story GetStory(string sessionId)
        {
            lock (_sessions)
            {
                if (!_sessions.TryGetValue(sessionId, out var story))
                    throw new ArgumentException($"Unknown session: {sessionId}");
                return story;
            }
        }

        private static StoryStateDto ToDto(Story story, string text)
        {
            var choices = new ChoiceDto[story.currentChoices.Count];
            for (var i = 0; i < story.currentChoices.Count; i++)
            {
                choices[i] = new ChoiceDto
                {
                    Index = story.currentChoices[i].index,
                    Text = story.currentChoices[i].text
                };
            }

            return new StoryStateDto
            {
                Text = text,
                Choices = choices,
                CanContinue = story.canContinue,
                Tags = story.currentTags?.ToArray() ?? Array.Empty<string>()
            };
        }
    }
}
