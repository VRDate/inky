package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariableTextTest {

    // --- Sequences ---

    val sequence =
        """=== test
        |The radio hissed into life. {"Three!"|"Two!"|"One!"|There was the white noise racket of an explosion.}
        |+ [Again] -> test
      """.trimMargin()

    @Test fun `step through each element and repeat the final element`() {
        val story = InkParser.parse(sequence, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("The radio hissed into life. \"Three!\"", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("The radio hissed into life. \"Two!\"", text1[1])
        story.choose(0)
        val text2 = story.next()
        assertEquals(3, text2.size)
        assertEquals("The radio hissed into life. \"One!\"", text2[2])
        story.choose(0)
        val text3 = story.next()
        assertEquals(4, text3.size)
        assertEquals("The radio hissed into life. There was the white noise racket of an explosion.", text3[3])
        story.choose(0)
        val text4 = story.next()
        assertEquals(5, text4.size)
        assertEquals("The radio hissed into life. There was the white noise racket of an explosion.", text4[4])
    }

    // --- Cycles ---

    val cycle =
        """=== test
        |The radio hissed into life. {&"Three!"|"Two!"|"One!"}
        |+ [Again] -> test
      """.trimMargin()

    @Test fun `cycle through the element repeatedly`() {
        val story = InkParser.parse(cycle, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("The radio hissed into life. \"Three!\"", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("The radio hissed into life. \"Two!\"", text1[1])
        story.choose(0)
        val text2 = story.next()
        assertEquals(3, text2.size)
        assertEquals("The radio hissed into life. \"One!\"", text2[2])
        story.choose(0)
        val text3 = story.next()
        assertEquals(4, text3.size)
        assertEquals("The radio hissed into life. \"Three!\"", text3[3])
        story.choose(0)
        val text4 = story.next()
        assertEquals(5, text4.size)
        assertEquals("The radio hissed into life. \"Two!\"", text4[4])
    }

    // --- Once-only lists ---

    val once =
        """=== test
        |The radio hissed into life. {!"Three!"|"Two!"|"One!"}
        |+ [Again] -> test
      """.trimMargin()

    @Test fun `step through each element and return no text once the list is exhausted`() {
        val story = InkParser.parse(once, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("The radio hissed into life. \"Three!\"", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("The radio hissed into life. \"Two!\"", text1[1])
        story.choose(0)
        val text2 = story.next()
        assertEquals(3, text2.size)
        assertEquals("The radio hissed into life. \"One!\"", text2[2])
        story.choose(0)
        val text3 = story.next()
        assertEquals(4, text3.size)
        assertEquals("The radio hissed into life.", text3[3])
        story.choose(0)
        val text4 = story.next()
        assertEquals(5, text4.size)
        assertEquals("The radio hissed into life.", text4[4])
    }

    val shuffle =
        """=== test
        |The radio hissed into life. {~"Three!"|"Two!"|"One!"}
        |+ [Again] -> test
      """.trimMargin()

    @Test fun `cycle through the element repeatedly - shuffle`() {
        val story = InkParser.parse(shuffle, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        val res0 = text0[0] == "The radio hissed into life. \"Three!\"" || text0[0] == "The radio hissed into life. \"Two!\"" || text0[0] == "The radio hissed into life. \"One!\""
        assertEquals(true, res0)
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        val res1 = text1[1] == "The radio hissed into life. \"Three!\"" || text1[1] == "The radio hissed into life. \"Two!\"" || text1[1] == "The radio hissed into life. \"One!\""
        assertEquals(true, res1)
        story.choose(0)
        val text2 = story.next()
        assertEquals(3, text2.size)
        val res2 = text2[2] == "The radio hissed into life. \"Three!\"" || text2[2] == "The radio hissed into life. \"Two!\"" || text2[2] == "The radio hissed into life. \"One!\""
        assertEquals(true, res2)
        story.choose(0)
        val text3 = story.next()
        assertEquals(4, text3.size)
        val res3 = text3[3] == "The radio hissed into life. \"Three!\"" || text3[3] == "The radio hissed into life. \"Two!\"" || text3[3] == "The radio hissed into life. \"One!\""
        assertEquals(true, res3)
    }

    val emptyElements =
        """=== test
        |The radio hissed into life. {||"One!"}
        |+ [Again] -> test
      """.trimMargin()

    @Test fun `allow for empty text elements in the list`() {
        val story = InkParser.parse(emptyElements, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("The radio hissed into life.", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("The radio hissed into life.", text1[1])
        story.choose(0)
        val text2 = story.next()
        assertEquals(3, text2.size)
        assertEquals("The radio hissed into life. \"One!\"", text2[2])
    }

    val listInChoice =
        """=== test
        |He looked at me oddly.
        |+ ["Hello, {&Master|Monsieur|you}!"] -> test
      """.trimMargin()

    @Test fun `be usable in a choice test`() {
        val story = InkParser.parse(listInChoice, TestWrapper(), "Test")
        story.next()
        assertEquals("\"Hello, Master!\"", story.choiceText(0))
        story.choose(0)
        story.next()
        assertEquals("\"Hello, Monsieur!\"", story.choiceText(0))
        story.choose(0)
        story.next()
        assertEquals("\"Hello, you!\"", story.choiceText(0))
    }

    /*
val nestedList =
  """=== test
    |The radio hissed into life. {||"One!"}
    |+ [Again] -> test
  """.trimMargin()
*/
    // TODO: add test

    val one =
        """=== test
        |VAR x = 1
        |We needed to find {? x : nothing|one apple|two pears|many oranges}.
        |-> END
      """.trimMargin()

    @Test fun `return the text string in the sequence if the condition is a valid value - 1`() {
        val story = InkParser.parse(one, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("We needed to find one apple.", text0[0])
    }

    val minusOne =
        """=== test
        |VAR x = -1
        |We needed to find {? x : nothing|one apple|two pears|many oranges}.
        |-> END
      """.trimMargin()

    @Test fun `return the text string in the sequence if the condition is a valid value - 2`() {
        val story = InkParser.parse(minusOne, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("We needed to find nothing.", text0[0])
    }

    val ten =
        """=== test
        |VAR x = 10
        |We needed to find {? x : nothing|one apple|two pears|many oranges}.
        |-> END
      """.trimMargin()

    @Test fun `return the text string in the sequence if the condition is a valid value - 3`() {
        val story = InkParser.parse(ten, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("We needed to find many oranges.", text0[0])
    }
}
