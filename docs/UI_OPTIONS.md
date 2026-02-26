# Inky -- Comprehensive UI/GUI Options Reference

This document describes every user-interface element, menu item, toolbar button, keyboard shortcut, and configurable option available in the Inky Electron application.

---

## Table of Contents

1. [Application Menu Structure](#1-application-menu-structure)
   - [macOS Application Menu (Inky)](#11-macos-application-menu-inky)
   - [File Menu](#12-file-menu)
   - [Edit Menu](#13-edit-menu)
   - [View Menu](#14-view-menu)
   - [Story Menu](#15-story-menu)
   - [Ink Menu](#16-ink-menu)
   - [Window Menu](#17-window-menu)
   - [Help Menu](#18-help-menu)
   - [Context Menu (Right-Click)](#19-context-menu-right-click)
2. [Toolbar](#2-toolbar)
3. [Sidebar](#3-sidebar)
   - [File Browser](#31-file-browser-navigation-panel)
   - [Knot Browser](#32-knot-browser-panel)
4. [Editor Pane (Ace Editor)](#4-editor-pane-ace-editor)
5. [Player Pane](#5-player-pane)
6. [Go-to-Anything Dialog](#6-go-to-anything-dialog)
7. [Expression Watch](#7-expression-watch)
8. [Theme Options](#8-theme-options)
9. [Zoom Levels](#9-zoom-levels)
10. [RTL / Bidirectional (Bidi) Support](#10-rtl--bidirectional-bidi-support)
11. [Keyboard Shortcuts Reference](#11-keyboard-shortcuts-reference)

---

## 1. Application Menu Structure

The menu bar contains menus in the following order: **File**, **Edit**, **View**, **Story**, **Ink**, **Window**, **Help**. On macOS an additional application menu is prepended.

### 1.1 macOS Application Menu (Inky)

Shown only on macOS (`process.platform === 'darwin'`).

| Menu Item       | Accelerator       | Description                                  |
|-----------------|-------------------|----------------------------------------------|
| About Inky      | --                | Opens the About window                       |
| *(separator)*   |                   |                                              |
| Services        | --                | Standard macOS Services submenu              |
| *(separator)*   |                   |                                              |
| Hide Inky       | Cmd+H             | Hides the application                        |
| Hide Others     | Cmd+Alt+H         | Hides all other applications                 |
| Show All        | --                | Un-hides all hidden applications             |
| *(separator)*   |                   |                                              |
| Quit            | Cmd+Q             | Quits the application                        |

### 1.2 File Menu

| Menu Item                    | Accelerator         | Description                                                                 |
|------------------------------|---------------------|-----------------------------------------------------------------------------|
| New Project                  | Ctrl/Cmd+N          | Creates a new empty Inky project                                            |
| New Included Ink File        | Ctrl/Cmd+Alt+N      | Creates a new include file within the current project                       |
| *(separator)*                |                     |                                                                             |
| Open...                      | Ctrl/Cmd+O          | Opens a file dialog to choose an existing .ink project                      |
| Open Recent                  | --                  | Submenu listing recently opened project paths (with a **Clear** option)     |
| *(separator)*                |                     |                                                                             |
| Save Project                 | Ctrl/Cmd+S          | Saves the current project to disk                                           |
| *(separator)*                |                     |                                                                             |
| Export to JSON...            | Ctrl/Cmd+Shift+S    | Exports the compiled story to a JSON file                                   |
| Export for web...            | --                  | Exports a complete web-playable package (HTML + JS)                         |
| Export story.js only...      | Ctrl/Cmd+Alt+S      | Exports only the story.js file for web integration                          |
| *(separator)*                |                     |                                                                             |
| Close                        | Ctrl/Cmd+W          | Closes the current window                                                   |

### 1.3 Edit Menu

| Menu Item                    | Accelerator               | Description                                       |
|------------------------------|---------------------------|---------------------------------------------------|
| Undo                         | Ctrl/Cmd+Z                | Undoes the last action                             |
| Redo                         | Shift+Ctrl/Cmd+Z          | Redoes the last undone action                      |
| *(separator)*                |                           |                                                    |
| Cut                          | Ctrl/Cmd+X                | Cuts selected text to clipboard                    |
| Copy                         | Ctrl/Cmd+C                | Copies selected text to clipboard                  |
| Paste                        | Ctrl/Cmd+V                | Pastes text from clipboard                         |
| Select All                   | Ctrl/Cmd+A                | Selects all text in the focused area               |
| *(separator)*                |                           |                                                    |
| Useful Keyboard Shortcuts    | --                        | Displays a dialog listing useful keyboard shortcuts|

### 1.4 View Menu

| Menu Item                    | Accelerator                          | Type       | Description                                                |
|------------------------------|--------------------------------------|------------|------------------------------------------------------------|
| Toggle Full Screen           | Ctrl+Cmd+F (macOS) / F11 (Win/Linux)| Action     | Toggles the window between full-screen and windowed mode   |
| Theme                        | --                                   | Submenu    | Radio group to select the application color theme          |
| -- Light                     | --                                   | Radio      | Light theme (default)                                      |
| -- Dark                      | --                                   | Radio      | Dark theme                                                 |
| -- Contrast                  | --                                   | Radio      | High-contrast theme                                        |
| -- Focus                     | --                                   | Radio      | Focus theme (reduced visual clutter)                       |
| Zoom %                       | --                                   | Submenu    | Radio group to choose a specific zoom percentage           |
| -- 50%                       | --                                   | Radio      | 50 % zoom                                                  |
| -- 75%                       | --                                   | Radio      | 75 % zoom                                                  |
| -- 100%                      | --                                   | Radio      | 100 % zoom (default)                                       |
| -- 125%                      | --                                   | Radio      | 125 % zoom                                                 |
| -- 150%                      | --                                   | Radio      | 150 % zoom                                                 |
| -- 175%                      | --                                   | Radio      | 175 % zoom                                                 |
| -- 200%                      | --                                   | Radio      | 200 % zoom                                                 |
| -- 250%                      | --                                   | Radio      | 250 % zoom                                                 |
| -- 300%                      | --                                   | Radio      | 300 % zoom                                                 |
| Zoom (Increase)              | Ctrl/Cmd+=                           | Action     | Increases zoom by an incremental step (+2 px font change)  |
| Zoom (Decrease)              | Ctrl/Cmd+-                           | Action     | Decreases zoom by an incremental step (-2 px font change)  |
| Auto-complete                | --                                   | Checkbox   | Toggles Ace editor auto-completion (basic + live). On by default |
| Play view animation          | --                                   | Checkbox   | Toggles fade-in animation when new text appears in the player pane |
| Bidify (RTL)                 | --                                   | Submenu    | Options for right-to-left / bidirectional text support (see Section 10) |
| -- Bidify in editor          | --                                   | Checkbox   | Enables `unicode-bidi: plaintext` on editor lines          |
| -- Bidify in player          | --                                   | Checkbox   | Applies bidi processing to player output text              |
| -- Strip bidi on save (Recommended) | --                            | Checkbox   | Strips bidi control characters when saving .ink files. On by default |
| -- Bidify exported JSON      | --                                   | Checkbox   | Includes bidi-processed text in exported JSON              |

### 1.5 Story Menu

| Menu Item                    | Accelerator         | Type       | Description                                                          |
|------------------------------|---------------------|------------|----------------------------------------------------------------------|
| Go to anything...            | Ctrl/Cmd+P          | Action     | Opens the fuzzy-search "Go to Anything" dialog (see Section 6)      |
| Next Issue                   | Ctrl/Cmd+.          | Action     | Jumps to the next compiler issue (error, warning, or TODO)           |
| Add watch expression...      | --                  | Action     | Adds a new expression watch row at the top of the player pane        |
| Tags visible                 | --                  | Checkbox   | Toggles visibility of ink tags in the player pane. On by default     |
| Word count and more          | Ctrl/Cmd+Shift+C    | Action     | Displays project statistics (words, knots, stitches, functions, choices, gathers, diverts) |

### 1.6 Ink Menu

The Ink menu provides a library of insertable ink code snippets. Selecting any snippet inserts its ink source code at the current editor cursor position.

#### Built-in snippet categories

| Category            | Snippets                                                                                                                                               |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| Basic structure     | Knot (main section), Stitch (sub-section), Divert, Ending indicator                                                                                   |
| Choices             | Basic Choice, Sticky choice, Choice without printing, Choice with mixed output                                                                         |
| Variables           | Global variable, Temporary variable, Modify variable, Get variable type                                                                                |
| Inline logic        | Condition (inline)                                                                                                                                     |
| Multi-line logic    | Condition (multi-line)                                                                                                                                 |
| Comments            | Single-line comment, Block comment                                                                                                                     |
| *(separator)*       |                                                                                                                                                        |
| List-handling       | List: pop, List: pop_random, List: LIST_NEXT and LIST_PREV, List: list_item_is_member_of, List: list_random_subset, List: list_random_subset_of_size, List: string_to_list |
| Useful functions    | Logic: maybe, Mathematics: divisor, Mathematics: abs, Flow: came_from, Flow: seen_very_recently, Flow: seen_more_recently_than, Flow: seen_this_scene, Flow: thread_in_tunnel, Printing: a (or an), Printing: UPPERCASE, Printing: print_number, Printing: list_with_commas |
| Useful systems      | List Items as Integer Variables, Swing Variables, Storylets                                                                                            |
| *(separator)*       |                                                                                                                                                        |
| Full stories        | Crime Scene (from Writing with Ink), Swindlestones (from Sorcery!), Pontoon Game (from Overboard!), The Intercept                                     |

#### Custom snippets

Users can define additional custom snippet menus via project settings (`customInkSnippets`). Custom snippets appear below a separator after the built-in categories. Each custom snippet may have a `name`, `ink` content, optional `accelerator`/`shortcut`, and nested `submenu` arrays.

### 1.7 Window Menu

| Menu Item                    | Accelerator                                | Description                                                     |
|------------------------------|--------------------------------------------|-----------------------------------------------------------------|
| Minimize                     | Ctrl/Cmd+M                                 | Minimizes the current window                                    |
| Close                        | Ctrl/Cmd+W                                 | Closes the current window                                       |
| Developer                    | --                                         | Submenu with developer tools                                    |
| -- Reload web view           | Ctrl/Cmd+R                                 | Reloads the renderer (prompts to confirm, warns about unsaved changes) |
| -- Toggle Developer Tools    | Alt+Cmd+I (macOS) / Ctrl+Shift+I (Win/Linux)| Opens or closes Chromium DevTools                              |
| *(separator)* *(macOS only)* |                                            |                                                                  |
| Bring All to Front *(macOS)* | --                                         | Brings all Inky windows to the front                            |

### 1.8 Help Menu

| Menu Item             | Accelerator | Description                                         |
|-----------------------|-------------|-----------------------------------------------------|
| Show Documentation    | F1          | Opens the ink documentation in a separate window     |
| About Inky *(Win/Linux)* | --       | Opens the About window (on non-macOS platforms)      |

### 1.9 Context Menu (Right-Click)

Right-clicking anywhere in the application window shows a context menu with the following items:

| Menu Item | Description          |
|-----------|----------------------|
| Cut       | Cuts selected text   |
| Copy      | Copies selected text |
| Paste     | Pastes from clipboard|

---

## 2. Toolbar

The toolbar is a fixed bar at the top of the window (56 px tall on macOS, 34 px on Windows/Linux). It is divided into three regions:

### Left Buttons

| Button         | Icon               | Tooltip               | Action                                                   |
|----------------|--------------------|-----------------------|----------------------------------------------------------|
| Nav Toggle     | Hamburger menu     | "Toggle file browser" | Shows or hides the file browser sidebar panel            |
| Knot Toggle    | Category icon      | "Toggle knot browser" | Shows or hides the knot/stitch/function browser sidebar panel |
| Nav Back       | Left arrow         | "Navigate back"       | Navigates backward in the editor navigation history      |
| Nav Forward    | Right arrow        | "Navigate forward"    | Navigates forward in the editor navigation history       |

### Center Area

- **Title (h1)**: Displays the currently active ink filename. On macOS this is centered in the toolbar title area. On Windows/Linux the title is hidden (the native window title is used instead via IPC `set-native-window-title`).
- **Issues Message**: Displays "No issues." in italic when compilation has zero issues.
- **Issues Summary**: When issues exist, shows colored counts with icons:
  - **Error count** (red icon) -- compile errors and runtime errors
  - **Warning count** (yellow icon) -- compile warnings
  - **TODO count** (green icon) -- TODO markers in ink source
- **Issues Popup**: Hovering over the issues summary reveals a popup table listing every issue with its line number, message, and type (color-coded). Clicking an issue row navigates the editor to that line.

### Right Buttons

| Button      | Icon            | Tooltip                 | Action                                                            |
|-------------|-----------------|-------------------------|-------------------------------------------------------------------|
| Busy Spinner| Animated GIF    | --                      | Visible when the compiler is running; hidden otherwise            |
| Step Back   | Single reply    | "Rewind a single choice"| Rewinds the story by one choice in the player pane                |
| Rewind      | Double reply    | "Restart story"         | Restarts the entire story from the beginning                      |

---

## 3. Sidebar

The sidebar is a collapsible panel on the left side of the main content area. It supports showing one or both of its panels simultaneously. The sidebar width defaults to 200 px and can be resized via a draggable split handle. When both panels are visible the sidebar expands to double width, with each panel occupying half.

### 3.1 File Browser (Navigation Panel)

Toggled via the **Nav Toggle** toolbar button.

- **Main ink file** section: Shows the root/main ink file with a book icon. The filename is displayed, and the item is bolded if there are unsaved changes.
- **Include groups**: Included files are grouped by their directory path. Each group has a title showing the directory name.
- **Unused files**: Files that exist on disk but are not referenced by any `INCLUDE` statement are grouped under "Unused files".
- **File states**:
  - *Active*: The currently viewed file is highlighted (bold).
  - *Unsaved*: Files with unsaved changes are bolded.
  - *Loading*: Files currently being loaded appear with reduced opacity.
- **Footer -- Add new include**:
  - A "+" button labeled "Add new include" is shown at the bottom.
  - Clicking it reveals a form with:
    - A text input for the new ink filename (placeholder: "Folder/inkFileName.ink")
    - A checkbox: "Add to main ink" (checked by default)
    - **Add** and **Cancel** buttons
  - Pressing Enter in the input field confirms the addition. Pressing Escape cancels.
  - If the filename does not end in `.ink`, the extension is appended automatically.

### 3.2 Knot Browser Panel

Toggled via the **Knot Toggle** toolbar button.

- If no knots, stitches, or functions exist, displays a tooltip: "Knots, stitches and functions are indexed here".
- Otherwise, displays three collapsible sections:
  - **Content**: Lists all knots (non-function flow sections) with a knot icon. Stitches within each knot are shown indented with a stitch icon.
  - **Functions**: Lists all functions with a function icon.
  - **Externals**: Lists all external function declarations.
- **Active tracking**: As the cursor moves in the editor, the current knot and stitch are automatically highlighted in the browser.
- Clicking any item jumps the editor to that knot/stitch/function row.

---

## 4. Editor Pane (Ace Editor)

The editor occupies the left half of the main content area (or all available space minus the sidebar width). It uses the [Ace Editor](https://ace.c9.io/) with custom ink syntax highlighting.

### Editor Features

| Feature                   | Description                                                                                   |
|---------------------------|-----------------------------------------------------------------------------------------------|
| Syntax highlighting       | Custom Ace mode for the ink language (InkMode) with highlighting for knots, diverts, choices, tags, comments, TODOs, etc. |
| Auto-completion           | Basic and live auto-completion using a custom `inkCompleter` that indexes all project symbols. Can be toggled via View > Auto-complete. |
| Error/Warning markers     | Compiler errors show red line highlights (`ace-error`), warnings show orange (`ace-warning`), TODOs show green (`ace-todo`). Gutter icons appear for annotated lines. |
| Line numbers              | Gutter with line numbers (light gray text on white background).                               |
| Print margin              | Disabled (`setShowPrintMargin(false)`).                                                       |
| Find and Replace          | Ace built-in: Ctrl/Cmd+F (Find), Ctrl/Cmd+H (Find and Replace).                             |
| Toggle Comment            | Ctrl/Cmd+/ -- toggles line comments.                                                          |
| Multiple cursors          | Ctrl+Alt+Up / Ctrl+Option+Up to add cursor above; Ctrl+Alt+Down / Ctrl+Option+Down to add cursor below. |
| Code folding              | Alt+L / Ctrl+Option+Down -- temporarily fold/unfold the selected region.                     |
| Alt+Click navigation      | Holding Alt and clicking a **divert target** jumps to its definition. Holding Alt and clicking an **INCLUDE filepath** opens that file. The cursor changes to a pointer when hovering over clickable tokens while Alt is held. |
| Divert/Include underlines | Divert targets and include file paths are underlined in the editor for visual identification. |
| Cursor position tracking  | As the cursor moves, the knot browser updates to highlight the current knot/stitch.          |
| Session per file          | Each ink file gets its own Ace EditSession, preserving undo history, cursor position, and scroll state when switching between files. |
| Snippet insertion         | Snippets from the Ink menu are inserted at the current cursor position via `editor.insert()`. |
| RTL / Bidi support        | When "Bidify in editor" is enabled, the CSS class `bidify-enabled` is added to the editor element, which sets `unicode-bidi: plaintext` on all `.ace_line` elements. |
| Hidden cursors            | When the editor is not focused, the cursor and bracket matchers are hidden via CSS opacity. |

---

## 5. Player Pane

The player pane occupies the right half of the main content area. It displays the compiled story output as the ink runtime executes.

### Player Components

| Component              | Description                                                                                                                 |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| **Story text**         | Paragraphs of story output (`<p class="storyText">`). Each word is wrapped in a `<span>` for individual clickability. Text direction is auto-detected (`dir="auto"`). |
| **Choices**            | Displayed as centered clickable links (`<p class="choice">`). Clicking a choice removes all visible choices, inserts a horizontal divider, and continues the story. |
| **Tags**               | Shown as monospace text prefixed with `#` (`<p class="tags">`). Choice-level tags appear inline after the choice text. Tags can be hidden via Story > Tags visible. |
| **Horizontal dividers**| `<hr>` elements inserted between story turns to visually separate sections.                                                 |
| **End of story**       | A bold centered message ("End of story") when the story reaches an ending.                                                  |
| **Errors**             | Runtime errors and warnings appear as red clickable links that navigate to the source line in the editor.                    |
| **Diagnostic messages**| Compiler crash output is shown in a `<pre>` block with gray monospace text.                                                 |
| **Evaluation results** | Results from watch expressions appear as small pill-shaped badges (gray background for success, red for errors).             |
| **Scroll animation**   | When new content is added, the player auto-scrolls with a 500 ms animation (100 ms if animation is disabled).               |
| **Fade-in animation**  | New paragraphs, choices, and tags fade in with a 1000 ms opacity animation (when enabled via View > Play view animation).   |
| **Alt+Click to source**| Holding Alt and clicking any word in the story text jumps the editor to the corresponding source position in the ink file.   |
| **Bidify support**     | When "Bidify in player" is enabled, all story text is processed through the `bidify()` function before display.             |
| **Custom instructions** | If a project defines an instruction prefix, paragraphs starting with that prefix are styled in a red monospace font.       |
| **Double buffering**   | The player uses a hidden buffer for off-screen rendering during recompilation, then swaps it in when the new session is ready. |

---

## 6. Go-to-Anything Dialog

Opened via **Ctrl/Cmd+P** or **Story > Go to anything...**

The Go-to-Anything dialog is a floating search overlay that provides fuzzy-finding across the entire project.

### Search Capabilities

| Input Type           | What It Matches                                                              |
|----------------------|------------------------------------------------------------------------------|
| Plain text           | Fuzzy-matches file names, symbol names (knots, stitches, functions), and content lines across all project files |
| Number (e.g. `42`)   | Jumps to line 42 of the active file                                          |
| Dotted path (e.g. `knot.stitch`) | Looks up an internal ink runtime path and navigates to the corresponding source location |

### Result Types

- **File** (file icon): Matches against ink file names. Shows directory ancestry.
- **Symbol** (pencil icon): Matches against knot, stitch, and function names. Shows ancestry chain and source file/line.
- **Content** (plain text): Matches against the text content of all lines. Shows file path and line number.
- **Go to line** (arrow icon): Direct line number navigation.
- **Runtime path** (magnifier icon): Resolved internal runtime path lookup.

### Interaction

- **Up/Down arrows**: Navigate through results.
- **Enter**: Jumps to the selected result.
- **Escape**: Closes the dialog and restores the original cursor position.
- **Mouse hover**: Selects the hovered result.
- **Mouse click**: Jumps to the clicked result.
- Results are built incrementally (10 per 35 ms tick) to keep the UI responsive.
- Maximum of 1000 results displayed.

---

## 7. Expression Watch

Added via **Story > Add watch expression...**

Expression watches allow you to evaluate ink expressions after every turn.

- Each watch expression appears as a row at the top of the player pane in a table.
- The expression input is a miniature Ace editor with full ink syntax highlighting, single-line mode, no gutter, and no print margin.
- Default placeholder content: `x is {x}` (with both `x` instances multi-selected for easy renaming).
- A **remove button** (cancel icon) on the right side of each row removes that expression.
- After every turn, each expression is evaluated in order and the result is displayed as a pill-shaped badge in the player output.
- Errors in expression evaluation are shown in red.

---

## 8. Theme Options

Accessible via **View > Theme**. The selected theme persists across sessions via view settings.

| Theme      | Description                                                                     |
|------------|---------------------------------------------------------------------------------|
| **Light**  | Default light theme. White backgrounds, dark text.                              |
| **Dark**   | Dark background with light text. Applied via the CSS class `dark` on `.window`. |
| **Contrast** | High-contrast variant for accessibility. Applied via the CSS class `contrast`.  |
| **Focus**  | Reduced visual clutter theme. Applied via the CSS class `focus`.                |

Theme changes are applied globally to all open project windows, as well as the About and Documentation windows. The current theme is stored in `localStorage` and in the shared view settings file.

---

## 9. Zoom Levels

Accessible via **View > Zoom %** (preset percentages), **View > Zoom (Increase)** (Ctrl/Cmd+=), or **View > Zoom (Decrease)** (Ctrl/Cmd+-).

### Preset Zoom Levels

50%, 75%, 100%, 125%, 150%, 175%, 200%, 250%, 300%

### Zoom Behavior

- Preset zoom: Computes font size as `baseFontSize * zoomPercent / 100`. The editor base is 12 px and the player base is 14 px.
- Incremental zoom: Adjusts font size by +/- 2 px per step.
- Both the editor and player panes are zoomed simultaneously.
- The current zoom level persists across sessions.

---

## 10. RTL / Bidirectional (Bidi) Support

Accessible via **View > Bidify (RTL)** submenu. These options enable authoring and displaying right-to-left languages such as Arabic and Hebrew.

| Option                            | Default | Description                                                                              |
|-----------------------------------|---------|------------------------------------------------------------------------------------------|
| **Bidify in editor**              | Off     | Adds the CSS class `bidify-enabled` to the editor, setting `unicode-bidi: plaintext` on all Ace editor lines. This allows mixed RTL/LTR text to display correctly in the editor. |
| **Bidify in player**              | Off     | Processes all story output text through the `bidify()` function before rendering in the player pane. Also triggers a recompile to apply the change to existing content. |
| **Strip bidi on save (Recommended)** | On   | Strips Unicode bidi control characters from ink file content when saving. This prevents invisible control characters from accumulating in source files. |
| **Bidify exported JSON**          | Off     | When exporting to JSON, applies bidi processing to the exported story text.              |

All bidi settings persist across sessions in the shared view settings.

---

## 11. Keyboard Shortcuts Reference

### Application-Level Shortcuts

| Shortcut                          | Action                                     |
|-----------------------------------|--------------------------------------------|
| Ctrl/Cmd+N                        | New Project                                |
| Ctrl/Cmd+Alt+N                    | New Included Ink File                      |
| Ctrl/Cmd+O                        | Open...                                    |
| Ctrl/Cmd+S                        | Save Project                               |
| Ctrl/Cmd+Shift+S                  | Export to JSON...                           |
| Ctrl/Cmd+Alt+S                    | Export story.js only...                     |
| Ctrl/Cmd+W                        | Close window                               |
| Ctrl/Cmd+Z                        | Undo                                       |
| Shift+Ctrl/Cmd+Z                  | Redo                                       |
| Ctrl/Cmd+X                        | Cut                                        |
| Ctrl/Cmd+C                        | Copy                                       |
| Ctrl/Cmd+V                        | Paste                                      |
| Ctrl/Cmd+A                        | Select All                                 |
| Ctrl+Cmd+F (macOS) / F11          | Toggle Full Screen                         |
| Ctrl/Cmd+=                        | Zoom In                                    |
| Ctrl/Cmd+-                        | Zoom Out                                   |
| Ctrl/Cmd+P                        | Go to Anything...                          |
| Ctrl/Cmd+.                        | Next Issue                                 |
| Ctrl/Cmd+Shift+C                  | Word count and more (project statistics)   |
| Ctrl/Cmd+M                        | Minimize window                            |
| Ctrl/Cmd+R                        | Reload web view (with confirmation)        |
| Alt+Cmd+I (macOS) / Ctrl+Shift+I  | Toggle Developer Tools                     |
| F1                                | Show Documentation                         |
| Cmd+Q (macOS)                     | Quit application                           |
| Cmd+H (macOS)                     | Hide application                           |
| Cmd+Alt+H (macOS)                 | Hide other applications                    |

### Editor Shortcuts (Ace Editor)

| Shortcut                          | Action                                     |
|-----------------------------------|--------------------------------------------|
| Ctrl/Cmd+F                        | Find                                       |
| Ctrl/Cmd+H                        | Find and Replace                           |
| Ctrl/Cmd+/                        | Toggle Comment                             |
| Ctrl+Alt+Up / Ctrl+Option+Up      | Add Multicursor Above                      |
| Ctrl+Alt+Down / Ctrl+Option+Down  | Add Multicursor Below                      |
| Alt+L / Ctrl+Option+Down          | Temporarily Fold/Unfold Selection          |
| Alt+Click on divert target        | Jump to divert target definition           |
| Alt+Click on INCLUDE path         | Open the included file                     |

### Go-to-Anything Dialog Shortcuts

| Shortcut  | Action                                         |
|-----------|-------------------------------------------------|
| Up arrow  | Select previous result                          |
| Down arrow| Select next result                               |
| Enter     | Navigate to selected result                      |
| Escape    | Close dialog and restore cursor                  |

### Player Pane Interactions

| Interaction                 | Action                                             |
|-----------------------------|----------------------------------------------------|
| Click a choice              | Selects that choice and continues the story        |
| Alt+Click a story word      | Jumps the editor to the corresponding source line  |

---

## Appendix: Unsaved Changes Dialog

When closing a window with unsaved changes, a dialog is presented:

- **Message**: "Would you like to save changes before exiting?"
- **Detail**: "Your changes will be lost if you don't save."
- **Buttons**: Save, Don't save, Cancel

---

## Appendix: File Open Behavior

- **Drag and drop**: Dragging an `.ink` file onto the Inky icon or window opens it. If the file is already open, the existing window is focused.
- **Command-line** (Windows): Passing a `.ink` file as a command-line argument opens it on launch.
- **macOS open-file event**: Standard macOS file association and open events are handled.
