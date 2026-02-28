package ink.kt

/**
 * WASM actual — ink.kt compiled to WebAssembly.
 * Replaces legacy pure JS (inkjs) with compiled Kotlin WASM runtime.
 * Compilation uses ink.kt directly (no blade-ink Java compiler).
 */
actual object InkPlatform {

    actual fun createStory(json: String, legacy: Boolean): Story {
        return Story(json)
    }

    actual fun compile(source: String, legacy: Boolean): InkLauncher.CompileResult {
        return InkLauncher.CompileResult(
            success = false,
            errors = listOf("Ink compilation not yet available on WASM target. Use pre-compiled JSON with startSessionFromJson().")
        )
    }
}
