package ink.kt

/**
 * JS actual — uses Node.js `fs` module for resource loading.
 * Test resources are expected at the project root under src/commonTest/resources/.
 */
actual object TestResources {

    actual fun loadText(uri: String): String {
        val path = uri.removePrefix("resource:")
        // Node.js fs.readFileSync — test resources at commonTest/resources/
        val fs = js("require('fs')")
        val candidates = arrayOf(
            "src/commonTest/resources/$path",
            "ink-kmp-mcp/src/commonTest/resources/$path",
            path
        )
        for (candidate in candidates) {
            try {
                return (fs.readFileSync(candidate, "utf-8") as String)
            } catch (_: dynamic) {
                continue
            }
        }
        error("Test resource not found: $path")
    }

    actual fun loadLines(uri: String): List<String> = loadText(uri).lines()

    actual fun loadTextOrNull(uri: String): String? = try {
        loadText(uri)
    } catch (_: dynamic) {
        null
    }

    actual fun tempDir(prefix: String): String {
        val os = js("require('os')")
        val fs = js("require('fs')")
        val tmpDir = (os.tmpdir() as String) + "/$prefix-" + kotlin.random.Random.nextInt(100000)
        fs.mkdirSync(tmpDir, js("{ recursive: true }"))
        return tmpDir
    }

    actual fun exists(path: String): Boolean {
        val cleanPath = path.removePrefix("resource:")
        val fs = js("require('fs')")
        return try {
            fs.existsSync("src/commonTest/resources/$cleanPath") as Boolean
        } catch (_: dynamic) {
            false
        }
    }
}
