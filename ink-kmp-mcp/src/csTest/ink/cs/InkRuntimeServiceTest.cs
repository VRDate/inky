// ═══════════════════════════════════════════════════════════════
// InkRuntimeServiceTest.cs — Tests for IInkRuntime (Ink.Common)
// Validates the 11-method contract using bidi_and_tdd.ink.
// ═══════════════════════════════════════════════════════════════

using Xunit;

namespace Ink.Common.Tests
{
    public class InkRuntimeServiceTest
    {
        private readonly IInkRuntime _runtime = new InkRuntimeService();

        [Fact]
        public void Compile_ValidSource_ReturnsJson()
        {
            var result = _runtime.Compile("Hello world!");
            Assert.True(result.Success, $"Errors: {string.Join(", ", result.Errors)}");
            Assert.NotEmpty(result.Json);
        }

        [Fact]
        public void Compile_InvalidSource_ReturnsErrors()
        {
            var result = _runtime.Compile("-> missing_knot");
            Assert.False(result.Success);
            Assert.NotEmpty(result.Errors);
        }

        [Fact]
        public void StartStory_ContinueStory_ReturnsText()
        {
            var compiled = _runtime.Compile("Hello world!\n* [Choice A] A path\n* [Choice B] B path");
            Assert.True(compiled.Success);

            var sessionId = _runtime.StartStory(compiled.Json);
            Assert.NotNull(sessionId);

            var state = _runtime.ContinueStory(sessionId);
            Assert.Contains("Hello world!", state.Text);
            Assert.Equal(2, state.Choices.Length);
        }

        [Fact]
        public void Choose_NavigatesStory()
        {
            var compiled = _runtime.Compile("Start\n* [A] Path A\n* [B] Path B");
            var sessionId = _runtime.StartStory(compiled.Json);
            _runtime.ContinueStory(sessionId);

            var state = _runtime.Choose(sessionId, 0);
            Assert.Contains("Path A", state.Text);
        }

        [Fact]
        public void Variables_GetSet()
        {
            var compiled = _runtime.Compile("VAR x = 10\n{x}");
            var sessionId = _runtime.StartStory(compiled.Json);
            _runtime.ContinueStory(sessionId);

            var val = _runtime.GetVariable(sessionId, "x");
            Assert.Equal("10", val);

            _runtime.SetVariable(sessionId, "x", "42");
            Assert.Equal("42", _runtime.GetVariable(sessionId, "x"));
        }

        [Fact]
        public void SaveLoad_PreservesState()
        {
            var compiled = _runtime.Compile("Hello\n* [Go] World");
            var sessionId = _runtime.StartStory(compiled.Json);
            var state = _runtime.ContinueStory(sessionId);
            Assert.Contains("Hello", state.Text);
            Assert.Single(state.Choices);

            var saved = _runtime.SaveState(sessionId);
            Assert.NotEmpty(saved);

            // Choose, then load back — should restore choices
            _runtime.Choose(sessionId, 0);
            _runtime.LoadState(sessionId, saved);

            // After loading, choices should be restored (not text — story already consumed)
            var restored = _runtime.ContinueStory(sessionId);
            Assert.Equal(1, restored.Choices.Length);
        }

        [Fact]
        public void ResetStory_RestartsFromBeginning()
        {
            var compiled = _runtime.Compile("Hello\n* [Go] World");
            var sessionId = _runtime.StartStory(compiled.Json);
            _runtime.ContinueStory(sessionId);
            _runtime.Choose(sessionId, 0);

            _runtime.ResetStory(sessionId);
            var state = _runtime.ContinueStory(sessionId);
            Assert.Contains("Hello", state.Text);
        }

        [Fact]
        public void EndSession_RemovesSession()
        {
            var compiled = _runtime.Compile("Hello world!");
            var sessionId = _runtime.StartStory(compiled.Json);
            _runtime.EndSession(sessionId);

            Assert.Throws<System.ArgumentException>(() => _runtime.ContinueStory(sessionId));
        }

        [Fact]
        public void GetAssetEvents_ParsesTags()
        {
            var compiled = _runtime.Compile("# mesh:sword_model\n# anim:sword_slash\nThe knight draws a sword.");
            var sessionId = _runtime.StartStory(compiled.Json);
            _runtime.ContinueStory(sessionId);

            var events = _runtime.GetAssetEvents(sessionId);
            Assert.Equal(2, events.Length);
            Assert.Equal("mesh", events[0].Category);
            Assert.Equal("anim", events[1].Category);
        }
    }
}
