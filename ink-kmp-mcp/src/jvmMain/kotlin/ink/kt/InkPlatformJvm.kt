package ink.kt

import com.bladecoder.ink.compiler.Compiler
import com.bladecoder.ink.runtime.Error.ErrorType

/**
 * JVM actual — uses blade-ink Java compiler for legacy compilation,
 * ink.kt Story for runtime (both read identical JSON format).
 *
 * When `legacy=false`, [InkLauncher] uses [InkParser] directly (common code).
 * This actual is only invoked for `legacy=true`.
 */
actual object InkPlatform {

    actual fun createStory(json: String, legacy: Boolean): Story {
        return Story(json)
    }

    actual fun compile(source: String, legacy: Boolean): InkLauncher.CompileResult {
        require(legacy) { "Non-legacy compile uses InkParser in common code" }
        return compileWithBladeInk(source)
    }

    private fun compileWithBladeInk(source: String): InkLauncher.CompileResult {
        return try {
            val errors = mutableListOf<String>()
            val options = Compiler.Options()
            options.errorHandler = com.bladecoder.ink.runtime.Error.ErrorHandler { message, errorType ->
                if (errorType == ErrorType.Error) {
                    errors.add(message)
                }
            }
            val compiler = Compiler(source, options)
            val runtimeStory = compiler.compile()
            if (errors.isNotEmpty()) {
                InkLauncher.CompileResult(success = false, errors = errors)
            } else if (runtimeStory == null) {
                InkLauncher.CompileResult(success = false, errors = listOf("Compilation returned null"))
            } else {
                val json = runtimeStory.toJson()
                InkLauncher.CompileResult(success = true, json = json)
            }
        } catch (e: Exception) {
            InkLauncher.CompileResult(success = false, errors = listOf(e.message ?: "Compilation failed"))
        }
    }
}
