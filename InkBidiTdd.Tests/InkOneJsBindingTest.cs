using System.Text;
using System.Text.Json;
using Ink.Runtime;
using Xunit;
using static InkBidiTdd.Tests.InkTestFixtures;

namespace InkBidiTdd.Tests;

/// <summary>
/// Tests the 10-method OneJS binding contract (Compile → StartStory → ContinueStory →
/// Choose → GetVariable → SetVariable → SaveState → LoadState → ResetStory → EndSession)
/// without Unity dependency.
///
/// Mirrors the exact API shape of InkOneJsBinding.cs but uses System.Text.Json instead
/// of UnityEngine.JsonUtility and plain classes instead of MonoBehaviour.
///
/// Uses <see cref="InkStorySession.CompileLocked"/> for thread-safe compilation and
/// <see cref="InkTestFixtures"/> companion object for shared fixtures.
/// </summary>
public class InkOneJsBindingTest
{
    // ═══════════════════════════════════════════════════════════════
    // PORTABLE BRIDGE — mirrors InkOneJsBinding without Unity deps
    // ═══════════════════════════════════════════════════════════════

    /// <summary>
    /// Non-Unity implementation of the 10-method ink bridge contract.
    /// Same API as InkOneJsBinding.cs but uses System.Text.Json.
    /// Compilation delegated to <see cref="InkStorySession.CompileLocked"/>.
    /// </summary>
    private class InkBridge
    {
        private readonly Dictionary<string, Story> _sessions = new();
        private int _nextSessionId;

        public string Compile(string source)
        {
            try
            {
                var (story, errors) = InkStorySession.CompileLocked(source);
                if (story == null || errors.Count > 0)
                    return JsonSerializer.Serialize(new { json = "", errors = errors.ToArray() });
                var json = story.ToJson();
                return JsonSerializer.Serialize(new { json, errors = Array.Empty<string>() });
            }
            catch (Exception ex)
            {
                return JsonSerializer.Serialize(new { json = "", errors = new[] { ex.Message } });
            }
        }

        public string StartStory(string json)
        {
            var story = new Story(json);
            var sessionId = $"session_{_nextSessionId++}";
            _sessions[sessionId] = story;
            return sessionId;
        }

        public string ContinueStory(string sessionId)
        {
            var story = GetStory(sessionId);
            var sb = new StringBuilder();
            while (story.canContinue)
                sb.Append(story.Continue());
            return SerializeState(story, sb.ToString());
        }

        public string Choose(string sessionId, int choiceIndex)
        {
            var story = GetStory(sessionId);
            story.ChooseChoiceIndex(choiceIndex);
            return ContinueStory(sessionId);
        }

        public string GetVariable(string sessionId, string name)
        {
            var story = GetStory(sessionId);
            var value = story.variablesState[name];
            return JsonSerializer.Serialize(new { value = value?.ToString() ?? "null" });
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

        public string SaveState(string sessionId)
            => GetStory(sessionId).state.ToJson();

        public void LoadState(string sessionId, string stateJson)
            => GetStory(sessionId).state.LoadJson(stateJson);

        public void ResetStory(string sessionId)
            => GetStory(sessionId).ResetState();

        public void EndSession(string sessionId)
            => _sessions.Remove(sessionId);

        public bool HasSession(string sessionId)
            => _sessions.ContainsKey(sessionId);

        private Story GetStory(string sessionId)
            => _sessions.TryGetValue(sessionId, out var story)
                ? story
                : throw new ArgumentException($"Unknown session: {sessionId}");

        private static string SerializeState(Story story, string text)
        {
            var choices = story.currentChoices.Select(c => new { index = c.index, text = c.text }).ToArray();
            var tags = story.currentTags?.ToArray() ?? Array.Empty<string>();
            return JsonSerializer.Serialize(new { text, choices, canContinue = story.canContinue, tags });
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS — JSON DTO parsing
    // ═══════════════════════════════════════════════════════════════

    private readonly InkBridge _bridge = new();

    private record CompileResultDto(string json, string[] errors);
    private record StoryStateDto(string text, ChoiceDto[] choices, bool canContinue, string[] tags);
    private record ChoiceDto(int index, string text);

    private static CompileResultDto ParseCompileResult(string json)
    {
        var root = JsonDocument.Parse(json).RootElement;
        return new CompileResultDto(
            root.GetProperty("json").GetString()!,
            root.GetProperty("errors").EnumerateArray().Select(e => e.GetString()!).ToArray());
    }

    private static StoryStateDto ParseStoryState(string json)
    {
        var root = JsonDocument.Parse(json).RootElement;
        return new StoryStateDto(
            root.GetProperty("text").GetString()!,
            root.GetProperty("choices").EnumerateArray().Select(c =>
                new ChoiceDto(c.GetProperty("index").GetInt32(), c.GetProperty("text").GetString()!)).ToArray(),
            root.GetProperty("canContinue").GetBoolean(),
            root.GetProperty("tags").EnumerateArray().Select(t => t.GetString()!).ToArray());
    }

    private static string ParseVariableValue(string json)
        => JsonDocument.Parse(json).RootElement.GetProperty("value").GetString()!;

    // ═══════════════════════════════════════════════════════════════
    // 1. COMPILE
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Compile_returns_valid_JSON_with_no_errors()
    {
        var result = ParseCompileResult(_bridge.Compile(BidiTddSource));
        Assert.Empty(result.errors);
        Assert.NotEmpty(result.json);
        Assert.Contains("\"inkVersion\"", result.json);
    }

    [Fact]
    public void Compile_invalid_source_returns_errors()
    {
        var result = ParseCompileResult(_bridge.Compile("=== broken ink {{{}}} ==="));
        Assert.NotEmpty(result.errors);
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. START STORY
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void StartStory_creates_session()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        Assert.StartsWith("session_", sessionId);
        Assert.True(_bridge.HasSession(sessionId));
    }

    [Fact]
    public void StartStory_increments_session_ids()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var id1 = _bridge.StartStory(compiled.json);
        var id2 = _bridge.StartStory(compiled.json);
        Assert.NotEqual(id1, id2);
        _bridge.EndSession(id1);
        _bridge.EndSession(id2);
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. CONTINUE STORY
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void ContinueStory_returns_main_menu()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        var state = ParseStoryState(_bridge.ContinueStory(sessionId));

        Assert.True(state.text.Contains("Inky Test Suite") || state.text.Contains("חבילת בדיקות"));
        Assert.True(state.choices.Length >= 5);
        Assert.False(state.canContinue);
        _bridge.EndSession(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. CHOOSE
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Choose_advances_story()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        _bridge.ContinueStory(sessionId);
        var afterChoice = ParseStoryState(_bridge.Choose(sessionId, 0));
        Assert.NotEmpty(afterChoice.text);
        _bridge.EndSession(sessionId);
    }

    [Fact]
    public void Choose_smoke_test_path()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        var initial = ParseStoryState(_bridge.ContinueStory(sessionId));

        var smokeIndex = Array.FindIndex(initial.choices,
            c => c.text.Contains("Smoke") || c.text.Contains("בדיקה מהירה"));
        Assert.True(smokeIndex >= 0, "Should find Smoke Test choice");

        var afterSmoke = ParseStoryState(_bridge.Choose(sessionId, smokeIndex));
        Assert.True(afterSmoke.text.Contains("שלום") || afterSmoke.text.Contains("Hello"));
        _bridge.EndSession(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. GET VARIABLE
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void GetVariable_reads_initial_values()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        _bridge.ContinueStory(sessionId);

        Assert.Equal("100", ParseVariableValue(_bridge.GetVariable(sessionId, "health")));
        Assert.Equal("50", ParseVariableValue(_bridge.GetVariable(sessionId, "gold")));
        Assert.Equal("both", ParseVariableValue(_bridge.GetVariable(sessionId, "lang")));
        _bridge.EndSession(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. SET VARIABLE
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void SetVariable_updates_values()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        _bridge.ContinueStory(sessionId);

        _bridge.SetVariable(sessionId, "lang", "he");
        Assert.Equal("he", ParseVariableValue(_bridge.GetVariable(sessionId, "lang")));

        _bridge.SetVariable(sessionId, "health", "75");
        Assert.Equal("75", ParseVariableValue(_bridge.GetVariable(sessionId, "health")));

        _bridge.SetVariable(sessionId, "show_emoji", "false");
        Assert.Equal("False", ParseVariableValue(_bridge.GetVariable(sessionId, "show_emoji")));
        _bridge.EndSession(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. SAVE STATE
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void SaveState_returns_valid_JSON()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        _bridge.ContinueStory(sessionId);

        var savedState = _bridge.SaveState(sessionId);
        Assert.NotEmpty(savedState);
        Assert.StartsWith("{", savedState);
        JsonDocument.Parse(savedState);
        _bridge.EndSession(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. LOAD STATE
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void LoadState_restores_previous_position()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        var initial = ParseStoryState(_bridge.ContinueStory(sessionId));
        var savedState = _bridge.SaveState(sessionId);

        _bridge.Choose(sessionId, 0);
        _bridge.LoadState(sessionId, savedState);
        var restored = ParseStoryState(_bridge.ContinueStory(sessionId));

        Assert.Equal(initial.choices.Length, restored.choices.Length);
        _bridge.EndSession(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. RESET STORY
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void ResetStory_returns_to_beginning()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        _bridge.ContinueStory(sessionId);
        _bridge.Choose(sessionId, 0);

        _bridge.ResetStory(sessionId);
        var afterReset = ParseStoryState(_bridge.ContinueStory(sessionId));
        Assert.True(afterReset.choices.Length >= 5);
        _bridge.EndSession(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 10. END SESSION
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void EndSession_removes_session()
    {
        var compiled = ParseCompileResult(_bridge.Compile(BidiTddSource));
        var sessionId = _bridge.StartStory(compiled.json);
        Assert.True(_bridge.HasSession(sessionId));
        _bridge.EndSession(sessionId);
        Assert.False(_bridge.HasSession(sessionId));
    }

    [Fact]
    public void EndSession_unknown_id_does_not_throw()
        => _bridge.EndSession("nonexistent_session");

    [Fact]
    public void ContinueStory_unknown_session_throws()
        => Assert.Throws<ArgumentException>(() => _bridge.ContinueStory("nonexistent"));

    // ═══════════════════════════════════════════════════════════════
    // FULL LIFECYCLE — 10-method contract end-to-end
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Full_lifecycle_10_method_contract()
    {
        // 1. Compile
        var compileResult = ParseCompileResult(_bridge.Compile(BidiTddSource));
        Assert.Empty(compileResult.errors);

        // 2. StartStory
        var sid = _bridge.StartStory(compileResult.json);
        Assert.True(_bridge.HasSession(sid));

        // 3. ContinueStory
        var initial = ParseStoryState(_bridge.ContinueStory(sid));
        Assert.True(initial.choices.Length >= 5);

        // 4. Choose
        Assert.NotEmpty(ParseStoryState(_bridge.Choose(sid, 0)).text);

        // 5. GetVariable
        Assert.Equal("100", ParseVariableValue(_bridge.GetVariable(sid, "health")));

        // 6. SetVariable
        _bridge.SetVariable(sid, "health", "75");
        Assert.Equal("75", ParseVariableValue(_bridge.GetVariable(sid, "health")));

        // 7. SaveState
        var saved = _bridge.SaveState(sid);
        Assert.NotEmpty(saved);

        // 8. LoadState
        _bridge.LoadState(sid, saved);
        Assert.Equal("75", ParseVariableValue(_bridge.GetVariable(sid, "health")));

        // 9. ResetStory
        _bridge.ResetStory(sid);
        Assert.True(ParseStoryState(_bridge.ContinueStory(sid)).choices.Length >= 5);

        // 10. EndSession
        _bridge.EndSession(sid);
        Assert.False(_bridge.HasSession(sid));
    }
}
