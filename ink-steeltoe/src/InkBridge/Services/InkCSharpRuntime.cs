// C# ink runtime wrapper — wraps Ink.Runtime.Story for direct execution.
// Uses the same C# source as ink-unity/Assets/Ink/InkRuntime/.
//
// In production, this project references the ink C# source directly.
// For now, this is a stub that will be connected when the ink source is added.

using InkBridge.Models;
using System.Collections.Concurrent;

namespace InkBridge.Services;

/// <summary>
/// Wraps the C# ink runtime (Ink.Runtime.Story) for story execution.
/// All C# code compiles for WebGL (IL2CPP) — same source as Unity.
/// </summary>
public class InkCSharpRuntime
{
    private readonly ConcurrentDictionary<string, StoryWrapper> _sessions = new();
    private int _nextId;

    /// <summary>Start a story from compiled JSON.</summary>
    public string StartStory(string json)
    {
        var id = $"csharp-{Interlocked.Increment(ref _nextId)}";
        // TODO: Replace with actual Ink.Runtime.Story when C# source is added
        _sessions[id] = new StoryWrapper(json);
        return id;
    }

    /// <summary>Continue the story, draining all available text.</summary>
    public StoryState ContinueStory(string sessionId)
    {
        var wrapper = GetSession(sessionId);
        return wrapper.Continue();
    }

    /// <summary>Make a choice.</summary>
    public StoryState Choose(string sessionId, int choiceIndex)
    {
        var wrapper = GetSession(sessionId);
        return wrapper.Choose(choiceIndex);
    }

    /// <summary>Get a variable value.</summary>
    public object? GetVariable(string sessionId, string name)
    {
        var wrapper = GetSession(sessionId);
        return wrapper.GetVariable(name);
    }

    /// <summary>Save story state to JSON.</summary>
    public string SaveState(string sessionId)
    {
        var wrapper = GetSession(sessionId);
        return wrapper.SaveState();
    }

    /// <summary>Load story state from JSON.</summary>
    public void LoadState(string sessionId, string stateJson)
    {
        var wrapper = GetSession(sessionId);
        wrapper.LoadState(stateJson);
    }

    /// <summary>Reset story to beginning.</summary>
    public void ResetStory(string sessionId)
    {
        var wrapper = GetSession(sessionId);
        wrapper.Reset();
    }

    /// <summary>End a session.</summary>
    public void EndSession(string sessionId)
    {
        _sessions.TryRemove(sessionId, out _);
    }

    private StoryWrapper GetSession(string id)
    {
        if (!_sessions.TryGetValue(id, out var wrapper))
            throw new KeyNotFoundException($"Session not found: {id}");
        return wrapper;
    }
}

/// <summary>
/// Stub wrapper around ink story. Will be replaced with actual
/// Ink.Runtime.Story when the C# source files are added.
/// </summary>
internal class StoryWrapper
{
    private readonly string _json;

    public StoryWrapper(string json)
    {
        _json = json;
    }

    public StoryState Continue()
    {
        // Stub — returns placeholder until ink C# runtime source is added
        return new StoryState(
            Text: "[InkBridge C# runtime — connect ink source to enable]",
            Choices: Array.Empty<Choice>(),
            CanContinue: false,
            Tags: Array.Empty<string>()
        );
    }

    public StoryState Choose(int index)
    {
        return Continue();
    }

    public object? GetVariable(string name) => null;
    public string SaveState() => "{}";
    public void LoadState(string json) { }
    public void Reset() { }
}
