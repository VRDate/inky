/**
 * CodeMirror 6 StreamLanguage tokenizer for ink.
 *
 * Uses StreamLanguage.define() with a line-by-line tokenizer that maps
 * ink grammar patterns to CodeMirror token tags.
 *
 * Ported from: app/renderer/ace-ink-mode/ace-ink.js
 * Grammar from: @inky/ink-language
 */

import { StreamLanguage, type StreamParser } from "@codemirror/language";
import {
  KNOT_REGEX,
  STITCH_REGEX,
  CHOICE_REGEX,
  GATHER_REGEX,
  LOGIC_LINE_REGEX,
  VAR_DECL_REGEX,
  LIST_DECL_REGEX,
  TODO_REGEX,
  ESCAPE_REGEX,
  GLUE_REGEX,
  TAG_REGEX,
  DIVERT_SPECIAL_REGEX,
  DIVERT_TUNNEL_REGEX,
  DIVERT_REGEX,
  DIVERT_BARE_REGEX,
  INLINE_CONDITIONAL_REGEX,
  INLINE_SEQUENCE_REGEX,
} from "@inky/ink-language";

interface InkState {
  /** Current parsing context */
  context: "start" | "comment-block" | "comment-doc" | "logic-multiline" | "choice-line" | "gather-line" | "logic-line";
  /** Nesting depth for braces in logic */
  braceDepth: number;
}

function makeState(): InkState {
  return { context: "start", braceDepth: 0 };
}

/** ink StreamParser for CodeMirror 6 */
export const inkStreamParser: StreamParser<InkState> = {
  name: "ink",

  startState: makeState,

  copyState(state: InkState): InkState {
    return { ...state };
  },

  token(stream, state): string | null {
    // ── Block comment ──
    if (state.context === "comment-block" || state.context === "comment-doc") {
      if (stream.match(/\*\//)) {
        const tok = state.context === "comment-doc" ? "comment" : "comment";
        state.context = "start";
        return tok;
      }
      stream.next();
      return "comment";
    }

    // ── Start of line detection ──
    if (stream.sol()) {
      state.context = "start";
      state.braceDepth = 0;
    }

    // ── Escape sequences ──
    if (stream.match(ESCAPE_REGEX)) return "escape";

    // ── Comments ──
    if (stream.match(/\/\*\*/)) {
      state.context = "comment-doc";
      return "comment";
    }
    if (stream.match(/\/\*/)) {
      state.context = "comment-block";
      return "comment";
    }
    if (stream.match(/\/\/.*/)) return "comment";

    // ── At start of line ──
    if (stream.sol()) {
      // TODO
      if (stream.match(TODO_REGEX)) return "meta";

      // Knot declaration
      if (stream.match(KNOT_REGEX)) return "heading";

      // Stitch declaration
      if (stream.match(STITCH_REGEX)) return "heading2";

      // VAR / CONST
      if (stream.match(VAR_DECL_REGEX)) {
        state.context = "start";
        return "keyword";
      }

      // LIST
      if (stream.match(LIST_DECL_REGEX)) {
        state.context = "start";
        return "keyword";
      }

      // INCLUDE
      if (stream.match(/(\s*)(INCLUDE\b)/)) return "keyword";

      // EXTERNAL
      if (stream.match(/(\s*)(EXTERNAL\b)/)) return "keyword";

      // Choice
      if (stream.match(CHOICE_REGEX)) {
        state.context = "choice-line";
        return "list";
      }

      // Gather
      if (stream.match(GATHER_REGEX)) {
        state.context = "gather-line";
        return "list";
      }

      // Logic line (~)
      if (stream.match(LOGIC_LINE_REGEX)) {
        state.context = "logic-line";
        return "operator";
      }

      // Multiline logic block start
      if (stream.match(/^(\s*)(\{)(?:([^}:]+)(:))?(?=[^}]*$)/)) {
        state.context = "logic-multiline";
        state.braceDepth = 1;
        return "brace";
      }
    }

    // ── Multiline logic context ──
    if (state.context === "logic-multiline") {
      if (stream.match(/\}/)) {
        state.braceDepth--;
        if (state.braceDepth <= 0) state.context = "start";
        return "brace";
      }
      if (stream.match(/\{/)) {
        state.braceDepth++;
        return "brace";
      }
      if (stream.match(/^\s*else\s*:/)) return "keyword";
      if (stream.match(/^(\s*)(-)(?!>)/)) return "operator";
    }

    // ── Inline patterns (any context) ──

    // Glue
    if (stream.match(GLUE_REGEX)) return "atom";

    // Divert to DONE/END
    if (stream.match(DIVERT_SPECIAL_REGEX)) return "keyword";

    // Tunnel onwards
    if (stream.match(DIVERT_TUNNEL_REGEX)) return "keyword";

    // Vanilla divert
    if (stream.match(DIVERT_REGEX)) return "link";

    // Bare divert
    if (stream.match(DIVERT_BARE_REGEX)) return "operator";

    // Inline conditional
    if (stream.match(INLINE_CONDITIONAL_REGEX)) {
      return "operator";
    }

    // Inline sequence
    if (stream.match(INLINE_SEQUENCE_REGEX)) {
      return "operator";
    }

    // Brace open/close (inline logic)
    if (stream.match(/\{/)) return "brace";
    if (stream.match(/\}/)) return "brace";

    // Pipe in sequence
    if (stream.match(/\|(?!\|)/)) return "operator";

    // Tags
    if (stream.match(TAG_REGEX)) return "meta";

    // Weave brackets in choices
    if (state.context === "choice-line") {
      if (stream.match(/\[/)) return "bracket";
      if (stream.match(/\]/)) return "bracket";
    }

    // Default: advance one character
    stream.next();
    return null;
  },

  languageData: {
    commentTokens: { line: "//", block: { open: "/*", close: "*/" } },
  },
};

/** CodeMirror 6 Language instance for ink */
export const inkLanguage = StreamLanguage.define(inkStreamParser);
