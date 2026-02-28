const { _electron: electron } = require('playwright-core');
const assert = require('assert');
const path = require('path');

const electronBinary = path.join(__dirname, '..', 'node_modules', 'electron', 'dist', 'electron');
const mainScript = path.join(__dirname, '..', 'main-process', 'main.js');

// Helper: launch the Electron app and return { electronApp, window }
async function launchApp() {
    const electronApp = await electron.launch({
        executablePath: electronBinary,
        args: ['--no-sandbox', mainScript],
        env: { ...process.env, NODE_ENV: 'test' }
    });

    const window = await electronApp.firstWindow();
    // Wait for the app to be ready (ace editor loaded)
    await window.waitForSelector('#editor .ace_content', { timeout: 15000 });
    return { electronApp, window };
}

// Helper: force-quit the app without save dialogs
async function forceQuitApp(electronApp) {
    if (!electronApp) return;
    try {
        await electronApp.evaluate(({ app }) => { app.exit(0); });
    } catch (e) {
        // App already closed
    }
}

// Helper: set the Ace editor content
async function setEditorContent(window, text) {
    await window.evaluate((t) => {
        var editor = ace.edit("editor");
        editor.setValue(t, 1);
    }, text);
}

// Helper: wait for story text to appear in the player (uses 'attached' to
// ignore jQuery fade-in opacity which Playwright treats as not visible)
async function waitForStoryText(window, timeout) {
    timeout = timeout || 10000;
    await window.waitForSelector('#player .innerText.active .storyText',
        { state: 'attached', timeout: timeout });
}

// Helper: wait for a choice to appear in the player
async function waitForChoice(window, timeout) {
    timeout = timeout || 10000;
    await window.waitForSelector('#player .innerText.active .choice',
        { state: 'attached', timeout: timeout });
}

// Helper: click a choice and wait for subsequent story text by counting
// storyText elements in the DOM (bypasses visibility/opacity issues).
// Uses Playwright's real mouse click (force: true) because jQuery event
// handlers don't respond to native DOM .click() calls.
async function clickChoiceAndWaitForText(window, expectedCount, timeout) {
    timeout = timeout || 20000;
    // Wait for compilation to stabilize (the live compiler has an initial
    // setTimeout and a 250ms interval that may trigger recompiles)
    await window.waitForFunction(() => {
        var spinner = document.querySelector('.busySpinner');
        return !spinner || !spinner.classList.contains('active');
    }, { timeout: 5000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 1500));

    await window.click('#player .innerText.active .choice a', { force: true });
    // Wait until the expected number of .storyText elements exist
    await window.waitForFunction((count) => {
        var texts = document.querySelectorAll('#player .innerText.active .storyText');
        return texts.length >= count;
    }, expectedCount, { timeout: timeout });
}

// Helper: get text of nth storyText element (0-indexed)
async function getStoryTextAt(window, index) {
    return await window.evaluate((idx) => {
        var texts = document.querySelectorAll('#player .innerText.active .storyText');
        return texts[idx] ? texts[idx].textContent.trim() : null;
    }, index);
}

describe('application launch tests', function () {
    this.timeout(30000);

    let electronApp, window;

    beforeEach(async function () {
        ({ electronApp, window } = await launchApp());
    });

    afterEach(async function () {
        await forceQuitApp(electronApp);
    });

    it('shows an initial window', async function () {
        const windows = electronApp.windows();
        assert.strictEqual(windows.length, 1);
    });

    it('reads the title', async function () {
        const title = await window.textContent('.title');
        assert.strictEqual(title.trim(), 'Untitled.ink');
    });

    it('opens the menu', async function () {
        await window.click('.icon-menu');
        const sidebar = await window.waitForSelector('.sidebar:not(.hidden)', { timeout: 5000 });
        assert.ok(sidebar, 'sidebar should be visible after clicking menu');
    });
});

describe('compiles hello world game', function () {
    this.timeout(30000);

    let electronApp, window;

    beforeEach(async function () {
        ({ electronApp, window } = await launchApp());
    });

    afterEach(async function () {
        await forceQuitApp(electronApp);
    });

    it('writes and reads hello world', async function () {
        const input = 'Hello World!';
        await setEditorContent(window, input);
        await waitForStoryText(window);
        const text = await getStoryTextAt(window, 0);
        assert.strictEqual(text, input);
    });

    it('writes and selects a choice', async function () {
        const input = 'Hello World!\n* Hello back\n  Nice to hear from you!\n-> END';
        await setEditorContent(window, input);
        await waitForChoice(window);

        // Click choice and wait for 3 story text elements (original + choice echo + continuation)
        await clickChoiceAndWaitForText(window, 3);

        const choiceText = await getStoryTextAt(window, 1);
        assert.strictEqual(choiceText, 'Hello back');

        const answerText = await getStoryTextAt(window, 2);
        assert.strictEqual(answerText, 'Nice to hear from you!');
    });

    it('suppresses choice text', async function () {
        const input = 'Hello World!\n* [Hello back]\n  Nice to hear from you!\n-> END';
        await setEditorContent(window, input);
        await waitForChoice(window);

        // With suppressed choice text, only 2 story text elements (original + continuation)
        await clickChoiceAndWaitForText(window, 2);

        const answerText = await getStoryTextAt(window, 1);
        assert.strictEqual(answerText, 'Nice to hear from you!');
    });

    it('shows TODOs', async function () {
        const input = '-\n* Rock\n* Paper\n* Scissors\nTODO: Make this more interesting';
        await setEditorContent(window, input);

        // When issues exist, .issuesMessage becomes hidden and .issuesSummary is shown.
        // Check that the issue summary becomes visible (indicating TODOs were detected).
        await window.waitForFunction(() => {
            var summary = document.querySelector('.issuesSummary');
            return summary && !summary.classList.contains('hidden');
        }, { timeout: 15000 });

        // Verify the TODO count is displayed
        const todoVisible = await window.evaluate(() => {
            var todoEl = document.querySelector('.issueCount.todo');
            return todoEl && todoEl.style.display !== 'none';
        });
        assert.ok(todoVisible, 'TODO issue count should be visible');
    });
});
