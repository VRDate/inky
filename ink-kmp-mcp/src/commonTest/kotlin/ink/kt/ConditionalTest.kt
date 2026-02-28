package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConditionalTest {

    // --- a conditional if-then statement ---

    val ifTrue =
        """=== test
          |VAR x = 2
          |VAR y = 0
          |{ x > 0:
          |    ~ y = x - 1
          |}
          |The value is {y}.
        """.trimMargin()

    @Test fun `executes the statements if the condition evaluates to true`() {
        val story = InkParser.parse(ifTrue, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("The value is 1.", text[0])
    }

    val ifFalse =
        """=== test
        |VAR x = 0
        |VAR y = 3
        |{ x > 0:
        |    ~ y = x - 1
        |}
        |The value is {y}.
      """.trimMargin()

    @Test fun `does not evaluate the statement if the condition evaluates to false`() {
        val story = InkParser.parse(ifFalse, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("The value is 3.", text[0])
    }

    val ifElse =
        """=== test
        |VAR x = 0
        |VAR y = 3
        |{ x > 0:
        |    ~ y = x - 1
        |- else:
        |    ~ y = x + 1
        |}
        |The value is {y}.
      """.trimMargin()

    @Test fun `evaluates the else statement if it exists and no other condition evaluates to true`() {
        val story = InkParser.parse(ifElse, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("The value is 1.", text[0])
    }

    // --- a conditional with multiple options ---

    @Test fun `executes the first statement if the first condition is true`() {
        val ifExt =
            """=== test
        |VAR x = 0
        |VAR y = 3
        |{
        |    - x == 0:
        |        ~ y = 0
        |    - x > 0:
        |        ~ y = x - 1
        |    - else:
        |        ~ y = x + 1
        |}
        |The value is {y}.
      """.trimMargin()
        val story = InkParser.parse(ifExt, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("The value is 0.", text[0])
    }

    @Test fun `executes the first statement if the first condition is true, even if other statements are also true`() {
        val ifExt =
            """=== test
        |VAR x = 0
        |VAR y = 3
        |{
        |    - x == 0:
        |        ~ y = 0
        |    - x >= 0:
        |        ~ y = x - 1
        |    - else:
        |        ~ y = x + 1
        |}
        |The value is {y}.
      """.trimMargin()
        val story = InkParser.parse(ifExt, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("The value is 0.", text[0])
    }

    @Test fun `executes the second statement if the second condition is true`() {
        val ifExt =
            """=== test
        |VAR x = 2
        |VAR y = 3
        |{
        |    - x == 0:
        |        ~ y = 0
        |    - x > 0:
        |        ~ y = x - 1
        |    - else:
        |        ~ y = x + 1
        |}
        |The value is {y}.
      """.trimMargin()
        val story = InkParser.parse(ifExt, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("The value is 1.", text[0])
    }

    @Test fun `executes the else statement if no other condition is true`() {
        val ifElseExtText1 =
            """=== test
        |VAR x = 0
        |{
        |    - x == 0:
        |      This is text 1.
        |    - x > 0:
        |      This is text 2.
        |    - else:
        |      This is text 3.
        |}
        |+ [The Choice.] -> to_end
        |=== to_end
        |This is the end.
      """.trimMargin()
        val story = InkParser.parse(ifElseExtText1, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("This is text 1.", text[0])
        assertEquals(1, story.choiceSize)
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("This is the end.", text1[1])
    }

    val ifElseExtText2 =
        """=== test
        |VAR x = 2
        |{
        |    - x == 0:
        |      This is text 1.
        |    - x > 0:
        |      This is text 2.
        |    - else:
        |      This is text 3.
        |}
        |+ [The Choice.] -> to_end
        |=== to_end
        |This is the end.
      """.trimMargin()

    @Test fun `evaluate an extended else statement with text and divert at the end - 1`() {
        val story = InkParser.parse(ifElseExtText2, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("This is text 2.", text[0])
        assertEquals(1, story.choiceSize)
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("This is the end.", text1[1])
    }

    val ifElseExtText3 =
        """=== test
        |VAR x = -2
        |{
        |    - x == 0:
        |      This is text 1.
        |    - x > 0:
        |      This is text 2.
        |    - else:
        |      This is text 3.
        |}
        |+ [The Choice.] -> to_end
        |=== to_end
        |This is the end.
      """.trimMargin()

    @Test fun `evaluate an extended else statement with text and divert at the end - 2`() {
        val story = InkParser.parse(ifElseExtText3, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("This is text 3.", text[0])
        assertEquals(1, story.choiceSize)
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("This is the end.", text1[1])
    }

    // --- a conditional statement ---

    val condText =
        """ === start_test
        |"We are going on a trip," said Monsieur Fogg.
        |* [The wager.] -> know_about_wager
        |* [I was surprised.] -> i_stared
        |
        |=== know_about_wager
        |I had heard about the wager.
        |-> i_stared
        |
        |=== i_stared
        |I stared at Monsieur Fogg.
        |{ know_about_wager:
        |    <> "But surely you are not serious?" I demanded.
        |- else:
        |    <> "But there must be a reason for this trip," I observed.
        |}
        |He said nothing in reply, merely considering his newspaper with as much thoroughness as entomologist considering his latest pinned addition.
      """.trimMargin()

    @Test fun `work with conditional content which is not only logic (example 1)`() {
        val story = InkParser.parse(condText, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        val text = story.next()
        assertEquals(4, text.size)
        assertEquals("I stared at Monsieur Fogg. \"But surely you are not serious?\" I demanded.", text[2])
    }

    @Test fun `work with conditional content which is not only logic (example 2)`() {
        val story = InkParser.parse(condText, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        val text = story.next()
        assertEquals(3, text.size)
        assertEquals("I stared at Monsieur Fogg. \"But there must be a reason for this trip,\" I observed.", text[1])
    }

    val condOpt =
        """ === start_test
          |I looked...
          |* [at the door]
          |  -> door_open
          |* [outside]
          |  -> leave
          |
          |=== door_open
          |at the door. It was open.
          |-> leave
          |
          |=== leave
          |I stood up and...        |
          |{ door_open:
          |    *   I strode out of the compartment[] and I fancied I heard my master quietly tutting to himself.           -> go_outside
          |- else:
          |    *   I asked permission to leave[] and Monsieur Fogg looked surprised.   -> open_door
          |    *   I stood and went to open the door[]. Monsieur Fogg seemed untroubled by this small rebellion. -> open_door
          |}
        """.trimMargin()

    @Test fun `work with options as conditional content (example 1)`() {
        val story = InkParser.parse(condOpt, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        story.next()
        assertEquals(1, story.choiceSize)
    }

    @Test fun `work with options as conditional content (example 2)`() {
        val story = InkParser.parse(condOpt, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        story.next()
        assertEquals(2, story.choiceSize)
    }

    // --- a multi-line list block ---

    val stopping =
        """=== test
        |{ stopping:
        |    - I entered the casino.
        |    - I entered the casino again.
        |    - Once more, I went inside.
        |}
        |+ [Try again] -> test
      """.trimMargin()

    @Test fun `should go through the alternatives and stick on last when the keyword is stopping`() {
        val story = InkParser.parse(stopping, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("I entered the casino.", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("I entered the casino again.", text1[1])
        story.choose(0)
        val text2 = story.next()
        assertEquals(3, text2.size)
        assertEquals("Once more, I went inside.", text2[2])
        story.choose(0)
        val text3 = story.next()
        assertEquals(4, text3.size)
        assertEquals("Once more, I went inside.", text3[3])
    }

    val cycle =
        """=== test
        |{ cycle:
        |    - I held my breath.
        |    - I waited impatiently.
        |    - I paused.
        |}
        |+ [Try again] -> test
      """.trimMargin()

    @Test fun `should show each in turn and then cycle when the keyword is cycle`() {
        val story = InkParser.parse(cycle, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("I held my breath.", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("I waited impatiently.", text1[1])
        story.choose(0)
        val text2 = story.next()
        assertEquals(3, text2.size)
        assertEquals("I paused.", text2[2])
        story.choose(0)
        val text3 = story.next()
        assertEquals(4, text3.size)
        assertEquals("I held my breath.", text3[3])
    }

    val once =
        """=== test
        |{ once:
        |    - Would my luck hold?
        |    - Could I win the hand?
        |}
        |+ [Try again] -> test
      """.trimMargin()

    @Test fun `should show each, once, in turn, until all have been shown when the keyword is once`() {
        val story = InkParser.parse(once, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("Would my luck hold?", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("Could I win the hand?", text1[1])
        story.choose(0)
        val text2 = story.next()
        assertEquals(2, text2.size)
        story.choose(0)
        val text3 = story.next()
        assertEquals(2, text3.size)
    }

    val shuffle =
        """=== test
        |{ shuffle:
        |    -   Ace of Hearts.
        |    -   King of Spades.
        |    -   2 of Diamonds.
        |}
        |+ [Try again] -> test
      """.trimMargin()

    @Test fun `should show one at random when the keyword is shuffle`() {
        val story = InkParser.parse(shuffle, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        story.choose(0)
        val text2 = story.next()
        assertEquals(3, text2.size)
        story.choose(0)
        val text3 = story.next()
        assertEquals(4, text3.size)
        // No check of the result, as that is random
    }

    val multiline =
        """=== test
        |{ stopping:
        |    -   At the table, I drew a card. Ace of Hearts.
        |    -   2 of Diamonds.
        |        "Should I hit you again," the croupier asks.
        |    -   King of Spades.
        |    "You lose," he crowed.
        |}
        |+ [Draw a card] I drew a card. -> test
      """.trimMargin()

    @Test fun `should show multiple lines of texts from multiline list blocks`() {
        val story = InkParser.parse(multiline, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("At the table, I drew a card. Ace of Hearts.", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(3, text1.size)
        assertEquals("I drew a card. 2 of Diamonds.", text1[1])
        assertEquals("\"Should I hit you again,\" the croupier asks.", text1[2])
        story.choose(0)
        val text2 = story.next()
        assertEquals(5, text2.size)
        assertEquals("I drew a card. King of Spades.", text2[3])
        assertEquals("\"You lose,\" he crowed.", text2[4])
    }

    val multilineDivert =
        """=== test
        |{ stopping:
        |    -   At the table, I drew a card. Ace of Hearts.
        |    -   2 of Diamonds.
        |        "Should I hit you again," the croupier asks.
        |    -   King of Spades.
        |        -> he_crowed
        |}
        |+ [Draw a card] I drew a card. -> test
        |
        |== he_crowed
        |"You lose," he crowed.
      """.trimMargin()

    @Test fun `should allow for embedded diverts`() {
        val story = InkParser.parse(multilineDivert, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("At the table, I drew a card. Ace of Hearts.", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(3, text1.size)
        assertEquals("I drew a card. 2 of Diamonds.", text1[1])
        assertEquals("\"Should I hit you again,\" the croupier asks.", text1[2])
        story.choose(0)
        val text2 = story.next()
        assertEquals(5, text2.size)
        assertEquals("I drew a card. King of Spades.", text2[3])
        assertEquals("\"You lose,\" he crowed.", text2[4])
    }

    val multilineChoice =
        """=== test
        |{ stopping:
        |    -   At the table, I drew a card. Ace of Hearts.
        |    -   2 of Diamonds.
        |        "Should I hit you again," the croupier asks.
        |        * [No.] I left the table. -> END
        |    -   King of Spades.
        |        "You lose," he crowed.
        |        -> END
        |}
        |+ [Draw a card] I drew a card. -> test
      """.trimMargin()

    @Test fun `should allow for embedded choices`() {
        val story = InkParser.parse(multilineChoice, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("At the table, I drew a card. Ace of Hearts.", text0[0])
        story.choose(0)
        story.next()
        assertEquals(2, story.choiceSize)
        story.choose(0)
        val text2 = story.next()
        assertEquals(4, text2.size)
        assertEquals("I left the table.", text2[3])
    }

    // --- a conditional block within a choices ---

    val condChoice1 =
        """=== knot
        |VAR choice = 1
        |This is a knot.
        |* [I have chosen.]
        |  { choice > 0:
        |  	  -> choice_1
        |  	- else:
        |  	  -> choice_0
        |  }
        |* I have failed.
        |  -> END
        |=== choice_0
        |This is choice 0.
        |-> END
        |=== choice_1
        |This is choice 1.
        |-> END
      """.trimMargin()

    @Test fun `should allow for diverts in the conditional that direct to another knot`() {
        val story = InkParser.parse(condChoice1, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("This is a knot.", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("This is choice 1.", text1[1])
    }

    val condChoice0 =
        """=== knot
        |VAR choice = 0
        |This is a knot.
        |* [I have chosen.]
        |  { choice > 0:
        |  	  -> choice_1
        |  	- else:
        |  	  -> choice_0
        |  }
        |* I have failed.
        |  -> END
        |=== choice_0
        |This is choice 0.
        |-> END
        |=== choice_1
        |This is choice 1.
        |-> END
      """.trimMargin()

    @Test fun `should allow for diverts in the else clause`() {
        val story = InkParser.parse(condChoice0, TestWrapper(), "Test")
        val text0 = story.next()
        assertEquals(1, text0.size)
        assertEquals("This is a knot.", text0[0])
        story.choose(0)
        val text1 = story.next()
        assertEquals(2, text1.size)
        assertEquals("This is choice 0.", text1[1])
    }
}
