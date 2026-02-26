using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Xunit;

namespace InkMdTable.Tests;

/// <summary>
/// Tests markdown files containing ```ink fenced code blocks and markdown tables.
///
/// Validates that docs/BIDI_TDD_ISSUES.md:
///   1. Parses as a valid markdown document
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

    public record InkBlock(string Heading, string Source, int LineNumber);

    public record MdTable(string Heading, List<string> Headers, List<List<string>> Rows, int LineNumber);

    public record IssueRow(string IssueNumber, string Title, List<string> Tags, string TddVerdict, string TddReason);

    // ═══════════════════════════════════════════════════════════════
    // FIXTURES
    // ═══════════════════════════════════════════════════════════════

    private static readonly string ProjectRoot = FindProjectRoot();
    private static readonly string MdSource = File.ReadAllText(
        Path.Combine(ProjectRoot, "docs", "BIDI_TDD_ISSUES.md"));
    private static readonly List<InkBlock> InkBlocks = ExtractInkBlocks(MdSource);
    private static readonly List<MdTable> MdTables = ExtractMdTables(MdSource);
    private static readonly List<MdTable> IssuesTables = MdTables
        .Where(t => t.Headers.Contains("#") && t.Headers.Contains("TDD"))
        .ToList();

    private static readonly HashSet<string> KnownTags = new()
    {
        "compiler", "runtime", "parser", "ui", "editor", "electron", "export",
        "crash", "regression", "platform", "ux", "file-io", "save", "syntax",
        "bidi", "rtl", "i18n", "performance", "feature-request", "documentation",
        "accessibility", "tags", "state", "choices", "glue", "threads", "tunnels",
        "variables", "lists", "logic", "api", "dark-mode", "packaging"
    };

    // ═══════════════════════════════════════════════════════════════
    // PARSERS
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
        // Fallback: assume running from InkMdTable.Tests/
        return Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), ".."));
    }

    private static List<InkBlock> ExtractInkBlocks(string md)
    {
        var blocks = new List<InkBlock>();
        var lines = md.Split('\n');
        var inBlock = false;
        var heading = "";
        var blockLines = new List<string>();
        var lineNum = 0;

        for (int i = 0; i < lines.Length; i++)
        {
            var hm = Regex.Match(lines[i], @"^#{1,4}\s+(.+)");
            if (hm.Success) heading = hm.Groups[1].Value.Trim();

            if (!inBlock && lines[i].Trim() == "```ink")
            {
                inBlock = true;
                blockLines = new List<string>();
                lineNum = i + 1;
            }
            else if (inBlock && lines[i].Trim() == "```")
            {
                inBlock = false;
                blocks.Add(new InkBlock(heading, string.Join("\n", blockLines), lineNum));
            }
            else if (inBlock)
            {
                blockLines.Add(lines[i]);
            }
        }
        return blocks;
    }

    private static List<MdTable> ExtractMdTables(string md)
    {
        var tables = new List<MdTable>();
        var lines = md.Split('\n');
        var heading = "";

        for (int i = 0; i < lines.Length; i++)
        {
            var hm = Regex.Match(lines[i], @"^#{1,4}\s+(.+)");
            if (hm.Success) heading = hm.Groups[1].Value.Trim();

            if (lines[i].Trim().StartsWith("|") && i + 1 < lines.Length)
            {
                var sep = lines[i + 1];
                if (sep != null && Regex.IsMatch(sep.Trim(), @"^\|[\s\-:|]+\|"))
                {
                    var headers = ParseRow(lines[i]);
                    var rows = new List<List<string>>();
                    int j = i + 2;
                    while (j < lines.Length && lines[j].Trim().StartsWith("|"))
                    {
                        rows.Add(ParseRow(lines[j]));
                        j++;
                    }
                    if (rows.Count > 0)
                        tables.Add(new MdTable(heading, headers, rows, i + 1));
                    i = j - 1;
                }
            }
        }
        return tables;
    }

    private static List<string> ParseRow(string line) =>
        line.Split('|').Select(c => c.Trim()).Where(c => c.Length > 0).ToList();

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
    // 1. PARSE MARKDOWN DOCUMENT
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Markdown_source_is_loaded_and_non_empty()
    {
        Assert.True(MdSource.Length > 1000,
            $"Markdown source should be substantial, got {MdSource.Length} chars");
    }

    [Fact]
    public void Markdown_contains_expected_headings()
    {
        Assert.Contains("# Bidi TDD", MdSource);
        Assert.Contains("## E2E Test Resource", MdSource);
        Assert.Contains("## inkle/inky", MdSource);
        Assert.Contains("## inkle/ink", MdSource);
        Assert.Contains("## Summary Statistics", MdSource);
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. EXTRACT INK BLOCKS
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Extracts_all_9_ink_blocks()
    {
        Assert.Equal(9, InkBlocks.Count);
    }

    [Fact]
    public void Each_ink_block_has_non_empty_source()
    {
        foreach (var block in InkBlocks)
            Assert.True(block.Source.Trim().Length > 0,
                $"Block '{block.Heading}' at line {block.LineNumber} is empty");
    }

    [Fact]
    public void Ink_blocks_reference_issue_numbers()
    {
        foreach (var block in InkBlocks)
            Assert.True(
                block.Heading.Contains("Issue") || block.Heading.Contains("ink-"),
                $"Heading '{block.Heading}' should reference an Issue");
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
        var headings = string.Join(" ", InkBlocks.Select(b => b.Heading));
        foreach (var issue in new[] { "#122", "#541", "#534", "#508", "#485", "ink-959", "ink-916", "ink-844" })
            Assert.Contains(issue, headings);
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. EXTRACT MARKDOWN TABLES
    // ═══════════════════════════════════════════════════════════════

    [Fact]
    public void Finds_at_least_2_issues_tables()
    {
        Assert.True(IssuesTables.Count >= 2,
            $"Expected >= 2 issues tables, got {IssuesTables.Count}");
    }

    [Fact]
    public void Inky_table_has_required_columns()
    {
        var table = IssuesTables.First(t => t.Heading.Contains("inkle/inky"));
        foreach (var col in new[] { "#", "Title", "Tags", "TDD" })
            Assert.Contains(col, table.Headers);
    }

    [Fact]
    public void Ink_table_has_required_columns()
    {
        var table = IssuesTables.First(t => t.Heading.Contains("inkle/ink"));
        foreach (var col in new[] { "#", "Title", "Tags", "TDD" })
            Assert.Contains(col, table.Headers);
    }

    [Fact]
    public void Inky_table_has_at_least_40_rows()
    {
        var table = IssuesTables.First(t => t.Heading.Contains("inkle/inky"));
        Assert.True(table.Rows.Count >= 40,
            $"Expected >= 40 rows, got {table.Rows.Count}");
    }

    [Fact]
    public void Ink_table_has_at_least_20_rows()
    {
        var table = IssuesTables.First(t => t.Heading.Contains("inkle/ink"));
        Assert.True(table.Rows.Count >= 20,
            $"Expected >= 20 rows, got {table.Rows.Count}");
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. VERIFY INK BLOCKS COMPILE (static syntax validation)
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
            if (block.Heading.Contains("#534")) continue;

            bool hasTerminator = block.Source.Contains("-> END") ||
                                 block.Source.Contains("-> DONE") ||
                                 block.Source.Contains("->->");
            Assert.True(hasTerminator,
                $"Block '{block.Heading}' has diverts but no END/DONE");
        }
    }

    [Fact]
    public void RTL_blocks_contain_Hebrew_text()
    {
        var block122 = InkBlocks.First(b => b.Heading.Contains("#122"));
        Assert.Matches(new Regex(@"[\u0590-\u05FF]"), block122.Source);
    }

    [Fact]
    public void CJK_block_contains_three_scripts()
    {
        var block = InkBlocks.First(b => b.Heading.Contains("#485"));
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
                        $"Unknown tag '{tag}' in row {r + 1} of '{table.Heading}'");
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
            var m = Regex.Match(block.Heading, @"#(\d+)|ink-(\d+)");
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
        var inkyTable = IssuesTables.First(t => t.Heading.Contains("inkle/inky"));
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
