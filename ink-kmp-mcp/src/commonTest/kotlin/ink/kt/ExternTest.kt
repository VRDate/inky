package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternTest {

    // --- extern function calls on an InkCallable object ---

    val helloWorld =
        """=== test_knot
          |{x.hello()}
          |-> END
        """.trimMargin()

    @Test fun `should be possible to call on an object without any parameters`() {
        val story = InkParser.parse(helloWorld, TestWrapper(), "Test")
        story.putVariable("x", TestClass())
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Hello, is it me you're looking for?", text[0])
    }

    val helloNoBrace =
        """=== test_knot
          |{x.hello()}
          |-> END
        """.trimMargin()

    @Test fun `should be possible to call on an object without any parameters and no function brace`() {
        val story = InkParser.parse(helloNoBrace, TestWrapper(), "Test")
        story.putVariable("x", TestClass())
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Hello, is it me you're looking for?", text[0])
    }

    val mambo =
        """=== test_knot
          | VAR y = 5
          |{x.number(y)}
          |-> END
        """.trimMargin()

    @Test fun `should be possible to call on an object with a parameter defined`() {
        val story = InkParser.parse(mambo, TestWrapper(), "Test")
        story.putVariable("x", TestClass())
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Mambo Number 5", text[0])
    }

    val externChoices =
        """=== choice_test ===
          |Test conditional choices
          |+ {x.wrong()} not displayed
          |+ shown
          """.trimMargin()

    @Test fun `resolve external bools correctly in conditional choices`() {
        val story = InkParser.parse(externChoices, TestWrapper(), "Test")
        story.putVariable("x", TestClass())
        story.next()
        assertEquals(1, story.choiceSize)
    }
}
