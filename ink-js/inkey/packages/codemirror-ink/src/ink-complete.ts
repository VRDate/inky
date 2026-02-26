/**
 * Autocompletion for ink language in CodeMirror 6.
 *
 * Provides:
 * - Ink keywords (VAR, CONST, LIST, DONE, END, etc.)
 * - Knot/stitch names scanned from the document
 */

import { autocompletion, type CompletionContext, type CompletionResult } from "@codemirror/autocomplete";
import { INK_KEYWORDS, KNOT_REGEX, STITCH_REGEX } from "@inky/ink-language";

/** Scan document for knot and stitch names */
function scanKnotStitchNames(doc: string): string[] {
  const names: string[] = [];
  const lines = doc.split("\n");
  for (const line of lines) {
    const knotMatch = line.match(KNOT_REGEX);
    if (knotMatch && knotMatch[6]) {
      names.push(knotMatch[6]);
    }
    const stitchMatch = line.match(STITCH_REGEX);
    if (stitchMatch && stitchMatch[4]) {
      names.push(stitchMatch[4]);
    }
  }
  return [...new Set(names)];
}

/** Ink autocompletion source */
function inkCompletionSource(context: CompletionContext): CompletionResult | null {
  const word = context.matchBefore(/\w+/);
  if (!word && !context.explicit) return null;

  const from = word ? word.from : context.pos;
  const docText = context.state.doc.toString();
  const knotNames = scanKnotStitchNames(docText);

  const options = [
    ...INK_KEYWORDS.map((kw) => ({
      label: kw,
      type: "keyword" as const,
      detail: "Ink Keyword",
    })),
    ...knotNames.map((name) => ({
      label: name,
      type: "function" as const,
      detail: "Knot/Stitch",
    })),
  ];

  return { from, options };
}

/** CodeMirror extension for ink autocompletion */
export const inkCompletion = autocompletion({
  override: [inkCompletionSource],
});
