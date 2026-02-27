/**
 * @inky/react-ink-editor — React components for ink authoring and playback.
 *
 * Two modes (like Unity Edit/Play):
 * - Edit: Remirror MD editor (left) + live inkjs play preview (right)
 * - Play: Readonly story player (full width)
 *
 * Runs in three environments:
 * - Ktor MCP server (static import — built bundle served by Ktor)
 * - Unity WebGL via OneJS (React runs inside Unity, bridges to C# ink runtime)
 * - Electron (ink-ai-assistant)
 */

// Context & Provider
export { InkEditorProvider, useInkEditor, type EditorMode, type InkEditorProviderProps } from "./InkEditorProvider";

// Editor Components
export { InkCodeEditor, type InkCodeEditorProps } from "./InkCodeEditor";
export { InkRemirrorEditor, type InkRemirrorEditorProps } from "./InkRemirrorEditor";

// Player Components
export { InkPlayPreview, type InkPlayPreviewProps } from "./InkPlayPreview";
export { InkPlayer, type InkPlayerProps } from "./InkPlayer";

// Mode Toggle
export { ModeToggle, type ModeToggleProps } from "./ModeToggle";

// Runtime Adapter
export {
  type InkRuntimeAdapter,
  type InkChoice,
  type InkStoryState,
  type RuntimeEnvironment,
  detectEnvironment,
  createRuntimeAdapter,
  createInkJsAdapter,
  createMcpAdapter,
  createOneJsAdapter,
} from "./InkRuntimeAdapter";
