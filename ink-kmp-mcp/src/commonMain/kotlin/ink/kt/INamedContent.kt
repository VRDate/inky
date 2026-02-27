package ink.kt

/**
 * Interface for ink objects that have a name (knots, stitches, functions).
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.INamedContent` — interface with `name` and `hasValidName`
 * - Java: `INamedContent` — same interface
 * - JS: `INamedContent` — same
 *
 * Kotlin: Same interface. Implemented by Container, ListDefinition.
 */
interface INamedContent {
    val name: String?
    val hasValidName: Boolean
}
