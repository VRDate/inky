package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlueTest {

    // --- Glue ---

    @Test fun `bind text together across multiple lines of text`() {
        val simpleGlue =
            """=== test_knot ===
            |Some <>
            |content<>
            | with glue.
            |""".trimMargin()

        val story = InkParser.parse(simpleGlue, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Some content with glue.", text[0])
    }

    @Test fun `bind text together across multiple knots-stitches`() {
        val glueWithDivert =
            """=== hurry_home ===
            |We hurried home <>
            |-> to_savile_row
            |
            |=== to_savile_row ===
            |to Savile Row
            |-> as_fast_as_we_could
            |
            |=== as_fast_as_we_could ===
            |<> as fast as we could.""".trimMargin()

        val story = InkParser.parse(glueWithDivert, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("We hurried home to Savile Row as fast as we could.", text[0])
    }
}
