using System.Text;
using Ink;
using Ink.Runtime;

namespace InkBidiTdd.Tests;

/// <summary>
/// Fluent API for ink Story interaction with built-in thread-safe compilation.
///
/// The ink C# compiler has non-thread-safe static state (CharacterSet HashSet,
/// InkParser tables). A SemaphoreSlim(1,1) serializes compilation while leaving
/// per-session Story operations fully parallel — same as Java/JS runtimes.
///
/// Usage:
/// <code>
///   var s = InkStorySession.Compile(source).Continue();
///   s.Choose("Smoke").Continue().AssertText("שלום");
///   s.Set("lang", "he").Reset().Continue().AssertChoices(5);
///   s.Save().Choose(0).Continue().Load().AssertChoices(5);
/// </code>
/// </summary>
public class InkStorySession
{
    private static readonly SemaphoreSlim CompileSemaphore = new(1, 1);

    private readonly Story _story;
    private string _text = "";
    private string? _savedState;

    private InkStorySession(Story story) => _story = story;

    // ── Factory ────────────────────────────────────────────────────

    /// <summary>Thread-safe compile from ink source. Asserts no errors.</summary>
    public static InkStorySession Compile(string source)
    {
        var (story, errors) = CompileLocked(source);
        if (errors.Count > 0 || story == null)
            throw new InvalidOperationException(
                $"Ink compilation failed:\n  {string.Join("\n  ", errors)}");
        return new InkStorySession(story);
    }

    /// <summary>Thread-safe compile returning (session, errors) for error-path tests.</summary>
    public static (InkStorySession? session, List<string> errors) TryCompile(string source)
    {
        var (story, errors) = CompileLocked(source);
        return story != null && errors.Count == 0
            ? (new InkStorySession(story), errors)
            : (null, errors);
    }

    /// <summary>Create session from pre-compiled JSON (no semaphore needed).</summary>
    public static InkStorySession FromJson(string json)
        => new(new Story(json));

    // ── Fluent navigation ──────────────────────────────────────────

    /// <summary>Continue until the story stops. Accumulates text.</summary>
    public InkStorySession Continue()
    {
        var sb = new StringBuilder();
        while (_story.canContinue)
            sb.Append(_story.Continue());
        _text = sb.ToString();
        return this;
    }

    /// <summary>Choose by index, then continue.</summary>
    public InkStorySession Choose(int index)
    {
        _story.ChooseChoiceIndex(index);
        return Continue();
    }

    /// <summary>Choose by text match (first choice containing any of the given strings).</summary>
    public InkStorySession Choose(params string[] textContains)
    {
        var idx = _story.currentChoices
            .FindIndex(c => textContains.Any(t => c.text.Contains(t)));
        if (idx < 0)
            throw new InvalidOperationException(
                $"No choice matching [{string.Join(", ", textContains)}] in: " +
                string.Join(", ", _story.currentChoices.Select(c => $"'{c.text}'")));
        _story.ChooseChoiceIndex(idx);
        return Continue();
    }

    /// <summary>Set a variable.</summary>
    public InkStorySession Set(string name, object value)
    {
        _story.variablesState[name] = value;
        return this;
    }

    /// <summary>Save current state (retrievable via <see cref="SavedState"/>).</summary>
    public InkStorySession Save()
    {
        _savedState = _story.state.ToJson();
        return this;
    }

    /// <summary>Load previously saved state.</summary>
    public InkStorySession Load()
    {
        if (_savedState == null)
            throw new InvalidOperationException("No saved state — call Save() first");
        _story.state.LoadJson(_savedState);
        return this;
    }

    /// <summary>Reset story to initial state.</summary>
    public InkStorySession Reset()
    {
        _story.ResetState();
        return this;
    }

    // ── Accessors ──────────────────────────────────────────────────

    /// <summary>Text from the last Continue() call.</summary>
    public string Text => _text;

    /// <summary>Current choices.</summary>
    public List<Choice> Choices => _story.currentChoices;

    /// <summary>Can the story continue?</summary>
    public bool CanContinue => _story.canContinue;

    /// <summary>Get a typed variable.</summary>
    public T Var<T>(string name) => (T)_story.variablesState[name];

    /// <summary>Get raw variable value.</summary>
    public object? Var(string name) => _story.variablesState[name];

    /// <summary>Last saved state JSON (null if Save() not called).</summary>
    public string? SavedState => _savedState;

    /// <summary>Export compiled story as JSON.</summary>
    public string ToJson() => _story.ToJson();

    /// <summary>Global tags (may be null).</summary>
    public List<string>? GlobalTags => _story.globalTags;

    /// <summary>Current tags.</summary>
    public List<string>? CurrentTags => _story.currentTags;

    /// <summary>Evaluate an ink function.</summary>
    public object? EvalFunction(string name, params object[] args)
        => _story.EvaluateFunction(name, args);

    /// <summary>Direct access to the underlying Story for advanced use.</summary>
    public Story Story => _story;

    // ── Fluent assertions ──────────────────────────────────────────

    /// <summary>Assert text contains at least one of the given strings.</summary>
    public InkStorySession AssertText(params string[] contains)
    {
        if (!contains.Any(s => _text.Contains(s)))
            throw new Xunit.Sdk.XunitException(
                $"Text should contain one of [{string.Join(", ", contains)}].\n" +
                $"Got: {_text[..Math.Min(200, _text.Length)]}...");
        return this;
    }

    /// <summary>Assert minimum number of choices available.</summary>
    public InkStorySession AssertChoices(int minCount)
    {
        if (_story.currentChoices.Count < minCount)
            throw new Xunit.Sdk.XunitException(
                $"Expected >= {minCount} choices, got {_story.currentChoices.Count}");
        return this;
    }

    /// <summary>Assert a variable equals expected value.</summary>
    public InkStorySession AssertVar<T>(string name, T expected)
    {
        var actual = _story.variablesState[name];
        if (!Equals(actual, expected))
            throw new Xunit.Sdk.XunitException(
                $"Variable '{name}': expected {expected}, got {actual}");
        return this;
    }

    // ── Compile lock ───────────────────────────────────────────────

    /// <summary>
    /// Thread-safe compile — serializes via SemaphoreSlim(1,1).
    /// Public so InkBridge and other callers can reuse the lock.
    /// </summary>
    public static (Story? story, List<string> errors) CompileLocked(string source)
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
            return (story, errors);
        }
        finally
        {
            CompileSemaphore.Release();
        }
    }
}
