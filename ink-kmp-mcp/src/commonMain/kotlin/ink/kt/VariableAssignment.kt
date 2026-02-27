package ink.kt

/**
 * Runtime instruction that assigns a value to a variable.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.VariableAssignment` — same fields
 * - Java: `VariableAssignment` — same
 * - JS: `VariableAssignment` — same
 *
 * Kotlin: Same structure. `variableName`, `isNewDeclaration`, `isGlobal` properties.
 */
class VariableAssignment(
    var variableName: String? = null,
    var isNewDeclaration: Boolean = false
) : InkObject() {
    var isGlobal: Boolean = false

    override fun toString(): String =
        "VarAssign to $variableName"
}
