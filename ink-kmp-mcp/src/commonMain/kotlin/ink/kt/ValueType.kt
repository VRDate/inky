package ink.kt

/**
 * Enum of all value types in the ink runtime.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.ValueType` — enum
 * - Java: `ValueType` — enum
 * - JS: uses string/number type discrimination
 *
 * Kotlin: Same enum. Bool, Int, Float, String, DivertTarget, VariablePointer, List.
 */
enum class ValueType {
    // Used in coercion
    Bool,
    Int,
    Float,
    List,
    String,
    // Not used for coercion described above
    DivertTarget,
    VariablePointer
}
