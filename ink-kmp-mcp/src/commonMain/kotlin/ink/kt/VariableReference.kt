package ink.kt

/**
 * Runtime instruction that reads a variable value.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.VariableReference` — same fields
 * - Java: `VariableReference` — same
 * - JS: `VariableReference` — same
 *
 * Kotlin: Same structure. `name` (variable name) and optional `pathForCount`.
 */
class VariableReference(var name: String? = null) : InkObject() {

    var pathForCount: Path? = null

    val containerForCount: Container?
        get() = resolvePath(pathForCount!!).container

    var pathStringForCount: String?
        get() = pathForCount?.let { compactPathString(it) }
        set(value) {
            if (value == null) pathForCount = null
            else pathForCount = Path(value)
        }

    override fun toString(): String {
        return if (name != null) "var($name)"
        else "read_count($pathStringForCount)"
    }
}
