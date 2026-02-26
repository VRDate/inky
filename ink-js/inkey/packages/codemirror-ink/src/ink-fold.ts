/**
 * Folding for ink language in CodeMirror 6.
 *
 * Fold ranges:
 * - Knot: from === name === to next === (knot boundary)
 * - Stitch: from = name to next = or === (stitch or knot boundary)
 *
 * Ported from: app/renderer/ace-ink-mode/ace-ink.js (inkFoldingRules)
 */

import { foldService } from "@codemirror/language";
import { FOLD_START_REGEX, KNOT_BOUNDARY_REGEX, STITCH_BOUNDARY_REGEX } from "@inky/ink-language";

/** CodeMirror fold service for ink knots and stitches */
export const inkFolding = foldService.of((state, lineStart) => {
  const line = state.doc.lineAt(lineStart);
  const text = line.text;

  const match = text.match(FOLD_START_REGEX);
  if (!match) return null;

  const isKnot = match.groups?.knot != null || (match[2] && match[2].length >= 2);
  const boundaryRegex = isKnot ? KNOT_BOUNDARY_REGEX : STITCH_BOUNDARY_REGEX;

  // Find the end of the fold region
  let endLine = line.number;
  const maxLine = state.doc.lines;

  for (let i = line.number + 1; i <= maxLine; i++) {
    const nextLine = state.doc.line(i);
    if (boundaryRegex.test(nextLine.text)) {
      break;
    }
    endLine = i;
  }

  // Skip trailing empty lines
  while (endLine > line.number && state.doc.line(endLine).text.trim() === "") {
    endLine--;
  }

  if (endLine <= line.number) return null;

  return {
    from: line.to,
    to: state.doc.line(endLine).to,
  };
});
