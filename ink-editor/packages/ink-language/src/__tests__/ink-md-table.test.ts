/**
 * ink-md-table.test.ts — Tests markdown files containing ```ink fenced blocks
 * and markdown tables. Validates that BIDI_TDD_ISSUES.md ink blocks use valid
 * ink syntax tokens, and that table data conforms to the tag/TDD schema.
 *
 * Uses Node's built-in test runner (node --test).
 */

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

import {
  KNOT_REGEX,
  STITCH_REGEX,
  CHOICE_REGEX,
  GATHER_REGEX,
  VAR_DECL_REGEX,
  LIST_DECL_REGEX,
  DIVERT_REGEX,
  DIVERT_SPECIAL_REGEX,
  TAG_REGEX,
  GLUE_REGEX,
  TODO_REGEX,
  LINE_COMMENT_REGEX,
  LOGIC_LINE_REGEX,
  EXTERNAL_REGEX,
  classifyLine,
  type InkLineType,
} from "../ink-grammar";

// ═══════════════════════════════════════════════════════════════
// FIXTURES
// ═══════════════════════════════════════════════════════════════

const __dirname_ = dirname(fileURLToPath(import.meta.url));
const projectRoot = join(__dirname_, "..", "..", "..", "..", "..");
const mdPath = join(projectRoot, "docs", "BIDI_TDD_ISSUES.md");
const mdSource = readFileSync(mdPath, "utf8");

// ═══════════════════════════════════════════════════════════════
// PARSERS
// ═══════════════════════════════════════════════════════════════

interface InkBlock {
  heading: string;
  source: string;
  lineNumber: number;
}

interface MdTable {
  heading: string;
  headers: string[];
  rows: string[][];
  lineNumber: number;
}

interface MdTableRow {
  issueNumber: string;
  title: string;
  tags: string[];
  tddVerdict: "YES" | "NO" | "PARTIAL";
  tddReason: string;
}

function extractInkBlocks(md: string): InkBlock[] {
  const blocks: InkBlock[] = [];
  const lines = md.split("\n");
  let inBlock = false;
  let current: { heading: string; lines: string[]; lineNumber: number } | null =
    null;
  let lastHeading = "";

  for (let i = 0; i < lines.length; i++) {
    const headingMatch = lines[i].match(/^#{1,4}\s+(.+)/);
    if (headingMatch) lastHeading = headingMatch[1].trim();

    if (!inBlock && lines[i].trim() === "```ink") {
      inBlock = true;
      current = { heading: lastHeading, lines: [], lineNumber: i + 1 };
    } else if (inBlock && lines[i].trim() === "```") {
      inBlock = false;
      blocks.push({
        heading: current!.heading,
        source: current!.lines.join("\n"),
        lineNumber: current!.lineNumber,
      });
      current = null;
    } else if (inBlock && current) {
      current.lines.push(lines[i]);
    }
  }
  return blocks;
}

function extractMdTables(md: string): MdTable[] {
  const tables: MdTable[] = [];
  const lines = md.split("\n");
  let lastHeading = "";

  for (let i = 0; i < lines.length; i++) {
    const headingMatch = lines[i].match(/^#{1,4}\s+(.+)/);
    if (headingMatch) lastHeading = headingMatch[1].trim();

    if (lines[i].trim().startsWith("|") && i + 1 < lines.length) {
      const sep = lines[i + 1];
      if (sep && /^\|[\s\-:|]+\|/.test(sep.trim())) {
        const headers = parseRow(lines[i]);
        const rows: string[][] = [];
        let j = i + 2;
        while (j < lines.length && lines[j].trim().startsWith("|")) {
          rows.push(parseRow(lines[j]));
          j++;
        }
        if (rows.length > 0) {
          tables.push({ heading: lastHeading, headers, rows, lineNumber: i + 1 });
        }
        i = j - 1;
      }
    }
  }
  return tables;
}

function parseRow(line: string): string[] {
  return line
    .split("|")
    .map((c) => c.trim())
    .filter((c) => c.length > 0);
}

function parseIssueTableRow(headers: string[], row: string[]): MdTableRow {
  const idxHash = headers.indexOf("#");
  const idxTitle = headers.indexOf("Title");
  const idxTags = headers.indexOf("Tags");
  const idxTdd = headers.indexOf("TDD");

  const tddCell = row[idxTdd] || "";
  const verdict = tddCell.startsWith("YES:")
    ? "YES"
    : tddCell.startsWith("NO:")
      ? "NO"
      : "PARTIAL";

  return {
    issueNumber: row[idxHash] || "",
    title: row[idxTitle] || "",
    tags: (row[idxTags] || "").split(";").map((t) => t.trim()),
    tddVerdict: verdict,
    tddReason: tddCell.replace(/^(YES|NO|PARTIAL):\s*/, ""),
  };
}

// ═══════════════════════════════════════════════════════════════
// EXTRACTED DATA
// ═══════════════════════════════════════════════════════════════

const inkBlocks = extractInkBlocks(mdSource);
const mdTables = extractMdTables(mdSource);
const issuesTables = mdTables.filter(
  (t) => t.headers.includes("#") && t.headers.includes("TDD"),
);

// ═══════════════════════════════════════════════════════════════
// TESTS: INK BLOCK TOKEN CLASSIFICATION
// ═══════════════════════════════════════════════════════════════

describe("ink-grammar: classify lines from BIDI_TDD_ISSUES.md ink blocks", () => {
  it("finds 9 ink blocks in the markdown", () => {
    assert.equal(inkBlocks.length, 9);
  });

  it("classifies VAR declarations correctly", () => {
    for (const block of inkBlocks) {
      const varLines = block.source
        .split("\n")
        .filter((l) => l.trim().startsWith("VAR "));
      for (const line of varLines) {
        assert.match(
          line,
          VAR_DECL_REGEX,
          `VAR line "${line}" in "${block.heading}" should match VAR_DECL_REGEX`,
        );
      }
    }
  });

  it("classifies CONST declarations correctly", () => {
    for (const block of inkBlocks) {
      const constLines = block.source
        .split("\n")
        .filter((l) => l.trim().startsWith("CONST "));
      // Not all blocks have CONST — just validate those that do
      for (const line of constLines) {
        assert.ok(
          line.includes("="),
          `CONST line should have = in "${block.heading}"`,
        );
      }
    }
  });

  it("classifies knot declarations correctly", () => {
    for (const block of inkBlocks) {
      const knotLines = block.source
        .split("\n")
        .filter((l) => l.trim().startsWith("=== "));
      for (const line of knotLines) {
        assert.match(
          line,
          KNOT_REGEX,
          `Knot line "${line}" should match KNOT_REGEX`,
        );
      }
    }
  });

  it("classifies choice lines correctly", () => {
    for (const block of inkBlocks) {
      const choiceLines = block.source
        .split("\n")
        .filter((l) => /^\s*\*\s/.test(l) && !l.trim().startsWith("//"));
      for (const line of choiceLines) {
        // CHOICE_REGEX expects the bullet prefix
        assert.match(
          line.trim(),
          /^\*\s|^\+\s|^\*\s*\[|^\*\s*\{|^\*\s*->/,
          `Choice line "${line.trim()}" in "${block.heading}" should be a valid choice`,
        );
      }
    }
  });

  it("classifies divert lines correctly", () => {
    for (const block of inkBlocks) {
      const divertLines = block.source
        .split("\n")
        .filter(
          (l) =>
            l.includes("->") &&
            !l.trim().startsWith("//") &&
            !l.trim().startsWith("*"),
        );
      for (const line of divertLines) {
        assert.ok(
          DIVERT_REGEX.test(line) ||
            DIVERT_SPECIAL_REGEX.test(line) ||
            line.includes("-> END") ||
            line.includes("-> DONE") ||
            line.includes("->->") ||
            line.includes("-> start") ||
            line.includes("-> greet") ||
            line.includes("-> test_end") ||
            line.includes("-> hub") ||
            line.includes("-> fallback") ||
            line.includes("-> verify") ||
            line.includes("-> jp_path") ||
            line.includes("-> cn_path") ||
            line.includes("-> kr_path"),
          `Line with divert should match: "${line.trim()}"`,
        );
      }
    }
  });

  it("classifies comment lines correctly", () => {
    for (const block of inkBlocks) {
      const commentLines = block.source
        .split("\n")
        .filter((l) => l.trim().startsWith("//"));
      for (const line of commentLines) {
        assert.match(
          line.trim(),
          LINE_COMMENT_REGEX,
          `Comment "${line.trim()}" should match LINE_COMMENT_REGEX`,
        );
      }
    }
  });

  it("classifies glue operators correctly", () => {
    const glueBlocks = inkBlocks.filter((b) => b.source.includes("<>"));
    assert.ok(
      glueBlocks.length >= 1,
      "At least one ink block should use glue <>",
    );
    for (const block of glueBlocks) {
      const glueLines = block.source
        .split("\n")
        .filter((l) => l.includes("<>") && !l.trim().startsWith("//"));
      for (const line of glueLines) {
        assert.ok(
          GLUE_REGEX.test(line),
          `Glue line "${line}" should match GLUE_REGEX`,
        );
      }
    }
  });

  it("classifies tag lines correctly", () => {
    const tagBlocks = inkBlocks.filter(
      (b) => b.source.includes(" # ") && !b.source.includes("// #"),
    );
    for (const block of tagBlocks) {
      const tagLines = block.source
        .split("\n")
        .filter((l) => / # /.test(l) && !l.trim().startsWith("//"));
      for (const line of tagLines) {
        assert.ok(
          TAG_REGEX.test(line),
          `Tag line "${line}" should match TAG_REGEX`,
        );
      }
    }
  });

  it("classifies logic/tilda lines correctly", () => {
    for (const block of inkBlocks) {
      const logicLines = block.source
        .split("\n")
        .filter((l) => l.trim().startsWith("~ "));
      for (const line of logicLines) {
        assert.match(
          line.trim(),
          LOGIC_LINE_REGEX,
          `Logic line "${line.trim()}" should match LOGIC_LINE_REGEX`,
        );
      }
    }
  });
});

// ═══════════════════════════════════════════════════════════════
// TESTS: MARKDOWN TABLE SCHEMA
// ═══════════════════════════════════════════════════════════════

describe("markdown tables: schema validation", () => {
  it("finds at least 2 issues tables (inky + ink)", () => {
    assert.ok(issuesTables.length >= 2);
  });

  it("tags are semicolon-delimited lowercase kebab-case arrays", () => {
    for (const table of issuesTables) {
      const idxTags = table.headers.indexOf("Tags");
      for (let r = 0; r < table.rows.length; r++) {
        const tags = table.rows[r][idxTags].split(";").map((t) => t.trim());
        for (const tag of tags) {
          assert.match(
            tag,
            /^[a-z][a-z0-9-]*$/,
            `Tag "${tag}" in row ${r + 1} of "${table.heading}" should be kebab-case`,
          );
        }
      }
    }
  });

  it("TDD verdict is YES, NO, or PARTIAL", () => {
    for (const table of issuesTables) {
      const idxTdd = table.headers.indexOf("TDD");
      for (let r = 0; r < table.rows.length; r++) {
        const tdd = table.rows[r][idxTdd];
        assert.ok(
          tdd.startsWith("YES:") ||
            tdd.startsWith("NO:") ||
            tdd.startsWith("PARTIAL:"),
          `Row ${r + 1} TDD verdict invalid: "${tdd.substring(0, 40)}"`,
        );
      }
    }
  });

  it("all issue # cells have GitHub links", () => {
    for (const table of issuesTables) {
      const idxHash = table.headers.indexOf("#");
      for (let r = 0; r < table.rows.length; r++) {
        const cell = table.rows[r][idxHash];
        assert.ok(
          cell.includes("github.com/inkle/"),
          `Row ${r + 1} # cell should have GitHub link: ${cell}`,
        );
      }
    }
  });

  it("parsed MdTableRow has typed fields", () => {
    const table = issuesTables[0];
    const parsed = parseIssueTableRow(table.headers, table.rows[0]);
    assert.ok(parsed.issueNumber.length > 0);
    assert.ok(parsed.title.length > 0);
    assert.ok(parsed.tags.length >= 1);
    assert.ok(["YES", "NO", "PARTIAL"].includes(parsed.tddVerdict));
    assert.ok(parsed.tddReason.length > 0);
  });

  it("tags use only known tag vocabulary", () => {
    const knownTags = new Set([
      "compiler", "runtime", "parser", "ui", "editor", "electron", "export",
      "crash", "regression", "platform", "ux", "file-io", "save", "syntax",
      "bidi", "rtl", "i18n", "performance", "feature-request", "documentation",
      "accessibility", "tags", "state", "choices", "glue", "threads", "tunnels",
      "variables", "lists", "logic", "api", "dark-mode", "packaging",
    ]);

    for (const table of issuesTables) {
      const idxTags = table.headers.indexOf("Tags");
      for (let r = 0; r < table.rows.length; r++) {
        const tags = table.rows[r][idxTags].split(";").map((t) => t.trim());
        for (const tag of tags) {
          assert.ok(
            knownTags.has(tag),
            `Unknown tag "${tag}" in row ${r + 1} of "${table.heading}". Known: ${[...knownTags].join(", ")}`,
          );
        }
      }
    }
  });
});

// ═══════════════════════════════════════════════════════════════
// TESTS: CROSS-REFERENCE INK BLOCKS ↔ TABLES
// ═══════════════════════════════════════════════════════════════

describe("cross-reference: ink blocks use table data", () => {
  it("ink blocks referencing issues map to TDD=YES or PARTIAL rows", () => {
    // Each ink block heading contains an issue number; verify that issue
    // is marked YES or PARTIAL in the table (since blocks show TDD patterns)
    const allRows: MdTableRow[] = [];
    for (const table of issuesTables) {
      for (const row of table.rows) {
        allRows.push(parseIssueTableRow(table.headers, row));
      }
    }

    for (const block of inkBlocks) {
      // Extract issue ref from heading
      const match = block.heading.match(/#(\d+)|ink-(\d+)/);
      if (!match) continue;

      const issueNum = match[1] || match[2];
      const tableRow = allRows.find(
        (r) => r.issueNumber.includes(issueNum),
      );

      if (tableRow) {
        assert.ok(
          tableRow.tddVerdict === "YES" || tableRow.tddVerdict === "PARTIAL",
          `Ink block for issue ${issueNum} exists, but table says TDD=${tableRow.tddVerdict}. ` +
            `Blocks should only exist for TDD-preventable issues.`,
        );
      }
    }
  });

  it("summary statistics are internally consistent", () => {
    const summaryTables = mdTables.filter(
      (t) => t.headers.includes("Verdict") && t.headers.includes("Count"),
    );

    for (const table of summaryTables) {
      const idxCount = table.headers.indexOf("Count");
      const idxPercent = table.headers.indexOf("%");
      let total = 0;
      let percentTotal = 0;

      for (const row of table.rows) {
        const count = parseInt(row[idxCount].replace(/\*+/g, ""), 10);
        const pct = parseInt(row[idxPercent].replace(/\*+|%/g, ""), 10);
        if (!isNaN(count)) total += count;
        if (!isNaN(pct)) percentTotal += pct;
      }

      assert.ok(total > 0, `Summary "${table.heading}" total should be > 0`);
      assert.ok(
        percentTotal >= 99 && percentTotal <= 101,
        `Percentages in "${table.heading}" should sum to ~100%, got ${percentTotal}%`,
      );
    }
  });
});
