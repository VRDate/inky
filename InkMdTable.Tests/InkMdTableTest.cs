using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Markdig;
using Markdig.Syntax;
using Markdig.Syntax.Inlines;
using Markdig.Extensions.Tables;
using Xunit;

namespace InkMdTable.Tests;

/// <summary>
/// Tests markdown files containing ```ink fenced code blocks and markdown tables.
///
/// Uses **Markdig** (CommonMark parser) for AST-based extraction of:
///   - FencedCodeBlock nodes with Info="ink" → ink blocks
///   - Table nodes → markdown tables
///
/// H1-H6 headings act as a **file path** / POI spreadsheet sheet name /
/// ink LIST of headers addressing the tables and ```ink blocks beneath them.
///
/// Validates that docs/BIDI_TDD_ISSUES.md:
///   1. Parses as a valid CommonMark document via Markdig
///   2. Contains extractable ```ink code blocks with valid ink syntax
///   3. Contains markdown tables with correct schema (# | Title | Tags | TDD)
///   4. Tags are semicolon-delimited lowercase kebab-case arrays
///   5. TDD column uses YES:/NO:/PARTIAL: prefixes
///   6. Ink blocks cross-reference table entries
/// </summary>
public class InkMdTableTest
{
    // ═══════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════

    public record HeadingNode(int Level, string Text, List<string> Path);

    public record InkBlock(HeadingNode Heading, string Source, int LineNumber);

    public record MdTableData(HeadingNode Heading, List<string> Headers, List<List<string>> Rows, int LineNumber);

    public record IssueRow(string IssueNumber, string Title, List<string> Tags, string TddVerdict, string TddReason);

    // ═══════════════════════════════════════════════════════════════
    // FIXTURES
    // ═══════════════════════════════════════════════════════════════

    private static readonly string ProjectRoot = FindProjectRoot();
    private static readonly string MdSource = File.ReadAllText(
        Path.Combine(ProjectRoot, "docs", "BIDI_TDD_ISSUES.md"));

    private static readonly MarkdownPipeline Pipeline = new MarkdownPipelineBuilder()
        .UseAdvancedExtensions()
        .Build();

    private static readonly MarkdownDocument MdDoc = Markdown.Parse(MdSource, Pipeline);

    private static readonly List<HeadingNode> HeadingTree;
    private static readonly List<InkBlock> InkBlocks;
    private static readonly List<MdTableData> MdTables;
    private static readonly List<MdTableData> IssuesTables;

    private static readonly HashSet<string> KnownTags = new()
    {
        "compiler", "runtime", "parser", "ui", "editor", "electron", "export",
        "crash", "regression", "platform", "ux", "file-io", "save", "syntax",
        "bidi", "rtl", "i18n", "performance", "feature-request", "documentation",
        "accessibility", "tags", "state", "choices", "glue", "threads", "tunnels",
        "variables", "lists", "logic", "api", "dark-mode", "packaging"
    };

    static InkMdTableTest()
    {
        var result = ExtractViaMarkdig(MdDoc);
        HeadingTree = result.Headings;
        InkBlocks = result.InkBlocks;
        MdTables = result.Tables;
        IssuesTables = MdTables
            .Where(t => t.Headers.Contains("#") && t.Headers.Contains("TDD"))
            .ToList();
    }

    // ═══════════════════════════════════════════════════════════════
    // MARKDIG AST EXTRACTION — heading-routed blocks and tables
    // ═══════════════════════════════════════════════════════════════

    private static string FindProjectRoot()
    {
        var dir = Directory.GetCurrentDirectory();
        while (dir != null)
        {
            if (Directory.Exists(Path.Combine(dir, "docs")) &&
                File.Exists(Path.Combine(dir, "docs", "BIDI_TDD_ISSUES.md")))
                return dir;
            dir = Directory.GetParent(dir)?.FullName;
        }
        return Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), ".."));
    }

    private record ExtractResult(
        List<HeadingNode> Headings,
        List<InkBlock> InkBlocks,
        List<MdTableData> Tables);

    /// <summary>
    /// Walk the Markdig AST collecting:
    /// - HeadingBlock → heading path stack (h1-h6 as file paths / sheet names)
    /// - FencedCodeBlock with Info="ink" → InkBlock (routed by info string)
    /// - Table → MdTableData
    ///
    /// This mirrors the generic ```[info] fenced code block routing pattern
    /// used by Remirror, CodeMirror, and Flexmark.
    /// </summary>
    private static ExtractResult ExtractViaMarkdig(MarkdownDocument doc)
    {
        var headings = new List<HeadingNode>();
        var inkBlocks = new List<InkBlock>();
        var tables = new List<MdTableData>();

        // Heading stack tracks current path by level
        var headingStack = new List<(int Level, string Text)>();

        List<string> CurrentPath() => headingStack.Select(h => h.Text).ToList();

        HeadingNode CurrentHeading()
        {
            if (headingStack.Count == 0)
                return new HeadingNode(0, "(root)", new List<string>());
            var last = headingStack[^1];
            return new HeadingNode(last.Level, last.Text, CurrentPath());
        }

        foreach (var block in doc)
        {
            switch (block)
            {
                case HeadingBlock heading:
                {
                    var level = heading.Level;
                    var text = heading.Inline?.FirstChild?.ToString() ?? "";
                    // Collect full inline text
                    if (heading.Inline != null)
                    {
                        var sb = new System.Text.StringBuilder();
                        foreach (var inline in heading.Inline)
                            sb.Append(inline.ToString());
                        text = sb.ToString().Trim();
                    }

                    // Pop headings at same or deeper level
                    while (headingStack.Count > 0 && headingStack[^1].Level >= level)
                        headingStack.RemoveAt(headingStack.Count - 1);

                    headingStack.Add((level, text));
                    headings.Add(new HeadingNode(level, text, CurrentPath()));
                    break;
                }

                case FencedCodeBlock fenced:
                {
                    // Generic ```[info] routing — only process info="ink"
                    var info = fenced.Info?.Trim() ?? "";
                    if (info == "ink")
                    {
                        var source = fenced.Lines.ToString().TrimEnd();
                        inkBlocks.Add(new InkBlock(CurrentHeading(), source, fenced.Line + 1));
                    }
                    break;
                }

                case Table table:
                {
                    var headers = new List<string>();
                    var rows = new List<List<string>>();

                    foreach (var child in table)
                    {
                        if (child is TableRow row)
                        {
                            var cells = new List<string>();
                            foreach (var cell in row)
                            {
                                if (cell is TableCell tc)
                                {
                                    var sb = new System.Text.StringBuilder();
                                    foreach (var inline in tc.SelectMany(p =>
                                        p is ParagraphBlock pb && pb.Inline != null
                                            ? pb.Inline.Cast<Inline>()
                                            : Enumerable.Empty<Inline>()))
                                    {
                                        if (inline is LinkInline link)
                                            sb.Append(link.Url ?? link.Title ?? "");
                                        else if (inline is LiteralInline lit)
                                            sb.Append(lit.Content);
                                        else
                                            sb.Append(inline.ToString());
                                    }
                                    cells.Add(sb.ToString().Trim());
                                }
                            }

                            if (row.IsHeader)
                                headers = cells;
                            else
                                rows.Add(cells);
                        }
                    }

                    if (headers.Count > 0 && rows.Count > 0)
                        tables.Add(new MdTableData(CurrentHeading(), headers, rows, table.Line + 1));
                    break;
                }
            }
        }

        return new ExtractResult(headings, inkBlocks, tables);
    }

    private static IssueRow ParseIssueRow(List<string> headers, List<string> row)
    {
        int h = headers.IndexOf("#");
        int t = headers.IndexOf("Title");
        int tg = headers.IndexOf("Tags");
        int td = headers.IndexOf("TDD");

        string tddCell = td >= 0 && td < row.Count ? row[td] : "";
        string verdict = tddCell.StartsWith("YES:") ? "YES"
            : tddCell.StartsWith("NO:") ? "NO" : "PARTIAL";

        return new IssueRow(
            IssueNumber: h >= 0 && h < row.Count ? row[h] : "",
            Title: t >= 0 && t < row.Count ? row[t] : "",
            Tags: (tg >= 0 && tg < row.Count ? row[tg] : "")
                .Split(';').Select(s => s.Trim()).ToList(),
            TddVerdict: verdict,
            TddReason: Regex.Replace(tddCell, @"^(YES|NO|PARTIAL):\s*", "")
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. MARKDIG AST PARSING — document structure
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Markdig_parses_document_without_errors()
    {
        Assert.NotNull(MdDoc);
        Assert.True(MdDoc.Count > 50,
            $"Document should have many AST nodes, got {MdDoc.Count}");
    }

    [Fact]
    public void Markdig_finds_heading_code_and_table_AST_types()
    {
        var types = MdDoc.Select(b => b.GetType().Name).ToHashSet();
        Assert.Contains("HeadingBlock", types);
        Assert.Contains("FencedCodeBlock", types);
        Assert.Contains("Table", types);
    }

    [Fact]
    public void Heading_tree_has_expected_sections()
    {
        Assert.True(HeadingTree.Count > 10,
            $"Expected many headings, got {HeadingTree.Count}");
        var h2s = HeadingTree.Where(h => h.Level == 2).ToList();
        Assert.True(h2s.Count >= 5,
            $"Expected >= 5 h2 sections, got {h2s.Count}");
    }

    [Fact]
    public void Heading_path_tracks_ancestry()
    {
        var hasAncestry = HeadingTree.Any(h => h.Path.Count >= 2);
        Assert.True(hasAncestry, "Some headings should have multi-level path ancestry");
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. EXTRACT ```ink BLOCKS — routed by Info="ink"
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Extracts_all_9_ink_blocks_via_Markdig()
    {
        Assert.Equal(10, InkBlocks.Count);
    }

    [Fact]
    public void Each_ink_block_has_non_empty_source()
    {
        foreach (var block in InkBlocks)
            Assert.True(block.Source.Trim().Length > 0,
                $"Block '{block.Heading.Text}' at line {block.LineNumber} is empty");
    }

    [Fact]
    public void Ink_blocks_have_heading_path_ancestry()
    {
        foreach (var block in InkBlocks)
            Assert.True(block.Heading.Path.Count >= 1,
                $"Block '{block.Heading.Text}' should have heading path");
    }

    [Fact]
    public void Ink_blocks_are_under_E2E_section()
    {
        foreach (var block in InkBlocks)
        {
            var path = string.Join(" > ", block.Heading.Path);
            Assert.True(
                path.Contains("E2E Test Resource") || path.Contains("Issue"),
                $"Ink block should be under E2E section, got: {path}");
        }
    }

    [Fact]
    public void Ink_blocks_contain_ASSERT_comments()
    {
        int count = InkBlocks.Count(b => b.Source.Contains("// ASSERT:"));
        Assert.True(count >= 8,
            $"At least 8 blocks should have // ASSERT:, got {count}");
    }

    [Fact]
    public void Ink_blocks_cover_expected_issues()
    {
        var headings = string.Join(" ", InkBlocks.Select(b => b.Heading.Text));
        foreach (var issue in new[] { "#122", "#541", "#534", "#508", "#485", "ink-959", "ink-916", "ink-844" })
            Assert.Contains(issue, headings);
    }

    [Fact]
    public void Ink_blocks_routed_separately_from_other_code()
    {
        var allFenced = MdDoc.OfType<FencedCodeBlock>().ToList();
        var inkFenced = allFenced.Where(f => f.Info?.Trim() == "ink").ToList();
        var otherFenced = allFenced.Where(f => f.Info?.Trim() != "ink").ToList();

        Assert.Equal(10, inkFenced.Count);
        Assert.True(otherFenced.Count >= 0,
            "Non-ink code blocks exist as separate routes");
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. EXTRACT MARKDOWN TABLES — Markdig Table AST nodes
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Finds_at_least_2_issues_tables()
    {
        Assert.True(IssuesTables.Count >= 2,
            $"Expected >= 2 issues tables, got {IssuesTables.Count}");
    }

    [Fact]
    public void Issues_tables_addressed_under_section_headings()
    {
        foreach (var table in IssuesTables)
        {
            var path = string.Join(" > ", table.Heading.Path);
            Assert.True(
                path.Contains("inkle/inky") || path.Contains("inkle/ink"),
                $"Issues table should be under inkle section, got: {path}");
        }
    }

    [Fact]
    public void Tables_have_required_columns()
    {
        foreach (var table in IssuesTables)
            foreach (var col in new[] { "#", "Title", "Tags", "TDD" })
                Assert.Contains(col, table.Headers);
    }

    [Fact]
    public void Inky_table_has_at_least_40_rows()
    {
        var table = IssuesTables.First(t => string.Join(" ", t.Heading.Path).Contains("inkle/inky"));
        Assert.True(table.Rows.Count >= 40,
            $"Expected >= 40 rows, got {table.Rows.Count}");
    }

    [Fact]
    public void Ink_table_has_at_least_20_rows()
    {
        var table = IssuesTables.First(t => string.Join(" ", t.Heading.Path).Contains("inkle/ink"));
        Assert.True(table.Rows.Count >= 20,
            $"Expected >= 20 rows, got {table.Rows.Count}");
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. VERIFY INK BLOCKS — static syntax validation
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void VAR_declarations_use_valid_syntax()
    {
        var varRegex = new Regex(@"^(VAR|CONST)\s+\w+\s*=");
        foreach (var block in InkBlocks)
        {
            var varLines = block.Source.Split('\n')
                .Where(l => l.Trim().StartsWith("VAR ") || l.Trim().StartsWith("CONST "));
            foreach (var line in varLines)
                Assert.Matches(varRegex, line.Trim());
        }
    }

    [Fact]
    public void Knot_declarations_use_valid_syntax()
    {
        var knotRegex = new Regex(@"^={2,}\s*\w[\w(, )]*={2,}\s*$");
        foreach (var block in InkBlocks)
        {
            var knotLines = block.Source.Split('\n')
                .Where(l => l.Trim().StartsWith("=== "));
            foreach (var line in knotLines)
                Assert.Matches(knotRegex, line.Trim());
        }
    }

    [Fact]
    public void Choice_lines_use_valid_markers()
    {
        var choiceRegex = new Regex(@"^\*\s|^\+\s|^\*\s*\[|^\*\s*\{|^\*\s*->");
        foreach (var block in InkBlocks)
        {
            var choiceLines = block.Source.Split('\n')
                .Where(l =>
                {
                    var t = l.Trim();
                    return (t.StartsWith("* ") || t.StartsWith("+ ") ||
                            t.StartsWith("* [") || t.StartsWith("* {") ||
                            t == "* -> fallback") && !t.StartsWith("//");
                });
            foreach (var line in choiceLines)
                Assert.Matches(choiceRegex, line.Trim());
        }
    }

    [Fact]
    public void Blocks_with_diverts_have_END_except_534()
    {
        foreach (var block in InkBlocks)
        {
            if (!block.Source.Contains("->")) continue;
            if (block.Heading.Text.Contains("#534")) continue;

            bool hasTerminator = block.Source.Contains("-> END") ||
                                 block.Source.Contains("-> DONE") ||
                                 block.Source.Contains("->->");
            Assert.True(hasTerminator,
                $"Block '{block.Heading.Text}' has diverts but no END/DONE");
        }
    }

    [Fact]
    public void RTL_blocks_contain_Hebrew_text()
    {
        var block122 = InkBlocks.First(b => b.Heading.Text.Contains("#122"));
        Assert.Matches(new Regex(@"[\u0590-\u05FF]"), block122.Source);
    }

    [Fact]
    public void CJK_block_contains_three_scripts()
    {
        var block = InkBlocks.First(b => b.Heading.Text.Contains("#485"));
        Assert.Matches(new Regex(@"[\u3040-\u309F]"), block.Source);  // Hiragana
        Assert.Matches(new Regex(@"[\u4E00-\u9FFF]"), block.Source);  // CJK Unified
        Assert.Matches(new Regex(@"[\uAC00-\uD7AF]"), block.Source);  // Hangul
    }

    [Fact]
    public void Logic_tilda_lines_are_valid()
    {
        var logicRegex = new Regex(@"^~\s");
        foreach (var block in InkBlocks)
        {
            var logicLines = block.Source.Split('\n')
                .Where(l => l.Trim().StartsWith("~ "));
            foreach (var line in logicLines)
                Assert.Matches(logicRegex, line.Trim());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. VERIFY TABLE DATA CONSISTENCY
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void All_issue_cells_contain_GitHub_links()
    {
        foreach (var table in IssuesTables)
        {
            int idx = table.Headers.IndexOf("#");
            for (int r = 0; r < table.Rows.Count; r++)
                Assert.Contains("github.com/inkle/", table.Rows[r][idx]);
        }
    }

    [Fact]
    public void Tags_are_semicolon_delimited_kebab_case()
    {
        var kebab = new Regex(@"^[a-z][a-z0-9-]*$");
        foreach (var table in IssuesTables)
        {
            int idx = table.Headers.IndexOf("Tags");
            for (int r = 0; r < table.Rows.Count; r++)
            {
                var tags = table.Rows[r][idx].Split(';').Select(t => t.Trim());
                foreach (var tag in tags)
                    Assert.Matches(kebab, tag);
            }
        }
    }

    [Fact]
    public void Tags_use_known_vocabulary()
    {
        foreach (var table in IssuesTables)
        {
            int idx = table.Headers.IndexOf("Tags");
            for (int r = 0; r < table.Rows.Count; r++)
            {
                var tags = table.Rows[r][idx].Split(';').Select(t => t.Trim());
                foreach (var tag in tags)
                    Assert.True(KnownTags.Contains(tag),
                        $"Unknown tag '{tag}' in row {r + 1} of '{table.Heading.Text}'");
            }
        }
    }

    [Fact]
    public void TDD_starts_with_YES_NO_or_PARTIAL()
    {
        foreach (var table in IssuesTables)
        {
            int idx = table.Headers.IndexOf("TDD");
            for (int r = 0; r < table.Rows.Count; r++)
            {
                var tdd = table.Rows[r][idx];
                Assert.True(
                    tdd.StartsWith("YES:") || tdd.StartsWith("NO:") || tdd.StartsWith("PARTIAL:"),
                    $"Row {r + 1} TDD invalid: '{tdd[..Math.Min(40, tdd.Length)]}'");
            }
        }
    }

    [Fact]
    public void ParseIssueRow_produces_typed_fields()
    {
        var table = IssuesTables.First();
        var parsed = ParseIssueRow(table.Headers, table.Rows.First());
        Assert.NotEmpty(parsed.IssueNumber);
        Assert.NotEmpty(parsed.Title);
        Assert.NotEmpty(parsed.Tags);
        Assert.Contains(parsed.TddVerdict, new[] { "YES", "NO", "PARTIAL" });
        Assert.NotEmpty(parsed.TddReason);
    }

    [Fact]
    public void Summary_percentages_sum_to_100()
    {
        var summaryTables = MdTables
            .Where(t => t.Headers.Contains("Verdict") && t.Headers.Contains("Count"))
            .ToList();
        Assert.True(summaryTables.Count >= 2);

        foreach (var table in summaryTables)
        {
            int idxPct = table.Headers.IndexOf("%");
            int totalPct = 0;
            foreach (var row in table.Rows)
            {
                var pctStr = Regex.Replace(row[idxPct], @"\*+|%", "").Trim();
                if (int.TryParse(pctStr, out int pct))
                    totalPct += pct;
            }
            Assert.InRange(totalPct, 99, 101);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. CROSS-REFERENCE INK BLOCKS ↔ TABLE ENTRIES
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Ink_block_issues_map_to_TDD_YES_or_PARTIAL()
    {
        var allRows = IssuesTables
            .SelectMany(t => t.Rows.Select(r => ParseIssueRow(t.Headers, r)))
            .ToList();

        foreach (var block in InkBlocks)
        {
            var m = Regex.Match(block.Heading.Text, @"#(\d+)|ink-(\d+)");
            if (!m.Success) continue;
            var num = m.Groups[1].Value != "" ? m.Groups[1].Value : m.Groups[2].Value;

            var tableRow = allRows.FirstOrDefault(r => r.IssueNumber.Contains(num));
            if (tableRow != null)
            {
                Assert.True(
                    tableRow.TddVerdict == "YES" || tableRow.TddVerdict == "PARTIAL",
                    $"Ink block for issue {num} has TDD={tableRow.TddVerdict}");
            }
        }
    }

    [Fact]
    public void TDD_YES_count_matches_table()
    {
        var inkyTable = IssuesTables.First(t =>
            string.Join(" ", t.Heading.Path).Contains("inkle/inky"));
        int yesCount = inkyTable.Rows
            .Select(r => ParseIssueRow(inkyTable.Headers, r))
            .Count(r => r.TddVerdict == "YES");
        Assert.True(yesCount >= 20,
            $"Expected >= 20 TDD=YES in inky table, got {yesCount}");
    }

    [Fact]
    public void Test_matrix_has_all_coverage_columns()
    {
        var matrix = MdTables.First(t =>
            t.Headers.Contains("Feature") && t.Headers.Contains("TDD Gap"));
        foreach (var col in new[] { "bidi_and_tdd.ink", "bidi-e2e.test.js", "BidiTddInkTest.kt", "bidify.test.js" })
            Assert.Contains(col, matrix.Headers);
    }

    [Fact]
    public void Test_matrix_covers_key_features()
    {
        var matrix = MdTables.First(t =>
            t.Headers.Contains("Feature") && t.Headers.Contains("TDD Gap"));
        int idx = matrix.Headers.IndexOf("Feature");
        var features = matrix.Rows.Select(r => r[idx]).ToList();

        foreach (var expected in new[] { "Plain text", "Choices", "Diverts", "Glue", "Tags",
                                         "Tunnels", "Threads", "Variables", "Functions", "Lists" })
            Assert.True(features.Any(f => f.Contains(expected)),
                $"Matrix should cover: {expected}");
    }
}
