package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeclarationTest {

    // --- a variable declaration ---

    val variableDeclaration =
        """=== test_knot ===
          |VAR friendly_name_of_player = "Jackie"
          |VAR age = 23
          |
          |"My name is Jean Passepartout, but my friend's call me {friendly_name_of_player}. I'm {age} years old."
        """.trimMargin()

    @Test fun `be declared with a VAR statement and print out a text value when used in content - 1`() {
        val story = InkParser.parse(variableDeclaration, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("\"My name is Jean Passepartout, but my friend's call me Jackie. I'm 23 years old.\"", text[0])
    }

    val varCalc =
        """=== set_some_variables ===
          | VAR knows = false
          | VAR x = 2
          | VAR y = 3
          | VAR c = 4
          | ~ knows = true
          | ~ x = (x * x) - (y * y) + c
          | ~ y = 2 * x * y
          |
          |    The values are {knows} and {x} and {y}.
        """.trimMargin()

    @Test fun `be declared with a VAR statement and print out a text value when used in content - 2`() {
        val story = InkParser.parse(varCalc, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("The values are 1 and -1 and -6.", text[0])
    }

    val varDivert =
        """=== test_knot ===
          |VAR current_epilogue = -> everybody_dies
          |Divert as variable example
          |-> continue_or_quit
          |
          |=== continue_or_quit
          |Give up now, or keep trying to save your Kingdom?
          |*  [Keep trying!]   -> continue_or_quit
          |*  [Give up]        -> current_epilogue
          |
          |=== everybody_dies
          |Everybody dies.
          |-> END
        """.trimMargin()

    @Test fun `be declarable as diverts and be usable in text`() {
        val story = InkParser.parse(varDivert, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        val text = story.next()
        assertEquals(3, text.size)
        assertEquals("Everybody dies.", text[2])
    }
}
