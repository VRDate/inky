package ink.kt

/**
 * JS actual — ink.kt only. No blade-ink Java compiler available on JS target.
 * Compilation is not supported on JS; use pre-compiled JSON.
 */
actual object InkPlatform {

    actual fun createStory(json: String, legacy: Boolean): Story {
        // JS target: always use ink.kt Story (no legacy engine available)
        return Story(json)
    }

    actual fun compile(source: String, legacy: Boolean): InkLauncher.CompileResult {
        return InkLauncher.CompileResult(
            success = false,
            errors = listOf("Ink compilation not available on JS target. Use pre-compiled JSON with startSessionFromJson().")
        )
    }
}
