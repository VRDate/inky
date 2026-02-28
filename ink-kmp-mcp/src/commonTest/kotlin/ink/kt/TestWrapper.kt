package ink.kt

/**
 * Test stub for [StoryWrapper] — pure Kotlin, no JVM dependencies.
 *
 * Ported from `ink.java.mica.test.helpers.TestWrapper` (Spek/JVM).
 * Uses [getFileContent] (String) instead of `getStream` (InputStream).
 */
class TestWrapper : StoryWrapper {

    override fun getFileContent(fileId: String): String {
        if (fileId.equals("includeTest1", ignoreCase = true)) {
            return """|=== includeKnot ===
                      |This is an included knot.
                      """.trimMargin()
        }
        throw UnsupportedOperationException("Included fileId $fileId not mocked")
    }

    override fun getStoryObject(objId: String): Any {
        throw UnsupportedOperationException("not implemented")
    }

    override fun getInterrupt(s: String): StoryInterrupt {
        throw UnsupportedOperationException("not implemented")
    }

    override fun resolveTag(t: String) {
        // NOOP
    }

    override fun logDebug(m: String) {
        // NOOP
    }

    override fun logError(m: String) {
        // NOOP
    }

    override fun logException(e: Exception) {
        // NOOP
    }
}
