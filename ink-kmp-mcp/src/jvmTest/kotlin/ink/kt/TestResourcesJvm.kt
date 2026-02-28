package ink.kt

/**
 * JVM actual — uses ClassLoader for classpath resources, java.io.File for file:// URIs.
 */
actual object TestResources {

    actual fun loadText(uri: String): String = when {
        uri.startsWith("file://") -> java.io.File(java.net.URI(uri)).readText()
        uri.startsWith("http://") || uri.startsWith("https://") ->
            java.net.URI(uri).toURL().readText()
        else -> {
            val path = uri.removePrefix("resource:")
            val stream = TestResources::class.java.classLoader.getResourceAsStream(path)
                ?: error("Test resource not found: $path")
            stream.bufferedReader().use { it.readText() }
        }
    }

    actual fun loadLines(uri: String): List<String> = loadText(uri).lines()

    actual fun loadTextOrNull(uri: String): String? = try {
        loadText(uri)
    } catch (_: Exception) {
        null
    }

    actual fun tempDir(prefix: String): String =
        kotlin.io.path.createTempDirectory(prefix).toString()

    actual fun exists(path: String): Boolean =
        TestResources::class.java.classLoader
            .getResource(path.removePrefix("resource:")) != null
}
