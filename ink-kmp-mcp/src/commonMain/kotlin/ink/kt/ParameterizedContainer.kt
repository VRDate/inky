package ink.kt

internal open class ParameterizedContainer(
    id: String = "",
    val parameters: List<String> = emptyList(),
    parent: Container? = null,
    lineNumber: Int = 0
) : Container() {

    init {
        this.id = id
        this.text = ""
        this.parent = parent
        this.lineNumber = lineNumber
    }

    internal val values: MutableMap<String, Any> = mutableMapOf()

    fun hasValue(key: String): Boolean = values.containsKey(key)

    fun getValue(key: String): Any = values[key]!!

    fun setValue(key: String, value: Any) {
        values[key] = value
    }

    companion object {
        fun getParameters(header: String): List<String> {
            val params = mutableListOf<String>()
            if (header.contains(Symbol.BRACE_LEFT)) {
                val paramStr = header.substring(
                    header.indexOf(Symbol.BRACE_LEFT) + 1,
                    header.indexOf(Symbol.BRACE_RIGHT)
                )
                paramStr.split(",")
                    .dropLastWhile(String::isEmpty)
                    .mapTo(params) { it.trim() }
            }
            return params
        }
    }
}
