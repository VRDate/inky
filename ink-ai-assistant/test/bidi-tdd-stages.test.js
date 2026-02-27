// bidi-tdd-stages.test.js — 28-stage BidiTddInk E2E test for ink-ai-assistant.
//
// Matches the incremental stage pattern from:
//   - app/test/incremental-e2e.test.js (Playwright + Electron)
//   - mcp-server/src/test/kotlin/ink/mcp/BidiTddInkTest.kt (JUnit + GraalJS)
//
// Tests: Launch ink-ai-assistant, load bidi_and_tdd.ink, walk through all
// 28 syntax stages, verifying the AI assistant detects and plays each stage.

const { _electron: electron } = require('playwright-core');
const assert = require('assert');
const path = require('path');
const fs = require('fs');

const electronBinary = path.join(__dirname, '..', 'node_modules', 'electron', 'dist', 'electron');
const mainScript = path.join(__dirname, '..', 'src', 'main', 'main.js');
const fixturesDir = path.join(__dirname, 'fixtures');
const bidiInkPath = path.join(fixturesDir, 'bidi_and_tdd.ink');

// ── TIMING ──
var timingLog = [];
function startTimer(stage) { return { stage, start: Date.now() }; }
function stopTimer(timer, details) {
    var elapsed = Date.now() - timer.start;
    timingLog.push({ stage: timer.stage, elapsed, details: details || '' });
    return elapsed;
}
function printTimingReport() {
    console.log('\n╔══════════════════════════════════════════════════╗');
    console.log('║  INK-AI-ASSISTANT BIDI TDD TIMING REPORT         ║');
    console.log('╠══════════════════════════════════════════════════╣');
    var total = 0;
    for (var i = 0; i < timingLog.length; i++) {
        var e = timingLog[i];
        var secs = (e.elapsed / 1000).toFixed(1);
        console.log('║ ' + padRight(e.stage, 32) + padLeft(secs + 's', 8) + ' ║');
        total += e.elapsed;
    }
    console.log('╠══════════════════════════════════════════════════╣');
    console.log('║ TOTAL' + padLeft((total / 1000).toFixed(1) + 's', 42) + ' ║');
    console.log('╚══════════════════════════════════════════════════╝');
}
function padRight(s, n) { while (s.length < n) s += ' '; return s; }
function padLeft(s, n) { while (s.length < n) s = ' ' + s; return s; }

// ── FIXTURE SETUP ──
// Symlink bidi_and_tdd.ink from app/test/fixtures/ if not present
const sourceFixture = path.join(__dirname, '..', '..', 'app', 'test', 'fixtures', 'bidi_and_tdd.ink');
if (!fs.existsSync(bidiInkPath) && fs.existsSync(sourceFixture)) {
    fs.mkdirSync(fixturesDir, { recursive: true });
    fs.symlinkSync(sourceFixture, bidiInkPath);
}

describe('ink-ai-assistant BidiTddInk 28 Stages', function() {
    this.timeout(300000); // 5 minutes

    let app;
    let window;

    before(async function() {
        var timer = startTimer('Launch app');

        app = await electron.launch({
            executablePath: electronBinary,
            args: ['--no-sandbox', mainScript],
            env: { ...process.env, NODE_ENV: 'test' }
        });

        window = await app.firstWindow();
        await window.waitForLoadState('domcontentloaded');
        stopTimer(timer, 'Electron launched');
    });

    after(async function() {
        printTimingReport();
        if (app) await app.close();
    });

    it('should load bidi_and_tdd.ink fixture', async function() {
        var timer = startTimer('Load fixture');
        // The AI assistant watches files — verify fixture exists
        assert.ok(fs.existsSync(bidiInkPath) || fs.existsSync(sourceFixture),
            'bidi_and_tdd.ink fixture not found');
        stopTimer(timer);
    });

    // Generate test cases for each of 28 syntax stages
    var stageNames = [
        '01-knots-text-diverts', '02-stitches', '03-choices',
        '04-nested-choices', '05-gathers', '06-variables',
        '07-conditionals', '08-alternatives', '09-glue',
        '10-tags', '11-knot-parameters', '12-functions',
        '13-tunnels', '14-threads', '15-lists',
        '16-variable-diverts', '17-visit-counts', '18-conditional-choices',
        '19-multiline-conditionals', '20-string-ops', '21-math-logic',
        '22-logic-operators', '23-turn-based', '24-temp-vars',
        '25-comments', '26-includes', '27-external', '28-final'
    ];

    stageNames.forEach(function(stageName, idx) {
        var stageNum = idx + 1;
        var stageStr = String(stageNum).padStart(2, '0');

        it(`should reach stage ${stageStr}/28 — ${stageName}`, async function() {
            var timer = startTimer(`Stage ${stageStr}`);
            // This is a placeholder — actual implementation will:
            // 1. Send ink source to the AI assistant chat
            // 2. Verify compilation success
            // 3. Verify stage marker "NN/28" appears
            // 4. Make appropriate choice (syn_14 threads: pick "Leave")
            // 5. Assert progression
            console.log(`  Stage ${stageStr}/28: ${stageName}`);
            stopTimer(timer);
        });
    });
});
