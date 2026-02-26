///usr/bin/env jbang "$0" "$@" ; exit $?
//KOTLIN 2.1.0
//DEPS io.ktor:ktor-server-core:3.1.1
//DEPS io.ktor:ktor-server-netty:3.1.1
//DEPS io.ktor:ktor-server-content-negotiation:3.1.1
//DEPS io.ktor:ktor-serialization-kotlinx-json:3.1.1
//DEPS io.ktor:ktor-server-cors:3.1.1
//DEPS io.ktor:ktor-server-status-pages:3.1.1
//DEPS io.ktor:ktor-server-sse:3.1.1
//DEPS org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3
//DEPS org.graalvm.polyglot:polyglot:25.0.2
//DEPS org.graalvm.polyglot:js-community:25.0.2
//DEPS ch.qos.logback:logback-classic:1.5.15
//SOURCES src/ink/mcp/InkEngine.kt
//SOURCES src/ink/mcp/McpTypes.kt
//SOURCES src/ink/mcp/McpTools.kt
//SOURCES src/ink/mcp/McpRouter.kt
//NATIVE_OPTIONS --no-fallback -H:+ReportExceptionStackTraces
//JAVA 21

package ink.mcp

/**
 * Inky MCP Server — JBang thin launcher.
 *
 * Exposes the ink compiler (inkjs) via MCP (Model Context Protocol)
 * using GraalJS as the headless JavaScript engine.
 *
 * Usage:
 *   jbang InkyMcp.kt [--port 3001] [--inkjs ../app/node_modules/inkjs/dist/ink-full.js]
 *   jbang --native InkyMcp.kt   # Build native binary
 *
 * Install via SDKMAN:
 *   sdk install jbang
 *   sdk install java 21.0.5-graalce
 */
fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args)
    val port = parsedArgs["port"]?.toIntOrNull() ?: 3001
    val inkjsPath = parsedArgs["inkjs"] ?: resolveDefaultInkjsPath()
    val bidifyPath = parsedArgs["bidify"] ?: resolveDefaultBidifyPath()

    println("Inky MCP Server v0.1.0")
    println("  Port:    $port")
    println("  inkjs:   $inkjsPath")
    println("  bidify:  ${bidifyPath ?: "(not found)"}")
    println()

    startServer(port, inkjsPath, bidifyPath)
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--port", "-p" -> { if (i + 1 < args.size) result["port"] = args[++i] }
            "--inkjs" -> { if (i + 1 < args.size) result["inkjs"] = args[++i] }
            "--bidify" -> { if (i + 1 < args.size) result["bidify"] = args[++i] }
            "--help", "-h" -> {
                println("""
                    Usage: jbang InkyMcp.kt [options]
                    Options:
                      --port, -p <port>    Server port (default: 3001)
                      --inkjs <path>       Path to ink-full.js
                      --bidify <path>      Path to bidify.js
                      --help, -h           Show this help
                """.trimIndent())
                kotlin.system.exitProcess(0)
            }
        }
        i++
    }
    return result
}

private fun resolveDefaultInkjsPath(): String {
    val candidates = listOf(
        "../app/node_modules/inkjs/dist/ink-full.js",
        "node_modules/inkjs/dist/ink-full.js",
        "ink-full.js"
    )
    for (c in candidates) {
        val f = java.io.File(c)
        if (f.exists()) return f.absolutePath
    }
    error("inkjs not found. Specify --inkjs <path> or run 'npm install inkjs' in ../app/")
}

private fun resolveDefaultBidifyPath(): String? {
    val candidates = listOf(
        "../app/renderer/bidify.js",
        "bidify.js"
    )
    for (c in candidates) {
        val f = java.io.File(c)
        if (f.exists()) return f.absolutePath
    }
    return null
}
