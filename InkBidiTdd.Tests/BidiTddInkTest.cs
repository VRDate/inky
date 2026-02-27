using System.Text;
using System.Text.RegularExpressions;
using Ink;
using Ink.Runtime;
using Xunit;
using IOPath = System.IO.Path;

namespace InkBidiTdd.Tests;

/// <summary>
/// C# ink compiler/runtime integration tests for bidi_and_tdd.ink.
///
/// Mirrors the KT BidiTddInkTest — validates that the C# ink compiler
/// and runtime handle all 28 syntax features with bilingual (Hebrew+English) content.
///
/// This validates the same ink source that KT tests via GraalJS+inkjs,
/// but here we use the native C# ink compiler+runtime directly.
/// </summary>
public class BidiTddInkTest
{
    // ═══════════════════════════════════════════════════════════════
    // FIXTURE
    // ═══════════════════════════════════════════════════════════════

    private static readonly string BidiTddSource = LoadFixture();

    private static string LoadFixture()
    {
        var candidates = new[]
        {
            IOPath.Combine(AppContext.BaseDirectory, "fixtures", "bidi_and_tdd.ink"),
            IOPath.Combine(FindProjectRoot(), "ink-kmp-mcp", "src", "test", "resources", "bidi_and_tdd.ink"),
            IOPath.Combine(FindProjectRoot(), "ink-electron", "test", "fixtures", "bidi_and_tdd.ink"),
        };

        foreach (var path in candidates)
        {
            if (File.Exists(path))
                return File.ReadAllText(path);
        }

        throw new FileNotFoundException(
            $"bidi_and_tdd.ink not found. Searched:\n  {string.Join("\n  ", candidates)}");
    }

    private static string FindProjectRoot()
    {
        var dir = Directory.GetCurrentDirectory();
        while (dir != null)
        {
            if (Directory.Exists(IOPath.Combine(dir, "ink-csharp")) ||
                File.Exists(IOPath.Combine(dir, ".git")))
                return dir;
            dir = Directory.GetParent(dir)?.FullName;
        }
        return Directory.GetCurrentDirectory();
    }

    /// <summary>Compile ink source, collecting errors via callback. Returns (story, errors).</summary>
    private static (Story? story, List<string> errors) CompileSource(string source)
    {
        var errors = new List<string>();
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

    /// <summary>Compile bidi_and_tdd.ink and return a Story, asserting no errors.</summary>
    private static Story CompileAndStart(string? source = null)
    {
        var (story, errors) = CompileSource(source ?? BidiTddSource);
        Assert.NotNull(story);
        Assert.Empty(errors);
        return story!;
    }

    /// <summary>Continue the story until it stops, collecting all text.</summary>
    private static string ContinueAll(Story story)
    {
        var sb = new StringBuilder();
        while (story.canContinue)
            sb.Append(story.Continue());
        return sb.ToString();
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPILATION
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Compile_bidi_and_tdd_ink_source()
    {
        var (story, errors) = CompileSource(BidiTddSource);
        Assert.NotNull(story);
        Assert.Empty(errors);
    }

    [Fact]
    public void Compile_produces_valid_JSON()
    {
        var story = CompileAndStart();
        var json = story.ToJson();
        Assert.StartsWith("{", json);
        Assert.Contains("\"inkVersion\"", json);
    }

    [Fact]
    public void Compiled_JSON_contains_Hebrew_text()
    {
        var story = CompileAndStart();
        var json = story.ToJson();
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
        var story = CompileAndStart();
        var text = ContinueAll(story);

        Assert.True(
            text.Contains("Inky Test Suite") || text.Contains("חבילת בדיקות"),
            "Main menu should contain title text");
        Assert.True(story.currentChoices.Count >= 5,
            $"Main menu should have at least 5 choices, got {story.currentChoices.Count}");
    }

    // ═══════════════════════════════════════════════════════════════
    // SMOKE TEST — basic compile + play
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Smoke_test_path_works()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        var smokeIndex = story.currentChoices
            .FindIndex(c => c.text.Contains("Smoke") || c.text.Contains("בדיקה מהירה"));
        Assert.True(smokeIndex >= 0, "Should find Smoke Test choice");

        story.ChooseChoiceIndex(smokeIndex);
        var afterSmoke = ContinueAll(story);
        Assert.True(
            afterSmoke.Contains("שלום") || afterSmoke.Contains("Hello"),
            "Smoke test should output text");
    }

    // ═══════════════════════════════════════════════════════════════
    // 28 SYNTAX FEATURES — play through syn_01..syn_28
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Syntax_features_play_through_28_stages()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        var syntaxIndex = story.currentChoices
            .FindIndex(c => c.text.Contains("Syntax") || c.text.Contains("28") || c.text.Contains("תחביר"));
        Assert.True(syntaxIndex >= 0, "Should find Syntax choice");

        story.ChooseChoiceIndex(syntaxIndex);

        var stageCount = 0;
        var allText = new StringBuilder();
        var stageRegex = new Regex(@"(\d{2})/28");

        for (var turn = 0; turn < 300; turn++)
        {
            var text = ContinueAll(story);
            allText.Append(text);

            foreach (Match match in stageRegex.Matches(text))
            {
                var stage = int.Parse(match.Groups[1].Value);
                stageCount = Math.Max(stageCount, stage);
            }

            if (text.Contains("28/28"))
                break;

            if (story.currentChoices.Count > 0)
            {
                var leaveIdx = story.currentChoices
                    .FindIndex(c => c.text.Contains("Leave") || c.text.Contains("עזוב"));
                var pick = leaveIdx >= 0 ? leaveIdx : 0;
                story.ChooseChoiceIndex(pick);
            }
            else
            {
                break;
            }
        }

        Assert.True(stageCount >= 28, $"Should reach stage 28/28, reached {stageCount}/28");
        Assert.True(
            allText.ToString().Contains("שלום") || allText.ToString().Contains("Hello"),
            "Should contain bilingual text");
    }

    // ═══════════════════════════════════════════════════════════════
    // VARIABLES — bilingual text support
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Variables_work_with_bilingual_text()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        Assert.Equal(100, (int)story.variablesState["health"]);
        Assert.Equal(50, (int)story.variablesState["gold"]);
        Assert.Equal("both", (string)story.variablesState["lang"]);

        story.variablesState["lang"] = "he";
        Assert.Equal("he", (string)story.variablesState["lang"]);
    }

    [Fact]
    public void Functions_evaluate_correctly()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        var result = story.EvaluateFunction("clamp", 150, 0, 100);
        Assert.Equal(100, result);

        var result2 = story.EvaluateFunction("clamp", -10, 0, 100);
        Assert.Equal(0, result2);
    }

    [Fact]
    public void Global_tags_accessible()
    {
        var story = CompileAndStart();
        // bidi_and_tdd.ink has no global tags at top level — just verify the API doesn't throw
        var tags = story.globalTags;
        // globalTags returns null when no tags exist, which is valid
        Assert.True(tags == null || tags.Count >= 0);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE — save / load round-trip
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Save_and_load_state_round_trip()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        var savedState = story.state.ToJson();
        Assert.NotNull(savedState);
        Assert.NotEmpty(savedState);

        if (story.currentChoices.Count > 0)
        {
            story.ChooseChoiceIndex(0);
            ContinueAll(story);
        }

        story.state.LoadJson(savedState);
        Assert.True(story.currentChoices.Count >= 5,
            "After restore, should have main menu choices");
    }

    [Fact]
    public void Reset_story_returns_to_beginning()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        if (story.currentChoices.Count > 0)
        {
            story.ChooseChoiceIndex(0);
            ContinueAll(story);
        }

        story.ResetState();
        ContinueAll(story);
        Assert.True(story.currentChoices.Count >= 5,
            "Reset should restore initial choices");
    }

    // ═══════════════════════════════════════════════════════════════
    // BIDI MUSEUM — 10 RTL scripts
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Bidi_museum_all_10_scripts_render()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        var museumIndex = story.currentChoices
            .FindIndex(c => c.text.Contains("Museum") || c.text.Contains("מוזיאון"));
        Assert.True(museumIndex >= 0, "Should find Museum choice");

        story.ChooseChoiceIndex(museumIndex);
        ContinueAll(story);

        var allIndex = story.currentChoices
            .FindIndex(c => c.text.Contains("All 10") || c.text.Contains("10"));
        Assert.True(allIndex >= 0, "Should find 'All 10' choice");

        story.ChooseChoiceIndex(allIndex);
        var text = ContinueAll(story);

        var scripts = new (string Name, string Sample)[]
        {
            ("Hebrew", "שלום"),
            ("Arabic", "مرحبا"),
            ("Persian", "سلام"),
            ("Urdu", "ہیلو"),
            ("Yiddish", "שלום וועלט"),
            ("Syriac", "ܫܠܡܐ"),
            ("Thaana", "ހެލޯ"),
            ("N'Ko", "ߊߟߎ"),
            ("Samaritan", "ࠔࠋࠌ"),
            ("Mandaic", "ࡔࡋࡀࡌࡀ"),
        };

        foreach (var (name, sample) in scripts)
        {
            Assert.True(text.Contains(sample),
                $"{name} script text '{sample}' should appear in museum output. Got: {text[..Math.Min(200, text.Length)]}...");
        }
    }

    [Fact]
    public void Individual_Hebrew_path_works()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        var museumIndex = story.currentChoices
            .FindIndex(c => c.text.Contains("Museum") || c.text.Contains("מוזיאון"));
        Assert.True(museumIndex >= 0);
        story.ChooseChoiceIndex(museumIndex);
        ContinueAll(story);

        var heIndex = story.currentChoices.FindIndex(c => c.text.Contains("Hebrew"));
        Assert.True(heIndex >= 0);
        story.ChooseChoiceIndex(heIndex);
        var text = ContinueAll(story);

        Assert.Contains("שלום", text);
        Assert.True(story.currentChoices.Count > 0, "Should have OK/Broken check choices");
    }

    // ═══════════════════════════════════════════════════════════════
    // LISTS — syn_15: LIST operators
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void LIST_declarations_accessible()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        var mood = story.variablesState["mood"];
        Assert.NotNull(mood);

        var inventory = story.variablesState["inventory"];
        Assert.NotNull(inventory);
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
        var (_, errors) = CompileSource(BidiTddSource);
        Assert.Empty(errors);
    }

    [Fact]
    public void Tunnel_syntax_in_source()
    {
        Assert.Contains("->->", BidiTddSource);
        var (_, errors) = CompileSource(BidiTddSource);
        Assert.Empty(errors);
    }

    [Fact]
    public void Thread_syntax_in_source()
    {
        Assert.Contains("<- syn_thread", BidiTddSource);
        var (_, errors) = CompileSource(BidiTddSource);
        Assert.Empty(errors);
    }

    [Fact]
    public void Glue_operator_in_source()
    {
        Assert.Contains("<>", BidiTddSource);
        var (_, errors) = CompileSource(BidiTddSource);
        Assert.Empty(errors);
    }

    [Fact]
    public void Tag_syntax_in_source()
    {
        Assert.True(BidiTddSource.Contains("# ") || BidiTddSource.Contains("#tag"));
        var (_, errors) = CompileSource(BidiTddSource);
        Assert.Empty(errors);
    }

    [Fact]
    public void String_operations_with_Hebrew_compile()
    {
        Assert.Contains("שלום לכולם", BidiTddSource);
        var (_, errors) = CompileSource(BidiTddSource);
        Assert.Empty(errors);
    }

    [Fact]
    public void Math_operations_compile()
    {
        Assert.Contains("a mod b", BidiTddSource);
        var (_, errors) = CompileSource(BidiTddSource);
        Assert.Empty(errors);
    }

    [Fact]
    public void Visit_count_and_TURNS_SINCE_in_source()
    {
        Assert.Contains("TURNS_SINCE", BidiTddSource);
        var (_, errors) = CompileSource(BidiTddSource);
        Assert.Empty(errors);
    }

    [Fact]
    public void INCLUDE_syntax_present_in_source_comments()
    {
        Assert.Contains("// INCLUDE", BidiTddSource);
    }

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
        var story1 = CompileAndStart();
        var json = story1.ToJson();

        var story2 = new Story(json);
        var text = ContinueAll(story2);

        Assert.True(
            text.Contains("Inky Test Suite") || text.Contains("חבילת בדיקות"),
            "JSON round-trip story should show main menu");
        Assert.True(story2.currentChoices.Count >= 5,
            "JSON round-trip story should have main menu choices");
    }

    // ═══════════════════════════════════════════════════════════════
    // BILINGUAL t() FUNCTION
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Bilingual_t_function_works()
    {
        var story = CompileAndStart();
        ContinueAll(story);

        Assert.Equal("both", (string)story.variablesState["lang"]);

        story.variablesState["lang"] = "he";
        story.ResetState();
        story.variablesState["lang"] = "he";
        var heText = ContinueAll(story);

        story.ResetState();
        story.variablesState["lang"] = "en";
        var enText = ContinueAll(story);

        Assert.NotEmpty(heText);
        Assert.NotEmpty(enText);
    }
}
