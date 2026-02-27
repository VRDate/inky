/**
 * Unified ink+markdown grammar — merged regex-only line classifier.
 *
 * Both ink and markdown are text-based formats parseable by regex only.
 * This module merges both grammars into a single line-level classifier
 * where every line is unambiguous by its leading regex pattern.
 * No AST parser needed, no fenced code blocks needed.
 *
 * Ported from: app/renderer/ace-ink-mode/ace-ink.js (ink patterns)
 * Extended with: markdown patterns (headings, tables, inline markup)
 *
 * Consumed by: CodeMirror 6, Remirror node views, ACE, and the KMP
 * Kotlin mirror (InkMdGrammar.kt in commonMain — pure regex, zero deps).
 *
 * Heading hierarchy (file name = title):
 *   - # (H1) = section name
 *   - ## (H2) = chapter
 *   - ### (H3) = knot (=== name ===)
 *   - #### (H4) = stitch (= name)
 *   - ##### (H5) = thread (<- name)
 *   - ###### (H6) = label / metadata
 *
 * Key disambiguation:
 *   - # at line start = section name (H1)
 *   - # after text = ink tag (inline: "text # tag1 # tag2")
 *   - | ... | = markdown table row (data tables)
 *   - All ink patterns (===, *, +, ~, VAR, ->) take precedence over markdown
 */

// ─── Token Types ─────────────────────────────────────────────────────

/** All ink token type names */
export type InkTokenType =
  | "knot.declaration"
  | "knot.declaration.name"
  | "knot.declaration.punctuation"
  | "knot.declaration.function"
  | "knot.declaration.parameters"
  | "stitch.declaration"
  | "stitch.declaration.name"
  | "stitch.declaration.punctuation"
  | "stitch.declaration.parameters"
  | "choice"
  | "choice.bullets"
  | "choice.label"
  | "choice.label.name"
  | "choice.weaveBracket"
  | "choice.weaveInsideBrackets"
  | "gather"
  | "gather.bullets"
  | "gather.label"
  | "gather.label.name"
  | "divert.operator"
  | "divert.target"
  | "divert.to-special"
  | "divert.to-tunnel"
  | "divert.parameter"
  | "divert.parameter.operator"
  | "var-decl"
  | "var-decl.keyword"
  | "var-decl.name"
  | "list-decl"
  | "list-decl.keyword"
  | "list-decl.name"
  | "include"
  | "include.keyword"
  | "include.filepath"
  | "external"
  | "external.keyword"
  | "external.declaration.name"
  | "external.declaration.parameters"
  | "logic.punctuation"
  | "logic.tilda"
  | "logic.inline"
  | "logic.inline.conditional.condition"
  | "logic.inline.conditional.punctuation"
  | "logic.inline.innerContent"
  | "logic.sequence"
  | "logic.sequence.operator"
  | "logic.sequence.punctuation"
  | "logic.sequence.innerContent"
  | "logic.conditional.multiline.condition"
  | "logic.conditional.multiline.condition.punctuation"
  | "logic.multiline.branch"
  | "logic.multiline.branch.operator"
  | "logic.multiline.branch.condition"
  | "logic.multiline.innerContent"
  | "conditional.multiline.else"
  | "comment"
  | "comment.block"
  | "comment.doc"
  | "tag"
  | "todo"
  | "todo.TODO"
  | "glue"
  | "escape"
  | "md.heading"
  | "md.heading.marker"
  | "md.heading.text"
  | "md.table.row"
  | "md.table.separator"
  | "md.table.cell"
  | "md.bold"
  | "md.italic"
  | "md.link"
  | "md.link.text"
  | "md.link.url"
  | "md.image"
  | "md.code"
  | "md.horizontal-rule"
  | "md.definition-value"
  | "md.definition-term"
  | "text";

// ─── Token Categories (for highlight mapping) ───────────────────────

/** Map token types to semantic categories for theme-agnostic highlighting */
export type InkTokenCategory =
  | "structure"    // knot/stitch declarations
  | "name"         // knot/stitch/label names
  | "punctuation"  // ===, =, ->, {, }, etc.
  | "choice"       // choice bullets and content
  | "gather"       // gather bullets
  | "divert"       // divert arrows and targets
  | "keyword"      // VAR, CONST, LIST, INCLUDE, EXTERNAL
  | "variable"     // variable names in declarations
  | "logic"        // inline logic, conditionals, sequences
  | "comment"      // all comment types
  | "tag"          // # tags
  | "todo"         // TODO markers
  | "glue"         // <> glue operator
  | "escape"       // \ escape sequences
  | "string"       // string literals
  | "md-heading"   // ## through ###### headings
  | "md-table"     // | cell | cell | table rows
  | "md-markup"    // bold, italic, link, image, code, hr
  | "text";        // plain text (shared ink + md)

/** Map each token type to its category */
export function tokenCategory(token: InkTokenType): InkTokenCategory {
  if (token.startsWith("knot.") || token.startsWith("stitch.")) {
    if (token.endsWith(".name")) return "name";
    if (token.endsWith(".punctuation")) return "punctuation";
    if (token.endsWith(".function")) return "keyword";
    if (token.endsWith(".parameters")) return "variable";
    return "structure";
  }
  if (token.startsWith("choice")) return "choice";
  if (token.startsWith("gather")) return "gather";
  if (token.startsWith("divert")) {
    if (token.includes("target") || token.includes("special")) return "name";
    if (token.includes("operator")) return "punctuation";
    return "divert";
  }
  if (token.startsWith("var-decl") || token.startsWith("list-decl")) {
    if (token.endsWith(".keyword")) return "keyword";
    if (token.endsWith(".name")) return "variable";
    return "keyword";
  }
  if (token.startsWith("include") || token.startsWith("external")) {
    if (token.endsWith(".keyword")) return "keyword";
    if (token.endsWith(".name")) return "name";
    return "keyword";
  }
  if (token.startsWith("logic")) {
    if (token.includes("punctuation")) return "punctuation";
    return "logic";
  }
  if (token.startsWith("comment")) return "comment";
  if (token.startsWith("tag")) return "tag";
  if (token.startsWith("todo")) return "todo";
  if (token === "glue") return "glue";
  if (token === "escape") return "escape";
  // Markdown tokens (merged ink+md grammar)
  if (token.startsWith("md.heading")) return "md-heading";
  if (token.startsWith("md.table")) return "md-table";
  if (token.startsWith("md.")) return "md-markup";
  return "text";
}

// ─── Line-Level Patterns ─────────────────────────────────────────────
// These regex patterns identify the type of an ink line at the start.
// Ported directly from Ace $rules.

/** Knot declaration: === knot_name === or === function knot_name(params) === */
export const KNOT_REGEX = /^(\s*)(={2,})(\s*)(function\s+)?(\s*)(\w+)(\s*)(\([\w,\s\->]*\))?(\s*)(={1,})?/;

/** Stitch declaration: = stitch_name or = stitch_name(params) */
export const STITCH_REGEX = /^(\s*)(=)(\s*)(\w+)(\s*)(\([\w,\s\->]*\))?/;

/** Choice line: * or + (possibly nested) with optional label */
export const CHOICE_REGEX = /^(\s*)((?:[*+]\s?)+)(\s*)(?:(\(\s*)(\w+)(\s*\)))?/;

/** Gather line: - (not ->) */
export const GATHER_REGEX = /^(\s*)((?:-(?!>)\s*)+)/;

/** Logic line: ~ expression */
export const LOGIC_LINE_REGEX = /^\s*~\s*/;

/** VAR or CONST declaration */
export const VAR_DECL_REGEX = /^(\s*)(VAR|CONST)\b/;

/** LIST declaration */
export const LIST_DECL_REGEX = /^(\s*)(LIST)\b/;

/** INCLUDE statement */
export const INCLUDE_REGEX = /(\s*)(INCLUDE\b)/;

/** EXTERNAL function declaration */
export const EXTERNAL_REGEX = /(\s*)(EXTERNAL\b)/;

/** TODO marker */
export const TODO_REGEX = /^(\s*)(TODO\b)(.*)/;

/** Single-line comment */
export const LINE_COMMENT_REGEX = /(\/\/)(.*$)/;

/** Block comment start */
export const BLOCK_COMMENT_START_REGEX = /\/\*/;

/** Doc comment start */
export const DOC_COMMENT_START_REGEX = /\/\*\*/;

/** Block comment end */
export const BLOCK_COMMENT_END_REGEX = /\*\//;

// ─── Inline Patterns ─────────────────────────────────────────────────

/** Divert to DONE or END: -> DONE, -> END, <- DONE */
export const DIVERT_SPECIAL_REGEX = /(->|<-)(\s*)(DONE|END)(\s*)/;

/** Tunnel onwards: ->-> target */
export const DIVERT_TUNNEL_REGEX = /(->->)(\s*)(\w[\w.\s]*)/;

/** Divert with parameters: -> knot(params) */
export const DIVERT_PARAMS_REGEX = /(->|<-)(\s*)(\w[\w.\s]*?)(\s*)(\()/;

/** Vanilla divert: -> target */
export const DIVERT_REGEX = /(->|<-)(\s*)(\w[\w.\s]*?)(\s*)(?![\w.])/;

/** Bare divert operator */
export const DIVERT_BARE_REGEX = /->/;

/** Inline conditional: {condition: ...} */
export const INLINE_CONDITIONAL_REGEX = /(\{)([^:|}]+:)/;

/** Inline sequence: {~a|b|c} or {&a|b|c} */
export const INLINE_SEQUENCE_REGEX = /(\{)(\s*)((?:~|&|!|\$)?)(?=[^|}]*\|)/;

/** Inline logic: { ... } */
export const INLINE_LOGIC_REGEX = /\{/;

/** Multiline logic block start: { or {condition: */
export const MULTILINE_LOGIC_REGEX = /^(\s*)(\{)(?:([^}:]+)(:))?(?=[^}]*$)/;

/** Inline tag: text # tag1 # tag2 (only after content, not at line start) */
export const TAG_REGEX = /(?<=\S\s*)#[^\[\]$#]+/;

/** Glue operator: <> */
export const GLUE_REGEX = /<>/;

/** Escape sequences: \[, \], \\, \~, etc. */
export const ESCAPE_REGEX = /\\[[\]()\\~{}/#*+\-]/;

// ─── Markdown Patterns (merged ink+md grammar) ──────────────────────
// Both ink and markdown are line-oriented text formats parseable by regex.
// No AST parser needed — every line is self-classifying by its leading pattern.
// This eliminates the need for ```ink fenced code blocks.

/** H1 = section name (file name = title, # = section name) */
export const MD_HEADING_H1_REGEX = /^(#)\s+(.+)$/;

/** H2 = chapter / file-level grouping */
export const MD_HEADING_H2_REGEX = /^(#{2})\s+(.+)$/;

/** Markdown heading H3 = ink knot equivalent: ### knot_name */
export const MD_HEADING_H3_REGEX = /^(#{3})\s+(\w[\w_]*)(\s*)(\([\w,\s\->]*\))?$/;

/** Markdown heading H4 = ink stitch equivalent: #### stitch_name */
export const MD_HEADING_H4_REGEX = /^(#{4})\s+(\w[\w_]*)(\s*)(\([\w,\s\->]*\))?$/;

/** Markdown heading H5 = ink thread: ##### thread_name (concurrent) */
export const MD_HEADING_H5_REGEX = /^(#{5})\s+(\w[\w_]*)$/;

/** Markdown heading H6 (labels / metadata / footnotes). */
export const MD_HEADING_H6_REGEX = /^(#{6})\s+(.+)$/;

/** Markdown table row: | cell | cell | */
export const MD_TABLE_ROW_REGEX = /^\|.*\|$/;

/** Markdown table separator: |---|---|---| */
export const MD_TABLE_SEPARATOR_REGEX = /^\|[\s:]*-{3,}[\s:]*\|/;

/** Ordered list / numbered choice: 1. item (= <ol><li>) */
export const MD_ORDERED_LIST_REGEX = /^(\s*)(\d+\.)\s+/;

/** Definition list value: ": value" (= ink VAR/LIST declaration) */
export const MD_DEFINITION_VALUE_REGEX = /^(\s*):\s+(.+)$/;

/** Markdown horizontal rule: --- or *** or ___ (3+) */
export const MD_HORIZONTAL_RULE_REGEX = /^(\*{3,}|-{3,}|_{3,})$/;

/** Markdown link: [text](url) */
export const MD_LINK_REGEX = /\[([^\]]+)\]\(([^)]+)\)/;

/** Markdown image: ![alt](url) */
export const MD_IMAGE_REGEX = /!\[([^\]]*)\]\(([^)]+)\)/;

/** Markdown bold: **text** or __text__ */
export const MD_BOLD_REGEX = /(\*\*|__)(.+?)\1/;

/** Markdown italic: *text* or _text_ */
export const MD_ITALIC_REGEX = /([*_])(.+?)\1/;

/** Markdown inline code: `code` */
export const MD_INLINE_CODE_REGEX = /`([^`]+)`/;

// ─── Unified Line Classifier (merged ink+md) ────────────────────────

export type InkLineType =
  | "knot"              // === name === or ### name (H3 = knot)
  | "stitch"            // = name or #### name (H4 = stitch)
  | "choice"            // * or + (ul choice)
  | "choice-ordered"    // 1. 2. 3. (ol numbered choice)
  | "gather"            // - (not ->)
  | "thread"            // <- thread or ##### thread (H5 = concurrent thread)
  | "logic"             // ~ expression
  | "var-decl"          // VAR or CONST
  | "list-decl"         // LIST
  | "include"           // INCLUDE
  | "external"          // EXTERNAL
  | "todo"              // TODO
  | "comment"           // // or /* */
  | "multiline-logic"   // { ... } block
  | "section"           // # (H1 = section name, file name = title)
  | "md-heading"        // ## (H2 = chapter)
  | "md-table-row"      // | cell | cell |
  | "md-table-separator" // |---|---|
  | "md-horizontal-rule" // --- or *** or ___
  | "md-definition-value" // : value (= ink VAR, part of <dl>)
  | "text";             // prose (shared ink + md)

/**
 * Classify a line as ink or markdown by its leading regex pattern.
 *
 * Ink and markdown are both text formats parseable by regex only.
 * Every line is unambiguous — no fenced code blocks needed.
 *
 * Heading hierarchy (file name = title):
 *   - # (H1) = section name
 *   - ## (H2) = chapter
 *   - ### (H3) = knot (=== name ===)
 *   - #### (H4) = stitch (= name)
 *   - ##### (H5) = thread (<- name)
 *   - ###### (H6) = label / metadata
 *
 * Ink tags are inline only: "text # tag1 # tag2" (not at line start)
 */
export function classifyLine(line: string): InkLineType {
  const trimmed = line.trimStart();
  // Ink patterns first (more specific)
  if (trimmed.startsWith("//") || trimmed.startsWith("/*")) return "comment";
  if (TODO_REGEX.test(line)) return "todo";
  if (KNOT_REGEX.test(line)) return "knot";
  if (STITCH_REGEX.test(line)) return "stitch";
  if (VAR_DECL_REGEX.test(line)) return "var-decl";
  if (LIST_DECL_REGEX.test(line)) return "list-decl";
  if (INCLUDE_REGEX.test(trimmed)) return "include";
  if (EXTERNAL_REGEX.test(trimmed)) return "external";
  if (CHOICE_REGEX.test(line)) return "choice";
  if (MD_ORDERED_LIST_REGEX.test(line)) return "choice-ordered";
  if (GATHER_REGEX.test(line)) return "gather";
  if (LOGIC_LINE_REGEX.test(line)) return "logic";
  if (MULTILINE_LOGIC_REGEX.test(line)) return "multiline-logic";
  // Heading hierarchy: H1=section, H2=chapter, H3=knot, H4=stitch, H5=thread, H6=label
  if (MD_HEADING_H3_REGEX.test(trimmed)) return "knot";
  if (MD_HEADING_H4_REGEX.test(trimmed)) return "stitch";
  if (MD_HEADING_H5_REGEX.test(trimmed)) return "thread";
  if (MD_HEADING_H1_REGEX.test(trimmed)) return "section";
  if (MD_HEADING_H2_REGEX.test(trimmed)) return "md-heading";
  if (MD_HEADING_H6_REGEX.test(trimmed)) return "md-heading";
  // Markdown table, definition list, and layout
  if (MD_TABLE_SEPARATOR_REGEX.test(trimmed)) return "md-table-separator";
  if (MD_TABLE_ROW_REGEX.test(trimmed)) return "md-table-row";
  if (MD_DEFINITION_VALUE_REGEX.test(trimmed)) return "md-definition-value";
  if (MD_HORIZONTAL_RULE_REGEX.test(trimmed)) return "md-horizontal-rule";
  return "text";
}

// ─── Folding ─────────────────────────────────────────────────────────

/** Detect if a line starts a foldable region (knot or stitch) */
export const FOLD_START_REGEX = /^(\s*)(=)(?<knot>={1,})?(\s*)(function\s+)?(\s*)(\w+)(\s*)(\([\w,\s\->]*\))?(\s*)(={1,})?/;

/** Check if a line is a knot boundary (for fold end detection) */
export const KNOT_BOUNDARY_REGEX = /^(\s*)(={2,})(\s*)(function\s+)?(\s*)(\w+)/;

/** Check if a line is a stitch or knot boundary */
export const STITCH_BOUNDARY_REGEX = /^(\s*)(={1,})(\s*)(function\s+)?(\s*)(\w+)/;
