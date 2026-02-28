package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncludeTest {

    // --- Includes ---

    @Test fun `process an INCLUDE statement and add the content of the include file`() {
        val include1 =
            """INCLUDE includeTest1
            |=== knotA ===
            |This is a knot. -> includeKnot
      """.trimMargin()

        val story = InkParser.parse(include1, TestWrapper(), "Test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("This is a knot. This is an included knot.", text[0])
    }
}
