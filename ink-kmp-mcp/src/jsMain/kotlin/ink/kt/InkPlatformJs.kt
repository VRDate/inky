package ink.kt

/**
 * JS actual — ink.kt only. No blade-ink Java compiler available on JS target.
 *
 * When `legacy=false`, [InkLauncher] uses [InkParser] directly (common code).
 * This actual is only invoked for `legacy=true`, which is not available on JS.
 */
actual object InkPlatform {

    actual fun createStory(json: String, legacy: Boolean): Story {
        return Story(json)
    }

    actual fun compile(source: String, legacy: Boolean): InkLauncher.CompileResult {
        return InkLauncher.CompileResult(
            success = false,
            errors = listOf("Legacy ink compilation not available on JS target. Use legacy=false for InkParser.")
        )
    }
}
