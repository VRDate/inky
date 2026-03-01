# JBang Native Launcher — Multi-Mode Runtime with Smart Configuration

> JBang thin launcher + GraalVM native-image for the Inky MCP server
> OS/headless detection, CPU/GPU system info, smart mode-based state configuration
>
> **PlantUML diagram**: [`jbang-launcher.puml`](jbang-launcher.puml)
> **Related**: [`KMP_CICD.md`](KMP_CICD.md) | [`MAUI_CICD.md`](MAUI_CICD.md) | [`UNITY_CICD.md`](UNITY_CICD.md)

---

## 1. Launcher Modes — 5 Running Configurations

| Mode | Command | LLM | Ink Tools | Collab | Use Case |
|------|---------|-----|-----------|--------|----------|
| `mcp` | `jbang InkyMcp.kt` | JLama + LM Studio + 11 cloud | 79 tools | Yjs WebSocket | Full MCP server (default) |
| `jlama` | `jbang InkyMcp.kt --mode jlama` | JLama local only | 79 tools | Yjs WebSocket | Local inference, no cloud |
| `lmstudio` | `jbang InkyMcp.kt --mode lmstudio` | LM Studio external | 79 tools | Yjs WebSocket | External LLM backend |
| `pwa` | `jbang InkyMcp.kt --mode pwa` | Disabled | 46 tools (ink-only) | Yjs WebSocket | Lightweight, no LLM overhead |
| `electron` | `cd ink-electron && npm start` | N/A | inkjs + inklecate | N/A | Desktop editor (standalone) |

### Mode → Engine Initialization

| Engine | mcp | jlama | lmstudio | pwa | electron |
|--------|-----|-------|----------|-----|----------|
| InkEngine (GraalJS + inkjs) | ✅ | ✅ | ✅ | ✅ | ❌ (uses inkjs directly) |
| ColabEngine (Yjs) | ✅ | ✅ | ✅ | ✅ | ❌ |
| InkAuthEngine (Keycloak) | ✅ | ✅ | ✅ | ✅ | ❌ |
| EditEngine + Ink2PumlEngine | ✅ | ✅ | ✅ | ✅ | ❌ |
| AssetEventBus (RSocket) | ✅ | ✅ | ✅ | ✅ | ❌ |
| LlmEngine (JLama) | ✅ | ✅ | ❌ | ❌ | ❌ |
| LmStudioEngine | ❌ | ❌ | ✅ | ❌ | ❌ |
| CamelRoutes (LLM pipeline) | ✅ | ✅ | ❌ | ❌ | ❌ |
| InkCalendarEngine (iCal4j) | ✅ | ✅ | ✅ | ✅ | ❌ |
| InkVCardEngine (ez-vcard) | ✅ | ✅ | ✅ | ✅ | ❌ |
| InkWebDavEngine (Sardine) | ✅ | ✅ | ✅ | ✅ | ❌ |

---

## 2. OS & Headless Detection

Smart detection at startup — probe the runtime environment to auto-configure mode.

### SystemInfo Data Model

```kotlin
data class SystemInfo(
    val os: OS,              // Linux, macOS, Windows
    val arch: Arch,          // x86_64, aarch64
    val headless: Boolean,   // no DISPLAY/WAYLAND_DISPLAY
    val container: Boolean,  // /.dockerenv or cgroup
    val wsl: Boolean,        // /proc/version contains "microsoft"
    val termux: Boolean,     // TERMUX_VERSION env var
    val gpu: GpuInfo?,       // Vulkan/OpenGL availability
    val cpuCores: Int,       // Runtime.availableProcessors()
    val memory: Long,        // Runtime.maxMemory() in bytes
    val vectorApi: Boolean,  // jdk.incubator.vector available (JLama needs this)
)

enum class OS { Linux, macOS, Windows }
enum class Arch { x86_64, aarch64 }

data class GpuInfo(
    val vulkan: Boolean,     // vulkaninfo --summary exit code 0
    val opengl: Boolean,     // glxinfo or EGL available
    val renderer: String?,   // GPU name (e.g. "llvmpipe", "NVIDIA RTX 4090")
    val software: Boolean,   // true if mesa/llvmpipe (no real GPU)
)
```

### Detection Logic

| Check | Method | Fallback |
|-------|--------|----------|
| **OS** | `System.getProperty("os.name")` | `"linux"` |
| **Arch** | `System.getProperty("os.arch")` | `"x86_64"` |
| **Headless** | `DISPLAY == null && WAYLAND_DISPLAY == null` | `true` on servers |
| **Container** | `File("/.dockerenv").exists()` or `/proc/1/cgroup` contains `"docker"` | `false` |
| **WSL** | `/proc/version` contains `"microsoft"` or `"WSL"` | `false` |
| **Termux** | `System.getenv("TERMUX_VERSION") != null` | `false` |
| **GPU/Vulkan** | `/dev/dri/renderD*` exists + `vulkaninfo --summary` exit code | `null` |
| **GPU/OpenGL** | `glxinfo` or EGL probe | `null` |
| **Vector API** | `Class.forName("jdk.incubator.vector.FloatVector")` | `false` |
| **CPU cores** | `Runtime.getRuntime().availableProcessors()` | `1` |
| **Memory** | `Runtime.getRuntime().maxMemory()` | `256MB` |

### Existing `osClassifier()` in `build.gradle.kts`

The build already detects OS/arch for JLama native dependencies:

```kotlin
fun osClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osName = when {
        os.contains("linux") -> "linux"
        os.contains("mac") || os.contains("darwin") -> "macos"
        os.contains("win") -> "windows"
        else -> "linux"
    }
    val archName = when {
        arch.contains("aarch64") || arch.contains("arm64") -> "aarch_64"
        else -> "x86_64"
    }
    return "$osName-$archName"
}
```

---

## 3. Smart Mode Configuration — State Machine

Auto-configure mode based on detected `SystemInfo`. Explicit `--mode` flag always overrides.

### Decision Tree

```
SystemInfo detected
  ├── explicit --mode flag → use that mode (override)
  │
  ├── headless + container + no GPU
  │   └── mode=pwa (lightest, no LLM — Docker CI runner)
  │
  ├── headless + container + GPU
  │   └── mode=mcp (full server, GPU-accelerated LLM)
  │
  ├── headless + WSL
  │   └── mode=mcp (WSL dev server, JLama if vectorApi)
  │
  ├── headless + Termux
  │   └── mode=pwa (limited resources, no LLM)
  │
  ├── !headless + macOS (Apple Silicon)
  │   └── mode=mcp (desktop dev, full features)
  │
  ├── !headless + Linux
  │   └── mode=mcp (desktop dev, JLama if vectorApi)
  │
  └── !headless + Windows
      └── mode=mcp (desktop dev, suggest LM Studio)
```

### JLama Availability Gate

JLama requires `jdk.incubator.vector` (Vector API) for SIMD-accelerated inference:

```
vectorApi available?
  ├── yes + cpuCores >= 4 + memory >= 4GB
  │   └── JLama enabled (auto-load model if --model specified)
  │
  ├── yes + cpuCores < 4 or memory < 4GB
  │   └── JLama disabled, suggest --mode lmstudio
  │
  └── no (JDK < 21 or missing --add-modules)
      └── JLama disabled (no vector intrinsics)
```

JVM flags required for JLama: `--add-modules jdk.incubator.vector --enable-preview`

---

## 4. JBang Launcher Architecture

Two JVM entry points (same server), plus Electron as separate process:

| Launcher | File | SDK | Build Time | Native? |
|----------|------|-----|-----------|---------|
| **JBang** | `ink-kmp-mcp/InkyMcp.kt` | JBang + GraalVM 25 | ~5s (cached) | `jbang --native` |
| **Gradle** | `ink-kmp-mcp/src/jvmMain/kotlin/ink/mcp/Main.kt` | Gradle 9 + GraalVM 25 | ~30s | `gradle nativeCompile` |
| **Fat JAR** | `inky-mcp.jar` | `java -jar` | pre-built | N/A |
| **Electron** | `ink-electron/main-process/main.js` | Node.js 24 + npm | `npm start` | `@electron/packager` |

### JBang `InkyMcp.kt` Structure

```
///usr/bin/env jbang "$0" "$@" ; exit $?
//KOTLIN 2.1.0
//DEPS  (19 dependencies: Ktor, GraalJS, Camel, JLama, PlantUML, iCal4j, etc.)
//SOURCES (19 source files)
//JAVA_OPTIONS --add-modules jdk.incubator.vector --enable-preview
//NATIVE_OPTIONS --no-fallback -H:+ReportExceptionStackTraces
//JAVA 21

fun main(args) → parseArgs → startServer(port, mode, inkjsPath, ...)
```

### Gradle `Main.kt` Structure

Same `main(args)` but:
- Reads additional env vars (`KEYCLOAK_REALM_URL`, `KEYCLOAK_CLIENT_ID`, etc.)
- Resolves inkjs from KMP source set paths (`src/jsMain/ink/js/electron/node_modules/inkjs/...`)
- Both entry points call `McpRouter.startServer()` with identical parameters

---

## 5. Native Image

### JBang Native Build

```bash
jbang --native InkyMcp.kt
# Produces: InkyMcp (native binary, ~100MB)
```

### Gradle Native Build

```bash
gradle nativeCompile
# Produces: build/native/nativeCompile/inky-mcp
```

### Native-Image Options

`//NATIVE_OPTIONS --no-fallback -H:+ReportExceptionStackTraces`

Requires reflection configuration for:
- Ktor Netty (server internals)
- kotlinx-serialization (JSON processing)
- GraalJS polyglot (JS engine initialization)
- Camel routes (dynamic routing)
- Jackson + msgpack (RSocket serialization)

### GraalVM 25 Note

macOS x64 support **dropped** in GraalVM 25 — Apple Silicon (aarch64) only. Linux and Windows x64 still supported.

---

## 6. CLI Arguments

```
--mode, -m <mode>        mcp | jlama | lmstudio | pwa (default: auto-detect)
--port, -p <port>        Server port (default: 3001)
--inkjs <path>           Path to ink-full.js
--bidify <path>          Path to bidify.js
--model <id>             Auto-load JLama model on startup
--model-cache <path>     GGUF model cache dir (default: ~/.jlama)
--lm-studio-url <url>    LM Studio URL (default: http://localhost:1234/v1)
--lm-studio-model <name> LM Studio model name
--no-llm                 Shortcut for --mode pwa
--auto                   Enable smart mode auto-detection (planned)
--info                   Print SystemInfo and exit (planned)
--help, -h               Show help
```

---

## 7. Startup Banner

Enhanced startup output showing detected system info:

```
Inky MCP Server v0.4.0
  OS:          Linux x86_64 (Ubuntu 22.04)
  Environment: Docker (headless)
  GPU:         mesa-vulkan (software rendering)
  CPU:         8 cores, 16GB RAM
  Vector API:  available (JLama enabled)
  Mode:        mcp (auto-detected → container + GPU)
  Port:        3001
  inkjs:       /app/ink-full.js
  bidify:      /app/bidify.js
  Auth:        Keycloak (https://auth.example.com/realms/inky)
  WebDAV:      /dav/ (ink-scripts filesystem)
  Collab:      WebSocket /collab/:docId
  LLM:         JLama + LM Studio + 11 cloud services
```

---

## 8. Integration with Electron

Electron desktop app (`ink-electron/`) is a **separate process** — it does NOT use JBang. It uses:
- `inklecate` subprocess (C# compiler, bundled binary)
- `inkjs` (JS runtime, via npm)
- ACE editor (in-process)

The MCP server can run alongside Electron as an optional backend:

```bash
# Terminal 1: MCP server (JBang or Gradle)
jbang InkyMcp.kt --mode mcp --port 3001

# Terminal 2: Electron desktop editor
cd ink-electron && npm start
```

Or Electron runs standalone — no server needed for basic editing.

### Electron Build

```bash
# Development
cd ink-electron && npm start

# Package for distribution
npx @electron/packager . Inky --platform=darwin --arch=x64 --icon=./resources/Icon.icns
npx @electron/packager . Inky --platform=win32  --arch=x64 --icon=./resources/Icon1024.png.ico
npx @electron/packager . Inky --platform=linux  --arch=x64

# CI/CD: .github/workflows/build-executables.yaml
```

---

## 9. Setup & SDK Management

### `setup.sh`

Installs all required SDKs via sdkman:

```bash
sdk install java 25.0.2-graal   # Oracle GraalVM 25
sdk install jbang               # JBang launcher
sdk install gradle 9.3.1        # Gradle build tool
```

### `.sdkmanrc` (project root)

Pins SDK versions for all developers + CI:

```
java=25.0.2-graal
kotlin=2.3.0
```

Run `sdk env` in the project directory to auto-switch.

### Environment Compatibility

| Environment | JVM Source | Node Source | .NET Source |
|-------------|-----------|-------------|-------------|
| **Docker** | sdkman in Dockerfile | nodesource | packages.microsoft.com |
| **Ubuntu** | sdkman `.sdkmanrc` | nvm or nodesource | dotnet-install.sh |
| **WSL** | sdkman `.sdkmanrc` | nvm | dotnet-install.sh |
| **macOS** | sdkman `.sdkmanrc` | nvm or brew | brew or dotnet-install.sh |
| **Termux** | `pkg install openjdk-21` | `pkg install nodejs` | N/A |

---

## 10. Server Transport Endpoints

All modes (except Electron) expose the same transport layer via Ktor:

| Endpoint | Protocol | Purpose |
|----------|----------|---------|
| `GET /sse` | Server-Sent Events | MCP event stream |
| `POST /message` | JSON-RPC | MCP tool invocation |
| `WS /collab/:docId` | WebSocket | Yjs CRDT collaboration |
| `WS /rsocket` | RSocket/WebSocket | Asset events (msgpack) |
| `POST /api/*` | REST | Direct API access |
| `GET /dav/*` | WebDAV | Ink script filesystem |

---

## References

- [JBang](https://www.jbang.dev/) — Java/Kotlin script runner
- [SDKMAN](https://sdkman.io/) — JDK/tool manager
- [GraalVM 25](https://www.graalvm.org/) — Oracle GraalVM with GraalJS
- [GraalJS](https://www.graalvm.org/latest/reference-manual/js/) — Polyglot JS engine
- [`InkyMcp.kt`](../../ink-kmp-mcp/InkyMcp.kt) — JBang launcher source
- [`Main.kt`](../../ink-kmp-mcp/src/jvmMain/kotlin/ink/mcp/Main.kt) — Gradle entry point
- [`McpRouter.kt`](../../ink-kmp-mcp/src/jvmMain/kotlin/ink/mcp/McpRouter.kt) — Server initialization
- [`INK_KMP_MCP.md`](INK_KMP_MCP.md) — Full MCP server architecture
