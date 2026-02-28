package ink.mcp

/**
 * Inky MCP Server — Multi-mode entry point.
 *
 * Modes:
 *   mcp       — Full MCP server with SSE transport (default)
 *   jlama     — MCP server with local JLama inference
 *   lmstudio  — MCP server using external LM Studio
 *   pwa       — Lightweight ink-only server (no LLM)
 *
 * Usage:
 *   gradle run --args='--mode mcp'
 *   gradle run --args='--mode lmstudio --lm-studio-url http://localhost:1234/v1'
 *   gradle run --args='--mode jlama --model thinking-1.7b'
 *   gradle run --args='--mode pwa --no-llm'
 */
fun main(args: Array<String>) {
    val parsedArgs = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--port", "-p" -> { if (i + 1 < args.size) parsedArgs["port"] = args[++i] }
            "--mode", "-m" -> { if (i + 1 < args.size) parsedArgs["mode"] = args[++i] }
            "--inkjs" -> { if (i + 1 < args.size) parsedArgs["inkjs"] = args[++i] }
            "--bidify" -> { if (i + 1 < args.size) parsedArgs["bidify"] = args[++i] }
            "--model-cache" -> { if (i + 1 < args.size) parsedArgs["model-cache"] = args[++i] }
            "--model" -> { if (i + 1 < args.size) parsedArgs["model"] = args[++i] }
            "--lm-studio-url" -> { if (i + 1 < args.size) parsedArgs["lm-studio-url"] = args[++i] }
            "--lm-studio-model" -> { if (i + 1 < args.size) parsedArgs["lm-studio-model"] = args[++i] }
            "--no-llm" -> { parsedArgs["mode"] = "pwa" }
        }
        i++
    }

    val mode = parsedArgs["mode"] ?: "mcp"
    val port = parsedArgs["port"]?.toIntOrNull() ?: 3001
    val inkjsPath = parsedArgs["inkjs"] ?: resolveDefault(
        "src/jsMain/ink/js/electron/node_modules/inkjs/dist/ink-full.js",
        "../ink-electron/node_modules/inkjs/dist/ink-full.js",
        "node_modules/inkjs/dist/ink-full.js",
        "ink-full.js"
    ) ?: error("inkjs not found. Specify --inkjs <path>")

    val bidifyPath = parsedArgs["bidify"] ?: resolveDefault(
        "src/jsMain/ink/js/electron/renderer/bidify.js",
        "../ink-electron/renderer/bidify.js",
        "bidify.js"
    )

    val enableLlm = mode != "pwa"
    val modelCachePath = parsedArgs["model-cache"]
    val autoLoadModel = parsedArgs["model"]
    val lmStudioUrl = parsedArgs["lm-studio-url"] ?: "http://localhost:1234/v1"
    val lmStudioModel = parsedArgs["lm-studio-model"]

    val keycloakUrl = System.getenv("KEYCLOAK_REALM_URL")

    println("Inky MCP Server v0.3.1")
    println("  Mode:        $mode")
    println("  Port:        $port")
    println("  inkjs:       $inkjsPath")
    println("  bidify:      ${bidifyPath ?: "(not found)"}")
    println("  Auth:        ${if (keycloakUrl != null) "Keycloak ($keycloakUrl)" else "disabled (open access)"}")
    println("  WebDAV:      /dav/ (ink-scripts filesystem)")
    println("  Collab:      WebSocket /collab/:docId")
    when (mode) {
        "jlama" -> {
            println("  LLM:         JLama (local)")
            println("  Model cache: ${modelCachePath ?: "~/.jlama"}")
            if (autoLoadModel != null) println("  Auto-load:   $autoLoadModel")
        }
        "lmstudio" -> {
            println("  LLM:         LM Studio (external)")
            println("  LM Studio:   $lmStudioUrl")
            if (lmStudioModel != null) println("  Model:       $lmStudioModel")
        }
        "pwa" -> println("  LLM:         disabled (ink-only mode)")
        else -> println("  LLM:         enabled (JLama + LM Studio supported)")
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

private fun resolveDefault(vararg candidates: String): String? {
    for (c in candidates) {
        val f = java.io.File(c)
        if (f.exists()) return f.absolutePath
    }
    return null
}
