package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionTest {

    // --- an expression ---

    val testStory =
        """== test_knot
          |Hello, world!
          |-> END
          """.trimMargin()

    @Test fun `should handle AND`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(1.0, Expression("1&&1").eval(vMap))
        assertEquals(0.0, Expression("1&&0").eval(vMap))
        assertEquals(0.0, Expression("0&&0").eval(vMap))
    }

    @Test fun `should handle OR`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(1.0, Expression("1||1").eval(vMap))
        assertEquals(1.0, Expression("1||0").eval(vMap))
        assertEquals(0.0, Expression("0||0").eval(vMap))
    }

    @Test fun `should handle comparison operators`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(1.0, Expression("2>1").eval(vMap))
        assertEquals(0.0, Expression("2<1").eval(vMap))
        assertEquals(0.0, Expression("1>2").eval(vMap))
        assertEquals(1.0, Expression("1<2").eval(vMap))
        assertEquals(0.0, Expression("1=2").eval(vMap))
        assertEquals(1.0, Expression("1=1").eval(vMap))
        assertEquals(1.0, Expression("1>=1").eval(vMap))
        assertEquals(1.0, Expression("1.1>=1").eval(vMap))
        assertEquals(0.0, Expression("1>=2").eval(vMap))
        assertEquals(1.0, Expression("1<=1").eval(vMap))
        assertEquals(0.0, Expression("1.1<=1").eval(vMap))
        assertEquals(1.0, Expression("1<=2").eval(vMap))
        assertEquals(1.0, Expression("1!=2").eval(vMap))
        assertEquals(0.0, Expression("1!=1").eval(vMap))
    }

    @Test fun `should handle combined comparisons`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(1.0, Expression("(2>1)||(1=0)").eval(vMap))
        assertEquals(0.0, Expression("(2>3)||(1=0)").eval(vMap))
        assertEquals(1.0, Expression("(2>3)||(1=0)||(1&&1)").eval(vMap))
    }

    @Test fun `should handle mixed arithmetic and comparison`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(0.0, Expression("1.5 * 7 = 3").eval(vMap))
        assertEquals(1.0, Expression("1.5 * 7 = 10.5").eval(vMap))
    }

    @Test fun `should handle NOT`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(0.0, Expression("not(1)").eval(vMap))
        assertEquals(1.0, Expression("not(0)").eval(vMap))
        assertEquals(1.0, Expression("not(1.5 * 7 = 3)").eval(vMap))
        assertEquals(0.0, Expression("not(1.5 * 7 = 10.5)").eval(vMap))
    }

    @Test fun `should handle constants TRUE and FALSE`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(1.0, Expression("TRUE!=FALSE").eval(vMap))
        assertEquals(0.0, Expression("TRUE==2").eval(vMap))
        assertEquals(1.0, Expression("NOT(TRUE)==FALSE").eval(vMap))
        assertEquals(1.0, Expression("NOT(FALSE)==TRUE").eval(vMap))
        assertEquals(0.0, Expression("TRUE && FALSE").eval(vMap))
        assertEquals(1.0, Expression("TRUE || FALSE").eval(vMap))
    }

    @Test fun `should handle IF function`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(5.0, Expression("if(TRUE, 5, 3)").eval(vMap))
        assertEquals(3.0, Expression("IF(FALSE, 5, 3)").eval(vMap))
        assertEquals(5.35, Expression("If(2, 5.35, 3)").eval(vMap))
    }

    // --- EvalEx expression extensions (mica-ink / ink.kt only) ---

    @Test fun `should handle ISNULL function`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(1.0, Expression("isnull(0)").eval(vMap))
        assertEquals(0.0, Expression("isnull(1)").eval(vMap))
        assertEquals(0.0, Expression("isnull(42)").eval(vMap))
        assertEquals(0.0, Expression("ISNULL(3.14)").eval(vMap))
    }

    @Test fun `should handle FLOOR function`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(3.0, Expression("floor(3.7)").eval(vMap))
        assertEquals(3.0, Expression("FLOOR(3.14)").eval(vMap))
        assertEquals(0.0, Expression("floor(0.9)").eval(vMap))
        assertEquals(0.0, Expression("floor(-0.5)").eval(vMap)) // truncates toward zero (toInt)
    }

    @Test fun `should handle RANDOM function`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        val result = Expression("random(6)").eval(vMap) as Double
        assertTrue(result >= 0.0 && result < 6.0, "random(6) should be in [0,6): was $result")
        assertEquals(0.0, Expression("random(0)").eval(vMap))
    }

    @Test fun `should handle PI and e constants`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        val pi = Expression("PI").eval(vMap) as Double
        assertTrue(pi > 3.14 && pi < 3.15, "PI should be ~3.14159: was $pi")
        val e = Expression("e").eval(vMap) as Double
        assertTrue(e > 2.71 && e < 2.72, "e should be ~2.71828: was $e")
    }

    @Test fun `should handle elvis operator`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(5.0, Expression("5 ?: 3").eval(vMap))
        assertEquals(3.0, Expression("0 ?: 3").eval(vMap))
    }

    @Test fun `should handle not-equal alias`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(1.0, Expression("1 <> 2").eval(vMap))
        assertEquals(0.0, Expression("1 <> 1").eval(vMap))
    }

    @Test fun `should handle arithmetic operators`() {
        val story = InkParser.parse(testStory, TestWrapper(), "Test")
        val vMap = story.variableMap()
        assertEquals(5.0, Expression("2 + 3").eval(vMap))
        assertEquals(1.0, Expression("3 - 2").eval(vMap))
        assertEquals(6.0, Expression("2 * 3").eval(vMap))
        assertEquals(2.5, Expression("5 / 2").eval(vMap))
        assertEquals(1.0, Expression("7 % 3").eval(vMap))
        assertEquals(8.0, Expression("2 ^ 3").eval(vMap))
    }
}
