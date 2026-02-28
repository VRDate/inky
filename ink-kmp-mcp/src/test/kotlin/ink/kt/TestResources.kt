package ink.kt

/**
 * KMP-ready test resource abstraction — text-first, URI-based.
 *
 * Design: Parse TEXT (String), not streams. Accept URI for local/remote resources.
 * API is pure Kotlin (String in, String out). JVM classloader is internal detail.
 * Future: expect/actual for JS (fetch) / Native (file read) targets.
 *
 * URI patterns:
 *   "resource:bidi_and_tdd.ink"           → classpath resource
 *   "file:///path/to/file.ink"            → local file
 *   "https://example.com/story.ink"       → remote file (future)
 *   "bidi_and_tdd.ink"                    → classpath resource (shorthand)
 */
object TestResources {

    /** Load text from URI or classpath path. */
    fun loadText(uri: String): String = when {
        uri.startsWith("file://") -> java.io.File(java.net.URI(uri)).readText()
        uri.startsWith("http://") || uri.startsWith("https://") ->
            java.net.URI(uri).toURL().readText()
        else -> {
            val path = uri.removePrefix("resource:")
            val stream = TestResources::class.java.classLoader.getResourceAsStream(path)
                ?: error("Test resource not found: $path")
            stream.bufferedReader().use { it.readText() }
        }
    }

    /** Load lines from URI or classpath path. */
    fun loadLines(uri: String): List<String> = loadText(uri).lines()

    /** Load text, return null if not found. */
    fun loadTextOrNull(uri: String): String? = try {
        loadText(uri)
    } catch (_: Exception) {
        null
    }

    /** Create a temp directory for isolated tests. */
    fun tempDir(prefix: String): String =
        kotlin.io.path.createTempDirectory(prefix).toString()

    /** Classpath resource exists? */
    fun exists(path: String): Boolean =
        TestResources::class.java.classLoader
            .getResource(path.removePrefix("resource:")) != null
}
