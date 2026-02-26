///usr/bin/env jbang "$0" "$@" ; exit $?
//KOTLIN 2.1.0
//DEPS io.ktor:ktor-server-core:3.1.1
//DEPS io.ktor:ktor-server-netty:3.1.1
//DEPS io.ktor:ktor-server-content-negotiation:3.1.1
//DEPS io.ktor:ktor-serialization-kotlinx-json:3.1.1
//DEPS io.ktor:ktor-server-cors:3.1.1
//DEPS io.ktor:ktor-server-status-pages:3.1.1
//DEPS io.ktor:ktor-server-sse:3.1.1
//DEPS io.ktor:ktor-server-websockets:3.1.1
//DEPS io.ktor:ktor-server-auth:3.1.1
//DEPS io.ktor:ktor-server-auth-jwt:3.1.1
//DEPS org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3
//DEPS org.graalvm.polyglot:polyglot:25.0.2
//DEPS org.graalvm.polyglot:js:25.0.2
//DEPS org.apache.camel:camel-core:4.18.0
//DEPS org.apache.camel:camel-main:4.18.0
//DEPS org.apache.camel:camel-direct:4.18.0
//DEPS org.apache.camel:camel-langchain4j-chat:4.18.0
//DEPS org.apache.camel:camel-langchain4j-tools:4.18.0
//DEPS dev.langchain4j:langchain4j:1.11.0
//DEPS dev.langchain4j:langchain4j-jlama:1.11.0-beta19
//DEPS com.github.tjake:jlama-core:0.8.4
//DEPS dev.langchain4j:langchain4j-open-ai:1.11.0
//DEPS net.sourceforge.plantuml:plantuml-mit:1.2024.8
//DEPS org.mnode.ical4j:ical4j:4.0.7
//DEPS com.googlecode.ez-vcard:ez-vcard:0.12.1
//DEPS com.github.lookfirst:sardine:5.12
//DEPS ch.qos.logback:logback-classic:1.5.15
//SOURCES src/ink/mcp/InkEngine.kt
//SOURCES src/ink/mcp/McpTypes.kt
//SOURCES src/ink/mcp/McpTools.kt
//SOURCES src/ink/mcp/McpRouter.kt
//SOURCES src/ink/mcp/LlmEngine.kt
//SOURCES src/ink/mcp/LmStudioEngine.kt
//SOURCES src/ink/mcp/CamelRoutes.kt
//SOURCES src/ink/mcp/DictaLmConfig.kt
//SOURCES src/ink/mcp/LlmServiceConfig.kt
//SOURCES src/ink/mcp/ColabEngine.kt
//SOURCES src/ink/mcp/InkDebugEngine.kt
//SOURCES src/ink/mcp/InkEditEngine.kt
//SOURCES src/ink/mcp/InkMdEngine.kt
//SOURCES src/ink/mcp/SillyTavernConfig.kt
//SOURCES src/ink/mcp/Ink2PumlEngine.kt
//SOURCES src/ink/mcp/InkAuthEngine.kt
//SOURCES src/ink/mcp/InkCalendarEngine.kt
//SOURCES src/ink/mcp/InkVCardEngine.kt
//SOURCES src/ink/mcp/InkWebDavEngine.kt
//JAVA_OPTIONS --add-modules jdk.incubator.vector --enable-preview
//NATIVE_OPTIONS --no-fallback -H:+ReportExceptionStackTraces
//JAVA 21

package ink.mcp

/**
 * Inky MCP Server — JBang thin launcher with multi-mode support.
 *
 * Modes:
 *   mcp       — Full MCP server (default): 46 tools, collab, debug, edit
 *   jlama     — Local JLama inference + all ink tools
 *   lmstudio  — External LM Studio backend + all ink tools
 *   pwa       — Ink-only, no LLM (lightest mode)
 *
 * Usage:
 *   jbang InkyMcp.kt --mode mcp
 *   jbang InkyMcp.kt --mode jlama --model thinking-1.7b
 *   jbang InkyMcp.kt --mode lmstudio --lm-studio-url http://localhost:1234/v1
 *   jbang InkyMcp.kt --mode pwa
 *   jbang --native InkyMcp.kt   # Build native binary
 *
 * Install via SDKMAN:
 *   sdk install jbang
 *   sdk install java 21.0.5-graal
 */
fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args)
    val mode = parsedArgs["mode"] ?: "mcp"
    val port = parsedArgs["port"]?.toIntOrNull() ?: 3001
    val inkjsPath = parsedArgs["inkjs"] ?: resolveDefaultInkjsPath()
    val bidifyPath = parsedArgs["bidify"] ?: resolveDefaultBidifyPath()
    val enableLlm = mode != "pwa"
    val modelCachePath = parsedArgs["model-cache"]
    val autoLoadModel = parsedArgs["model"]
    val lmStudioUrl = parsedArgs["lm-studio-url"] ?: "http://localhost:1234/v1"
    val lmStudioModel = parsedArgs["lm-studio-model"]

    println("Inky MCP Server v0.3.0")
    println("  Mode:        $mode")
    println("  Port:        $port")
    println("  inkjs:       $inkjsPath")
    println("  bidify:      ${bidifyPath ?: "(not found)"}")
    println("  Collab:      WebSocket /collab/:docId")
    when (mode) {
        "jlama" -> println("  LLM:         JLama (local)")
        "lmstudio" -> println("  LLM:         LM Studio ($lmStudioUrl)")
        "pwa" -> println("  LLM:         disabled")
        else -> println("  LLM:         JLama + LM Studio + 11 cloud services")
    }
    println()

    startServer(
        port = port,
        inkjsPath = inkjsPath,
        bidifyPath = bidifyPath,
        enableLlm = enableLlm,
        modelCachePath = modelCachePath,
        mode = mode,
        autoLoadModel = autoLoadModel,
        lmStudioUrl = if (mode == "lmstudio") lmStudioUrl else null,
        lmStudioModel = lmStudioModel
    )
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--port", "-p" -> { if (i + 1 < args.size) result["port"] = args[++i] }
            "--mode", "-m" -> { if (i + 1 < args.size) result["mode"] = args[++i] }
            "--inkjs" -> { if (i + 1 < args.size) result["inkjs"] = args[++i] }
            "--bidify" -> { if (i + 1 < args.size) result["bidify"] = args[++i] }
            "--model-cache" -> { if (i + 1 < args.size) result["model-cache"] = args[++i] }
            "--model" -> { if (i + 1 < args.size) result["model"] = args[++i] }
            "--lm-studio-url" -> { if (i + 1 < args.size) result["lm-studio-url"] = args[++i] }
            "--lm-studio-model" -> { if (i + 1 < args.size) result["lm-studio-model"] = args[++i] }
            "--no-llm" -> { result["mode"] = "pwa" }
            "--help", "-h" -> {
                println("""
                    Usage: jbang InkyMcp.kt [options]

                    Modes:
                      --mode mcp          Full MCP server (default) — 61 tools
                      --mode jlama        Local JLama inference
                      --mode lmstudio     External LM Studio
                      --mode pwa          Ink-only, no LLM

                    Options:
                      --port, -p <port>             Server port (default: 3001)
                      --inkjs <path>                Path to ink-full.js
                      --bidify <path>               Path to bidify.js
                      --model <id>                  Auto-load model on startup
                      --model-cache <path>          GGUF cache dir (default: ~/.jlama)
                      --lm-studio-url <url>         LM Studio URL (default: http://localhost:1234/v1)
                      --lm-studio-model <name>      LM Studio model name
                      --no-llm                      Shortcut for --mode pwa
                      --help, -h                    Show this help

                    Tools: ink (17) + debug (8) + edit (6) + ink-md (3) + puml (5) + llm (8) + services (2) + collab (2) + calendar (4) + vcard (4) + auth (2)
                    Collab: Connect Yjs clients to ws://localhost:3001/collab/:docId
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
        "../ink-electron/node_modules/inkjs/dist/ink-full.js",
        "node_modules/inkjs/dist/ink-full.js",
        "ink-full.js"
    )
    for (c in candidates) {
        val f = java.io.File(c)
        if (f.exists()) return f.absolutePath
    }
    error("inkjs not found. Specify --inkjs <path> or run 'npm install inkjs' in ../ink-electron/")
}

private fun resolveDefaultBidifyPath(): String? {
    val candidates = listOf(
        "../ink-electron/renderer/bidify.js",
        "bidify.js"
    )
    for (c in candidates) {
        val f = java.io.File(c)
        if (f.exists()) return f.absolutePath
    }
    return null
}
