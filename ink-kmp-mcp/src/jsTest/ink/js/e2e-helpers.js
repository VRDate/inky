/**
 * Shared E2E test helpers — companion object pattern (mirrors KtTestFixtures).
 *
 * Extracts Electron/Playwright helper functions that were inline in
 * bidi-e2e.test.js into a reusable module.
 *
 * Provides: launchApp, forceQuitApp, editor manipulation, story interaction,
 * compilation helpers, and fixture paths.
 */

const { _electron: electron } = require('playwright-core');
const path = require('path');
const fs = require('fs');

// ═══════════════════════════════════════════════════════════════
// FIXTURE PATHS
// ═══════════════════════════════════════════════════════════════

const projectRoot = path.join(__dirname, '..');
const electronBinary = path.join(projectRoot, 'node_modules', 'electron', 'dist', 'electron');
const mainScript = path.join(projectRoot, 'main-process', 'main.js');
const bidiInkPath = path.join(__dirname, 'fixtures', 'bidi_and_tdd.ink');
const assertionsPath = path.join(__dirname, 'fixtures', 'bidi-assertions.json');

// ═══════════════════════════════════════════════════════════════
// FIXTURE DATA (lazy-loaded)
// ═══════════════════════════════════════════════════════════════

let _bidiInkContent = null;
let _bidiAssertions = null;

function getBidiInkContent() {
    if (!_bidiInkContent) _bidiInkContent = fs.readFileSync(bidiInkPath, 'utf8');
    return _bidiInkContent;
}

function getBidiAssertions() {
    if (!_bidiAssertions) _bidiAssertions = JSON.parse(fs.readFileSync(assertionsPath, 'utf8'));
    return _bidiAssertions;
}

// ═══════════════════════════════════════════════════════════════
// APP LIFECYCLE
// ═══════════════════════════════════════════════════════════════

/** Launch the Electron app and return { electronApp, window }. */
async function launchApp() {
    const electronApp = await electron.launch({
        executablePath: electronBinary,
        args: ['--no-sandbox', mainScript],
        env: { ...process.env, NODE_ENV: 'test' }
    });

    const window = await electronApp.firstWindow();
    await window.waitForSelector('#editor .ace_content', { timeout: 15000 });
    return { electronApp, window };
}

/** Force-quit the app without save dialogs. */
async function forceQuitApp(electronApp) {
    if (!electronApp) return;
    try {
        await electronApp.evaluate(({ app }) => { app.exit(0); });
    } catch (e) {
        // App already closed
    }
}

// ═══════════════════════════════════════════════════════════════
// EDITOR HELPERS
// ═══════════════════════════════════════════════════════════════

/** Set the Ace editor content programmatically. */
async function setEditorContent(window, text) {
    await window.evaluate((t) => {
        var editor = ace.edit("editor");
        editor.setValue(t, 1);
    }, text);
}

/** Get Ace editor content. */
async function getEditorContent(window) {
    return await window.evaluate(() => {
        var editor = ace.edit("editor");
        return editor.getValue();
    });
}

/** Type text into Ace editor using keyboard (simulates real typing). */
async function typeInEditor(window, text) {
    await window.click('#editor .ace_content');
    await new Promise(r => setTimeout(r, 200));

    for (var i = 0; i < text.length; i++) {
        var ch = text[i];
        if (ch === '\n') {
            await window.keyboard.press('Enter');
        } else if (ch === '\t') {
            await window.keyboard.press('Tab');
        } else {
            await window.keyboard.type(ch, { delay: 0 });
        }
    }
}

/** Clear the editor. */
async function clearEditor(window) {
    await window.evaluate(() => {
        var editor = ace.edit("editor");
        editor.setValue('', 1);
    });
}

// ═══════════════════════════════════════════════════════════════
// COMPILATION / STORY HELPERS
// ═══════════════════════════════════════════════════════════════

/** Wait for compilation to complete (spinner stops). */
async function waitForCompilation(window, timeout) {
    timeout = timeout || 30000;
    await window.waitForFunction(() => {
        var spinner = document.querySelector('.busySpinner');
        return !spinner || !spinner.classList.contains('active');
    }, { timeout: timeout }).catch(() => {});
    await new Promise(r => setTimeout(r, 2000));
}

/** Wait for story text to appear in the player. */
async function waitForStoryText(window, timeout) {
    timeout = timeout || 15000;
    await window.waitForSelector('#player .innerText.active .storyText',
        { state: 'attached', timeout: timeout });
}

/** Wait for a choice to appear in the player. */
async function waitForChoice(window, timeout) {
    timeout = timeout || 15000;
    await window.waitForSelector('#player .innerText.active .choice',
        { state: 'attached', timeout: timeout });
}

/** Get all story text content. */
async function getAllStoryText(window) {
    return await window.evaluate(() => {
        var texts = document.querySelectorAll('#player .innerText.active .storyText');
        var result = [];
        for (var i = 0; i < texts.length; i++) {
            result.push(texts[i].textContent.trim());
        }
        return result;
    });
}

/** Get all choice text. */
async function getAllChoiceText(window) {
    return await window.evaluate(() => {
        var choices = document.querySelectorAll('#player .innerText.active .choice a');
        var result = [];
        for (var i = 0; i < choices.length; i++) {
            result.push(choices[i].textContent.trim());
        }
        return result;
    });
}

/** Click a choice by index and wait for subsequent content. */
async function clickChoiceByIndex(window, index, timeout) {
    timeout = timeout || 20000;
    await window.waitForFunction(() => {
        var spinner = document.querySelector('.busySpinner');
        return !spinner || !spinner.classList.contains('active');
    }, { timeout: 5000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 1500));

    var selector = '#player .innerText.active .choice a';
    var choices = await window.$$(selector);
    if (index >= choices.length) {
        throw new Error('Choice index ' + index + ' out of range (only ' + choices.length + ' choices)');
    }

    var currentCount = await window.evaluate(() => {
        return document.querySelectorAll('#player .innerText.active .storyText').length;
    });

    await choices[index].click({ force: true });

    await window.waitForFunction((prevCount) => {
        var texts = document.querySelectorAll('#player .innerText.active .storyText');
        return texts.length > prevCount;
    }, currentCount, { timeout: timeout });
}

/** Click a choice by matching text. */
async function clickChoiceByText(window, textMatch, timeout) {
    timeout = timeout || 20000;
    await window.waitForFunction(() => {
        var spinner = document.querySelector('.busySpinner');
        return !spinner || !spinner.classList.contains('active');
    }, { timeout: 5000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 1500));

    var currentCount = await window.evaluate(() => {
        return document.querySelectorAll('#player .innerText.active .storyText').length;
    });

    var clicked = await window.evaluate((text) => {
        var choices = document.querySelectorAll('#player .innerText.active .choice a');
        for (var i = 0; i < choices.length; i++) {
            if (choices[i].textContent.indexOf(text) !== -1) {
                var $a = window.jQuery ? jQuery(choices[i]) : null;
                if ($a) {
                    $a.trigger('click');
                    return true;
                }
            }
        }
        return false;
    }, textMatch);

    if (!clicked) {
        var choiceLinks = await window.$$('#player .innerText.active .choice a');
        for (var link of choiceLinks) {
            var linkText = await link.textContent();
            if (linkText.indexOf(textMatch) !== -1) {
                await link.click({ force: true });
                clicked = true;
                break;
            }
        }
    }

    if (!clicked) {
        throw new Error('No choice found matching: ' + textMatch);
    }

    await window.waitForFunction((prevCount) => {
        var texts = document.querySelectorAll('#player .innerText.active .storyText');
        return texts.length > prevCount;
    }, currentCount, { timeout: timeout }).catch(() => {});
}

/** Check no compilation errors. */
async function hasNoErrors(window) {
    return await window.evaluate(() => {
        var errorElements = document.querySelectorAll('#player .innerText.active .error');
        return errorElements.length === 0;
    });
}

/** Get issue count info. */
async function getIssueInfo(window) {
    return await window.evaluate(() => {
        var errorCount = document.querySelector('.issueCount.error');
        var warningCount = document.querySelector('.issueCount.warning');
        var todoCount = document.querySelector('.issueCount.todo');
        return {
            errors: errorCount ? errorCount.textContent.trim() : '0',
            errorsVisible: errorCount ? errorCount.style.display !== 'none' : false,
            warnings: warningCount ? warningCount.textContent.trim() : '0',
            warningsVisible: warningCount ? warningCount.style.display !== 'none' : false,
            todos: todoCount ? todoCount.textContent.trim() : '0',
            todosVisible: todoCount ? todoCount.style.display !== 'none' : false
        };
    });
}

// ═══════════════════════════════════════════════════════════════
// EXPORTS
// ═══════════════════════════════════════════════════════════════

module.exports = {
    // Paths
    projectRoot,
    electronBinary,
    mainScript,
    bidiInkPath,
    assertionsPath,
    // Fixture data
    getBidiInkContent,
    getBidiAssertions,
    // App lifecycle
    launchApp,
    forceQuitApp,
    // Editor
    setEditorContent,
    getEditorContent,
    typeInEditor,
    clearEditor,
    // Compilation / story
    waitForCompilation,
    waitForStoryText,
    waitForChoice,
    getAllStoryText,
    getAllChoiceText,
    clickChoiceByIndex,
    clickChoiceByText,
    hasNoErrors,
    getIssueInfo,
};
