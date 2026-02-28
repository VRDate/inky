package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternTest {

    // --- a Kotlin class with public functions ---

    val helloWorld =
        """=== test_knot
          |{x.hello()}
          |-> END
        """.trimMargin()

    @Test fun `should be possible to call on an object without any parameters - Kotlin`() {
        val story = InkParser.parse(helloWorld, TestWrapper(), "Test")
        story.putVariable("x", TestClassKotlin())
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Hello, is it me you're looking for?", text[0])
    }

    val helloNoBrace =
        """=== test_knot
          |{x.hello()}
          |-> END
        """.trimMargin()

    @Test fun `should be possible to call on an object without any parameters and no function brace - Kotlin`() {
        val story = InkParser.parse(helloNoBrace, TestWrapper(), "Test")
        story.putVariable("x", TestClassKotlin())
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

    @Test fun `should be possible to call on an object with a parameter defined - Kotlin`() {
        val story = InkParser.parse(mambo, TestWrapper(), "Test")
        story.putVariable("x", TestClassKotlin())
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

    @Test fun `resolve external bools correctly in conditional choices - Kotlin`() {
        val story = InkParser.parse(externChoices, TestWrapper(), "Test")
        story.putVariable("x", TestClassKotlin())
        story.next()
        assertEquals(1, story.choiceSize)
    }

    // --- a Java class with public methods ---

    val helloWorldJava =
        """=== test_knot
          |{x.hello()}
          |-> END
        """.trimMargin()

    @Test fun `should be possible to call on an object without any parameters - Java`() {
        val story = InkParser.parse(helloWorldJava, TestWrapper(), "Test")
        story.putVariable("x", TestClassJava())
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Hello, is it me you're looking for?", text[0])
    }

    val helloNoBraceJava =
        """=== test_knot
          |{x.hello()}
          |-> END
        """.trimMargin()

    @Test fun `should be possible to call on an object without any parameters and no function brace - Java`() {
        val story = InkParser.parse(helloNoBraceJava, TestWrapper(), "Test")
        story.putVariable("x", TestClassJava())
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Hello, is it me you're looking for?", text[0])
    }

    val mamboJava =
        """=== test_knot
          | VAR y = 5
          |{x.number(y)}
          |-> END
        """.trimMargin()

    @Test fun `should be possible to call on an object with a parameter defined - Java`() {
        val story = InkParser.parse(mamboJava, TestWrapper(), "Test")
        story.putVariable("x", TestClassJava())
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Mambo Number 5", text[0])
    }

    val externChoicesJava =
        """=== choice_test ===
          |Test conditional choices
          |+ {x.wrong()} not displayed
          |+ shown
          """.trimMargin()

    @Test fun `resolve external bools correctly in conditional choices - Java`() {
        val story = InkParser.parse(externChoicesJava, TestWrapper(), "Test")
        story.putVariable("x", TestClassJava())
        story.next()
        assertEquals(1, story.choiceSize)
    }
}
