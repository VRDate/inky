package ink.kt.mica

import java.io.InputStream

/** Interface to the class used to convert file IDs/file names to an InputStream. */
// TODO: Replace InputStream with KMP-compatible type (okio.Source or String) for commonMain
interface StoryWrapper {
    fun getStream(fileId: String): InputStream
    fun getStoryObject(objId: String): Any
    fun getInterrupt(s: String): StoryInterrupt
    fun resolveTag(t: String)
    fun logDebug(m: String)
    fun logError(m: String)
    fun logException(e: Exception)
}
