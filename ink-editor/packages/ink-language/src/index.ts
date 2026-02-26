/**
 * @inky/ink-language — Runtime-agnostic ink grammar and token definitions.
 *
 * Shared by:
 *   - @inky/codemirror-ink (CodeMirror 6 extension)
 *   - @inky/remirror-ink (Remirror/ProseMirror extension)
 *   - Ace editor (existing mode consumes same patterns)
 *
 * Ported from: app/renderer/ace-ink-mode/ace-ink.js
 */

export {
  // Token types and categories
  type InkTokenType,
  type InkTokenCategory,
  tokenCategory,

  // Line classifier
  type InkLineType,
  classifyLine,

  // Line-level regex patterns
  KNOT_REGEX,
  STITCH_REGEX,
  CHOICE_REGEX,
  GATHER_REGEX,
  LOGIC_LINE_REGEX,
  VAR_DECL_REGEX,
  LIST_DECL_REGEX,
  INCLUDE_REGEX,
  EXTERNAL_REGEX,
  TODO_REGEX,
  LINE_COMMENT_REGEX,
  BLOCK_COMMENT_START_REGEX,
  DOC_COMMENT_START_REGEX,
  BLOCK_COMMENT_END_REGEX,

  // Inline patterns
  DIVERT_SPECIAL_REGEX,
  DIVERT_TUNNEL_REGEX,
  DIVERT_PARAMS_REGEX,
  DIVERT_REGEX,
  DIVERT_BARE_REGEX,
  INLINE_CONDITIONAL_REGEX,
  INLINE_SEQUENCE_REGEX,
  INLINE_LOGIC_REGEX,
  MULTILINE_LOGIC_REGEX,
  TAG_REGEX,
  GLUE_REGEX,
  ESCAPE_REGEX,

  // Folding
  FOLD_START_REGEX,
  KNOT_BOUNDARY_REGEX,
  STITCH_BOUNDARY_REGEX,
} from "./ink-grammar";

export {
  INK_KEYWORDS,
  type InkKeyword,
  INK_DECLARATION_KEYWORDS,
  INK_SPECIAL_TARGETS,
  INK_BUILTIN_FUNCTIONS,
} from "./ink-keywords";
