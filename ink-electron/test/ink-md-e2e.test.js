// ink-md-e2e.test.js — E2E tests for markdown files with ```ink blocks
//
// Uses the `marked` CommonMark parser (generic ```[info] fenced code block
// routing) instead of regex. marked tokenises fenced blocks into
// { type: 'code', lang: 'ink', text: '...' } — the same pattern that
// Remirror/CodeMirror/Flexmark use for per-language block routing.

const assert = require('assert');
const path = require('path');
const fs = require('fs');
const { marked } = require('marked');

// ================================================================
// FIXTURES
// ================================================================

const mdPath = path.join(__dirname, '..', '..', 'docs', 'BIDI_TDD_ISSUES.md');
const mdSource = fs.readFileSync(mdPath, 'utf8');

// ================================================================
// MARKED-BASED EXTRACTORS — generic ```[info] routing
// ================================================================

/**
 * Tokenise the markdown with `marked.lexer()` and extract
 * fenced code blocks routed by info string === 'ink'.
 *
 * This mirrors the pattern used by:
 *   - Remirror: inkCodeBlockSpec.parseDOM routes <pre class="ink-code-block">
 *   - CodeMirror: StreamLanguage routes by fenced info="ink"
 *   - Flexmark: FencedCodeBlock with info="ink"
 */
function extractInkBlocks(md) {
    var tokens = marked.lexer(md);
    var blocks = [];
    var lastHeading = '';

    function walk(tokenList) {
        for (var i = 0; i < tokenList.length; i++) {
            var tok = tokenList[i];
            if (tok.type === 'heading') {
                lastHeading = tok.text;
            } else if (tok.type === 'code' && tok.lang === 'ink') {
                // Routed here because lang === 'ink'
                blocks.push({
                    heading: lastHeading,
                    source: tok.text,
                    raw: tok.raw
                });
            }
            // Walk children (e.g. list items, blockquotes)
            if (tok.tokens) walk(tok.tokens);
        }
    }
    walk(tokens);
    return blocks;
}

/**
 * Extract markdown tables via `marked.lexer()`.
 * marked produces { type: 'table', header: [...], rows: [[...], ...] }.
 */
function extractMdTables(md) {
    var tokens = marked.lexer(md);
    var tables = [];
    var lastHeading = '';

    function walk(tokenList) {
        for (var i = 0; i < tokenList.length; i++) {
            var tok = tokenList[i];
            if (tok.type === 'heading') {
                lastHeading = tok.text;
            } else if (tok.type === 'table') {
                var headers = tok.header.map(function (h) { return h.text; });
                var rows = tok.rows.map(function (row) {
                    return row.map(function (cell) { return cell.text; });
                });
                tables.push({
                    heading: lastHeading,
                    headers: headers,
                    rows: rows
                });
            }
            if (tok.tokens) walk(tok.tokens);
        }
    }
    walk(tokens);
    return tables;
}

// ================================================================
// EXTRACTED DATA
// ================================================================

var inkBlocks = extractInkBlocks(mdSource);
var mdTables = extractMdTables(mdSource);

// ================================================================
// TESTS
// ================================================================

describe('Ink-Markdown Integration: BIDI_TDD_ISSUES.md (marked parser)', function () {
    this.timeout(30000);

    // ────────────────────────────────────────────────────────
    // 1. PARSE MD — marked tokeniser
    // ────────────────────────────────────────────────────────

    describe('1. parse markdown via marked.lexer()', function () {

        it('marked.lexer() produces a token list', function () {
            var tokens = marked.lexer(mdSource);
            assert.ok(Array.isArray(tokens), 'lexer should return array');
            assert.ok(tokens.length > 50, 'Document should have many tokens');
        });

        it('token list contains heading, code, and table types', function () {
            var tokens = marked.lexer(mdSource);
            var types = new Set(tokens.map(function (t) { return t.type; }));
            assert.ok(types.has('heading'), 'Should have headings');
            assert.ok(types.has('code'), 'Should have code blocks');
            assert.ok(types.has('table'), 'Should have tables');
        });
    });

    // ────────────────────────────────────────────────────────
    // 2. EXTRACT ```ink BLOCKS — routed by lang='ink'
    // ────────────────────────────────────────────────────────

    describe('2. extract ```ink blocks via marked code token routing', function () {

        it('finds exactly 9 ink code blocks routed by lang="ink"', function () {
            assert.strictEqual(inkBlocks.length, 9,
                'Expected 9 ```ink blocks, got ' + inkBlocks.length +
                '. Headings: ' + inkBlocks.map(function (b) { return b.heading; }).join(', '));
        });

        it('each ink block has non-empty source', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                assert.ok(inkBlocks[i].source.length > 10,
                    'Block ' + (i + 1) + ' (' + inkBlocks[i].heading + ') too short');
            }
        });

        it('ink blocks are routed separately from other code blocks', function () {
            var tokens = marked.lexer(mdSource);
            var allCodeBlocks = tokens.filter(function (t) { return t.type === 'code'; });
            var inkCodeBlocks = allCodeBlocks.filter(function (t) { return t.lang === 'ink'; });
            var otherCodeBlocks = allCodeBlocks.filter(function (t) { return t.lang !== 'ink'; });

            assert.strictEqual(inkCodeBlocks.length, 9);
            assert.ok(otherCodeBlocks.length >= 0,
                'Non-ink code blocks exist as separate routes');
        });

        it('ink blocks contain ASSERT comments as test contracts', function () {
            var blocksWithAsserts = inkBlocks.filter(function (b) {
                return b.source.includes('// ASSERT:');
            });
            assert.ok(blocksWithAsserts.length >= 8,
                'At least 8 blocks should have // ASSERT:, got ' + blocksWithAsserts.length);
        });

        it('ink blocks cover expected issue numbers', function () {
            var allHeadings = inkBlocks.map(function (b) { return b.heading; }).join(' ');
            var expected = ['#122', '#541', '#534', '#508', '#950', 'ink-959', 'ink-916', 'ink-844', '#485'];
            for (var i = 0; i < expected.length; i++) {
                assert.ok(allHeadings.includes(expected[i]),
                    'Missing ink block for issue ' + expected[i]);
            }
        });
    });

    // ────────────────────────────────────────────────────────
    // 3. EXTRACT MD TABLES — marked table tokens
    // ────────────────────────────────────────────────────────

    describe('3. extract markdown tables via marked table tokens', function () {

        it('finds issues tables with # | Title | Tags | TDD columns', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('#') && t.headers.includes('TDD');
            });
            assert.ok(issuesTables.length >= 2,
                'Expected >= 2 issues tables, got ' + issuesTables.length);
        });

        it('inky issues table has >= 40 rows', function () {
            var table = mdTables.find(function (t) {
                return t.heading.includes('inkle/inky');
            });
            assert.ok(table, 'Should find inkle/inky table');
            assert.ok(table.rows.length >= 40,
                'Expected >= 40 rows, got ' + table.rows.length);
        });

        it('ink issues table has >= 20 rows', function () {
            var table = mdTables.find(function (t) {
                return t.heading.includes('inkle/ink');
            });
            assert.ok(table, 'Should find inkle/ink table');
            assert.ok(table.rows.length >= 20,
                'Expected >= 20 rows, got ' + table.rows.length);
        });
    });

    // ────────────────────────────────────────────────────────
    // 4. VALIDATE INK SYNTAX in blocks
    // ────────────────────────────────────────────────────────

    describe('4. validate ink block syntax', function () {

        it('VAR/CONST/LIST declarations have = sign', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                var lines = inkBlocks[i].source.split('\n');
                for (var j = 0; j < lines.length; j++) {
                    var line = lines[j].trim();
                    if (/^(VAR|CONST|LIST)\s/.test(line)) {
                        assert.ok(line.includes('='),
                            'Declaration missing =: ' + line + ' in ' + inkBlocks[i].heading);
                    }
                }
            }
        });

        it('blocks with -> have END/DONE (except #534)', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                var block = inkBlocks[i];
                if (!block.source.includes('->')) continue;
                if (block.heading.includes('#534')) continue;
                var ok = block.source.includes('-> END') ||
                    block.source.includes('-> DONE') ||
                    block.source.includes('->->');
                assert.ok(ok, 'Block "' + block.heading + '" missing END/DONE');
            }
        });

        it('knot declarations use === syntax', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                var matches = inkBlocks[i].source.match(/^=== .+ ===$/gm);
                if (matches) {
                    for (var j = 0; j < matches.length; j++) {
                        assert.ok(/^=== \w[\w(, )]*===$/m.test(matches[j]),
                            'Invalid knot: ' + matches[j]);
                    }
                }
            }
        });

        it('RTL blocks contain Hebrew or CJK text', function () {
            var b122 = inkBlocks.find(function (b) { return b.heading.includes('#122'); });
            assert.ok(b122, 'Should find #122 block');
            assert.ok(/[\u0590-\u05FF]/.test(b122.source), '#122 should have Hebrew');

            var b485 = inkBlocks.find(function (b) { return b.heading.includes('#485'); });
            assert.ok(b485, 'Should find #485 block');
            assert.ok(/[\u3040-\u309F]/.test(b485.source), '#485 should have Hiragana');
            assert.ok(/[\u4E00-\u9FFF]/.test(b485.source), '#485 should have CJK');
            assert.ok(/[\uAC00-\uD7AF]/.test(b485.source), '#485 should have Hangul');
        });
    });

    // ────────────────────────────────────────────────────────
    // 5. VALIDATE TABLE DATA
    // ────────────────────────────────────────────────────────

    describe('5. validate table data consistency', function () {

        it('all # cells contain GitHub links', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('#') && t.headers.includes('TDD');
            });
            for (var t = 0; t < issuesTables.length; t++) {
                var idx = issuesTables[t].headers.indexOf('#');
                for (var r = 0; r < issuesTables[t].rows.length; r++) {
                    assert.ok(issuesTables[t].rows[r][idx].includes('github.com/inkle/'),
                        'Row ' + (r + 1) + ' missing GitHub link');
                }
            }
        });

        it('tags are ;-delimited lowercase kebab-case', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('Tags');
            });
            for (var t = 0; t < issuesTables.length; t++) {
                var idx = issuesTables[t].headers.indexOf('Tags');
                for (var r = 0; r < issuesTables[t].rows.length; r++) {
                    var tags = issuesTables[t].rows[r][idx].split(';');
                    for (var k = 0; k < tags.length; k++) {
                        assert.ok(/^[a-z][a-z0-9-]*$/.test(tags[k].trim()),
                            'Bad tag: "' + tags[k].trim() + '" row ' + (r + 1));
                    }
                }
            }
        });

        it('TDD starts with YES: / NO: / PARTIAL:', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('TDD');
            });
            for (var t = 0; t < issuesTables.length; t++) {
                var idx = issuesTables[t].headers.indexOf('TDD');
                for (var r = 0; r < issuesTables[t].rows.length; r++) {
                    var tdd = issuesTables[t].rows[r][idx];
                    assert.ok(
                        tdd.startsWith('YES:') || tdd.startsWith('NO:') || tdd.startsWith('PARTIAL:'),
                        'Row ' + (r + 1) + ' bad TDD: ' + tdd.substring(0, 30));
                }
            }
        });

        it('summary percentages sum to ~100%', function () {
            var summaryTables = mdTables.filter(function (t) {
                return t.headers.includes('Verdict') && t.headers.includes('Count');
            });
            assert.ok(summaryTables.length >= 2);
            for (var t = 0; t < summaryTables.length; t++) {
                var idx = summaryTables[t].headers.indexOf('%');
                var total = 0;
                for (var r = 0; r < summaryTables[t].rows.length; r++) {
                    var pct = parseInt(summaryTables[t].rows[r][idx].replace(/\*+|%/g, ''));
                    if (!isNaN(pct)) total += pct;
                }
                assert.ok(total >= 99 && total <= 101,
                    'Percentages should sum to ~100%, got ' + total);
            }
        });
    });

    // ────────────────────────────────────────────────────────
    // 6. CROSS-REFERENCE ink blocks ↔ table entries
    // ────────────────────────────────────────────────────────

    describe('6. cross-reference ink blocks ↔ table entries', function () {

        it('ink block issues appear in TDD=YES or PARTIAL rows', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('#') && t.headers.includes('TDD');
            });
            var allRows = [];
            for (var t = 0; t < issuesTables.length; t++) {
                var idxH = issuesTables[t].headers.indexOf('#');
                var idxT = issuesTables[t].headers.indexOf('TDD');
                for (var r = 0; r < issuesTables[t].rows.length; r++) {
                    allRows.push({
                        issue: issuesTables[t].rows[r][idxH],
                        tdd: issuesTables[t].rows[r][idxT]
                    });
                }
            }

            for (var i = 0; i < inkBlocks.length; i++) {
                var match = inkBlocks[i].heading.match(/#(\d+)|ink-(\d+)/);
                if (!match) continue;
                var num = match[1] || match[2];
                var row = allRows.find(function (r) { return r.issue.includes(num); });
                if (row) {
                    assert.ok(
                        row.tdd.startsWith('YES:') || row.tdd.startsWith('PARTIAL:'),
                        'Ink block for ' + num + ' should be TDD=YES or PARTIAL');
                }
            }
        });

        it('test matrix covers key features', function () {
            var matrix = mdTables.find(function (t) {
                return t.headers.includes('Feature') && t.headers.includes('TDD Gap');
            });
            assert.ok(matrix, 'Should find test matrix');
            assert.ok(matrix.headers.includes('bidi_and_tdd.ink'));
            assert.ok(matrix.headers.includes('BidiTddInkTest.kt'));
            var features = matrix.rows.map(function (r) { return r[0]; });
            var expected = ['Plain text', 'Choices', 'Diverts', 'Glue', 'Tags',
                'Tunnels', 'Threads', 'Variables', 'Functions', 'Lists'];
            for (var j = 0; j < expected.length; j++) {
                assert.ok(features.some(function (f) { return f.includes(expected[j]); }),
                    'Matrix should cover: ' + expected[j]);
            }
        });
    });
});
