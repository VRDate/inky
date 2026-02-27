package ink.kt.mica

interface Function {
    val numParams: Int
    val isFixedNumParams: Boolean
    fun eval(params: List<Any>, vMap: VariableMap): Any
}
