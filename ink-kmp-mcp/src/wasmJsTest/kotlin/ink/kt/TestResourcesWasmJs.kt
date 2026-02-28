package ink.kt

import kotlin.js.JsString
import kotlin.js.toJsString

/**
 * WASM actual — uses Node.js external declarations for resource loading.
 * WASM JS interop requires `external` functions, not `dynamic`/`js()` inline.
 */

// Node.js fs module bindings for WASM
@JsFun("(path, encoding) => require('fs').readFileSync(path, encoding)")
private external fun readFileSync(path: JsString, encoding: JsString): JsString

@JsFun("(path) => require('fs').existsSync(path)")
private external fun existsSync(path: JsString): Boolean

@JsFun("(path, options) => require('fs').mkdirSync(path, { recursive: true })")
private external fun mkdirSyncRecursive(path: JsString, options: JsString): JsAny?

@JsFun("() => require('os').tmpdir()")
private external fun tmpdir(): JsString

actual object TestResources {

    actual fun loadText(uri: String): String {
        val path = uri.removePrefix("resource:")
        val candidates = arrayOf(
            "src/commonTest/resources/$path",
            "ink-kmp-mcp/src/commonTest/resources/$path",
            path
        )
        for (candidate in candidates) {
            try {
                return readFileSync(candidate.toJsString(), "utf-8".toJsString()).toString()
            } catch (_: Throwable) {
                continue
            }
        }
        error("Test resource not found: $path")
    }

    actual fun loadLines(uri: String): List<String> = loadText(uri).lines()

    actual fun loadTextOrNull(uri: String): String? = try {
        loadText(uri)
    } catch (_: Throwable) {
        null
    }

    actual fun tempDir(prefix: String): String {
        val tmpBase = tmpdir().toString()
        val dir = "$tmpBase/$prefix-${kotlin.random.Random.nextInt(100000)}"
        mkdirSyncRecursive(dir.toJsString(), "{}".toJsString())
        return dir
    }

    actual fun exists(path: String): Boolean {
        val cleanPath = path.removePrefix("resource:")
        return try {
            existsSync("src/commonTest/resources/$cleanPath".toJsString())
        } catch (_: Throwable) {
            false
        }
    }
}
