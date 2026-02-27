package ink.kt

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
