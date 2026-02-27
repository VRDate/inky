package ink.kt

/**
 * Void value pushed to evaluation stack when a function returns nothing.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.Void` — marker class
 * - Java: `Void` — same (shadows java.lang.Void but in different package)
 * - JS: `Void` — same
 *
 * Kotlin: Same marker class. No collision since `kotlin.Unit` is the Kotlin void.
 */
class Void : InkObject()
