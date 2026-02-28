package ink.mcp

import ink.kt.TestResources

/**
 * Shared test fixtures — companion object pattern (mirrors C# InkTestFixtures).
 *
 * Centralizes project root resolution, engine creation, and fixture loading
 * that was duplicated across BidiTddInkTest.kt, McpToolsTest.kt,
 * McpRouterTest.kt, and InkMdTableTest.kt.
 *
 * C# equivalent: `InkTestFixtures` static class
 * KT pattern:    `object` with `by lazy` vals (same as companion object)
 *
 * Resource loading uses [TestResources] (KMP-ready, text-first, URI-based).
 * GraalJS engine paths remain JVM-specific (server-only, not KMP-portable).
 */
object KtTestFixtures {

    /** Mono repo root (parent of ink-kmp-mcp/, docs/, etc.). */
    val projectRoot: String = System.getProperty("user.dir")
        .let { dir ->
            val f = java.io.File(dir)
            if (f.name == "ink-kmp-mcp") f.parent else java.io.File(dir, "ink-kmp-mcp").parent
        }

    /** Path to inkjs full distribution (JVM/GraalJS only). */
    val inkjsPath: String = "$projectRoot/ink-kmp-mcp/src/jsMain/ink/js/electron/node_modules/inkjs/dist/ink-full.js"

    /** Path to bidify.js — null if file not present on disk (JVM/GraalJS only). */
    val bidifyPath: String? = "$projectRoot/ink-kmp-mcp/src/jsMain/ink/js/electron/renderer/bidify.js"
        .let { if (java.io.File(it).exists()) it else null }

    /** Shared InkEngine — GraalJS + inkjs (lazy, created once). JVM-only. */
    val engine: InkEngine by lazy {
        InkEngine(inkjsPath, bidifyPath)
    }

    /** bidi_and_tdd.ink source — loaded once from classpath resource. */
    val bidiTddSource: String by lazy {
        TestResources.loadText("bidi_and_tdd.ink")
    }

    /** BIDI_TDD_ISSUES.md source — loaded once from classpath resource. */
    val mdSource: String by lazy {
        TestResources.loadText("BIDI_TDD_ISSUES.md")
    }

    /** Path to docs/BIDI_TDD_ISSUES.md (for tests that need the filesystem path). */
    val bidiTddIssuesMdPath: String = "$projectRoot/docs/BIDI_TDD_ISSUES.md"
}
