/**
 * Ink language keywords — shared across all editor integrations.
 * Source: app/renderer/ace-ink-mode/ace-ink.js (keywords array)
 */

/** Built-in ink keywords for autocompletion and highlighting */
export const INK_KEYWORDS = [
  "CONST",
  "CHOICE_COUNT",
  "DONE",
  "END",
  "EXTERNAL",
  "INCLUDE",
  "LIST",
  "LIST_ALL",
  "LIST_COUNT",
  "LIST_INVERT",
  "LIST_MAX",
  "LIST_MIN",
  "LIST_RANGE",
  "LIST_VALUE",
  "LIST_RANDOM",
  "TODO",
  "TURNS_SINCE",
  "VAR",
] as const;

export type InkKeyword = (typeof INK_KEYWORDS)[number];

/** Declaration keywords that start a line */
export const INK_DECLARATION_KEYWORDS = ["VAR", "CONST", "LIST", "INCLUDE", "EXTERNAL"] as const;

/** Special divert targets */
export const INK_SPECIAL_TARGETS = ["DONE", "END"] as const;

/** Built-in function names */
export const INK_BUILTIN_FUNCTIONS = [
  "CHOICE_COUNT",
  "TURNS_SINCE",
  "LIST_ALL",
  "LIST_COUNT",
  "LIST_INVERT",
  "LIST_MAX",
  "LIST_MIN",
  "LIST_RANGE",
  "LIST_VALUE",
  "LIST_RANDOM",
] as const;
