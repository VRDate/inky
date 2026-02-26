/**
 * Runtime-agnostic ink language grammar — token types and regex patterns.
 *
 * Ported from: app/renderer/ace-ink-mode/ace-ink.js
 *
 * This module defines the ink tokenization rules in a format that can be
 * consumed by CodeMirror 6 (StreamLanguage), Remirror node views, or any
 * other editor that needs ink syntax highlighting.
 *
 * Each rule has:
 *   - name: semantic token name (used for highlighting)
 *   - regex: pattern to match
 *   - group: category for grouping rules
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
  | "text";        // plain text

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

/** Tag: # text */
export const TAG_REGEX = /#[^\[\]$]+/;

/** Glue operator: <> */
export const GLUE_REGEX = /<>/;

/** Escape sequences: \[, \], \\, \~, etc. */
export const ESCAPE_REGEX = /\\[[\]()\\~{}/#*+\-]/;

// ─── Line Classifier ─────────────────────────────────────────────────

export type InkLineType =
  | "knot"
  | "stitch"
  | "choice"
  | "gather"
  | "logic"
  | "var-decl"
  | "list-decl"
  | "include"
  | "external"
  | "todo"
  | "comment"
  | "multiline-logic"
  | "text";

/** Classify an ink line by its leading content */
export function classifyLine(line: string): InkLineType {
  const trimmed = line.trimStart();
  if (trimmed.startsWith("//") || trimmed.startsWith("/*")) return "comment";
  if (TODO_REGEX.test(line)) return "todo";
  if (KNOT_REGEX.test(line)) return "knot";
  if (STITCH_REGEX.test(line)) return "stitch";
  if (VAR_DECL_REGEX.test(line)) return "var-decl";
  if (LIST_DECL_REGEX.test(line)) return "list-decl";
  if (INCLUDE_REGEX.test(trimmed)) return "include";
  if (EXTERNAL_REGEX.test(trimmed)) return "external";
  if (CHOICE_REGEX.test(line)) return "choice";
  if (GATHER_REGEX.test(line)) return "gather";
  if (LOGIC_LINE_REGEX.test(line)) return "logic";
  if (MULTILINE_LOGIC_REGEX.test(line)) return "multiline-logic";
  return "text";
}

// ─── Folding ─────────────────────────────────────────────────────────

/** Detect if a line starts a foldable region (knot or stitch) */
export const FOLD_START_REGEX = /^(\s*)(=)(?<knot>={1,})?(\s*)(function\s+)?(\s*)(\w+)(\s*)(\([\w,\s\->]*\))?(\s*)(={1,})?/;

/** Check if a line is a knot boundary (for fold end detection) */
export const KNOT_BOUNDARY_REGEX = /^(\s*)(={2,})(\s*)(function\s+)?(\s*)(\w+)/;

/** Check if a line is a stitch or knot boundary */
export const STITCH_BOUNDARY_REGEX = /^(\s*)(={1,})(\s*)(function\s+)?(\s*)(\w+)/;
