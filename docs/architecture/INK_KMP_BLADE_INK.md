# Ink KMP Architecture — blade-ink-template Integration

## Overview

Kotlin Multiplatform (KMP) architecture for ink runtime, reusing existing open-source ink libraries per platform:

| Target | Runtime | Source |
|--------|---------|--------|
| JVM (Android, Desktop, Server) | blade-ink-java | [bladecoder/blade-ink](https://github.com/bladecoder/blade-ink) |
| JS (Browser, Node.js) | inkjs | [y-lohse/inkjs](https://github.com/y-lohse/inkjs) |
| Native (iOS, macOS, Linux) | blade-ink-rs (C FFI) | [bladecoder/blade-ink-rs](https://github.com/nicosResworx/blade-ink-rs) |
| WASM | inkjs via wasm-js | [y-lohse/inkjs](https://github.com/y-lohse/inkjs) |

### Reference Projects

| Project | Purpose | URL |
|---------|---------|-----|
| inkle/inky | Reference editor (Electron) | [github.com/inkle/inky](https://github.com/inkle/inky) |
| blade-ink-template | LibGDX multiplatform template | [github.com/bladecoder/blade-ink-template](https://github.com/bladecoder/blade-ink-template) |
| inkjs | JS compiler + runtime | [github.com/y-lohse/inkjs](https://github.com/y-lohse/inkjs) |
| vadimdemedes/ink | React for CLI (TUI) | [github.com/vadimdemedes/ink](https://github.com/vadimdemedes/ink) |
| vadimdemedes/ink-ui | CLI UI components | [github.com/vadimdemedes/ink-ui](https://github.com/vadimdemedes/ink-ui) |

> **Note**: `vadimdemedes/ink` is a **React renderer for CLI apps** — not related to inkle's ink scripting language. It can be used to build terminal-based ink story players. `micabytes/mica-ink` is deprecated and should not be used.

## KMP Module Structure

```
ink-kmp/
├── shared/                    # Common Kotlin code
│   ├── src/commonMain/        # expect declarations
│   │   └── ink/
│   │       ├── InkRuntime.kt      # expect interface
│   │       ├── InkStory.kt        # expect class
│   │       ├── InkParser.kt       # Pure Kotlin ink parser
│   │       └── BidiProcessor.kt   # Pure Kotlin bidi
│   ├── src/jvmMain/           # blade-ink-java actual
│   │   └── ink/
│   │       └── JvmInkRuntime.kt
│   ├── src/jsMain/            # inkjs actual
│   │   └── ink/
│   │       └── JsInkRuntime.kt
│   ├── src/nativeMain/        # blade-ink-rs FFI actual
│   │   └── ink/
│   │       └── NativeInkRuntime.kt
│   └── src/wasmJsMain/        # inkjs via wasm-js
│       └── ink/
│           └── WasmInkRuntime.kt
├── android/                   # Android app (Jetpack Compose)
├── desktop/                   # Desktop app (Compose Desktop)
├── web/                       # Browser app (Compose Web/JS)
├── ios/                       # iOS app (SwiftUI + KMP)
└── mcp-server/                # MCP Server (Ktor + GraalJS)
```

## blade-ink-template Integration

The [blade-ink-template](https://github.com/bladecoder/blade-ink-template) provides a LibGDX multiplatform game template that uses blade-ink for ink story playback.

### Reusable Patterns from blade-ink-template

1. **Story loading**: JSON deserialization → `Story` object
2. **Choice rendering**: Iterating `currentChoices` for UI
3. **State persistence**: `story.state.toJson()` / `story.state.loadJson()`
4. **Platform targets**: Android, iOS, Desktop via LibGDX

### Dependencies

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Pure Kotlin shared code (parser, bidi)
        }
        jvmMain.dependencies {
            implementation("com.bladecoder.ink:blade-ink:1.2.1")
        }
        jsMain.dependencies {
            implementation(npm("inkjs", "2.4.0"))
        }
    }
}
```

## MCP Server Architecture (v0.3.0)

The MCP server runs on JVM with GraalJS for inkjs access:

### 46 Tools in 7 Categories

| Category | Count | Engine |
|----------|-------|--------|
| Ink Core | 17 | InkEngine (GraalJS + inkjs) |
| Debug | 8 | InkDebugEngine |
| Edit | 6 | InkEditEngine |
| Ink+Markdown | 3 | InkMdEngine |
| LLM Models | 8 | LlmEngine (JLama) |
| Services | 2 | LlmServiceConfig (11 providers) |
| Collab | 2 | ColabEngine (Yjs WebSocket) |

### Supported LLM Services

| Service | Type | Default Model |
|---------|------|---------------|
| Claude (Anthropic) | Cloud | claude-sonnet-4-20250514 |
| Gemini (Google) | Cloud | gemini-2.0-flash |
| GitHub Copilot | Cloud | gpt-4o |
| Grok (xAI) | Cloud | grok-3 |
| Perplexity | Cloud | sonar-pro |
| OpenRouter | Aggregator | anthropic/claude-sonnet-4-20250514 |
| Together AI | Cloud | Llama-3.3-70B |
| Groq | Cloud | llama-3.3-70b-versatile |
| Comet Opik | Observability | opik-default |
| LM Studio | Local | (user's model) |
| Ollama | Local | llama3.3 |

### Collaboration (Yjs)

Real-time collaborative editing via WebSocket:

- **Ace Editor** (`.ink` files) → `y-ace` binding → Yjs CRDT
- **Remirror/CodeMirror** (`.md` templates) → `y-remirror`/`y-codemirror` → Yjs CRDT
- **Browser Extension** → `y-websocket` → Yjs CRDT

Server endpoint: `ws://localhost:3001/collab/:docId`

### Browser Extensions

| Browser | Manifest | Notes |
|---------|----------|-------|
| Chrome | manifest-chrome.json | MV3, Side Panel |
| Firefox | manifest-firefox.json | MV3, Sidebar |
| Edge | manifest-edge.json | MV3, Side Panel |
| Kiwi | manifest-kiwi.json | MV3, Popup only (mobile) |

## Diagrams

- `ink-kmp-blade-ink.puml` — KMP platform architecture with blade-ink
- `ink-mcp-tools.puml` — 46-tool MCP server overview
- `ink-collab-yjs.puml` — Yjs collaboration architecture
- `dictalm-gguf-models.puml` — DictaLM model selection guide
- `camel-llm-pipeline.puml` — Camel + LangChain4j pipeline
