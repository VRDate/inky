using IOPath = System.IO.Path;

namespace InkBidiTdd.Tests;

/// <summary>
/// Shared test fixtures — companion object pattern (mirrors KT companion object).
///
/// Centralizes project root resolution and fixture loading that was duplicated
/// across BidiTddInkTest.cs, InkOneJsBindingTest.cs, and InkMdTableTest.cs.
///
/// KT equivalent: <c>companion object { val bidiTddSource by lazy { ... } }</c>
/// </summary>
public static class InkTestFixtures
{
    /// <summary>Mono repo root (contains ink-csharp/, ink-kmp-mcp/, docs/, etc.).</summary>
    public static string ProjectRoot { get; } = FindProjectRoot();

    /// <summary>bidi_and_tdd.ink source — loaded once, shared across all tests.</summary>
    public static string BidiTddSource { get; } = LoadBidiTddSource();

    /// <summary>Path to docs/BIDI_TDD_ISSUES.md.</summary>
    public static string BidiTddIssuesMdPath =>
        IOPath.Combine(ProjectRoot, "docs", "BIDI_TDD_ISSUES.md");

    /// <summary>
    /// Walk up from CWD looking for the mono repo root.
    /// Matches on ink-csharp/ dir or .git file — same heuristic across all test projects.
    /// </summary>
    public static string FindProjectRoot()
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

    /// <summary>
    /// Load bidi_and_tdd.ink from build output or source tree.
    /// Searches: output/fixtures → ink-kmp-mcp/resources → ink-electron/fixtures.
    /// </summary>
    public static string LoadBidiTddSource()
    {
        var candidates = new[]
        {
            IOPath.Combine(AppContext.BaseDirectory, "fixtures", "bidi_and_tdd.ink"),
            IOPath.Combine(ProjectRoot, "ink-kmp-mcp", "src", "commonTest", "resources", "bidi_and_tdd.ink"),
            IOPath.Combine(ProjectRoot, "ink-kmp-mcp", "src", "test", "resources", "bidi_and_tdd.ink"),
            IOPath.Combine(ProjectRoot, "ink-kmp-mcp", "src", "jsTest", "ink", "js", "fixtures", "bidi_and_tdd.ink"),
        };

        foreach (var path in candidates)
        {
            if (File.Exists(path))
                return File.ReadAllText(path);
        }

        throw new FileNotFoundException(
            $"bidi_and_tdd.ink not found. Searched:\n  {string.Join("\n  ", candidates)}");
    }
}
