package ink.kt

/**
 * Test helper class for extern function tests (Java-style).
 * Mirrors TestClassKotlin with the same methods.
 */
@Suppress("unused")
class TestClassJava {

    fun hello(): String = "Hello, is it me you're looking for?"

    fun number(b: Double): String = "Mambo Number ${b.toInt()}"

    fun wrong(): Boolean = false
}
