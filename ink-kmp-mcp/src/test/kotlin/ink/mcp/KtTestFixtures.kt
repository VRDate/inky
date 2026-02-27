package ink.mcp

import java.io.File

/**
 * Shared test fixtures — companion object pattern (mirrors C# InkTestFixtures).
 *
 * Centralizes project root resolution, engine creation, and fixture loading
 * that was duplicated across BidiTddInkTest.kt, McpToolsTest.kt,
 * McpRouterTest.kt, and InkMdTableTest.kt.
 *
 * C# equivalent: `InkTestFixtures` static class
 * KT pattern:    `object` with `by lazy` vals (same as companion object)
 */
object KtTestFixtures {

    /** Mono repo root (parent of ink-kmp-mcp/, ink-electron/, docs/, etc.). */
    val projectRoot: String = System.getProperty("user.dir")
        .let { if (it.endsWith("ink-kmp-mcp")) it else "$it/ink-kmp-mcp" }
        .removeSuffix("/ink-kmp-mcp")

    /** Path to inkjs full distribution. */
    val inkjsPath: String = "$projectRoot/ink-electron/node_modules/inkjs/dist/ink-full.js"

    /** Path to bidify.js (null if file not present on disk). */
    val bidifyPath: String? = "$projectRoot/ink-electron/renderer/bidify.js"
        .let { if (File(it).exists()) it else null }

    /** Shared InkEngine — GraalJS + inkjs (lazy, created once). */
    val engine: InkEngine by lazy {
        InkEngine(inkjsPath, bidifyPath)
    }

    /** bidi_and_tdd.ink source — loaded once, shared across all tests. */
    val bidiTddSource: String by lazy {
        val resource = KtTestFixtures::class.java.getResource("/bidi_and_tdd.ink")
        resource?.readText()
            ?: File("$projectRoot/ink-electron/test/fixtures/bidi_and_tdd.ink").readText()
    }

    /** BIDI_TDD_ISSUES.md source — loaded once, shared across all tests. */
    val mdSource: String by lazy {
        File("$projectRoot/docs/BIDI_TDD_ISSUES.md").readText(Charsets.UTF_8)
    }

    /** Path to docs/BIDI_TDD_ISSUES.md. */
    val bidiTddIssuesMdPath: String = "$projectRoot/docs/BIDI_TDD_ISSUES.md"
}
