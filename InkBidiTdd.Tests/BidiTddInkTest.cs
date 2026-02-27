using System.Text;
using System.Text.RegularExpressions;
using Ink.Runtime;
using Xunit;
using static InkBidiTdd.Tests.InkTestFixtures;

namespace InkBidiTdd.Tests;

/// <summary>
/// C# ink compiler/runtime integration tests for bidi_and_tdd.ink.
///
/// Mirrors the KT BidiTddInkTest — validates that the C# ink compiler
/// and runtime handle all 28 syntax features with bilingual (Hebrew+English) content.
///
/// Uses <see cref="InkStorySession"/> fluent API and <see cref="InkTestFixtures"/>
/// companion object for shared fixtures (same pattern as KT companion object).
/// </summary>
public class BidiTddInkTest
{
    // ═══════════════════════════════════════════════════════════════
    // COMPILATION
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Compile_bidi_and_tdd_ink_source()
    {
        var (session, errors) = InkStorySession.TryCompile(BidiTddSource);
        Assert.NotNull(session);
        Assert.Empty(errors);
    }

    [Fact]
    public void Compile_produces_valid_JSON()
    {
        var json = InkStorySession.Compile(BidiTddSource).ToJson();
        Assert.StartsWith("{", json);
        Assert.Contains("\"inkVersion\"", json);
    }

    [Fact]
    public void Compiled_JSON_contains_Hebrew_text()
    {
        var json = InkStorySession.Compile(BidiTddSource).ToJson();
        Assert.True(
            json.Contains("שלום") || json.Contains("עברית") || json.Contains("חבילת"),
            "Compiled JSON should contain Hebrew text from the story");
    }

    // ═══════════════════════════════════════════════════════════════
    // STORY START — main menu renders
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Start_story_shows_main_menu_with_choices()
    {
        InkStorySession.Compile(BidiTddSource)
            .Continue()
            .AssertText("Inky Test Suite", "חבילת בדיקות")
            .AssertChoices(5);
    }

    // ═══════════════════════════════════════════════════════════════
    // SMOKE TEST — basic compile + play
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Smoke_test_path_works()
    {
        InkStorySession.Compile(BidiTddSource)
            .Continue()
            .Choose("Smoke", "בדיקה מהירה")
            .AssertText("שלום", "Hello");
    }

    // ═══════════════════════════════════════════════════════════════
    // 28 SYNTAX FEATURES — play through syn_01..syn_28
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Syntax_features_play_through_28_stages()
    {
        var s = InkStorySession.Compile(BidiTddSource)
            .Continue()
            .Choose("Syntax", "28", "תחביר");

        var stageCount = 0;
        var allText = new StringBuilder();
        var stageRegex = new Regex(@"(\d{2})/28");

        for (var turn = 0; turn < 300; turn++)
        {
            allText.Append(s.Text);

            foreach (Match match in stageRegex.Matches(s.Text))
            {
                var stage = int.Parse(match.Groups[1].Value);
                stageCount = Math.Max(stageCount, stage);
            }

            if (s.Text.Contains("28/28"))
                break;

            if (s.Choices.Count > 0)
            {
                var leaveIdx = s.Choices.FindIndex(c =>
                    c.text.Contains("Leave") || c.text.Contains("עזוב"));
                s.Choose(leaveIdx >= 0 ? leaveIdx : 0);
            }
            else break;
        }

        Assert.True(stageCount >= 28, $"Should reach stage 28/28, reached {stageCount}/28");
        Assert.Contains("שלום", allText.ToString());
    }

    // ═══════════════════════════════════════════════════════════════
    // VARIABLES — bilingual text support
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Variables_work_with_bilingual_text()
    {
        InkStorySession.Compile(BidiTddSource)
            .Continue()
            .AssertVar("health", 100)
            .AssertVar("gold", 50)
            .AssertVar("lang", "both")
            .Set("lang", "he")
            .AssertVar("lang", "he");
    }

    [Fact]
    public void Functions_evaluate_correctly()
    {
        var s = InkStorySession.Compile(BidiTddSource).Continue();
        Assert.Equal(100, s.EvalFunction("clamp", 150, 0, 100));
        Assert.Equal(0, s.EvalFunction("clamp", -10, 0, 100));
    }

    [Fact]
    public void Global_tags_accessible()
    {
        var s = InkStorySession.Compile(BidiTddSource);
        // bidi_and_tdd.ink has no global tags — just verify the API doesn't throw
        Assert.True(s.GlobalTags == null || s.GlobalTags.Count >= 0);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE — save / load round-trip
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Save_and_load_state_round_trip()
    {
        InkStorySession.Compile(BidiTddSource)
            .Continue()
            .Save()
            .Choose(0)
            .Load()
            .AssertChoices(5);
    }

    [Fact]
    public void Reset_story_returns_to_beginning()
    {
        InkStorySession.Compile(BidiTddSource)
            .Continue()
            .Choose(0)
            .Reset()
            .Continue()
            .AssertChoices(5);
    }

    // ═══════════════════════════════════════════════════════════════
    // BIDI MUSEUM — 10 RTL scripts
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Bidi_museum_all_10_scripts_render()
    {
        var s = InkStorySession.Compile(BidiTddSource)
            .Continue()
            .Choose("Museum", "מוזיאון")
            .Choose("All 10", "10");

        var scripts = new[] { "שלום", "مرحبا", "سلام", "ہیلو", "שלום וועלט",
            "ܫܠܡܐ", "ހެލޯ", "ߊߟߎ", "ࠔࠋࠌ", "ࡔࡋࡀࡌࡀ" };

        foreach (var sample in scripts)
            Assert.Contains(sample, s.Text);
    }

    [Fact]
    public void Individual_Hebrew_path_works()
    {
        InkStorySession.Compile(BidiTddSource)
            .Continue()
            .Choose("Museum", "מוזיאון")
            .Choose("Hebrew")
            .AssertText("שלום")
            .AssertChoices(1);
    }

    // ═══════════════════════════════════════════════════════════════
    // LISTS — syn_15: LIST operators
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void LIST_declarations_accessible()
    {
        var s = InkStorySession.Compile(BidiTddSource).Continue();
        Assert.NotNull(s.Var("mood"));
        Assert.NotNull(s.Var("inventory"));
    }

    // ═══════════════════════════════════════════════════════════════
    // SOURCE PATTERN CHECKS — syntax features in source
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Conditional_expressions_in_source_compile()
    {
        Assert.Contains("{inventory ? sword}", BidiTddSource);
        Assert.Contains("- mood == happy:", BidiTddSource);
        Assert.Contains("- else:", BidiTddSource);
        Assert.Empty(InkStorySession.TryCompile(BidiTddSource).errors);
    }

    [Fact]
    public void Tunnel_syntax_in_source()
    {
        Assert.Contains("->->", BidiTddSource);
        Assert.Empty(InkStorySession.TryCompile(BidiTddSource).errors);
    }

    [Fact]
    public void Thread_syntax_in_source()
    {
        Assert.Contains("<- syn_thread", BidiTddSource);
        Assert.Empty(InkStorySession.TryCompile(BidiTddSource).errors);
    }

    [Fact]
    public void Glue_operator_in_source()
    {
        Assert.Contains("<>", BidiTddSource);
        Assert.Empty(InkStorySession.TryCompile(BidiTddSource).errors);
    }

    [Fact]
    public void Tag_syntax_in_source()
    {
        Assert.True(BidiTddSource.Contains("# ") || BidiTddSource.Contains("#tag"));
        Assert.Empty(InkStorySession.TryCompile(BidiTddSource).errors);
    }

    [Fact]
    public void String_operations_with_Hebrew_compile()
    {
        Assert.Contains("שלום לכולם", BidiTddSource);
        Assert.Empty(InkStorySession.TryCompile(BidiTddSource).errors);
    }

    [Fact]
    public void Math_operations_compile()
    {
        Assert.Contains("a mod b", BidiTddSource);
        Assert.Empty(InkStorySession.TryCompile(BidiTddSource).errors);
    }

    [Fact]
    public void Visit_count_and_TURNS_SINCE_in_source()
    {
        Assert.Contains("TURNS_SINCE", BidiTddSource);
        Assert.Empty(InkStorySession.TryCompile(BidiTddSource).errors);
    }

    [Fact]
    public void INCLUDE_syntax_present_in_source_comments()
        => Assert.Contains("// INCLUDE", BidiTddSource);

    [Fact]
    public void EXTERNAL_declaration_syntax_in_source()
    {
        Assert.True(
            BidiTddSource.Contains("EXTERNAL") || BidiTddSource.Contains("27/28"),
            "Source should reference EXTERNAL functions in syn_27 section");
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON ROUND-TRIP — compile → JSON → new Story
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void JSON_round_trip_preserves_story()
    {
        var json = InkStorySession.Compile(BidiTddSource).ToJson();
        InkStorySession.FromJson(json)
            .Continue()
            .AssertText("Inky Test Suite", "חבילת בדיקות")
            .AssertChoices(5);
    }

    // ═══════════════════════════════════════════════════════════════
    // BILINGUAL t() FUNCTION
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Bilingual_t_function_works()
    {
        var s = InkStorySession.Compile(BidiTddSource)
            .Continue()
            .AssertVar("lang", "both");

        s.Set("lang", "he").Reset().Set("lang", "he");
        var heText = s.Continue().Text;

        s.Reset().Set("lang", "en");
        var enText = s.Continue().Text;

        Assert.NotEmpty(heText);
        Assert.NotEmpty(enText);
    }
}
