package ink.kt

/**
 * Glue marker that joins text output across line breaks in ink.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.Glue` — simple marker class
 * - Java: `Glue` — same
 * - JS: `Glue` — same
 *
 * Kotlin: Minimal class, same across all ports. `toString() = "Glue"`.
 */
class Glue : InkObject() {
    override fun toString(): String = "Glue"
}
