package ink.kt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnotTest {

    // --- A knot with a single line of plain text ---

    @Test fun `return a single line of plain text as output`() {
        val testData = """== hello_world
                       |Hello, World!""".trimMargin()

        val story = InkParser.parse(testData, TestWrapper(), "test")
        val text = story.next()
        assertEquals(1, text.size)
        assertEquals("Hello, World!", text[0])
    }

    // --- A knot with multiple lines line of plain text ---

    @Test fun `return an equal number of lines as output`() {
        val testData = """== hello_world
                        |Hello, world!
                        |Hello?
                        |Hello, are you there?""".trimMargin()

        val story = InkParser.parse(testData, TestWrapper(), "test")
        val text = story.next()
        assertEquals(3, text.size)
        assertEquals("Hello, world!", text[0])
        assertEquals("Hello?", text[1])
        assertEquals("Hello, are you there?", text[2])
    }

    // --- A knot with multiple lines line of plain text ---

    @Test fun `return an equal number of lines as output - 2`() {
        val testData = """== hello_world
                        |Hello, world!
                        |Hello?
                        |
                        |Hello, are you there?""".trimMargin()

        val story = InkParser.parse(testData, TestWrapper(), "test")
        val text = story.next()
        assertEquals(3, text.size)
        assertEquals("Hello, world!", text[0])
        assertEquals("Hello?", text[1])
        assertEquals("Hello, are you there?", text[2])
    }
}
