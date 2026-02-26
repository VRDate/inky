# Ink KMP + MCP Architecture

## Overview

This document describes the architecture for running the **ink** interactive fiction language across multiple platforms using **Kotlin Multiplatform (KMP)**, **MCP (Model Context Protocol)** for LLM integration, and **GraalJS** for headless JavaScript execution.

## Ecosystem Map

```
┌─────────────────────────────────────────────────────────┐
│                    Ink Language (inkle)                  │
│  .ink source → inklecate (C#) → .ink.json compiled      │
└───────────────────────┬─────────────────────────────────┘
                        │
         ┌──────────────┼──────────────┐
         ▼              ▼              ▼
   ┌──────────┐  ┌──────────────┐  ┌───────────────┐
   │  inkjs   │  │ blade-ink    │  │ blade-ink-rs  │
   │  (JS)    │  │ (Java/JVM)   │  │ (Rust + FFI)  │
   └────┬─────┘  └──────┬───────┘  └───────┬───────┘
        │               │                  │
        ▼               ▼                  ▼
   ┌──────────┐  ┌──────────────┐  ┌───────────────┐
   │  Inky    │  │ KMP JVM      │  │ KMP Native    │
   │  Editor  │  │ (Android,    │  │ (iOS, Linux,  │
   │ +MCP Srv │  │  Desktop)    │  │  macOS)       │
   └──────────┘  └──────────────┘  └───────────────┘
```

See: [`ink-kmp-architecture.puml`](ink-kmp-architecture.puml)

## Runtime Implementations

### 1. inkjs (JavaScript)

**Repository**: [inkle/ink](https://github.com/inkle/ink) (inkjs lives inside)
**Package**: `npm install inkjs`
**Size**: 243 KB (compiler+runtime), 126 KB (runtime only)
**License**: MIT

| Feature | Support |
|---------|---------|
| Compile .ink → JSON | Yes (`inkjs/full`) |
| Runtime playback | Yes |
| Variables, functions | Yes |
| External functions | Yes |
| Save/load state | Yes |
| Tags, tunnels, threads | Yes |
| Lists (enum-like) | Yes |
| Node.js + Browser | Yes |
| GraalJS compatible | Yes (verified) |
| Zero dependencies | Yes |

```javascript
const { Compiler, Story } = require('inkjs/full');
const compiler = new Compiler(inkSource);
const story = compiler.Compile();
while (story.canContinue) console.log(story.Continue());
```

### 2. blade-ink-java (JVM)

**Repository**: [bladecoder/blade-ink-java](https://github.com/bladecoder/blade-ink-java)
**Maven**: `com.bladecoder.ink:blade-ink:1.2.1`
**License**: MIT

| Feature | Support |
|---------|---------|
| Compile .ink → JSON | Yes |
| Runtime playback | Yes |
| Variables, functions | Yes |
| External functions | Yes |
| Save/load state | Yes |
| Tags, tunnels, threads | Yes |
| Conformance tests | Yes |
| Pure Java (no native) | Yes |

```kotlin
val story = Story(jsonString)
while (story.canContinue()) println(story.Continue())
val choices = story.getCurrentChoices()
story.chooseChoiceIndex(0)
```

### 3. blade-ink-rs (Rust)

**Repository**: [bladecoder/blade-ink-rs](https://github.com/bladecoder/blade-ink-rs)
**License**: MIT

| Feature | Support |
|---------|---------|
| Runtime playback | Yes |
| C FFI bindings | Yes |
| Python bindings | Yes |
| Compile from source | Partial |
| Native performance | Yes |

### 4. blade-ink-template (LibGDX)

**Repository**: [bladecoder/blade-ink-template](https://github.com/bladecoder/blade-ink-template)
**License**: Apache 2.0

A ready-made template using blade-ink-java + LibGDX for:
- Android
- iOS (via RoboVM)
- Desktop (via LWJGL3)

## KMP Strategy

### Target Matrix

| KMP Target | Ink Runtime | Approach |
|------------|-------------|----------|
| **JVM** (Android, Desktop) | blade-ink-java | Direct dependency |
| **JS** (Browser, Node) | inkjs | npm package via Kotlin/JS |
| **Native** (iOS, macOS, Linux) | blade-ink-rs | Via C FFI / cinterop |
| **WASM** (WasmJS, WASI) | inkjs | Bundled JS module |

### Common API (`expect`/`actual`)

```kotlin
// commonMain
expect class InkRuntime {
    fun compile(source: String): CompileResult
    fun createStory(json: String): InkStory
}

expect class InkStory {
    val canContinue: Boolean
    fun continueStory(): String
    val currentChoices: List<InkChoice>
    fun chooseChoice(index: Int)
    fun getVariable(name: String): Any?
    fun setVariable(name: String, value: Any?)
    fun saveState(): String
    fun loadState(json: String)
    fun reset()
}

// jvmMain — uses blade-ink-java
actual class InkStory(private val story: com.bladecoder.ink.runtime.Story) {
    actual val canContinue get() = story.canContinue()
    actual fun continueStory() = story.Continue()
    // ...
}

// jsMain — uses inkjs
actual class InkStory(private val story: dynamic) {
    actual val canContinue get() = story.canContinue as Boolean
    actual fun continueStory() = story.Continue() as String
    // ...
}
```

See: [`ink-kmp-classes.puml`](ink-kmp-classes.puml)

## MCP Server Architecture

### What is MCP?

[Model Context Protocol](https://modelcontextprotocol.io/) is a standard for connecting LLMs to external tools. The MCP server exposes ink compiler/runtime as tools that any MCP-compatible LLM (Claude, GPT, etc.) can invoke.

### Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.1.0 |
| Web framework | Ktor | 3.1.1 |
| JS engine | GraalJS (CE) | 25.0.2 |
| Serialization | kotlinx-serialization | 1.7.3 |
| Launcher | JBang | latest |
| JDK | GraalVM CE 21 | via SDKMAN |

### 17 MCP Tools

| Tool | Description |
|------|-------------|
| `compile_ink` | Compile .ink source to JSON |
| `start_story` | Compile + start interactive session |
| `start_story_json` | Start session from pre-compiled JSON |
| `continue_story` | Get next text segment |
| `choose` | Make a choice in the story |
| `get_variable` | Read an ink variable |
| `set_variable` | Set an ink variable |
| `save_state` | Save story state as JSON |
| `load_state` | Restore saved state |
| `reset_story` | Reset to beginning |
| `evaluate_function` | Call an ink function |
| `get_global_tags` | Get global ink tags |
| `list_sessions` | List active sessions |
| `end_session` | Close a session |
| `bidify` | Add bidi markers for RTL |
| `strip_bidi` | Remove bidi markers |
| `bidify_json` | Bidify compiled JSON |

### Transport

- **SSE** (Server-Sent Events): `GET /sse` → event stream, `POST /message` → JSON-RPC
- **REST API**: Direct `POST /api/compile`, `POST /api/start`, etc.
- **stdio** (planned): For local MCP client integration

See: [`mcp-server-sequence.puml`](mcp-server-sequence.puml)

### Quick Start

```bash
# Install tools via SDKMAN
sdk install java 21.0.5-graalce
sdk install jbang
sdk install gradle 8.12

# Install inkjs
cd app && npm install && cd ..

# Run with JBang (thin launcher)
cd mcp-server
jbang InkyMcp.kt

# Or with Gradle
gradle run

# Build native binary
jbang --native InkyMcp.kt
```

### Claude Desktop Integration

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "inky": {
      "command": "jbang",
      "args": ["run", "/path/to/mcp-server/InkyMcp.kt"]
    }
  }
}
```

## File Map

```
mcp-server/
├── InkyMcp.kt                   # JBang entry (//DEPS, //SOURCES)
├── build.gradle.kts             # Gradle build (alternative)
├── settings.gradle.kts
├── gradle.properties
├── setup.sh                     # SDKMAN setup script
└── src/ink/mcp/
    ├── Main.kt                  # Gradle entry point
    ├── InkEngine.kt             # GraalJS ↔ inkjs bridge
    ├── McpTypes.kt              # MCP JSON-RPC types
    ├── McpTools.kt              # 17 tool definitions + handlers
    └── McpRouter.kt             # Ktor SSE + REST routing

docs/architecture/
├── INK_KMP_MCP.md               # This document
├── ink-kmp-architecture.puml    # Component diagram
├── ink-kmp-classes.puml         # Class diagram
└── mcp-server-sequence.puml     # Sequence diagram
```

## References

- [inkle/ink](https://github.com/inkle/ink) — Ink language + inkjs
- [bladecoder/blade-ink-java](https://github.com/bladecoder/blade-ink-java) — Java runtime
- [bladecoder/blade-ink-rs](https://github.com/bladecoder/blade-ink-rs) — Rust runtime
- [bladecoder/blade-ink-template](https://github.com/bladecoder/blade-ink-template) — LibGDX template
- [GraalJS](https://www.graalvm.org/latest/reference-manual/js/) — Polyglot JS engine
- [Ktor](https://ktor.io/) — Kotlin web framework
- [MCP Spec](https://modelcontextprotocol.io/) — Model Context Protocol
- [JBang](https://www.jbang.dev/) — Java/Kotlin script runner
- [SDKMAN](https://sdkman.io/) — JDK/tool manager
- [KMP](https://kotlinlang.org/docs/multiplatform/kmp-overview.html) — Kotlin Multiplatform
