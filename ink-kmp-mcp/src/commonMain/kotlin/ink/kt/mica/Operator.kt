package ink.kt.mica

/**
 * Abstract definition of a supported operator.
 * An operator is defined by its name (pattern), precedence,
 * and whether it is left- or right-associative.
 */
abstract class Operator(
    val oper: String,
    val precedence: Int,
    val isLeftAssoc: Boolean
) {
    abstract fun eval(v1: Any, v2: Any): Any
}
