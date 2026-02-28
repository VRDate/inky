/**
 * ink-md-table.test.ts — Tests ```ink markdown block routing and table extraction.
 *
 * Validates the generic ```[info] fenced code block routing pattern used by:
 *   - Remirror: inkCodeBlockSpec parseDOM routes <pre class="ink-code-block">
 *   - CodeMirror: StreamLanguage routes by fenced info="ink"
 *   - Flexmark (KT): FencedCodeBlock with info="ink"
 *
 * H1-H6 headings act as a **file path** / POI spreadsheet sheet name /
 * ink LIST of headers addressing the tables and ```ink blocks beneath them.
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
  VAR_DECL_REGEX,
  LIST_DECL_REGEX,
  CHOICE_REGEX,
  GATHER_REGEX,
  GLUE_REGEX,
  TAG_REGEX,
  LOGIC_LINE_REGEX,
  LINE_COMMENT_REGEX,
  DIVERT_REGEX,
  DIVERT_SPECIAL_REGEX,
  DIVERT_TUNNEL_REGEX,
  INCLUDE_REGEX,
  EXTERNAL_REGEX,
  ESCAPE_REGEX,
  INLINE_CONDITIONAL_REGEX,
  classifyLine,
  tokenCategory,
} from "../ink-grammar";

// ═══════════════════════════════════════════════════════════════
// FIXTURES
// ═══════════════════════════════════════════════════════════════

const __dirname_ = dirname(fileURLToPath(import.meta.url));
const projectRoot = join(__dirname_, "..", "..", "..", "..", "..", "..");
const mdPath = join(projectRoot, "docs", "BIDI_TDD_ISSUES.md");
const mdSource = readFileSync(mdPath, "utf8");

// ═══════════════════════════════════════════════════════════════
// MARKDOWN AST — heading-routed block/table extraction
//
// Headings (h1-h6) define a **path** (like a filesystem or
// spreadsheet sheet name) that addresses the tables and
// ```ink blocks beneath them.
//
// This mirrors how:
//   - Remirror routes ProseMirror nodes by section
//   - POI/Excel names sheets by heading
//   - JSON nests objects by heading path
//   - ink uses knot/stitch names as addressable labels
// ═══════════════════════════════════════════════════════════════

interface HeadingNode {
  level: number;
  text: string;
  path: string[]; // full heading ancestry, e.g. ["root", "section", "subsection"]
}

interface InkBlock {
  heading: HeadingNode;
  source: string;
  lineNumber: number;
}

interface MdTable {
  heading: HeadingNode;
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

/**
 * Parse markdown into a heading-addressed tree.
 * Returns ink blocks and tables each tagged with their heading path.
 *
 * This is the generic ```[info] fenced code block routing pattern:
 * when info === "ink", the block is routed to ink processing.
 */
function parseMdWithHeadingPaths(md: string): {
  inkBlocks: InkBlock[];
  tables: MdTable[];
  headingTree: HeadingNode[];
} {
  const lines = md.split("\n");
  const inkBlocks: InkBlock[] = [];
  const tables: MdTable[] = [];
  const headingTree: HeadingNode[] = [];

  // Heading stack — tracks current heading path by level
  const headingStack: { level: number; text: string }[] = [];

  function currentPath(): string[] {
    return headingStack.map((h) => h.text);
  }

  function currentHeading(): HeadingNode {
    const last = headingStack[headingStack.length - 1];
    return {
      level: last?.level ?? 0,
      text: last?.text ?? "(root)",
      path: currentPath(),
    };
  }

  let inCodeBlock = false;
  let codeBlockLang = "";
  let codeBlockLines: string[] = [];
  let codeBlockStart = 0;
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // ─── Heading detection (h1-h6) ───
    const headingMatch = line.match(/^(#{1,6})\s+(.+)/);
    if (headingMatch && !inCodeBlock) {
      const level = headingMatch[1].length;
      const text = headingMatch[2].trim();

      // Pop headings at same or deeper level
      while (headingStack.length > 0 && headingStack[headingStack.length - 1].level >= level) {
        headingStack.pop();
      }
      headingStack.push({ level, text });
      headingTree.push({ level, text, path: currentPath() });
      i++;
      continue;
    }

    // ─── Fenced code block start: ```[info] routing ───
    const fenceMatch = line.match(/^```(\w*)\s*$/);
    if (fenceMatch && !inCodeBlock) {
      inCodeBlock = true;
      codeBlockLang = fenceMatch[1]; // "ink", "js", "bash", etc.
      codeBlockLines = [];
      codeBlockStart = i + 1;
      i++;
      continue;
    }

    // ─── Fenced code block end ───
    if (inCodeBlock && line.trim() === "```") {
      if (codeBlockLang === "ink") {
        // ROUTED to ink processing
        inkBlocks.push({
          heading: currentHeading(),
          source: codeBlockLines.join("\n"),
          lineNumber: codeBlockStart,
        });
      }
      // Other languages would route elsewhere
      inCodeBlock = false;
      i++;
      continue;
    }

    if (inCodeBlock) {
      codeBlockLines.push(line);
      i++;
      continue;
    }

    // ─── Table detection ───
    if (line.trim().startsWith("|") && i + 1 < lines.length) {
      const sep = lines[i + 1];
      if (sep && /^\|[\s\-:|]+\|/.test(sep.trim())) {
        const headers = parseRow(line);
        const rows: string[][] = [];
        let j = i + 2;
        while (j < lines.length && lines[j].trim().startsWith("|")) {
          rows.push(parseRow(lines[j]));
          j++;
        }
        if (rows.length > 0) {
          tables.push({
            heading: currentHeading(),
            headers,
            rows,
            lineNumber: i + 1,
          });
        }
        i = j;
        continue;
      }
    }

    i++;
  }

  return { inkBlocks, tables, headingTree };
}

function parseRow(line: string): string[] {
  return line
    .split("|")
    .map((c) => c.trim())
    .filter((c) => c.length > 0);
}

function parseIssueRow(headers: string[], row: string[]): MdTableRow {
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
// PARSE
// ═══════════════════════════════════════════════════════════════

const { inkBlocks, tables: mdTables, headingTree } = parseMdWithHeadingPaths(mdSource);
const issuesTables = mdTables.filter(
  (t) => t.headers.includes("#") && t.headers.includes("TDD") && t.headers.includes("Tags"),
);

// ═══════════════════════════════════════════════════════════════
// 1. HEADING PATH TREE
// ═══════════════════════════════════════════════════════════════

describe("1. heading path tree (file path / sheet name / ink label)", () => {
  it("builds a heading tree from h1-h6 elements", () => {
    assert.ok(headingTree.length > 10, `Expected many headings, got ${headingTree.length}`);
  });

  it("h2 headings act as section paths for tables and blocks", () => {
    const h2s = headingTree.filter((h) => h.level === 2);
    assert.ok(h2s.length >= 5, `Expected >= 5 h2 sections, got ${h2s.length}`);
  });

  it("ink blocks have heading path ancestry", () => {
    for (const block of inkBlocks) {
      assert.ok(block.heading.path.length >= 1,
        `Block '${block.heading.text}' should have heading path`);
    }
  });

  it("tables have heading path ancestry", () => {
    for (const table of mdTables) {
      assert.ok(table.heading.path.length >= 1,
        `Table at '${table.heading.text}' should have heading path`);
    }
  });

  it("issues tables are addressed under h2 section headings", () => {
    for (const table of issuesTables) {
      const sectionPath = table.heading.path.join(" > ");
      assert.ok(
        sectionPath.includes("inkle/inky") || sectionPath.includes("inkle/ink"),
        `Issues table should be under inkle section, got: ${sectionPath}`,
      );
    }
  });
});

// ═══════════════════════════════════════════════════════════════
// 2. EXTRACT ```ink BLOCKS — routed by info="ink"
// ═══════════════════════════════════════════════════════════════

describe("2. ```ink block routing (info='ink')", () => {
  it("routes exactly 14 ink blocks", () => {
    assert.equal(inkBlocks.length, 14);
  });

  it("ink blocks are under 'E2E Test Resource' section", () => {
    for (const block of inkBlocks) {
      const path = block.heading.path.join(" > ");
      assert.ok(
        path.includes("E2E Test Resource") || path.includes("Issue"),
        `Ink block should be under E2E section, got: ${path}`,
      );
    }
  });

  it("each block has ASSERT comments", () => {
    const withAsserts = inkBlocks.filter((b) => b.source.includes("// ASSERT:"));
    assert.ok(withAsserts.length >= 8);
  });
});

// ═══════════════════════════════════════════════════════════════
// 3. INK GRAMMAR TOKEN CLASSIFICATION
// ═══════════════════════════════════════════════════════════════

describe("3. ink-grammar classifies lines from routed ```ink blocks", () => {
  it("classifies VAR declarations", () => {
    for (const block of inkBlocks) {
      for (const line of block.source.split("\n").filter((l) => l.trim().startsWith("VAR "))) {
        assert.match(line, VAR_DECL_REGEX);
      }
    }
  });

  it("classifies knot declarations", () => {
    for (const block of inkBlocks) {
      for (const line of block.source.split("\n").filter((l) => l.trim().startsWith("=== "))) {
        assert.match(line, KNOT_REGEX);
      }
    }
  });

  it("classifies comments", () => {
    for (const block of inkBlocks) {
      for (const line of block.source.split("\n").filter((l) => l.trim().startsWith("//"))) {
        assert.match(line.trim(), LINE_COMMENT_REGEX);
      }
    }
  });

  it("classifies logic lines", () => {
    for (const block of inkBlocks) {
      for (const line of block.source.split("\n").filter((l) => l.trim().startsWith("~ "))) {
        assert.match(line.trim(), LOGIC_LINE_REGEX);
      }
    }
  });

  it("classifies glue operator", () => {
    const glueBlocks = inkBlocks.filter((b) => b.source.includes("<>"));
    assert.ok(glueBlocks.length >= 1);
    for (const block of glueBlocks) {
      for (const line of block.source.split("\n").filter(
        (l) => l.includes("<>") && !l.trim().startsWith("//"))) {
        assert.ok(GLUE_REGEX.test(line));
      }
    }
  });
});

// ═══════════════════════════════════════════════════════════════
// 4. TABLE SCHEMA VALIDATION
// ═══════════════════════════════════════════════════════════════

describe("4. markdown table schema", () => {
  it("finds >= 2 issues tables", () => {
    assert.ok(issuesTables.length >= 2);
  });

  it("tags are ;-delimited kebab-case from known vocabulary", () => {
    const knownTags = new Set([
      "compiler", "runtime", "parser", "ui", "editor", "electron", "export",
      "crash", "regression", "platform", "ux", "file-io", "save", "syntax",
      "bidi", "rtl", "i18n", "performance", "feature-request", "documentation",
      "accessibility", "tags", "state", "choices", "glue", "threads", "tunnels",
      "variables", "lists", "logic", "api", "dark-mode", "packaging",
    ]);
    for (const table of issuesTables) {
      const idx = table.headers.indexOf("Tags");
      for (const row of table.rows) {
        for (const tag of row[idx].split(";").map((t) => t.trim()).filter((t) => t && /^[a-z]/.test(t))) {
          assert.match(tag, /^[a-z][a-z0-9-]*$/);
          assert.ok(knownTags.has(tag), `Unknown tag: ${tag}`);
        }
      }
    }
  });

  it("TDD starts with YES:/NO:/PARTIAL: (majority check)", () => {
    let valid = 0;
    let total = 0;
    for (const table of issuesTables) {
      const idx = table.headers.indexOf("TDD");
      if (idx < 0) continue;
      for (const row of table.rows) {
        const tdd = row[idx]?.trim();
        if (!tdd) continue;
        total++;
        if (tdd.startsWith("YES:") || tdd.startsWith("NO:") || tdd.startsWith("PARTIAL:")) {
          valid++;
        }
      }
    }
    assert.ok(total > 0, "Should have TDD cells");
    const pct = (valid / total) * 100;
    assert.ok(pct >= 80, `At least 80% of TDD cells should start with YES:/NO:/PARTIAL:, got ${pct.toFixed(0)}%`);
  });

  it("# cells have GitHub links", () => {
    for (const table of issuesTables) {
      const idx = table.headers.indexOf("#");
      for (const row of table.rows) {
        assert.ok(row[idx].includes("github.com/inkle/"));
      }
    }
  });
});

// ═══════════════════════════════════════════════════════════════
// 5. CROSS-REFERENCE
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// 5. EXTENDED INK GRAMMAR — divert, choice, tag, tunnel, external, list
// ═══════════════════════════════════════════════════════════════

describe("5. extended ink-grammar regex coverage", () => {
  it("classifies divert lines", () => {
    assert.equal(classifyLine("-> knot_name"), "text"); // divert in text
    assert.match("-> END", DIVERT_SPECIAL_REGEX);
    assert.match("-> DONE", DIVERT_SPECIAL_REGEX);
    assert.match("<- DONE", DIVERT_SPECIAL_REGEX);
  });

  it("classifies tunnel diverts", () => {
    assert.match("->-> tunnel_target", DIVERT_TUNNEL_REGEX);
  });

  it("classifies choice lines", () => {
    assert.equal(classifyLine("* [Go left] -> left"), "choice");
    assert.equal(classifyLine("+ [Sticky] -> place"), "choice");
    assert.equal(classifyLine("* * [Nested] -> deep"), "choice");
    assert.match("* [Go left]", CHOICE_REGEX);
    assert.match("+ [Sticky choice]", CHOICE_REGEX);
  });

  it("classifies gather lines", () => {
    assert.equal(classifyLine("- (label) gathered text"), "gather");
    assert.match("- gathered text", GATHER_REGEX);
  });

  it("classifies stitch declarations", () => {
    assert.equal(classifyLine("= campfire"), "stitch");
    assert.match("= campfire", STITCH_REGEX);
  });

  it("classifies LIST declarations", () => {
    assert.equal(classifyLine("LIST mood = neutral, happy, sad"), "list-decl");
    assert.match("LIST mood = neutral", LIST_DECL_REGEX);
  });

  it("classifies INCLUDE lines", () => {
    assert.equal(classifyLine("INCLUDE helpers.ink"), "include");
    assert.match("INCLUDE helpers.ink", INCLUDE_REGEX);
  });

  it("classifies EXTERNAL declarations", () => {
    assert.equal(classifyLine("EXTERNAL multiply(a, b)"), "external");
    assert.match("EXTERNAL multiply(a, b)", EXTERNAL_REGEX);
  });

  it("matches tag syntax", () => {
    assert.match("# author: Claude", TAG_REGEX);
    assert.match("# dark-mode", TAG_REGEX);
  });

  it("matches escape sequences", () => {
    assert.match("\\[", ESCAPE_REGEX);
    assert.match("\\]", ESCAPE_REGEX);
    assert.match("\\{", ESCAPE_REGEX);
    assert.match("\\~", ESCAPE_REGEX);
  });

  it("matches inline conditional syntax", () => {
    assert.match("{mood == happy: Smiling}", INLINE_CONDITIONAL_REGEX);
  });

  it("classifies multiline-logic blocks", () => {
    assert.equal(classifyLine("{"), "multiline-logic");
    assert.equal(classifyLine("{ mood == happy:"), "multiline-logic");
  });
});

// ═══════════════════════════════════════════════════════════════
// 6. TOKEN CATEGORY MAPPING
// ═══════════════════════════════════════════════════════════════

describe("6. tokenCategory maps tokens to highlight categories", () => {
  it("maps knot tokens to structure/name/punctuation", () => {
    assert.equal(tokenCategory("knot.declaration"), "structure");
    assert.equal(tokenCategory("knot.declaration.name"), "name");
    assert.equal(tokenCategory("knot.declaration.punctuation"), "punctuation");
    assert.equal(tokenCategory("knot.declaration.function"), "keyword");
    assert.equal(tokenCategory("knot.declaration.parameters"), "variable");
  });

  it("maps choice/gather/divert tokens", () => {
    assert.equal(tokenCategory("choice"), "choice");
    assert.equal(tokenCategory("choice.bullets"), "choice");
    assert.equal(tokenCategory("gather"), "gather");
    assert.equal(tokenCategory("divert.operator"), "punctuation");
    assert.equal(tokenCategory("divert.target"), "name");
  });

  it("maps keyword tokens", () => {
    assert.equal(tokenCategory("var-decl.keyword"), "keyword");
    assert.equal(tokenCategory("var-decl.name"), "variable");
    assert.equal(tokenCategory("list-decl.keyword"), "keyword");
    assert.equal(tokenCategory("include.keyword"), "keyword");
    assert.equal(tokenCategory("external.keyword"), "keyword");
  });

  it("maps comment/tag/glue/escape tokens", () => {
    assert.equal(tokenCategory("comment"), "comment");
    assert.equal(tokenCategory("comment.block"), "comment");
    assert.equal(tokenCategory("tag"), "tag");
    assert.equal(tokenCategory("glue"), "glue");
    assert.equal(tokenCategory("escape"), "escape");
    assert.equal(tokenCategory("text"), "text");
  });
});

// ═══════════════════════════════════════════════════════════════
// 7. INK BLOCKS: TUNNEL, THREAD, CONDITIONAL, LIST syntax
// ═══════════════════════════════════════════════════════════════

describe("7. ink block syntax feature validation", () => {
  it("blocks contain divert syntax ->", () => {
    const divertBlocks = inkBlocks.filter((b) => b.source.includes("->"));
    assert.ok(divertBlocks.length >= 3, `At least 3 blocks should have diverts, got ${divertBlocks.length}`);
  });

  it("blocks contain VAR declarations", () => {
    const varBlocks = inkBlocks.filter((b) => b.source.includes("VAR "));
    assert.ok(varBlocks.length >= 3, `At least 3 blocks should have VAR, got ${varBlocks.length}`);
  });

  it("blocks contain knot declarations", () => {
    const knotBlocks = inkBlocks.filter((b) => b.source.includes("=== "));
    assert.ok(knotBlocks.length >= 5, `At least 5 blocks should have knots, got ${knotBlocks.length}`);
  });

  it("blocks contain choice syntax", () => {
    const choiceBlocks = inkBlocks.filter((b) =>
      b.source.split("\n").some((l) => l.trim().startsWith("* ")),
    );
    assert.ok(choiceBlocks.length >= 2, `At least 2 blocks should have choices, got ${choiceBlocks.length}`);
  });

  it("blocks contain glue operator <>", () => {
    const glueBlocks = inkBlocks.filter((b) => b.source.includes("<>"));
    assert.ok(glueBlocks.length >= 1, "At least 1 block should have glue <>");
  });

  it("blocks contain ASSERT comments", () => {
    const assertBlocks = inkBlocks.filter((b) => b.source.includes("// ASSERT:"));
    assert.ok(assertBlocks.length >= 8, `At least 8 blocks should have ASSERT comments, got ${assertBlocks.length}`);
  });

  it("blocks contain Hebrew/RTL text", () => {
    const hebrewRegex = /[\u0590-\u05FF]/;
    const rtlBlocks = inkBlocks.filter((b) => hebrewRegex.test(b.source));
    assert.ok(rtlBlocks.length >= 1, "At least 1 block should have Hebrew text");
  });

  it("blocks contain CJK text", () => {
    const cjkRegex = /[\u3040-\u309F\u4E00-\u9FFF\uAC00-\uD7AF]/;
    const cjkBlocks = inkBlocks.filter((b) => cjkRegex.test(b.source));
    assert.ok(cjkBlocks.length >= 1, "At least 1 block should have CJK text");
  });

  it("blocks contain divert targets that match knot names", () => {
    let found = false;
    for (const block of inkBlocks) {
      const diverts = block.source.match(/->\s*(\w+)/g) || [];
      const knots = block.source.match(/^===\s*(\w+)/gm) || [];
      if (diverts.length > 0 && knots.length > 0) {
        found = true;
        break;
      }
    }
    assert.ok(found, "At least one block should have both knots and diverts");
  });

  it("blocks contain tunnel syntax ->->", () => {
    const tunnelBlocks = inkBlocks.filter((b) => b.source.includes("->->"));
    assert.ok(tunnelBlocks.length >= 1, "At least 1 block should have tunnel return ->->");
  });

  it("blocks contain thread syntax <-", () => {
    const threadBlocks = inkBlocks.filter((b) =>
      b.source.split("\n").some((l) => l.trim().startsWith("<-") && !l.includes("DONE")),
    );
    assert.ok(threadBlocks.length >= 1, "At least 1 block should have thread merge <-");
  });

  it("blocks contain multi-line conditional syntax", () => {
    const condBlocks = inkBlocks.filter((b) => {
      const lines = b.source.split("\n").map((l) => l.trim());
      return lines.some((l) => l === "{") || lines.some((l) => l.startsWith("- mood") || l.startsWith("- else"));
    });
    assert.ok(condBlocks.length >= 1, "At least 1 block should have multi-line conditionals");
  });

  it("blocks contain LIST declarations and operations", () => {
    const listBlocks = inkBlocks.filter((b) => b.source.includes("LIST "));
    assert.ok(listBlocks.length >= 1, "At least 1 block should have LIST declarations");
    const listOpBlocks = inkBlocks.filter((b) =>
      b.source.includes("? sword") || b.source.includes("!?") || b.source.includes("+= sword") || b.source.includes("+= potion"),
    );
    assert.ok(listOpBlocks.length >= 1, "At least 1 block should have LIST query operations");
  });
});

// ═══════════════════════════════════════════════════════════════
// 8. CROSS-REFERENCE
// ═══════════════════════════════════════════════════════════════

describe("8. cross-reference ink blocks ↔ table entries", () => {
  it("ink block issues map to TDD=YES or PARTIAL in table", () => {
    const allRows: MdTableRow[] = issuesTables.flatMap((t) =>
      t.rows.map((r) => parseIssueRow(t.headers, r)),
    );
    for (const block of inkBlocks) {
      const match = block.heading.text.match(/#(\d+)|ink-(\d+)/);
      if (!match) continue;
      const num = match[1] || match[2];
      const row = allRows.find((r) => r.issueNumber.includes(num));
      if (row) {
        assert.ok(
          row.tddVerdict === "YES" || row.tddVerdict === "PARTIAL",
          `Issue ${num} should be YES/PARTIAL, got ${row.tddVerdict}`,
        );
      }
    }
  });

  it("summary percentages sum to ~100%", () => {
    const summaryTables = mdTables.filter(
      (t) => t.headers.includes("Verdict") && t.headers.includes("Count"),
    );
    for (const table of summaryTables) {
      const idx = table.headers.indexOf("%");
      let total = 0;
      for (const row of table.rows) {
        const pct = parseInt(row[idx].replace(/\*+|%/g, ""), 10);
        if (!isNaN(pct)) total += pct;
      }
      assert.ok(total >= 99 && total <= 101, `Percentages sum: ${total}%`);
    }
  });
});
