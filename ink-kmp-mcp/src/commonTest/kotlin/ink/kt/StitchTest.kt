package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StitchTest {

    // --- Stitches ---

    val autoStitch =
        """=== the_orient_express ===
        |
        |= in_first_class
        |    I settled my master.
        |    *  [Move to third class]
        |        -> in_third_class
        |    *  [Are you sure] -> the_orient_express
        |
        |= in_third_class
        |    I put myself in third.
      """.trimMargin()

    @Test fun `be automatically started with if there is no content in a knot`() {
        val story = InkParser.parse(autoStitch, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("I settled my master.", text[0])
    }

    @Test fun `be automatically diverted to if there is no other content in a knot`() {
        val story = InkParser.parse(autoStitch, TestWrapper(), "Test")
        story.next()
        story.choose(1)
        val text = story.next()
        assertEquals(2, text.size)
        assertEquals("I settled my master.", text[1])
    }

    val manualStitch =
        """=== the_orient_express ===
        |How shall we travel?
        |* [In first class] -> in_first_class
        |* [I'll go cheap] -> the_orient_express.in_third_class
        |
        |= in_first_class
        |    I settled my master.
        |    *   [Move to third class]
        |        -> in_third_class
        |
        |= in_third_class
        |    I put myself in third.
      """.trimMargin()

    @Test fun `not be diverted to if the knot has content`() {
        val story = InkParser.parse(manualStitch, TestWrapper(), "Test")
        val knotText = story.next()
        assertEquals(1, knotText.size)
        assertEquals("How shall we travel?", knotText[0])
        story.choose(1)
        val stitchText = story.next()
        assertEquals(2, stitchText.size)
        assertEquals("I put myself in third.", stitchText[1])
    }

    @Test fun `be usable locally without the full name`() {
        val story = InkParser.parse(manualStitch, TestWrapper(), "Test")
        val knotText = story.next()
        assertEquals(1, knotText.size)
        assertEquals("How shall we travel?", knotText[0])
        story.choose(0)
        val stitchText = story.next()
        assertEquals(2, stitchText.size)
        assertEquals("I settled my master.", stitchText[1])
    }
}
