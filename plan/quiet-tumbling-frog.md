# RTL Language Support for Inky (Issue #122)

## Context

Inky cannot properly display RTL languages (Arabic, Persian, Hebrew, etc.). The fix uses Unicode directional isolates (LRI U+2066, RLI U+2067, PDI U+2069) **for display only** — the document content, saved files, and compiler input remain clean. Markers are only applied at the view layer.

## Design Principle: Markers in View Only

- **Editor**: CSS-based RTL support (`unicode-bidi: plaintext` on `.ace_line`). No markers injected into the Ace document.
- **Player preview**: `bidify()` applied to text before DOM insertion. Display only — doesn't modify underlying story data.
- **Document/Save/Compile**: Always clean (no markers). Strip on save is a safety net.
- **Exported JSON**: Optional post-processing to add markers for external player compatibility.

## Four User Toggles (all OFF by default, except strip-on-save)

| Toggle | Default | What it does |
|--------|---------|--------------|
| Bidify in editor | OFF | Enables CSS `unicode-bidi: plaintext` on editor lines |
| Bidify in player | OFF | Applies `bidify()` to story text before DOM insertion |
| Strip bidi on save | ON (Recommended) | Strips any stray bidi markers when saving .ink files |
| Bidify exported JSON | OFF | Post-processes exported JSON to embed bidi markers |

## Plan

### 1. Create `app/renderer/bidify.js` — new file

Exports:
- **`bidify(text)`**: Process line-by-line. Group codepoints by script, wrap RTL runs with RLI...PDI and LTR strong runs with LRI...PDI. Strip existing isolates first (idempotent).
- **`stripBidi(text)`**: Remove all U+2066, U+2067, U+2069 characters.
- **`bidifyJson(jsonString)`**: Parse compiled ink JSON, bidify story text strings (those prefixed with `^`), re-serialize.

Script detection via codepoint ranges: Arabic U+0600-U+06FF + extensions, Hebrew U+0590-U+05FF, Syriac U+0700-U+074F, Thaana U+0780-U+07BF, NKo U+07C0-U+07FF, Samaritan U+0800-U+083F, Mandaic U+0840-U+085F.

### 2. Add UI toggles — View > "Bidify (RTL)" submenu

**`app/main-process/appmenus.js`**:
- Add four state vars: `bidifyEditorEnabled`, `bidifyPlayerEnabled`, `stripBidiOnSave`, `bidifyExportEnabled`
- Add submenu after "Play view animation" (~line 322):
  ```
  Bidify (RTL) >
    [ ] Bidify in editor
    [ ] Bidify in player
    [x] Strip bidi on save (Recommended)
    [ ] Bidify exported JSON
  ```
- Add four setters in exports

**`app/main-process/main.js`**: Four toggle callbacks following existing pattern (toggleAnimation/toggleAutoComplete). Each toggles setting, persists via `addOrChangeViewSetting`, sends IPC to all windows. Initialize from settings on startup. Sync on view settings change.

**`app/main-process/projectWindow.js`**:
- Add defaults to `getViewSettings()` (~line 319): all OFF except `stripBidiOnSave: true`
- Send all four settings to renderer on `dom-ready` (~line 96-103)

### 3. Modify `app/renderer/controller.js`

Four IPC listeners (~after line 337):
- `set-bidify-editor-enabled` → toggle CSS class on `#editor` (add/remove `.bidify-enabled`)
- `set-bidify-player-enabled` → `PlayerView.setBidifyEnabled(enabled)`
- `set-strip-bidi-on-save` → set flag on InkFile (static property)
- `set-bidify-export-enabled` → `LiveCompiler.setBidifyExportEnabled(enabled)`

### 4. Modify `app/renderer/playerView.js`

Display-only bidify:
```js
const { bidify } = require("./bidify.js");
var bidifyEnabled = false;
```

In `addTextSection(text)` (~line 114): `if (bidifyEnabled) text = bidify(text);`
Add `dir="auto"` on `<p class='storyText'>` and `<p class='choice'>`.
Add `setBidifyEnabled` to exports.

### 5. Modify `app/renderer/inkFile.js`

**Save only** — strip as safety net (~line 188):
```js
const { stripBidi } = require("./bidify.js");
// in save():
var fileContent = this.aceDocument.getValue() || "";
if (InkFile.stripBidiOnSave) fileContent = stripBidi(fileContent);
```

Add static property: `InkFile.stripBidiOnSave = true;`

No changes to `getValue()` or `tryLoadFromDisk()` — document stays clean.

### 6. Modify `app/renderer/liveCompiler.js`

Pass bidify export flag in compile instruction:
```js
var bidifyExportEnabled = false;
// in buildCompileInstruction():
compileInstruction.bidifyExportEnabled = bidifyExportEnabled;
```

No stripping needed before compile — document is already clean.
Add `setBidifyExportEnabled` to exports.

### 7. Modify `app/main-process/inklecate.js`

Post-process exported JSON when flag is set (~line 133):
```js
const { bidifyJson } = require("../renderer/bidify.js");
// in onEndOfStory, after export completes:
if (jsonExportPath && compileInstruction.bidifyExportEnabled) {
    let json = fs.readFileSync(jsonExportPath, 'utf8');
    json = bidifyJson(json);
    fs.writeFileSync(jsonExportPath, json);
}
```

### 8. Modify `app/renderer/main.css`

CSS-based RTL for editor (applied via `.bidify-enabled` class toggle):
```css
#editor.bidify-enabled .ace_line {
  unicode-bidi: plaintext;
}

#player .innerText p.storyText {
  unicode-bidi: plaintext;
}
```

### 9. Create `app/test/bidify.test.js` — new file

Tests for: `bidify()`, `stripBidi()`, `bidifyJson()`, round-trip, idempotency, multi-line, ink syntax preservation, empty input.

## Files Summary

| File | Action |
|------|--------|
| `app/renderer/bidify.js` | **Create** — bidify/stripBidi/bidifyJson |
| `app/main-process/appmenus.js` | **Modify** — add Bidify (RTL) submenu |
| `app/main-process/main.js` | **Modify** — 4 toggle callbacks |
| `app/main-process/projectWindow.js` | **Modify** — defaults + send to renderer |
| `app/renderer/controller.js` | **Modify** — 4 IPC listeners |
| `app/renderer/inkFile.js` | **Modify** — strip on save (safety net) |
| `app/renderer/liveCompiler.js` | **Modify** — pass export flag |
| `app/renderer/playerView.js` | **Modify** — display-only bidify |
| `app/main-process/inklecate.js` | **Modify** — post-process exported JSON |
| `app/renderer/main.css` | **Modify** — RTL CSS rules |
| `app/test/bidify.test.js` | **Create** — unit tests |

## Verification

1. Run `node app/test/bidify.test.js`
2. Verify all four toggles persist in `view-settings.json`
3. With "Bidify in editor" ON: editor renders RTL text correctly via CSS
4. With "Bidify in player" ON: preview shows RTL text correctly
5. With "Strip bidi on save" ON: saved .ink files have no stray markers
6. With "Bidify exported JSON" ON: exported JSON contains bidi markers
7. With all toggles OFF: no behavioral change from current Inky
