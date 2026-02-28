package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DivertTest {

    // --- Diverts ---

    val simpleDivert =
        """=== back_in_london ===
        |We arrived into London at 9.45pm exactly.
        |-> hurry_home
        |
        |=== hurry_home ===
        |We hurried home to Savile Row as fast as we could.
      """.trimMargin()

    @Test fun `divert text from one knot-stitch to another`() {
        val story = InkParser.parse(simpleDivert, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(2, text.size)
        assertEquals("We arrived into London at 9.45pm exactly.", text[0])
        assertEquals("We hurried home to Savile Row as fast as we could.", text[1])
    }

    val invisibleDivert =
        """=== hurry_home ===
        |We hurried home to Savile Row -> as_fast_as_we_could
        |
        |=== as_fast_as_we_could ===
        |as fast as we could.
      """.trimMargin()

    @Test fun `divert from one line of text to new content invisibly`() {
        val story = InkParser.parse(invisibleDivert, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("We hurried home to Savile Row as fast as we could.", text[0])
    }

    val divertOnChoice =
        """== paragraph_1 ===
        |You stand by the wall of Analand, sword in hand.
        |* [Open the gate] -> paragraph_2
        |
        |=== paragraph_2 ===
        |You open the gate, and step out onto the path.
      """.trimMargin()

    @Test fun `branch directly from choices`() {
        val story = InkParser.parse(divertOnChoice, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        val text = story.next()
        assertEquals(2, text.size)
        assertEquals("You open the gate, and step out onto the path.", text[1])
    }

    val complexBranching =
        """=== back_in_london ===
        |
        |We arrived into London at 9.45pm exactly.
        |
        |*   "There is not a moment to lose!"[] I declared.
        |    -> hurry_outside
        |
        |*   "Monsieur, let us savour this moment!"[] I declared.
        |    My master clouted me firmly around the head and dragged me out of the door.
        |    -> dragged_outside
        |
        |*   [We hurried home] -> hurry_outside
        |
        |
        |=== hurry_outside ===
        |We hurried home to Savile Row -> as_fast_as_we_could
        |
        |
        |=== dragged_outside ===
        |He insisted that we hurried home to Savile Row
        |-> as_fast_as_we_could
        |
        |
        |=== as_fast_as_we_could ===
        |<> as fast as we could.
      """.trimMargin()

    @Test fun `be usable to branch and join text seamlessly (example 1)`() {
        // First path through text
        val story = InkParser.parse(complexBranching, TestWrapper(), "Test")
        story.next()
        story.choose(0)
        val text = story.next()
        assertEquals(3, text.size)
        assertEquals("\"There is not a moment to lose!\" I declared.", text[1])
        assertEquals("We hurried home to Savile Row as fast as we could.", text[2])
    }

    @Test fun `be usable to branch and join text seamlessly (example 2)`() {
        // Second path through text
        val story = InkParser.parse(complexBranching, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        val text = story.next()
        assertEquals(4, text.size)
        assertEquals("\"Monsieur, let us savour this moment!\" I declared.", text[1])
        assertEquals("My master clouted me firmly around the head and dragged me out of the door.", text[2])
        assertEquals("He insisted that we hurried home to Savile Row as fast as we could.", text[3])
    }

    @Test fun `be usable to branch and join text seamlessly (example 3)`() {
        // Third path through text
        val story = InkParser.parse(complexBranching, TestWrapper(), "Test")
        story.next()
        story.choose(2)
        val text = story.next()
        assertEquals(2, text.size)
        assertEquals("We hurried home to Savile Row as fast as we could.", text[1])
    }
}
