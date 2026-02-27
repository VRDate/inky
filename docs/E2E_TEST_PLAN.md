# Inky — Detailed E2E / UX Test Plan

## 1. Test Environment Setup

### Prerequisites

| Component | Version | Notes |
|-----------|---------|-------|
| Node.js | ≥18 | Runtime |
| Electron | 30.0.4 | Via `devDependencies` |
| playwright-core | latest | Electron automation (replaces deprecated Spectron) |
| Mocha | 10.4.0 | Test runner |
| Xvfb | any | Required for headless Linux environments |

### Running Tests

```bash
# Install dependencies
cd app && npm install

# Unit tests only
npm run test:unit

# E2E tests (requires display — use xvfb-run on headless Linux)
xvfb-run --auto-servernum npm run test:e2e

# All tests
xvfb-run --auto-servernum npm test
```

### Playwright Electron Launch Pattern

```javascript
const { _electron: electron } = require('playwright-core');
const electronApp = await electron.launch({
    executablePath: 'node_modules/electron/dist/electron',
    args: ['main-process/main.js'],
    env: { ...process.env, NODE_ENV: 'test' }
});
const window = await electronApp.firstWindow();
await window.waitForSelector('#editor .ace_content', { timeout: 15000 });
```

### Key Testing Notes

- **Force-quit**: Use `electronApp.evaluate(({ app }) => { app.exit(0); })` in `afterEach` to avoid the "save changes" dialog blocking teardown.
- **jQuery click handlers**: Playwright's real mouse click (`window.click(selector, { force: true })`) must be used instead of `evaluate(() => el.click())` because jQuery `.on("click")` handlers do not fire from native DOM `.click()` calls.
- **Fade animations**: Story text starts at `opacity: 0` (jQuery fade-in). Use `{ state: 'attached' }` or `waitForFunction` when checking DOM presence instead of relying on default visibility checks.
- **Compilation stabilization**: After setting editor content, wait ~1.5s before interacting with the player. The live compiler has an initial `setTimeout(reloadInklecateSession, 1000)` and a 250ms polling interval with 500ms debounce that can cause race conditions.
- **Double buffering**: Player uses hidden/active buffer swapping. Always query `#player .innerText.active` for current content.

---

## 2. Application Launch Tests

### TC-AL-001: Initial Window Creation
- **Priority**: P0
- **Status**: Implemented (`test.js` — "shows an initial window")
- **Preconditions**: None
- **Steps**:
  1. Launch the Electron app
  2. Wait for `#editor .ace_content` to appear
  3. Check `electronApp.windows().length`
- **Expected**: Exactly 1 window is open

### TC-AL-002: Default Window Title
- **Priority**: P0
- **Status**: Implemented (`test.js` — "reads the title")
- **Preconditions**: None
- **Steps**:
  1. Launch the app
  2. Read `.title` text content
- **Expected**: Title is "Untitled.ink"

### TC-AL-003: Window Dimensions
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: None
- **Steps**:
  1. Launch the app
  2. Read window size via `electronApp.evaluate(({ BrowserWindow }) => BrowserWindow.getFocusedWindow().getSize())`
- **Expected**: Width ≈ 1300, Height ≈ 730

### TC-AL-004: Window Minimum Size
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: None
- **Steps**:
  1. Launch the app
  2. Resize window to 200×200
  3. Read actual size
- **Expected**: Width ≥ 350, Height ≥ 250

### TC-AL-005: Default Theme Applied
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: Fresh launch (no saved settings)
- **Steps**:
  1. Launch the app
  2. Check `document.body` for theme-related CSS
- **Expected**: Light theme active by default

---

## 3. Editor Tests

### TC-ED-001: Text Input via Ace Editor
- **Priority**: P0
- **Status**: Implemented (implicitly via "writes and reads hello world")
- **Preconditions**: App launched
- **Steps**:
  1. Set editor content via `ace.edit("editor").setValue(text, 1)`
  2. Read it back via `ace.edit("editor").getValue()`
- **Expected**: Content matches input

### TC-ED-002: Ace Editor Syntax Highlighting
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter ink content: `=== knot_name ===\nHello -> knot_name`
  2. Wait for compilation
  3. Check for `.ace_knot-declaration` and `.ace_divert` token classes
- **Expected**: Knot declaration and divert syntax are highlighted

### TC-ED-003: Auto-Complete (Enabled)
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched, auto-complete enabled (default)
- **Steps**:
  1. Set editor content with a knot: `=== my_knot ===\nHello\n`
  2. Type `-> my_` in the editor
  3. Check for autocomplete popup `.ace_autocomplete`
- **Expected**: Autocomplete popup appears with `my_knot` suggestion

### TC-ED-004: Auto-Complete (Disabled)
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched, auto-complete disabled via View menu
- **Steps**:
  1. Toggle auto-complete off via IPC: `set-autocomplete-disabled`
  2. Type text that would trigger autocomplete
  3. Check for absence of `.ace_autocomplete`
- **Expected**: No autocomplete popup

### TC-ED-005: Error Markers in Editor
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter invalid ink: `-> nonexistent_knot`
  2. Wait for compilation error
  3. Check for `.ace-error` marker in the editor
- **Expected**: Error line is marked with red highlight

### TC-ED-006: Warning Markers in Editor
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter ink that produces a warning
  2. Wait for compilation
  3. Check for `.ace-warning` marker
- **Expected**: Warning line is marked

### TC-ED-007: TODO Markers in Editor
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `TODO: fix this`
  2. Wait for compilation
  3. Check for `.ace-todo` marker
- **Expected**: TODO line is marked with info highlight

### TC-ED-008: Alt+Click Divert Navigation
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `=== target ===\nHello\n=== start ===\n-> target`
  2. Alt+click on `target` in the divert line
  3. Check cursor position moves to the `target` knot
- **Expected**: Editor navigates to the target knot definition

### TC-ED-009: Alt+Click Include Navigation
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App with multi-file project
- **Steps**:
  1. Open a project with `INCLUDE otherfile.ink`
  2. Alt+click on the include filename
- **Expected**: Editor opens the included file

---

## 4. Compilation Tests

### TC-CO-001: Live Compilation on Edit
- **Priority**: P0
- **Status**: Implemented (implicitly — "writes and reads hello world")
- **Preconditions**: App launched
- **Steps**:
  1. Enter valid ink: `Hello World!`
  2. Wait for `.storyText` to appear in the player
- **Expected**: Story text "Hello World!" appears in the player pane

### TC-CO-002: Compilation Debounce Behavior
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Set editor content
  2. Immediately set editor content again (within 250ms)
  3. Wait for compilation
- **Expected**: Only one compilation occurs (no double-compile)

### TC-CO-003: Error Detection and Display
- **Priority**: P0
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `-> nonexistent`
  2. Wait for `.issuesSummary:not(.hidden)`
  3. Check error count element
- **Expected**: Error count > 0 displayed in toolbar

### TC-CO-004: TODO Detection
- **Priority**: P0
- **Status**: Implemented (`test.js` — "shows TODOs")
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `-\n* Rock\n* Paper\n* Scissors\nTODO: Make this more interesting`
  2. Wait for `.issuesSummary:not(.hidden)`
  3. Check `.issueCount.todo` visibility
- **Expected**: TODO count is displayed

### TC-CO-005: Busy Spinner During Compilation
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter a large ink document
  2. Observe `.busySpinner` display property
- **Expected**: Spinner appears while compiling, disappears when done

---

## 5. Player / Story Tests

### TC-PL-001: Simple Text Display
- **Priority**: P0
- **Status**: Implemented (`test.js` — "writes and reads hello world")
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `Hello World!`
  2. Wait for `.storyText`
  3. Read text content
- **Expected**: "Hello World!" displayed

### TC-PL-002: Choice Rendering
- **Priority**: P0
- **Status**: Implemented (implicitly — "writes and selects a choice")
- **Preconditions**: App launched
- **Steps**:
  1. Enter ink with choices
  2. Wait for `.choice` elements
- **Expected**: Choices appear as clickable links

### TC-PL-003: Choice Selection
- **Priority**: P0
- **Status**: Implemented (`test.js` — "writes and selects a choice")
- **Preconditions**: Story with choices displayed
- **Steps**:
  1. Wait for compilation to stabilize (~1.5s)
  2. Click `.choice a` with `{ force: true }`
  3. Wait for new `.storyText` elements
- **Expected**: Choice text echoed, continuation text displayed, choices removed, `<hr>` divider added

### TC-PL-004: Choice Text Suppression
- **Priority**: P0
- **Status**: Implemented (`test.js` — "suppresses choice text")
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `Hello!\n* [Secret]\n  Revealed!\n-> END`
  2. Click the choice
  3. Count `.storyText` elements
- **Expected**: Only 2 storyText elements (no echoed choice text)

### TC-PL-005: Multiple Choice Sequences
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter ink with 2 consecutive choice points
  2. Select first choice, wait for second set
  3. Select second choice
- **Expected**: Both choices resolve correctly, story continues

### TC-PL-006: Tags Display
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `Hello # greeting # english`
  2. Wait for tags to appear
  3. Check for `.tags` element
- **Expected**: Tags displayed as `# greeting, english`

### TC-PL-007: Story Completion Message
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `Hello World!\n-> END`
  2. Wait for `.end` class element
- **Expected**: "End of story" message appears

### TC-PL-008: Rewind Button
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: Story with a choice already selected
- **Steps**:
  1. Enter story with a choice, select it
  2. Click the rewind button (`.rewind.button`)
- **Expected**: Story resets to beginning, original choices reappear

### TC-PL-009: Step-Back Button
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: Story with a choice already selected
- **Steps**:
  1. Enter story with a choice, select it
  2. Click step-back button (`.step-back.button`)
- **Expected**: Last choice is undone, previous state restored

### TC-PL-010: Alt+Click Jump to Source
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: Story text displayed
- **Steps**:
  1. Enter a multi-line story
  2. Wait for story text in player
  3. Alt+click a word span in the player
- **Expected**: Editor cursor jumps to corresponding source location

### TC-PL-011: Fade-In Animation
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: Animation enabled (default)
- **Steps**:
  1. Enter story content
  2. Immediately check `.storyText` opacity
  3. Wait 1.5 seconds, check opacity again
- **Expected**: Element starts at opacity 0, animates to 1.0

---

## 6. File Operations Tests

### TC-FO-001: New Window
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Trigger File > New via keyboard shortcut (Cmd/Ctrl+N)
  2. Count windows
- **Expected**: A second window opens with title "Untitled.ink"

### TC-FO-002: Save File
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched with content
- **Steps**:
  1. Set editor content
  2. Trigger save via IPC
  3. Check file was written to disk
- **Expected**: File saved with correct content

### TC-FO-003: Save Strips Bidi Markers
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: Strip bidi on save enabled (default)
- **Steps**:
  1. Set editor content containing Unicode bidi markers (U+2066, U+2067, U+2069)
  2. Save the file
  3. Read the saved file from disk
- **Expected**: Saved file has no bidi markers

### TC-FO-004: Export JSON
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched with valid ink content
- **Steps**:
  1. Enter valid ink story
  2. Trigger export JSON
  3. Check exported file
- **Expected**: Valid JSON file created

### TC-FO-005: Export JSON with Bidi
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: Bidify exported JSON enabled
- **Steps**:
  1. Enable "Bidify exported JSON" setting
  2. Enter story with RTL text
  3. Export JSON
  4. Check exported JSON for bidi markers in `^` prefixed strings
- **Expected**: Story text strings in JSON contain directional isolate markers

---

## 7. Navigation Tests

### TC-NA-001: Toggle File Browser Sidebar
- **Priority**: P0
- **Status**: Implemented (`test.js` — "opens the menu")
- **Preconditions**: App launched
- **Steps**:
  1. Click `.icon-menu` button
  2. Check sidebar visibility
- **Expected**: `.sidebar` loses `hidden` class, file nav wrapper visible

### TC-NA-002: Toggle Knot Browser Sidebar
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched with knots defined
- **Steps**:
  1. Enter ink with knots
  2. Click knot toggle button (`.knot-toggle`)
  3. Check `#knot-stitch-wrapper` visibility
- **Expected**: Knot browser becomes visible

### TC-NA-003: Go-to-Anything Dialog
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Trigger Cmd/Ctrl+P
  2. Check `#goto-anything` visibility
  3. Type a search term
  4. Check `.results` list
- **Expected**: Dialog opens, results appear matching the search

### TC-NA-004: Navigate Back/Forward
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App with navigation history (navigated between knots)
- **Steps**:
  1. Navigate to different knots
  2. Click nav-back button
  3. Click nav-forward button
- **Expected**: Editor returns to previous/next position

### TC-NA-005: Click Issue to Navigate
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: Story with errors/TODOs
- **Steps**:
  1. Enter ink with TODO on a specific line
  2. Hover `.issuesSummary` to show popup
  3. Click an issue row in the popup
- **Expected**: Editor cursor jumps to the issue's line

---

## 8. View / Theme Tests

### TC-VW-001: Switch to Dark Theme
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Send IPC `set-theme` with "dark"
  2. Check document body classes
- **Expected**: Dark theme styles applied

### TC-VW-002: Switch to Contrast Theme
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Send IPC `set-theme` with "contrast"
  2. Check CSS variables
- **Expected**: Contrast theme active

### TC-VW-003: Switch to Focus Theme
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Send IPC `set-theme` with "focus"
  2. Check CSS variables
- **Expected**: Focus theme active

### TC-VW-004: Zoom In
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched at default zoom (100%)
- **Steps**:
  1. Trigger zoom in (Cmd/Ctrl+=)
  2. Check editor font size
- **Expected**: Font size increases

### TC-VW-005: Zoom Out
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Trigger zoom out (Cmd/Ctrl+-)
  2. Check editor font size
- **Expected**: Font size decreases

### TC-VW-006: Toggle Animation
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Disable animation via IPC `set-animation-enabled` false
  2. Enter story content
  3. Check story text opacity immediately
- **Expected**: Text appears instantly at opacity 1.0 (no fade animation)

---

## 9. RTL / Bidi Tests

### TC-BD-001: Bidify Editor CSS Toggle
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Send IPC `set-bidify-editor-enabled` true
  2. Check for `bidify-enabled` class on `#editor`
  3. Verify CSS `unicode-bidi: plaintext` on `.ace_line`
  4. Send IPC `set-bidify-editor-enabled` false
  5. Verify class removed
- **Expected**: Class toggles correctly, CSS bidi rule applies

### TC-BD-002: Bidify Player Text
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched, bidify player enabled
- **Steps**:
  1. Send IPC `set-bidify-player-enabled` true
  2. Enter: `مرحبا Hello`
  3. Wait for story text
  4. Check for RLI (U+2067) and LRI (U+2066) markers in DOM text
- **Expected**: Text contains directional isolate markers

### TC-BD-003: Bidify Player Disabled (Default)
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched, bidify player disabled (default)
- **Steps**:
  1. Enter: `مرحبا Hello`
  2. Wait for story text
  3. Check for absence of bidi markers
- **Expected**: No directional isolate markers in text

### TC-BD-004: Strip Bidi on Save (Default Enabled)
- **Priority**: P1
- **Status**: Not Implemented (unit tested in `bidify.test.js`)
- **Preconditions**: App launched, strip bidi on save enabled (default)
- **Steps**:
  1. Enter editor content with embedded bidi markers
  2. Save the file
  3. Read saved file content
- **Expected**: Saved content has no U+2066, U+2067, U+2069 characters

### TC-BD-005: Strip Bidi on Save Disabled
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: Strip bidi on save disabled
- **Steps**:
  1. Send IPC `set-strip-bidi-on-save` false
  2. Enter content with bidi markers
  3. Save
  4. Read saved file
- **Expected**: Bidi markers preserved in saved file

### TC-BD-006: Bidify Exported JSON
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: Bidify export enabled
- **Steps**:
  1. Send IPC `set-bidify-export-enabled` true
  2. Enter story: `مرحبا`
  3. Export JSON
  4. Parse JSON file, find `^` prefixed strings
- **Expected**: Story text strings contain RLI/PDI markers

### TC-BD-007: Dir Attribute on Story Text
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter any story content
  2. Wait for `.storyText`
  3. Check `dir` attribute
- **Expected**: `<p class='storyText' dir='auto'>` — `dir="auto"` present

### TC-BD-008: Dir Attribute on Choices
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter story with choices
  2. Wait for `.choice`
  3. Check `dir` attribute
- **Expected**: `<p class='choice' dir='auto'>` — `dir="auto"` present

### TC-BD-009: Pure Arabic Text Rendering
- **Priority**: P1
- **Status**: Not Implemented (unit tested)
- **Preconditions**: App launched, bidify player enabled
- **Steps**:
  1. Enable bidify player
  2. Enter: `مرحبا بالعالم`
  3. Wait for story text
  4. Verify RLI markers wrap the Arabic text
- **Expected**: Text wrapped with RLI...PDI

### TC-BD-010: Pure Hebrew Text Rendering
- **Priority**: P1
- **Status**: Not Implemented (unit tested)
- **Preconditions**: App launched, bidify player enabled
- **Steps**:
  1. Enable bidify player
  2. Enter: `שלום עולם`
  3. Wait for story text
  4. Verify RLI markers wrap the Hebrew text
- **Expected**: Text wrapped with RLI...PDI

### TC-BD-011: Mixed LTR/RTL Text
- **Priority**: P1
- **Status**: Not Implemented (unit tested)
- **Preconditions**: App launched, bidify player enabled
- **Steps**:
  1. Enable bidify player
  2. Enter: `Hello مرحبا World`
  3. Wait for story text
  4. Verify LRI wraps English, RLI wraps Arabic
- **Expected**: `LRI+Hello+PDI مرحبا+RLI+PDI LRI+World+PDI`

### TC-BD-012: Ink Syntax Preserved with RTL
- **Priority**: P1
- **Status**: Not Implemented (unit tested)
- **Preconditions**: App launched, bidify player enabled
- **Steps**:
  1. Enable bidify player
  2. Enter ink with RTL choices: `* مرحبا\n  Hello back!\n-> END`
  3. Wait for choice to appear
  4. Click choice
  5. Verify story continues correctly
- **Expected**: Ink syntax (*, ->, ===) works correctly with RTL content

### TC-BD-013: Bidify Idempotency
- **Priority**: P2
- **Status**: Not Implemented (unit tested in `bidify.test.js`)
- **Preconditions**: None (unit test)
- **Steps**:
  1. Apply bidify to text
  2. Apply bidify to the result again
  3. Compare
- **Expected**: `bidify(bidify(x)) === bidify(x)`

### TC-BD-014: Bidify Round-Trip
- **Priority**: P2
- **Status**: Not Implemented (unit tested in `bidify.test.js`)
- **Preconditions**: None (unit test)
- **Steps**:
  1. Apply bidify to text
  2. Apply stripBidi to the result
  3. Compare with original
- **Expected**: `stripBidi(bidify(x)) === x`

---

## 10. Keyboard Shortcut Tests

### TC-KB-001: Cmd/Ctrl+N — New File
- **Priority**: P1
- **Status**: Not Implemented
- **Steps**: Press Cmd/Ctrl+N → new window opens
- **Expected**: New window with "Untitled.ink" title

### TC-KB-002: Cmd/Ctrl+O — Open File
- **Priority**: P1
- **Status**: Not Implemented
- **Steps**: Press Cmd/Ctrl+O → file dialog opens
- **Expected**: System open file dialog appears

### TC-KB-003: Cmd/Ctrl+S — Save
- **Priority**: P1
- **Status**: Not Implemented
- **Steps**: Press Cmd/Ctrl+S with content → file saved
- **Expected**: File written to disk

### TC-KB-004: Cmd/Ctrl+Shift+S — Export JSON
- **Priority**: P2
- **Status**: Not Implemented
- **Steps**: Press Cmd/Ctrl+Shift+S → export dialog
- **Expected**: JSON export initiated

### TC-KB-005: Cmd/Ctrl+P — Go to Anything
- **Priority**: P1
- **Status**: Not Implemented
- **Steps**: Press Cmd/Ctrl+P → dialog opens
- **Expected**: `#goto-anything` visible

### TC-KB-006: Cmd/Ctrl+= — Zoom In
- **Priority**: P2
- **Status**: Not Implemented
- **Steps**: Press Cmd/Ctrl+= → font size increases
- **Expected**: Editor zoom increases

### TC-KB-007: Cmd/Ctrl+- — Zoom Out
- **Priority**: P2
- **Status**: Not Implemented
- **Steps**: Press Cmd/Ctrl+- → font size decreases
- **Expected**: Editor zoom decreases

### TC-KB-008: Cmd/Ctrl+Z — Undo
- **Priority**: P1
- **Status**: Not Implemented
- **Steps**: Make editor change → Cmd/Ctrl+Z → change reverted
- **Expected**: Previous editor state restored

### TC-KB-009: Cmd/Ctrl+Shift+Z — Redo
- **Priority**: P1
- **Status**: Not Implemented
- **Steps**: Undo a change → Cmd/Ctrl+Shift+Z → change re-applied
- **Expected**: Undo is reversed

### TC-KB-010: Cmd/Ctrl+I — Next Issue
- **Priority**: P2
- **Status**: Not Implemented
- **Steps**: With issues present, press Cmd/Ctrl+I
- **Expected**: Editor navigates to next issue

---

## 11. Edge Cases

### TC-EC-001: Empty Document
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Clear editor content (empty string)
  2. Wait for compilation
  3. Check player state
- **Expected**: Player shows no story text, no errors

### TC-EC-002: Very Large Document
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Generate a 10,000 line ink story
  2. Set as editor content
  3. Wait for compilation
  4. Interact with choices
- **Expected**: App remains responsive, story works

### TC-EC-003: Invalid Ink Syntax
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `{{{{ invalid syntax }}}}`
  2. Wait for compilation
  3. Check for error display
- **Expected**: Errors shown in toolbar, marked in editor, no crash

### TC-EC-004: Special Characters in Story
- **Priority**: P1
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `He said "Hello" & she said <goodbye>`
  2. Wait for compilation
- **Expected**: Special characters display correctly in player (HTML-escaped)

### TC-EC-005: Unicode Emoji in Story
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Enter: `Hello 🌍 World 🎮`
  2. Wait for compilation
- **Expected**: Emoji display correctly

### TC-EC-006: Surrogate Pairs in Bidi
- **Priority**: P2
- **Status**: Not Implemented (unit tested — bidify handles cp > 0xFFFF)
- **Preconditions**: None
- **Steps**:
  1. Enter text with characters outside BMP mixed with RTL
  2. Apply bidify
- **Expected**: Surrogate pairs handled correctly, no character corruption

### TC-EC-007: Rapid Editor Changes
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Rapidly change editor content 10 times in quick succession
  2. Wait for final compilation
- **Expected**: Final content is compiled correctly, no stale compilations

### TC-EC-008: Choice Click During Recompile
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: Story with choices displayed
- **Steps**:
  1. Display a story with choices
  2. Modify editor content slightly (trigger recompile)
  3. Immediately click a choice
  4. Wait for stabilization
- **Expected**: No crash; story eventually displays correctly

---

## 12. Expression Watch Tests

### TC-EW-001: Add Watch Expression
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App launched
- **Steps**:
  1. Trigger "Add Watch Expression" via IPC
  2. Check for `.expressionWatch` row
- **Expected**: New expression editor row appears

### TC-EW-002: Evaluate Watch Expression
- **Priority**: P2
- **Status**: Not Implemented
- **Preconditions**: App with story using variables
- **Steps**:
  1. Enter: `VAR x = 5\nThe value is {x}.`
  2. Add watch expression `x`
  3. Wait for evaluation
  4. Check `.evaluationResult` content
- **Expected**: Expression result displays `5`

---

## 13. Test Coverage Summary

| Category | Total Tests | P0 | P1 | P2 | Implemented | Not Implemented |
|----------|-------------|----|----|-----|-------------|-----------------|
| Application Launch | 5 | 2 | 1 | 2 | 2 | 3 |
| Editor | 9 | 1 | 5 | 3 | 1 | 8 |
| Compilation | 5 | 2 | 0 | 3 | 2 | 3 |
| Player/Story | 11 | 4 | 4 | 3 | 4 | 7 |
| File Operations | 5 | 0 | 4 | 1 | 0 | 5 |
| Navigation | 5 | 1 | 3 | 1 | 1 | 4 |
| View/Theme | 6 | 0 | 2 | 4 | 0 | 6 |
| RTL/Bidi | 14 | 0 | 10 | 4 | 0 | 14 |
| Keyboard Shortcuts | 10 | 0 | 5 | 5 | 0 | 10 |
| Edge Cases | 8 | 0 | 3 | 5 | 0 | 8 |
| Expression Watch | 2 | 0 | 0 | 2 | 0 | 2 |
| **TOTAL** | **80** | **10** | **37** | **33** | **10** | **70** |

### Currently Implemented Tests

**E2E Tests** (`ink-electron/test/test.js` — Playwright + Mocha):
1. TC-AL-001: Shows an initial window
2. TC-AL-002: Reads the title
3. TC-NA-001: Opens the menu (file browser sidebar)
4. TC-PL-001 / TC-CO-001: Writes and reads hello world
5. TC-PL-003: Writes and selects a choice
6. TC-PL-004: Suppresses choice text
7. TC-CO-004: Shows TODOs

**Unit Tests** (`ink-electron/test/bidify.test.js` — Node.js assert):
- 29 tests covering `stripBidi()`, `bidify()`, `bidifyJson()` for Arabic, Hebrew, Persian, Syriac, Thaana, NKo, Samaritan, Mandaic scripts, plus idempotency and round-trip tests.
