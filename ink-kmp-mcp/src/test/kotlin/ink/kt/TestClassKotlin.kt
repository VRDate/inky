package ink.kt

/**
 * Test helper class for extern function tests.
 *
 * Ported from `ink.java.mica.test.helpers.TestClassKotlin` (JVM).
 * Implements [Expression.InkCallable] for pure Kotlin method dispatch (no reflection).
 */
class TestClassKotlin : Expression.InkCallable {

    fun hello(): String = "Hello, is it me you're looking for?"

    fun number(b: Double): String = "Mambo Number ${b.toInt()}"

    fun wrong(): Boolean = false

    override fun inkCall(method: String, params: List<Any>): Any = when (method) {
        "hello" -> hello()
        "number" -> number((params[0] as Number).toDouble())
        "wrong" -> wrong()
        else -> throw InkRunTimeException("Unknown method: $method")
    }
}
