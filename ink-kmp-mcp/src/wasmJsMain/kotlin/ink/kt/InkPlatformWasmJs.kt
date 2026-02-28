package ink.kt

/**
 * WASM actual — ink.kt compiled to WebAssembly.
 *
 * When `legacy=false`, [InkLauncher] uses [InkParser] directly (common code).
 * This actual is only invoked for `legacy=true`, which is not available on WASM.
 */
actual object InkPlatform {

    actual fun createStory(json: String, legacy: Boolean): Story {
        return Story(json)
    }

    actual fun compile(source: String, legacy: Boolean): InkLauncher.CompileResult {
        return InkLauncher.CompileResult(
            success = false,
            errors = listOf("Legacy ink compilation not available on WASM target. Use legacy=false for InkParser.")
        )
    }
}
