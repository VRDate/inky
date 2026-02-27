package ink.mcp

import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

/**
 * Loads Unicode data files with a three-tier strategy:
 *   1. Classpath resource (bundled in JAR)
 *   2. Local cache directory (~/.inky/unicode-cache/)
 *   3. Fetch from unicode.org permanent URL, then cache locally
 *
 * No extra dependencies — uses java.net.URL (JDK standard).
 */
open class UnicodeDataLoader(
    private val cacheDir: File = File(
        System.getProperty("user.home"), ".inky/unicode-cache"
    )
) {
    private val log = LoggerFactory.getLogger(UnicodeDataLoader::class.java)

    /**
     * Load lines from a Unicode data file.
     *
     * @param classpathResource resource path, e.g., "/unicode/emoji-test.txt"
     * @param remoteUrl fallback URL for fetching
     * @param cacheFileName local cache file name
     */
    fun loadLines(
        classpathResource: String,
        remoteUrl: String,
        cacheFileName: String
    ): List<String> {
        // 1. Try classpath
        val resource = javaClass.getResourceAsStream(classpathResource)
        if (resource != null) {
            log.info("Loaded {} from classpath", classpathResource)
            return resource.bufferedReader().use { it.readLines() }
        }

        // 2. Try cache
        val cached = File(cacheDir, cacheFileName)
        if (cached.exists() && cached.length() > 0) {
            log.info("Loaded {} from cache: {}", cacheFileName, cached.absolutePath)
            return cached.readLines()
        }

        // 3. Fetch from URL
        log.info("Fetching {} from {}", cacheFileName, remoteUrl)
        return try {
            val lines = URL(remoteUrl).openStream().bufferedReader().use { it.readLines() }
            // Cache for next time
            cacheDir.mkdirs()
            cached.writeText(lines.joinToString("\n"))
            log.info("Cached {} ({} lines) to {}", cacheFileName, lines.size, cached.absolutePath)
            lines
        } catch (e: Exception) {
            log.warn("Failed to fetch {}: {}", remoteUrl, e.message)
            emptyList()
        }
    }

    open fun loadEmojiTest(): List<String> = loadLines(
        EMOJI_TEST_RESOURCE, EMOJI_TEST_URL, "emoji-test.txt"
    )

    open fun loadUnicodeData(): List<String> = loadLines(
        UNICODE_DATA_RESOURCE, UNICODE_DATA_URL, "UnicodeData.txt"
    )

    companion object {
        const val EMOJI_TEST_URL = "https://unicode.org/Public/emoji/latest/emoji-test.txt"
        const val UNICODE_DATA_URL = "https://unicode.org/Public/UNIDATA/UnicodeData.txt"

        const val EMOJI_TEST_RESOURCE = "/unicode/emoji-test.txt"
        const val UNICODE_DATA_RESOURCE = "/unicode/UnicodeData.txt"
    }
}
