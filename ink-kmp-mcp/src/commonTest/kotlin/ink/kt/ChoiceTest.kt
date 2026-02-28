package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChoiceTest {

    // --- a section with a single choice ---

    val singleChoice =
        """== test_knot
          |Hello, world!
          |* Hello back!
          |  Nice to hear from you
          """.trimMargin()

    @Test fun `demarcates the end of the text for tue parent container`() {
        val story = InkParser.parse(singleChoice, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Hello, world!", text[0])
    }

    @Test fun `continues processing with the choice text when a choice is selected`() {
        val story = InkParser.parse(singleChoice, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        val text = story.next()
        assertEquals(3, text.size)
        assertEquals("Hello, world!", text[0])
        assertEquals("Hello back!", text[1])
        assertEquals("Nice to hear from you", text[2])
    }

    // --- a section with multiple choices ---

    @Test fun `continues with the text of the selected choice when multiple choices exist`() {
        val multiChoice =
            """== test_knot
          |Hello, world!
          |* Hello back!
          |  Nice to hear from you
          |* Goodbye
          |  See you later
          """.trimMargin()

        val story = InkParser.parse(multiChoice, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        val text = story.next()
        assertEquals(3, text.size)
        assertEquals("Goodbye", text[1])
        assertEquals("See you later", text[2])
    }

    // --- a choice with suppressed text ---

    @Test fun `should be suppressed in the text flow using bracket syntax`() {
        val suppressChoice =
            """== test_knot
          |Hello, world!
          |*  [Hello back!]
          |  Nice to hear from you.
      """.trimMargin()

        val story = InkParser.parse(suppressChoice, TestWrapper(), "Test")
        story.next()
        assertEquals("Hello back!", story.choiceText(0))
        story.choose(0)
        val text = story.next()
        assertEquals(2, text.size)
        assertEquals("Nice to hear from you.", text[1])
    }

    @Test fun `be mixed in to the text using bracket syntax`() {
        val mixedChoice =
            """== test_knot
                |Hello world!
                |*   Hello [back!] right back to you!
                |    Nice to hear from you.
                """.trimMargin()

        val story = InkParser.parse(mixedChoice, TestWrapper(), "Test")
        story.next()
        assertEquals("Hello back!", story.choiceText(0))
        story.choose(0)
        val text = story.next()
        assertEquals(3, text.size)
        assertEquals("Hello right back to you!", text[1])
        assertEquals("Nice to hear from you.", text[2])
    }

    // --- a section with once-only choices ---

    @Test fun `disappear when used the first time`() {
        val varyingChoice =
            """=== find_help ===
        |
        |    You search desperately for a friendly face in the crowd.
        |    *   The woman in the hat[?] pushes you roughly aside. -> find_help
        |    *   The man with the briefcase[?] looks disgusted as you stumble past him. -> find_help
      """.trimMargin()

        val story = InkParser.parse(varyingChoice, TestWrapper(), "Test")
        story.next()
        assertEquals(2, story.choiceSize)
        story.choose(0)
        story.next()
        assertEquals(1, story.choiceSize)
        assertEquals("The man with the briefcase?", story.choiceText(0))
    }

    // --- a sticky choices ---

    @Test fun `not disappear when used`() {
        val stickyChoice =
            """=== homers_couch ===
        |    +   [Eat another donut]
        |        You eat another donut. -> homers_couch
        |    *   [Get off the couch]
        |        You struggle up off the couch to go and compose epic poetry.
        |        -> END
      """.trimMargin()

        val story = InkParser.parse(stickyChoice, TestWrapper(), "Test")
        story.next()
        assertEquals(2, story.choiceSize)
        story.choose(0)
        story.next()
        assertEquals(2, story.choiceSize)
    }

    // --- a fallback choices ---

    val fallbackChoice =
        """=== find_help ===
        |
        |    You search desperately for a friendly face in the crowd.
        |    *   The woman in the hat[?] pushes you roughly aside. -> find_help
        |    *   The man with the briefcase[?] looks disgusted as you stumble past him. -> find_help
        |    *   [] But it is too late: you collapse onto the station platform. This is the end.
        |        -> END
      """.trimMargin()

    @Test fun `should not be shown if there are non-fallback choices available`() {
        val story = InkParser.parse(fallbackChoice, TestWrapper(), "Test")
        story.next()
        assertEquals(2, story.choiceSize)
    }

    @Test fun `should be diverted to directly if all other choices are exhausted`() {
        val story = InkParser.parse(fallbackChoice, TestWrapper(), "Test")
        story.next()
        assertEquals(2, story.choiceSize)
        story.choose(0)
        story.next()
        assertEquals(1, story.choiceSize)
        story.choose(0)
        val text = story.next()
        assertEquals(true, story.isEnded)
        assertEquals("But it is too late: you collapse onto the station platform. This is the end.", text[text.size - 1])
    }

    // TODO: Error if fallback choice is not the last.

    // --- a conditional choice ---

    @Test fun `should not be visible if the condition evaluates to 0 (false)`() {
        val conditionalChoice =
            """=== choice_test ===
        |Test conditional choices
        |* { true } { false } not displayed
        |* { true } { true } { true and true }  one
        |* { false } not displayed
        |* { true } two
        |* { true } { true } three
        |* { true } four
      """.trimMargin()

        val story = InkParser.parse(conditionalChoice, TestWrapper(), "Test")
        story.next()
        assertEquals(4, story.choiceSize)
    }

    // --- a labelled choice ---

    val labelFlow =
        """=== meet_guard ===
        |The guard frowns at you.
        |*   (greet) [Greet him]
        |    'Greetings.'
        |*   (get_out) 'Get out of my way[.'],' you tell the guard.
        |-   'Hmm,' replies the guard.
        |*   {greet}     'Having a nice day?'
        |*   'Hmm?'[] you reply.        |
        |*   {get_out} [Shove him aside]
        |    You shove him sharply. He stares in reply, and draws his sword!
        |    -> END
        |-   'Mff,' the guard replies, and then offers you a paper bag. 'Toffee?'
        |    -> END
      """.trimMargin()

    @Test fun `handle labels on choices and evaluate in expressions (example 1)`() {
        val story = InkParser.parse(labelFlow, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        story.next()
        assertEquals(2, story.choiceSize)
        assertEquals("\'Having a nice day?\'", story.choiceText(0))
    }

    @Test fun `handle labels on choices and evaluate in expressions (example 2)`() {
        val story = InkParser.parse(labelFlow, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        story.next()
        assertEquals(2, story.choiceSize)
        assertEquals("Shove him aside", story.choiceText(1))
    }

    @Test fun `allow label references out of scope using the full path id`() {
        val labelScope =
            """=== knot ===
          |  = stitch_one
          |    * an option
          |    - (gatherpoint) Some content.
          |      -> knot.stitch_two
          |  = stitch_two
          |    * {knot.stitch_one.gatherpoint} Found gatherpoint
        """.trimMargin()

        val story = InkParser.parse(labelScope, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        story.next()
        assertEquals(1, story.choiceSize)
        assertEquals("Found gatherpoint", story.choiceText(0))
    }

    // @Test fun `fail label references that are out of scope`() {
    //     val labelScopeError =
    //         """=== knot ===
    //         |  = stitch_one
    //         |    * an option
    //         |    - (gatherpoint) Some content.
    //         |      -> knot.stitch_two
    //         |  = stitch_two
    //         |    * {gatherpoint} Found gatherpoint
    //       """.trimMargin()
    //
    //     val story = InkParser.parse(labelScopeError, TestWrapper(), "Test")
    //     story.next()
    //     story.choose(0)
    //     val wrongNext = { story.next() }
    //     wrongNext shouldThrow InkRunTimeException::class
    // }

    // --- a divert choice ---

    // @Test fun `be used up if they are once-only and a divert goes through them`() {
    //     val divertChoice =
    //         """=== knot
    //           |You see a soldier.
    //           |*   [Pull a face]
    //           |    You pull a face, and the soldier comes at you! -> shove
    //           |*   (shove) [Shove the guard aside] You shove the guard to one side, but he comes back swinging.
    //           |*   {shove} [Grapple and fight] -> fight_the_guard
    //           |-   -> knot
    //         """.trimMargin()
    //
    //     val story = InkParser.parse(divertChoice, TestWrapper(), "Test")
    //     story.next()
    //     assertEquals(2, story.choiceSize)
    //     story.choose(0)
    //     val text = story.next()
    //     assertEquals(2, text.size)
    //     assertEquals("You pull a face, and the soldier comes at you! You shove the guard to one side, but he comes back swinging.", text[1])
    //     assertEquals(1, story.choiceSize)
    //     assertEquals("Grapple and fight", story.choiceText(0))
    // }
}
