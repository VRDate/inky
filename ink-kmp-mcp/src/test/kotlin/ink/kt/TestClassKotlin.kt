package ink.kt

/**
 * Test helper class for extern function tests.
 *
 * Ported from `ink.java.mica.test.helpers.TestClassKotlin` (JVM).
 * Uses Double instead of BigDecimal (KMP-compatible).
 */
@Suppress("unused")
class TestClassKotlin {

    fun hello(): String = "Hello, is it me you're looking for?"

    fun number(b: Double): String = "Mambo Number ${b.toInt()}"

    fun wrong(): Boolean = false
}
