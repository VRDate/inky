package ink.java.mica

/**
 * Interface to the class used to provide story file content and game objects.
 * KMP-compatible: uses String instead of InputStream.
 */
interface StoryWrapper {
    /** Read the ink file contents as a String. */
    fun getFileContent(fileId: String): String
    fun getStoryObject(objId: String): Any
    fun getInterrupt(s: String): StoryInterrupt
    fun resolveTag(t: String)
    fun logDebug(m: String)
    fun logError(m: String)
    fun logException(e: Exception)
}
