/**
 * Remirror Extension for ink interactive fiction.
 *
 * Registers ink-specific commands for the Remirror Markdown editor:
 * - insertKnot: Insert a new knot (=== name ===)
 * - insertChoice: Insert a choice (* [text])
 * - insertDivert: Insert a divert (-> target)
 * - insertInkBlock: Insert a fenced ink code block
 * - wrapInLogic: Wrap selection in logic braces { }
 */

import { PlainExtension, command, type CommandFunction } from "@remirror/core";

export class InkExtension extends PlainExtension {
  get name() {
    return "ink" as const;
  }

  /** Insert a new knot declaration at cursor */
  @command()
  insertKnot(name: string = "new_knot"): CommandFunction {
    return ({ tr, dispatch }) => {
      const text = `\n=== ${name} ===\n`;
      if (dispatch) {
        tr.insertText(text);
        dispatch(tr);
      }
      return true;
    };
  }

  /** Insert a choice at cursor */
  @command()
  insertChoice(text: string = "Choice text"): CommandFunction {
    return ({ tr, dispatch }) => {
      const choiceText = `* [${text}] `;
      if (dispatch) {
        tr.insertText(choiceText);
        dispatch(tr);
      }
      return true;
    };
  }

  /** Insert a divert at cursor */
  @command()
  insertDivert(target: string = "knot_name"): CommandFunction {
    return ({ tr, dispatch }) => {
      const divertText = `-> ${target}`;
      if (dispatch) {
        tr.insertText(divertText);
        dispatch(tr);
      }
      return true;
    };
  }

  /** Insert a fenced ink code block */
  @command()
  insertInkBlock(): CommandFunction {
    return ({ tr, dispatch }) => {
      const block = "\n```ink\n=== start ===\nHello world.\n-> END\n```\n";
      if (dispatch) {
        tr.insertText(block);
        dispatch(tr);
      }
      return true;
    };
  }

  /** Wrap current selection in logic braces */
  @command()
  wrapInLogic(): CommandFunction {
    return ({ tr, dispatch, state }) => {
      const { from, to } = state.selection;
      const selectedText = state.doc.textBetween(from, to);
      if (dispatch) {
        tr.replaceWith(from, to, state.schema.text(`{${selectedText}}`));
        dispatch(tr);
      }
      return true;
    };
  }
}
