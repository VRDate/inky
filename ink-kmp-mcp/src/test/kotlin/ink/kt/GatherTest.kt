package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatherTest {

    // --- an ink script containing a gather ---

    @Test fun `should gather the flow back together again after a list of choices with text`() {
        val gatherBasic =
            """=== test_knot ===
          |"What's that?" my master asked.
          |    *  "I am somewhat tired[."]," I repeated.
          |       "Really," he responded. "How deleterious."
          |    *  "Nothing, Monsieur!"[] I replied.
          |       "Very good, then."
          |    *  "I said, this journey is appalling[."] and I want no more of it."
          |       "Ah," he replied, not unkindly. "I see you are feeling frustrated. Tomorrow, things will improve."
          |- With that Monsieur Fogg left the room.
        """.trimMargin()
        val story = InkParser.parse(gatherBasic, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        val text = story.next()
        assertEquals(4, text.size)
        assertEquals("\"What's that?\" my master asked.", text[0])
        assertEquals("\"Nothing, Monsieur!\" I replied.", text[1])
        assertEquals("\"Very good, then.\"", text[2])
        assertEquals("With that Monsieur Fogg left the room.", text[3])
    }

    @Test fun `should gather the flow back together again after a list of choices without text`() {
        val gatherBasic =
            """=== test_knot ===
          |"What's that?" my master asked.
          |    *  ["I am somewhat tired."]
          |    *  ["Nothing, Monsieur!"]
          |- Monsieur Fogg ignored me and left the room.
        """.trimMargin()
        val story = InkParser.parse(gatherBasic, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        val text = story.next()
        assertEquals(2, text.size)
        assertEquals("\"What's that?\" my master asked.", text[0])
        assertEquals("Monsieur Fogg ignored me and left the room.", text[1])
    }

    @Test fun `should gather the flow back together again even if the `() {
        val gatherDivert =
            """=== test_knot ===
          |You walk to the bar, but it's so dark here you can't really make anything out. The foyer is back to the north.
          |-> test_options
          |
          |== test_options ==
          |* [Feel around for a light switch.]
          |* [Sit on a bar stool.]
          |+ [Go north.] -> foyer
          |- In the dark? You could easily disturb something.
          |  -> test_options
        """.trimMargin()
        val story = InkParser.parse(gatherDivert, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        val text = story.next()
        assertEquals(2, text.size)
        assertEquals("You walk to the bar, but it's so dark here you can't really make anything out. The foyer is back to the north.", text[0])
        assertEquals("In the dark? You could easily disturb something.", text[1])
        assertEquals(2, story.choiceSize)
    }

    val gatherChain =
        """=== escape ===
          |I ran through the forest, the dogs snapping at my heels.
          |    *   I checked the jewels[] were still in my pocket, and the feel of them brought a spring to my step. <>
          |    *  I did not pause for breath[] but kept on running. <>
          |    *   I cheered with joy. <>
          |-   The road could not be much further! Mackie would have the engine running, and then I'd be safe.
          |    *   I reached the road and looked about[]. And would you believe it?
          |    *   I should text to say Mackie is normally very reliable[]. He's never once let me down. Or rather, never once, previously to that night.
          |-   The road was empty. Mackie was nowhere to be seen.
        """.trimMargin()

    @Test fun `form chains of content with multiple gathers`() {
        val story = InkParser.parse(gatherChain, TestWrapper(), "Test")
        story.next()
        assertEquals(3, story.choiceSize)
        story.choose(1)
        val text0 = story.next()
        assertEquals(2, text0.size)
        assertEquals("I did not pause for breath but kept on running. The road could not be much further! Mackie would have the engine running, and then I'd be safe.", text0[1])
        assertEquals(2, story.choiceSize)
        story.choose(0)
        val text1 = story.next()
        assertEquals(4, text1.size)
        assertEquals("I reached the road and looked about. And would you believe it?", text1[2])
        assertEquals("The road was empty. Mackie was nowhere to be seen.", text1[3])
    }

    val nestedFlow =
        """=== test_knot ===
            |Well, Poirot? Murder or suicide?"
            |    *   "Murder!"
            |        "And who did it?"
            |        * *     "Detective-Inspector Japp!"
            |        * *     "Captain Hastings!"
            |        * *     "Myself!"
            |    *   "Suicide!"
            |    -   Mrs. Christie lowered her manuscript a moment. The rest of the writing group sat, open-mouthed.
          """.trimMargin()

    @Test fun `allow nested options to pop out to a higher level gather`() {
        val story = InkParser.parse(nestedFlow, TestWrapper(), "Test")
        story.next()
        assertEquals(2, story.choiceSize)
        story.choose(0)
        story.next()
        assertEquals(3, story.choiceSize)
        story.choose(2)
        val text = story.next()
        assertEquals(5, text.size)
        assertEquals("\"Myself!\"", text[3])
        assertEquals("Mrs. Christie lowered her manuscript a moment. The rest of the writing group sat, open-mouthed.", text[4])
    }

    val nestedGather =
        """=== test_knot ===
            |Well, Poirot? Murder or suicide?"
            |        *   "Murder!"
            |            "And who did it?"
            |            * *     "Detective-Inspector Japp!"
            |            * *     "Captain Hastings!"
            |            * *     "Myself!"
            |            - -     "You must be joking!"
            |            * *     "Mon ami, I am deadly serious."
            |            * *     "If only..."
            |        *   "Suicide!"
            |            "Really, Poirot? Are you quite sure?"
            |            * *     "Quite sure."
            |            * *     "It is perfectly obvious."
            |        -   Mrs. Christie lowered her manuscript a moment. The rest of the writing group sat, open-mouthed.
          """.trimMargin()

    @Test fun `allow nested gathers within the flow`() {
        val story = InkParser.parse(nestedGather, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        story.next()
        story.choose(2)
        val text0 = story.next()
        assertEquals(5, text0.size)
        assertEquals("\"Myself!\"", text0[3])
        assertEquals("\"You must be joking!\"", text0[4])
        assertEquals(2, story.choiceSize)
        story.choose(0)
        val text1 = story.next()
        assertEquals(7, text1.size)
        assertEquals("\"Mon ami, I am deadly serious.\"", text1[5])
        assertEquals("Mrs. Christie lowered her manuscript a moment. The rest of the writing group sat, open-mouthed.", text1[6])
    }

    val deepNesting =
        """=== test_knot ===
            |Tell us a tale, Captain!"
            |    *   "Very well, you sea-dogs. Here's a tale..."
            |        * *     "It was a dark and stormy night..."
            |                * * *   "...and the crew were restless..."
            |                        * * * *  "... and they said to their Captain..."
            |                                * * * * *       "...Tell us a tale Captain!"
            |    *   "No, it's past your bed-time."
            |-   To a man, the crew began to yawn.
          """.trimMargin()

    @Test fun `gather the flow back together again from arbitrarily deep options`() {
        val story = InkParser.parse(deepNesting, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        story.next()
        story.choose(0)
        story.next()
        story.choose(0)
        story.next()
        story.choose(0)
        story.next()
        story.choose(0)
        val text = story.next()
        assertEquals(7, text.size)
        assertEquals("\"...Tell us a tale Captain!\"", text[5])
        assertEquals("To a man, the crew began to yawn.", text[6])
    }

    val complexFlow =
        """=== test_knot ===
            |I looked at Monsieur Fogg
            | *   ... and I could contain myself no longer.
            |    'What is the purpose of our journey, Monsieur?'
            |    'A wager,' he replied.
            |    * *     'A wager!'[] I returned.
            |            He nodded.
            |            * * *   'But surely that is foolishness!'
            |            * * *  'A most serious matter then!'
            |            - - -   He nodded again.
            |            * * *   'But can we win?'
            |                    'That is what we will endeavour to find out,' he answered.
            |            * * *   'A modest wager, I trust?'
            |                    'Twenty thousand pounds,' he replied, quite flatly.
            |            * * *   I asked nothing further of him then[.], and after a final, polite cough, he offered nothing more to me. <>
            |    * *     'Ah[.'],' I replied, uncertain what I thought.
            |    - -     After that, <>
            |*   ... but I said nothing[] and <>
            |- we passed the day in silence.
            |  -> END
          """.trimMargin()

    @Test fun `offer a compact way to weave and blend text and options (Example 1)`() {
        val story = InkParser.parse(complexFlow, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        val text = story.next()
        assertEquals(2, text.size)
        assertEquals("... but I said nothing and we passed the day in silence.", text[1])
    }

    @Test fun `offer a compact way to weave and blend text and options (Example 2)`() {
        val story = InkParser.parse(complexFlow, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        val text0 = story.next()
        assertEquals(4, text0.size)
        story.choose(0)
        val text1 = story.next()
        assertEquals(6, text1.size)
        story.choose(1)
        val text2 = story.next()
        assertEquals(8, text2.size)
        story.choose(1)
        val text3 = story.next()
        assertEquals(11, text3.size)
    }

    @Test fun `offer a compact way to weave and blend text and options (Example 3)`() {
        val story = InkParser.parse(complexFlow, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        val text0 = story.next()
        assertEquals(4, text0.size)
        story.choose(0)
        val text1 = story.next()
        assertEquals(6, text1.size)
        story.choose(1)
        val text2 = story.next()
        assertEquals(8, text2.size)
        story.choose(2)
        val text3 = story.next()
        assertEquals(9, text3.size)
        assertEquals("I asked nothing further of him then, and after a final, polite cough, he offered nothing more to me. After that, we passed the day in silence.", text3[8])
    }
}
