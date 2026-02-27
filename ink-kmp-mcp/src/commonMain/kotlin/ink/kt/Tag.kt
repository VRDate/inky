package ink.kt

/**
 * Tag content in ink (lines starting with #).
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.Tag` — text property
 * - Java: `Tag` — same
 * - JS: `Tag` — same
 *
 * Kotlin: Simple wrapper, `val text: String`.
 */
class Tag(override var text: String) : InkObject() {
    override fun toString(): String = "# $text"
}
