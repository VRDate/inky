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

    // ── Parser constructor ────────────────────────────────────────────
    internal constructor(content: String, parent: Container?, lineNumber: Int) : this(content) {
        this.id = if (parent == null) content else contentId(parent)
        this.parent = parent
        this.lineNumber = lineNumber
    }

    override fun toString(): String = "# $text"
}
