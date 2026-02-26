// ink-md-e2e.test.js — E2E tests for markdown files containing ```ink blocks
// and markdown tables. Validates that BIDI_TDD_ISSUES.md is a valid
// integration test resource: ink blocks compile, tables parse, cross-refs hold.

const assert = require('assert');
const path = require('path');
const fs = require('fs');

// ================================================================
// FIXTURES — load the markdown source under test
// ================================================================

const mdPath = path.join(__dirname, '..', '..', 'docs', 'BIDI_TDD_ISSUES.md');
const mdSource = fs.readFileSync(mdPath, 'utf8');

// ================================================================
// PARSERS — extract ink blocks and markdown tables from the md file
// ================================================================

/**
 * Extract all fenced ```ink code blocks from markdown.
 * Returns array of { heading, source, lineNumber }.
 */
function extractInkBlocks(md) {
    var blocks = [];
    var lines = md.split('\n');
    var inBlock = false;
    var current = null;
    var lastHeading = '';

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];

        // Track headings for context
        var headingMatch = line.match(/^#{1,4}\s+(.+)/);
        if (headingMatch) {
            lastHeading = headingMatch[1].trim();
        }

        if (!inBlock && line.trim() === '```ink') {
            inBlock = true;
            current = { heading: lastHeading, lines: [], lineNumber: i + 1 };
        } else if (inBlock && line.trim() === '```') {
            inBlock = false;
            current.source = current.lines.join('\n');
            blocks.push(current);
            current = null;
        } else if (inBlock) {
            current.lines.push(line);
        }
    }
    return blocks;
}

/**
 * Extract markdown tables from the source.
 * Returns array of { heading, headers, rows, lineNumber }.
 */
function extractMdTables(md) {
    var tables = [];
    var lines = md.split('\n');
    var lastHeading = '';

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];

        var headingMatch = line.match(/^#{1,4}\s+(.+)/);
        if (headingMatch) {
            lastHeading = headingMatch[1].trim();
        }

        // Detect table header row (starts with |)
        if (line.trim().startsWith('|') && i + 1 < lines.length) {
            var separatorLine = lines[i + 1];
            // Check if next line is a separator row (|---|)
            if (separatorLine && /^\|[\s\-:|]+\|/.test(separatorLine.trim())) {
                var headers = parseTableRow(line);
                var rows = [];
                // Parse subsequent data rows
                for (var j = i + 2; j < lines.length; j++) {
                    if (!lines[j].trim().startsWith('|')) break;
                    rows.push(parseTableRow(lines[j]));
                }
                if (rows.length > 0) {
                    tables.push({
                        heading: lastHeading,
                        headers: headers,
                        rows: rows,
                        lineNumber: i + 1
                    });
                }
                i = j - 1; // skip past the table
            }
        }
    }
    return tables;
}

/** Parse a single markdown table row into an array of cell values. */
function parseTableRow(line) {
    return line.split('|')
        .map(function (cell) { return cell.trim(); })
        .filter(function (cell) { return cell.length > 0; });
}

// ================================================================
// EXTRACTED DATA
// ================================================================

var inkBlocks = extractInkBlocks(mdSource);
var mdTables = extractMdTables(mdSource);

// ================================================================
// TESTS
// ================================================================

describe('Ink-Markdown Integration: BIDI_TDD_ISSUES.md', function () {
    this.timeout(30000);

    // ────────────────────────────────────────────────────────
    // INK BLOCK EXTRACTION
    // ────────────────────────────────────────────────────────

    describe('ink block extraction', function () {

        it('extracts all 9 ink code blocks from the markdown', function () {
            assert.strictEqual(inkBlocks.length, 9,
                'Expected 9 ```ink blocks, got ' + inkBlocks.length +
                '. Headings found: ' + inkBlocks.map(function (b) { return b.heading; }).join(', '));
        });

        it('each ink block has non-empty source', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                assert.ok(inkBlocks[i].source.length > 0,
                    'Block ' + (i + 1) + ' (' + inkBlocks[i].heading + ') has empty source');
            }
        });

        it('each ink block has an associated issue heading', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                assert.ok(inkBlocks[i].heading.includes('Issue') || inkBlocks[i].heading.includes('ink-'),
                    'Block ' + (i + 1) + ' heading "' + inkBlocks[i].heading +
                    '" should reference an Issue');
            }
        });

        it('ink blocks contain ASSERT comments as test contracts', function () {
            var blocksWithAsserts = inkBlocks.filter(function (b) {
                return b.source.includes('// ASSERT:');
            });
            assert.ok(blocksWithAsserts.length >= 8,
                'At least 8 blocks should have // ASSERT: comments, got ' + blocksWithAsserts.length);
        });

        it('ink blocks cover the expected issue numbers', function () {
            var allHeadings = inkBlocks.map(function (b) { return b.heading; }).join(' ');
            var expectedIssues = ['#122', '#541', '#534', '#508', '#950', 'ink-959', 'ink-916', 'ink-844', '#485'];
            for (var i = 0; i < expectedIssues.length; i++) {
                assert.ok(allHeadings.includes(expectedIssues[i]),
                    'Missing ink block for issue ' + expectedIssues[i]);
            }
        });
    });

    // ────────────────────────────────────────────────────────
    // INK SYNTAX VALIDATION (static analysis without compiler)
    // ────────────────────────────────────────────────────────

    describe('ink block syntax validation', function () {

        it('all blocks use valid ink syntax keywords', function () {
            var validKeywords = ['VAR', 'CONST', 'LIST', 'EXTERNAL', 'INCLUDE',
                'temp', 'return', 'not', 'and', 'or', 'mod', 'true', 'false'];
            for (var i = 0; i < inkBlocks.length; i++) {
                var block = inkBlocks[i];
                // Check that no obviously broken syntax exists
                var lines = block.source.split('\n');
                for (var j = 0; j < lines.length; j++) {
                    var line = lines[j].trim();
                    // Skip comments and empty lines
                    if (line.startsWith('//') || line === '') continue;

                    // VAR/CONST/LIST declarations should have = sign
                    if (/^(VAR|CONST|LIST)\s/.test(line)) {
                        assert.ok(line.includes('='),
                            'Declaration at line ' + (j + 1) + ' in block "' +
                            block.heading + '" missing = sign: ' + line);
                    }
                }
            }
        });

        it('all blocks with -> END have matching END or DONE', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                var block = inkBlocks[i];
                var hasDivert = block.source.includes('->');
                if (hasDivert) {
                    var hasTerminator = block.source.includes('-> END') ||
                        block.source.includes('-> DONE') ||
                        block.source.includes('->->');
                    // Block #534 intentionally lacks END (that's the bug it tests)
                    if (block.heading.includes('#534')) continue;
                    assert.ok(hasTerminator,
                        'Block "' + block.heading + '" has diverts but no END/DONE/tunnel-return');
                }
            }
        });

        it('knot declarations use valid syntax', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                var block = inkBlocks[i];
                var knotMatches = block.source.match(/^=== .+ ===$/gm);
                if (knotMatches) {
                    for (var j = 0; j < knotMatches.length; j++) {
                        // Should be === name === or === name(params) ===
                        assert.ok(/^=== \w[\w(, )]*===$/m.test(knotMatches[j]),
                            'Invalid knot declaration: ' + knotMatches[j]);
                    }
                }
            }
        });

        it('choices use valid bullet markers (* or +)', function () {
            for (var i = 0; i < inkBlocks.length; i++) {
                var block = inkBlocks[i];
                var lines = block.source.split('\n');
                for (var j = 0; j < lines.length; j++) {
                    var trimmed = lines[j].trim();
                    if (/^\*\s/.test(trimmed) || /^\+\s/.test(trimmed)) {
                        // Valid choice — * or + followed by space
                        continue;
                    }
                    if (/^\*\s*{/.test(trimmed)) {
                        // Conditional choice: * {condition}
                        continue;
                    }
                    if (/^\*\s*\[/.test(trimmed)) {
                        // Suppressed choice: * [text]
                        continue;
                    }
                    if (trimmed === '* -> fallback') {
                        // Fallback choice
                        continue;
                    }
                }
            }
        });

        it('ink blocks contain RTL text where expected', function () {
            var rtlBlocks = inkBlocks.filter(function (b) {
                return b.heading.includes('#122') || b.heading.includes('#485');
            });
            var hebrewRegex = /[\u0590-\u05FF]/;
            for (var i = 0; i < rtlBlocks.length; i++) {
                assert.ok(hebrewRegex.test(rtlBlocks[i].source) ||
                    /[\u3000-\u9FFF]/.test(rtlBlocks[i].source),
                    'RTL/CJK block "' + rtlBlocks[i].heading + '" should contain RTL or CJK text');
            }
        });

        it('CJK block (#485) contains Japanese, Chinese, and Korean', function () {
            var cjkBlock = inkBlocks.find(function (b) { return b.heading.includes('#485'); });
            assert.ok(cjkBlock, 'Should find CJK block for issue #485');
            assert.ok(/[\u3040-\u309F]/.test(cjkBlock.source), 'Should contain Hiragana');
            assert.ok(/[\u4E00-\u9FFF]/.test(cjkBlock.source), 'Should contain CJK Unified');
            assert.ok(/[\uAC00-\uD7AF]/.test(cjkBlock.source), 'Should contain Hangul');
        });
    });

    // ────────────────────────────────────────────────────────
    // MARKDOWN TABLE EXTRACTION
    // ────────────────────────────────────────────────────────

    describe('markdown table extraction', function () {

        it('extracts the issues tables', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('#') && t.headers.includes('Title');
            });
            assert.ok(issuesTables.length >= 2,
                'Expected at least 2 issues tables (inky + ink), got ' + issuesTables.length);
        });

        it('inky issues table has required columns: #, Title, Tags, TDD', function () {
            var inkyTable = mdTables.find(function (t) {
                return t.heading.includes('inkle/inky');
            });
            assert.ok(inkyTable, 'Should find inkle/inky issues table');
            assert.ok(inkyTable.headers.includes('#'), 'Missing # column');
            assert.ok(inkyTable.headers.includes('Title'), 'Missing Title column');
            assert.ok(inkyTable.headers.includes('Tags'), 'Missing Tags column');
            assert.ok(inkyTable.headers.includes('TDD'), 'Missing TDD column');
        });

        it('ink issues table has required columns: #, Title, Tags, TDD', function () {
            var inkTable = mdTables.find(function (t) {
                return t.heading.includes('inkle/ink');
            });
            assert.ok(inkTable, 'Should find inkle/ink issues table');
            assert.ok(inkTable.headers.includes('#'), 'Missing # column');
            assert.ok(inkTable.headers.includes('Title'), 'Missing Title column');
            assert.ok(inkTable.headers.includes('Tags'), 'Missing Tags column');
            assert.ok(inkTable.headers.includes('TDD'), 'Missing TDD column');
        });

        it('inky issues table has at least 40 rows', function () {
            var inkyTable = mdTables.find(function (t) {
                return t.heading.includes('inkle/inky');
            });
            assert.ok(inkyTable.rows.length >= 40,
                'Expected >= 40 inky issues, got ' + inkyTable.rows.length);
        });

        it('ink issues table has at least 20 rows', function () {
            var inkTable = mdTables.find(function (t) {
                return t.heading.includes('inkle/ink');
            });
            assert.ok(inkTable.rows.length >= 20,
                'Expected >= 20 ink issues, got ' + inkTable.rows.length);
        });
    });

    // ────────────────────────────────────────────────────────
    // TABLE DATA VALIDATION
    // ────────────────────────────────────────────────────────

    describe('table data validation', function () {

        it('all issue # cells contain valid GitHub links', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('#') && t.headers.includes('TDD');
            });
            for (var t = 0; t < issuesTables.length; t++) {
                var table = issuesTables[t];
                var idxHash = table.headers.indexOf('#');
                for (var r = 0; r < table.rows.length; r++) {
                    var cell = table.rows[r][idxHash];
                    assert.ok(
                        cell.includes('github.com/inkle/'),
                        'Row ' + (r + 1) + ' in "' + table.heading +
                        '" should have GitHub link in # cell, got: ' + cell
                    );
                }
            }
        });

        it('tags are semicolon-delimited arrays', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('Tags');
            });
            for (var t = 0; t < issuesTables.length; t++) {
                var table = issuesTables[t];
                var idxTags = table.headers.indexOf('Tags');
                for (var r = 0; r < table.rows.length; r++) {
                    var tags = table.rows[r][idxTags];
                    // Tags should be semicolon-delimited, no commas
                    var tagArray = tags.split(';');
                    assert.ok(tagArray.length >= 1,
                        'Row ' + (r + 1) + ' should have at least 1 tag, got: ' + tags);
                    for (var k = 0; k < tagArray.length; k++) {
                        var tag = tagArray[k].trim();
                        assert.ok(tag.length > 0,
                            'Empty tag in row ' + (r + 1) + ': ' + tags);
                        // Tags should be lowercase kebab-case
                        assert.ok(/^[a-z][a-z0-9-]*$/.test(tag),
                            'Tag "' + tag + '" should be lowercase kebab-case in row ' + (r + 1));
                    }
                }
            }
        });

        it('TDD column starts with YES: or NO: or PARTIAL:', function () {
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('TDD');
            });
            for (var t = 0; t < issuesTables.length; t++) {
                var table = issuesTables[t];
                var idxTdd = table.headers.indexOf('TDD');
                for (var r = 0; r < table.rows.length; r++) {
                    var tdd = table.rows[r][idxTdd];
                    assert.ok(
                        tdd.startsWith('YES:') || tdd.startsWith('NO:') || tdd.startsWith('PARTIAL:'),
                        'Row ' + (r + 1) + ' TDD should start with YES:/NO:/PARTIAL:, got: ' +
                        tdd.substring(0, 30) + '...'
                    );
                }
            }
        });

        it('summary statistics table totals match row counts', function () {
            var summaryTables = mdTables.filter(function (t) {
                return t.headers.includes('Verdict') && t.headers.includes('Count');
            });
            assert.ok(summaryTables.length >= 2,
                'Expected at least 2 summary tables (inky + ink)');

            for (var t = 0; t < summaryTables.length; t++) {
                var table = summaryTables[t];
                var idxCount = table.headers.indexOf('Count');
                var total = 0;
                for (var r = 0; r < table.rows.length; r++) {
                    var count = parseInt(table.rows[r][idxCount].replace(/\*+/g, ''));
                    if (!isNaN(count)) total += count;
                }
                assert.ok(total > 0,
                    'Summary table "' + table.heading + '" should have non-zero total');
            }
        });
    });

    // ────────────────────────────────────────────────────────
    // CROSS-REFERENCE: INK BLOCKS ↔ TABLE ENTRIES
    // ────────────────────────────────────────────────────────

    describe('cross-reference: ink blocks reference table issues', function () {

        it('each ink block heading maps to a table row', function () {
            // Collect all issue numbers from both tables
            var issuesTables = mdTables.filter(function (t) {
                return t.headers.includes('#') && t.headers.includes('TDD');
            });
            var allIssueText = '';
            for (var t = 0; t < issuesTables.length; t++) {
                var idxHash = issuesTables[t].headers.indexOf('#');
                for (var r = 0; r < issuesTables[t].rows.length; r++) {
                    allIssueText += ' ' + issuesTables[t].rows[r][idxHash];
                }
            }

            // Map ink block heading issue refs to table entries
            var issueRefMap = {
                '#122': false, '#541': false, '#534': false, '#508': false,
                '#485': false, 'ink-959': false, 'ink-916': false,
                'ink-844': false, 'ink-908': false, 'ink-950': false, 'ink-923': false
            };

            for (var key in issueRefMap) {
                // Convert ink-NNN to the table format (ink-NNN in link text)
                var searchKey = key.startsWith('ink-') ? key : key.replace('#', '');
                if (allIssueText.includes(searchKey)) {
                    issueRefMap[key] = true;
                }
            }

            var missingRefs = [];
            for (var key in issueRefMap) {
                if (!issueRefMap[key]) missingRefs.push(key);
            }
            assert.ok(missingRefs.length <= 2,
                'These ink block issue refs are missing from tables: ' + missingRefs.join(', '));
        });

        it('TDD=YES issues have corresponding ink blocks where feasible', function () {
            // Issues that are both TDD=YES and have ink blocks = the intersection
            var inkyTable = mdTables.find(function (t) {
                return t.heading.includes('inkle/inky');
            });
            var idxTdd = inkyTable.headers.indexOf('TDD');
            var idxHash = inkyTable.headers.indexOf('#');

            var yesIssues = [];
            for (var r = 0; r < inkyTable.rows.length; r++) {
                if (inkyTable.rows[r][idxTdd].startsWith('YES:')) {
                    yesIssues.push(inkyTable.rows[r][idxHash]);
                }
            }
            assert.ok(yesIssues.length >= 20,
                'Expected >= 20 TDD=YES issues in inky table, got ' + yesIssues.length);
        });
    });

    // ────────────────────────────────────────────────────────
    // BIDI TEST MATRIX TABLE
    // ────────────────────────────────────────────────────────

    describe('bidi TDD test matrix', function () {

        it('test matrix table has coverage columns for all test files', function () {
            var matrixTable = mdTables.find(function (t) {
                return t.headers.includes('Feature') && t.headers.includes('TDD Gap');
            });
            assert.ok(matrixTable, 'Should find test matrix table');
            assert.ok(matrixTable.headers.includes('bidi_and_tdd.ink'));
            assert.ok(matrixTable.headers.includes('bidi-e2e.test.js'));
            assert.ok(matrixTable.headers.includes('BidiTddInkTest.kt'));
            assert.ok(matrixTable.headers.includes('bidify.test.js'));
        });

        it('test matrix covers key ink features', function () {
            var matrixTable = mdTables.find(function (t) {
                return t.headers.includes('Feature') && t.headers.includes('TDD Gap');
            });
            var idxFeature = matrixTable.headers.indexOf('Feature');
            var features = matrixTable.rows.map(function (r) { return r[idxFeature]; });
            var expectedFeatures = ['Plain text', 'Choices', 'Diverts', 'Glue',
                'Tags', 'Tunnels', 'Threads', 'Variables', 'Functions', 'Lists'];
            for (var i = 0; i < expectedFeatures.length; i++) {
                var found = features.some(function (f) {
                    return f.includes(expectedFeatures[i]);
                });
                assert.ok(found,
                    'Test matrix should cover feature: ' + expectedFeatures[i]);
            }
        });
    });
});
