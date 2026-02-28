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
}
