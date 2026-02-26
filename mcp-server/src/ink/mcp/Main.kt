package ink.mcp

/**
 * Entry point for Gradle-based builds.
 * JBang entry is InkyMcp.kt (project root).
 */
fun main(args: Array<String>) {
    val parsedArgs = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--port", "-p" -> { if (i + 1 < args.size) parsedArgs["port"] = args[++i] }
            "--inkjs" -> { if (i + 1 < args.size) parsedArgs["inkjs"] = args[++i] }
            "--bidify" -> { if (i + 1 < args.size) parsedArgs["bidify"] = args[++i] }
        }
        i++
    }

    val port = parsedArgs["port"]?.toIntOrNull() ?: 3001
    val inkjsPath = parsedArgs["inkjs"] ?: resolveDefault(
        "../app/node_modules/inkjs/dist/ink-full.js",
        "node_modules/inkjs/dist/ink-full.js",
        "ink-full.js"
    ) ?: error("inkjs not found. Specify --inkjs <path>")

    val bidifyPath = parsedArgs["bidify"] ?: resolveDefault(
        "../app/renderer/bidify.js",
        "bidify.js"
    )

    println("Inky MCP Server v0.1.0")
    println("  Port:    $port")
    println("  inkjs:   $inkjsPath")
    println("  bidify:  ${bidifyPath ?: "(not found)"}")
    println()

    startServer(port, inkjsPath, bidifyPath)
}

private fun resolveDefault(vararg candidates: String): String? {
    for (c in candidates) {
        val f = java.io.File(c)
        if (f.exists()) return f.absolutePath
    }
    return null
}
