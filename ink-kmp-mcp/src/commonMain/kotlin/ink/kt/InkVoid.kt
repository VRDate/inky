package ink.kt

/**
 * InkVoid value pushed to evaluation stack when a function returns nothing.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.Void` — marker class
 * - Java: `Void` — same (shadows java.lang.Void but in different package)
 * - JS: `Void` — same
 *
 * Kotlin: Renamed to `InkVoid` to avoid clashing with `java.lang.Void`.
 */
class InkVoid : InkObject()
