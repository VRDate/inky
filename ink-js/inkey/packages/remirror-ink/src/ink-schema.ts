/**
 * Custom ProseMirror node specs for structured ink elements.
 *
 * These extend the Markdown schema with ink-specific nodes:
 * - inkCodeBlock: fenced ```ink code blocks (rendered by Ace or CodeMirror)
 * - inkKnot: knot section header (=== name ===)
 * - inkChoice: interactive choice element
 */

import { type NodeSpec } from "@remirror/core";

/** ProseMirror node spec for ```ink fenced code blocks */
export const inkCodeBlockSpec: NodeSpec = {
  group: "block",
  content: "text*",
  marks: "",
  code: true,
  defining: true,
  attrs: {
    language: { default: "ink" },
    editor: { default: "codemirror" }, // "codemirror" | "ace"
  },
  parseDOM: [
    {
      tag: "pre.ink-code-block",
      getAttrs: (dom) => ({
        language: "ink",
        editor: (dom as HTMLElement).dataset.editor || "codemirror",
      }),
    },
  ],
  toDOM(node) {
    return [
      "pre",
      {
        class: `ink-code-block ink-code-block--${node.attrs.editor}`,
        "data-language": "ink",
        "data-editor": node.attrs.editor,
      },
      ["code", 0],
    ];
  },
};

/** ProseMirror node spec for ink knot headers (display only) */
export const inkKnotSpec: NodeSpec = {
  group: "block",
  content: "text*",
  attrs: {
    name: { default: "" },
    isFunction: { default: false },
    parameters: { default: "" },
  },
  parseDOM: [
    {
      tag: "h2.ink-knot",
      getAttrs: (dom) => ({
        name: (dom as HTMLElement).dataset.name || "",
        isFunction: (dom as HTMLElement).dataset.function === "true",
        parameters: (dom as HTMLElement).dataset.params || "",
      }),
    },
  ],
  toDOM(node) {
    const label = node.attrs.isFunction
      ? `function ${node.attrs.name}(${node.attrs.parameters})`
      : node.attrs.name;
    return [
      "h2",
      {
        class: "ink-knot",
        "data-name": node.attrs.name,
        "data-function": String(node.attrs.isFunction),
        "data-params": node.attrs.parameters,
      },
      `=== ${label} ===`,
    ];
  },
};

/** ProseMirror node spec for ink choices (interactive in play mode) */
export const inkChoiceSpec: NodeSpec = {
  group: "block",
  content: "inline*",
  attrs: {
    index: { default: 0 },
    isSticky: { default: false }, // + vs *
    label: { default: "" },
  },
  parseDOM: [
    {
      tag: "div.ink-choice",
      getAttrs: (dom) => ({
        index: parseInt((dom as HTMLElement).dataset.index || "0"),
        isSticky: (dom as HTMLElement).dataset.sticky === "true",
        label: (dom as HTMLElement).dataset.label || "",
      }),
    },
  ],
  toDOM(node) {
    const bullet = node.attrs.isSticky ? "+" : "*";
    return [
      "div",
      {
        class: "ink-choice",
        "data-index": String(node.attrs.index),
        "data-sticky": String(node.attrs.isSticky),
        "data-label": node.attrs.label,
      },
      ["span", { class: "ink-choice-bullet" }, bullet],
      ["span", { class: "ink-choice-content" }, 0],
    ];
  },
};
