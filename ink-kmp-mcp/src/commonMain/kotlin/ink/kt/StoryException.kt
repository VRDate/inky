package ink.kt

/**
 * Exception for ink story runtime errors (typically bugs in ink source, not engine).
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.StoryException` — extends Exception
 * - Java: `StoryException` — extends Exception
 * - JS: extends Error (JS standard)
 *
 * Kotlin: Same. `useEndLineNumber` flag for error position reporting.
 */
class StoryException(message: String? = null) : Exception(message) {
    var useEndLineNumber: Boolean = false
}
