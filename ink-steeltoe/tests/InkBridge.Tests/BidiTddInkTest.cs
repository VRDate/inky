// C# port of the 28-stage BidiTddInk test.
//
// Tests the C# ink runtime (Ink.Runtime.Story) directly,
// matching the same stages as:
//   - BidiTddInkTest.kt (JUnit + GraalJS + inkjs)
//   - incremental-e2e.test.js (Playwright + Electron + inkjs)
//   - bidi-tdd-stages.test.js (Playwright + ink-ai-assistant)
//
// Uses the shared fixture: app/test/fixtures/bidi_and_tdd.ink

using Xunit;
using InkBridge.Services;

namespace InkBridge.Tests;

public class BidiTddInkTest
{
    private readonly InkCSharpRuntime _runtime = new();

    // Path to shared fixture (compiled to JSON)
    // In CI, this would be pre-compiled from app/test/fixtures/bidi_and_tdd.ink
    private const string FixturePath = "../../../../app/test/fixtures/bidi_and_tdd.ink.json";

    [Fact(Skip = "Requires ink C# runtime source — connect Assets/Ink/InkRuntime/ to enable")]
    public void WalkThrough28SyntaxStages()
    {
        // TODO: When ink C# source is added:
        // 1. Load compiled JSON from fixture
        // 2. Start story session
        // 3. Walk through 28 stages, making choices
        // 4. Special handling for syn_14 (threads): pick "Leave"
        // 5. Assert reaching 28/28
        //
        // var json = File.ReadAllText(FixturePath);
        // var sessionId = _runtime.StartStory(json);
        // var state = _runtime.ContinueStory(sessionId);
        //
        // int stageCount = 0;
        // while (state.Choices.Length > 0 || state.CanContinue)
        // {
        //     // Check for stage marker "NN/28"
        //     var stageMatch = Regex.Match(state.Text, @"(\d+)/28");
        //     if (stageMatch.Success) stageCount = int.Parse(stageMatch.Groups[1].Value);
        //
        //     if (state.Choices.Length > 0)
        //     {
        //         // Thread knots (syn_14): pick "Leave" to avoid stranding
        //         var leaveIdx = Array.FindIndex(state.Choices,
        //             c => c.Text.Contains("Leave") || c.Text.Contains("עזוב"));
        //         var pick = leaveIdx >= 0 ? leaveIdx : 0;
        //         state = _runtime.Choose(sessionId, pick);
        //     }
        //     else if (state.CanContinue)
        //     {
        //         state = _runtime.ContinueStory(sessionId);
        //     }
        //     else break;
        // }
        //
        // Assert.Equal(28, stageCount);

        Assert.True(true, "Placeholder — enable when ink C# source is connected");
    }

    [Fact]
    public void RuntimeStartsAndStops()
    {
        var sessionId = _runtime.StartStory("{}");
        Assert.NotNull(sessionId);
        Assert.StartsWith("csharp-", sessionId);
        _runtime.EndSession(sessionId);
    }
}
