package ink.kt

/**
 * KMP test resource abstraction — text-first, URI-based.
 *
 * Design: Parse TEXT (String), not streams. Accept URI for local/remote resources.
 * API is pure Kotlin (String in, String out). Platform resource loading is internal detail.
 *
 * URI patterns:
 *   "resource:bidi_and_tdd.ink"           → classpath/bundled resource
 *   "file:///path/to/file.ink"            → local file (JVM/Native only)
 *   "bidi_and_tdd.ink"                    → classpath resource (shorthand)
 */
expect object TestResources {
    /** Load text from URI or classpath path. */
    fun loadText(uri: String): String

    /** Load lines from URI or classpath path. */
    fun loadLines(uri: String): List<String>

    /** Load text, return null if not found. */
    fun loadTextOrNull(uri: String): String?

    /** Create a temp directory for isolated tests. */
    fun tempDir(prefix: String): String

    /** Classpath resource exists? */
    fun exists(path: String): Boolean
}
