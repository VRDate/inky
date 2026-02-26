/**
 * Highlight style for ink language in CodeMirror 6.
 *
 * Maps ink token tags to colors. Works with both light and dark themes.
 */

import { HighlightStyle, syntaxHighlighting } from "@codemirror/language";
import { tags } from "@lezer/highlight";

/** Default ink highlight style */
export const inkHighlightStyle = HighlightStyle.define([
  // Knot/stitch declarations → blue headings
  { tag: tags.heading, color: "#2563eb", fontWeight: "bold" },
  { tag: tags.heading2, color: "#3b82f6", fontWeight: "bold" },

  // Keywords (VAR, CONST, LIST, INCLUDE, EXTERNAL, DONE, END)
  { tag: tags.keyword, color: "#9333ea", fontWeight: "bold" },

  // Choice/gather bullets → green
  { tag: tags.list, color: "#16a34a", fontWeight: "bold" },

  // Divert targets → purple links
  { tag: tags.link, color: "#7c3aed", textDecoration: "underline" },

  // Operators (diverts, logic ~, sequences)
  { tag: tags.operator, color: "#d97706" },

  // Braces (logic blocks)
  { tag: tags.brace, color: "#d97706" },

  // Brackets (weave)
  { tag: tags.bracket, color: "#059669" },

  // Comments
  { tag: tags.comment, color: "#6b7280", fontStyle: "italic" },

  // Tags → teal
  { tag: tags.meta, color: "#0d9488" },

  // Atoms (glue <>)
  { tag: tags.atom, color: "#e11d48" },

  // Escape sequences
  { tag: tags.escape, color: "#9ca3af" },
]);

/** CodeMirror extension for ink syntax highlighting */
export const inkSyntaxHighlighting = syntaxHighlighting(inkHighlightStyle);
