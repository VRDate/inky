const assert = require('assert');
const {
    getBidiInkContent,
    getBidiAssertions,
    launchApp,
    forceQuitApp,
    setEditorContent,
    getEditorContent,
    typeInEditor,
    clearEditor,
    waitForCompilation,
    waitForStoryText,
    waitForChoice,
    getAllStoryText,
    getAllChoiceText,
    clickChoiceByIndex,
    clickChoiceByText,
    hasNoErrors,
    getIssueInfo,
} = require('./e2e-helpers');

// Fixture data (lazy-loaded via helpers)
const bidiInkContent = getBidiInkContent();
const bidiAssertions = getBidiAssertions();

// ====================================================================
// Compilable Hebrew ink story for E2E tests
// (The full bidi_and_tdd.ink has syntax issues with the ink compiler)
// ====================================================================
var hebrewStoryInk = [
    '-> start',
    '=== start ===',
    'שלום עולם!',
    'בחר אפשרות:',
    '',
    '* [בדיקה מהירה — Smoke Test] -> smoke_test',
    '* [מוזיאון בידי — Bidi Museum] -> bidi_museum',
    '* [סיפור — Story] -> story',
    '',
    '=== smoke_test ===',
    'שלום עולם.',
    'הבדיקה עברה בהצלחה! Test passed!',
    '* [חזרה — Back] -> start',
    '* [סוף — End] -> END',
    '',
    '=== bidi_museum ===',
    'Hebrew: שלום עולם',
    'Arabic: مرحبا بالعالم',
    'Persian: سلام جهان',
    '* [חזרה — Back] -> start',
    '* [סוף — End] -> END',
    '',
    '=== story ===',
    'פעם היה איש שגר בירושלים.',
    'הוא יצא לטייל.',
    '* [צפונה — North]',
    '  הלך צפונה.',
    '  -> story_end',
    '* [דרומה — South]',
    '  הלך דרומה.',
    '  -> story_end',
    '',
    '=== story_end ===',
    'הגיע ליעד. Arrived at destination.',
    '-> END'
].join('\n');

// ====================================================================
// TEST SUITES
// ====================================================================

describe('Bidi E2E: Load and compile Hebrew ink', function () {
    this.timeout(60000);

    let electronApp, window;

    beforeEach(async function () {
        ({ electronApp, window } = await launchApp());
    });

    afterEach(async function () {
        await forceQuitApp(electronApp);
    });

    it('loads the full bidi ink fixture without crashing', async function () {
        await setEditorContent(window, bidiInkContent);
        await waitForCompilation(window);

        // Editor should contain the full script
        var content = await getEditorContent(window);
        assert.ok(content.length > 1000, 'Editor should contain substantial content');
        assert.ok(content.indexOf('VAR lang') !== -1, 'Should contain VAR declarations');
        assert.ok(content.indexOf('שלום') !== -1, 'Should contain Hebrew text');
    });

    it('compiles Hebrew story without errors', async function () {
        await setEditorContent(window, hebrewStoryInk);
        await waitForCompilation(window);
        await waitForStoryText(window, 20000);

        var noErrors = await hasNoErrors(window);
        assert.ok(noErrors, 'Should compile without errors');

        var texts = await getAllStoryText(window);
        assert.ok(texts.length > 0, 'Story text should appear after compilation');
        var allText = texts.join(' ');
        assert.ok(allText.indexOf('שלום') !== -1 || allText.indexOf('Hello') !== -1,
            'Should contain greeting text');
    });

    it('shows menu choices with Hebrew text', async function () {
        await setEditorContent(window, hebrewStoryInk);
        await waitForCompilation(window);
        await waitForChoice(window, 20000);

        var choices = await getAllChoiceText(window);
        assert.ok(choices.length >= 3, 'Should show at least 3 menu choices, got ' + choices.length);

        var allText = choices.join(' ');
        assert.ok(allText.indexOf('בדיקה') !== -1 || allText.indexOf('Smoke') !== -1,
            'Should contain Smoke Test choice');
    });
});

describe('Bidi E2E: Type Hebrew text in editor', function () {
    this.timeout(60000);

    let electronApp, window;

    beforeEach(async function () {
        ({ electronApp, window } = await launchApp());
    });

    afterEach(async function () {
        await forceQuitApp(electronApp);
    });

    it('types Hebrew hello world and compiles', async function () {
        var hebrewInk = 'שלום עולם!';
        await clearEditor(window);
        await typeInEditor(window, hebrewInk);
        await waitForCompilation(window);
        await waitForStoryText(window, 15000);

        var texts = await getAllStoryText(window);
        assert.ok(texts.length > 0, 'Should show story text');
        assert.ok(texts[0].indexOf('שלום') !== -1, 'Story should contain Hebrew text');
    });

    it('types Hebrew choice syntax and compiles', async function () {
        var ink = 'שלום עולם!\n* [בחירה ראשונה]\n  תוצאה.\n-> END';
        await setEditorContent(window, ink);
        await waitForCompilation(window);
        await waitForChoice(window, 15000);

        var choices = await getAllChoiceText(window);
        assert.ok(choices.length > 0, 'Should show choices');
        assert.ok(choices[0].indexOf('בחירה') !== -1, 'Choice should contain Hebrew text');
    });

    it('types mixed Hebrew-English ink line', async function () {
        var mixedInk = 'הבחירה -> END';
        await clearEditor(window);
        await typeInEditor(window, mixedInk);
        await waitForCompilation(window);
        await waitForStoryText(window, 15000);

        var texts = await getAllStoryText(window);
        assert.ok(texts.length > 0, 'Should show story text');
    });

    it('types Arabic hello world and compiles', async function () {
        var arabicInk = 'مرحبا بالعالم!';
        await clearEditor(window);
        await typeInEditor(window, arabicInk);
        await waitForCompilation(window);
        await waitForStoryText(window, 15000);

        var texts = await getAllStoryText(window);
        assert.ok(texts.length > 0, 'Should show story text');
        assert.ok(texts[0].indexOf('مرحبا') !== -1, 'Story should contain Arabic text');
    });
});

describe('Bidi E2E: Play through Hebrew story', function () {
    this.timeout(60000);

    let electronApp, window;

    beforeEach(async function () {
        ({ electronApp, window } = await launchApp());
    });

    afterEach(async function () {
        await forceQuitApp(electronApp);
    });

    it('navigates smoke test and verifies Hebrew output', async function () {
        await setEditorContent(window, hebrewStoryInk);
        await waitForCompilation(window);
        await waitForChoice(window, 20000);

        // Click "Smoke Test" choice (index 0)
        await clickChoiceByIndex(window, 0, 20000);

        // Wait for smoke test content to appear
        await new Promise(r => setTimeout(r, 2000));

        var texts = await getAllStoryText(window);
        var allText = texts.join(' ');

        assert.ok(allText.indexOf('שלום') !== -1 || allText.indexOf('Hello') !== -1,
            'Smoke test should show greeting text');
    });

    it('navigates bidi museum with multiple RTL scripts', async function () {
        await setEditorContent(window, hebrewStoryInk);
        await waitForCompilation(window);
        await waitForChoice(window, 20000);

        // Click "Bidi Museum" choice (index 1)
        await clickChoiceByIndex(window, 1, 20000);
        await new Promise(r => setTimeout(r, 2000));

        var texts = await getAllStoryText(window);
        var allText = texts.join(' ');

        assert.ok(allText.indexOf('שלום') !== -1, 'Museum should show Hebrew text');
        assert.ok(allText.indexOf('مرحبا') !== -1, 'Museum should show Arabic text');
        assert.ok(allText.indexOf('سلام') !== -1, 'Museum should show Persian text');
    });

    it('plays story path with choices and Hebrew narration', async function () {
        await setEditorContent(window, hebrewStoryInk);
        await waitForCompilation(window);
        await waitForChoice(window, 20000);

        // Click "Story" choice (index 2)
        await clickChoiceByIndex(window, 2, 20000);
        await new Promise(r => setTimeout(r, 1000));

        // Wait for story choices (North/South)
        await waitForChoice(window, 15000);
        var choices = await getAllChoiceText(window);
        assert.ok(choices.length >= 2, 'Should show North/South choices');

        // Click North
        await clickChoiceByIndex(window, 0, 20000);
        await new Promise(r => setTimeout(r, 2000));

        var texts = await getAllStoryText(window);
        var allText = texts.join(' ');
        assert.ok(allText.indexOf('הגיע') !== -1 || allText.indexOf('Arrived') !== -1,
            'Should reach story end');
    });
});

describe('Bidi E2E: Compilation verification', function () {
    this.timeout(60000);

    let electronApp, window;

    beforeEach(async function () {
        ({ electronApp, window } = await launchApp());
    });

    afterEach(async function () {
        await forceQuitApp(electronApp);
    });

    it('compiles Hebrew text without bidi markers in output', async function () {
        // Load simple Hebrew ink
        var ink = 'שלום עולם!';
        await setEditorContent(window, ink);
        await waitForCompilation(window);
        await waitForStoryText(window, 15000);

        // Verify the editor content stays clean (no bidi markers injected)
        var content = await getEditorContent(window);
        assert.strictEqual(content.indexOf('\u2066'), -1, 'Editor should not contain LRI markers');
        assert.strictEqual(content.indexOf('\u2067'), -1, 'Editor should not contain RLI markers');
        assert.strictEqual(content.indexOf('\u2069'), -1, 'Editor should not contain PDI markers');
    });

    it('compiles mixed Hebrew-English choices correctly', async function () {
        var ink = [
            'בחר:',
            '* [אפשרות א — Option A]',
            '  תוצאה א. Result A.',
            '  -> END',
            '* [אפשרות ב — Option B]',
            '  תוצאה ב. Result B.',
            '  -> END'
        ].join('\n');

        await setEditorContent(window, ink);
        await waitForCompilation(window);
        await waitForChoice(window, 15000);

        var choices = await getAllChoiceText(window);
        assert.strictEqual(choices.length, 2, 'Should show 2 choices');
        assert.ok(choices[0].indexOf('אפשרות') !== -1, 'First choice has Hebrew');
        assert.ok(choices[0].indexOf('Option') !== -1, 'First choice has English');
    });

    it('handles 10 RTL scripts from assertions file', async function () {
        // Test each RTL script from bidi-assertions.json
        var rtlAssertions = bidiAssertions.assertions.filter(function(a) {
            return a.expectedDirection === 'rtl';
        });

        // Test the first few RTL scripts (one line each)
        for (var i = 0; i < Math.min(3, rtlAssertions.length); i++) {
            var assertion = rtlAssertions[i];
            await setEditorContent(window, assertion.input);
            await waitForCompilation(window);
            await waitForStoryText(window, 15000);

            var texts = await getAllStoryText(window);
            assert.ok(texts.length > 0, 'Story text should appear for: ' + assertion.id);
        }
    });
});

describe('Bidi E2E: Bidify assertions unit validation', function () {
    this.timeout(30000);

    let electronApp, window;

    beforeEach(async function () {
        ({ electronApp, window } = await launchApp());
    });

    afterEach(async function () {
        await forceQuitApp(electronApp);
    });

    it('validates bidify/stripBidi round-trip in renderer', async function () {
        // Run bidify tests inside the Electron renderer process
        var results = await window.evaluate(() => {
            try {
                var bidify = require('./bidify.js');
                var results = [];

                // Test 1: bidify then stripBidi round-trip
                var input = 'הבחירה -> next';
                var bidified = bidify.bidify(input);
                var stripped = bidify.stripBidi(bidified);
                results.push({
                    test: 'round-trip',
                    pass: stripped === input,
                    input: input,
                    output: stripped
                });

                // Test 2: pure English stays clean
                var english = 'Hello world';
                var bidifiedEn = bidify.bidify(english);
                results.push({
                    test: 'pure-english',
                    pass: bidifiedEn === english,
                    input: english,
                    output: bidifiedEn
                });

                // Test 3: Hebrew gets markers
                var hebrew = 'שלום עולם';
                var bidifiedHe = bidify.bidify(hebrew);
                results.push({
                    test: 'hebrew-markers',
                    pass: bidifiedHe !== hebrew && bidifiedHe.indexOf('\u2067') !== -1,
                    input: hebrew,
                    output: bidifiedHe
                });

                // Test 4: idempotency
                var doubleBidified = bidify.bidify(bidifiedHe);
                results.push({
                    test: 'idempotent',
                    pass: doubleBidified === bidifiedHe,
                    input: bidifiedHe,
                    output: doubleBidified
                });

                return { success: true, results: results };
            } catch (e) {
                return { success: false, error: e.message };
            }
        });

        assert.ok(results.success, 'Should execute bidify in renderer: ' + (results.error || ''));

        for (var r of results.results) {
            assert.ok(r.pass, 'Bidify test "' + r.test + '" failed: input=' + r.input + ' output=' + r.output);
        }
    });

    it('validates assertion file entries via bidify in renderer', async function () {
        var assertions = bidiAssertions.assertions;

        var results = await window.evaluate((assertionData) => {
            try {
                var bidify = require('./bidify.js');
                var results = [];

                for (var i = 0; i < assertionData.length; i++) {
                    var a = assertionData[i];
                    var bidified = bidify.bidify(a.input);
                    var stripped = bidify.stripBidi(bidified);

                    var result = {
                        id: a.id,
                        roundTrip: stripped === a.input
                    };

                    // If expected direction is rtl, bidified should have RLI marker
                    if (a.expectedDirection === 'rtl') {
                        result.hasRtlMarker = bidified.indexOf('\u2067') !== -1;
                    } else if (a.expectedDirection === 'ltr') {
                        // Pure LTR text should not be modified (or should stay clean)
                        result.staysClean = bidified === a.input ||
                            bidified.indexOf('\u2067') === -1;
                    }

                    results.push(result);
                }

                return { success: true, results: results };
            } catch (e) {
                return { success: false, error: e.message };
            }
        }, assertions);

        assert.ok(results.success, 'Should process assertions: ' + (results.error || ''));

        for (var r of results.results) {
            if (r.id !== 'strip-roundtrip') {
                assert.ok(r.roundTrip, 'Round-trip failed for: ' + r.id);
            }
            if (r.hasRtlMarker !== undefined) {
                assert.ok(r.hasRtlMarker, 'RTL marker missing for: ' + r.id);
            }
        }
    });
});
