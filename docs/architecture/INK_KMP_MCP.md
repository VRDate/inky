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

## Per-Framework Ink Runtime

Each framework has its own ink compiler/runtime. GraalJS is to KT what OneJS is to Unity — an embedded JS engine alongside the native engine.

| Framework | Compiler | Runtime | JS Engine | Entry Point |
|-----------|----------|---------|-----------|-------------|
| **KT/JVM** (MCP server) | GraalJS + inkjs | GraalJS + inkjs | GraalVM Polyglot | `InkEngine.kt` → `ink-full.js` |
| **C#/Unity** | ink-csharp `Ink.Compiler` | ink-csharp `Ink.Runtime.Story` | OneJS (bridges JS → C#) | `InkOneJsBinding.cs` |
| **JS/Electron** (Inky desktop) | inklecate subprocess | inkjs directly | N/A (native JS) | `inklecate.js` |
| **JS/Browser** (inkey web) | inkjs directly | inkjs directly | N/A (native JS) | `InkRuntimeAdapter.ts` |

See: [`ink-per-framework-runtime.puml`](ink-per-framework-runtime.puml)

## Inky Editor Stack

Inky = JS compiler + editor. Two apps, shared grammar (`@inky/ink-language`):

| App | Editor | Content Type | Collaboration | Package |
|-----|--------|-------------|---------------|---------|
| **ink-electron** (desktop) | ACE | Ink source (.ink) | None | `ace-ink-mode/ace-ink.js` |
| **ink-js/inkey** (web) | CodeMirror 6 | Ink source + embedded blocks | Yjs (`y-codemirror.next`) | `@inky/codemirror-ink` |
| **ink-js/inkey** (web) | Remirror/ProseMirror | MD + \`\`\`ink blocks | Yjs (`y-prosemirror`) | `@inky/remirror-ink` |
| **ink-js/inkey** (web) | InkPlayer | Play mode (readonly) | — | `react-ink-editor/InkPlayer.tsx` |

Key React components (`@inky/react-ink-editor`):
- `InkCodeEditor.tsx` — CodeMirror 6 editor (ink source, edit mode)
- `InkProseEditor.tsx` — Remirror markdown editor (MD + ink blocks, edit mode)
- `InkPlayer.tsx` — Story player (play mode, uses inkjs directly)
- `InkEditorProvider.tsx` — Context provider (Yjs doc + runtime)
- `ModeToggle.tsx` — Edit/Play mode toggle

## Multi-Protocol Architecture

Four protocols work together to create LLM MCP agents + editor UI PWA with BabylonJS WebXR and Unity WebGL interop:

| Protocol | Transport | Encoding | Purpose |
|----------|-----------|----------|---------|
| **MCP** | SSE / stdio / REST | JSON-RPC | LLM ↔ ink tools (79 tools) |
| **Yjs** | HocusPocus WebSocket | CRDT Y.Doc | Real-time collaborative editing |
| **RSocket** | WebSocket (`/rsocket`) | msgpack | Event-driven asset pipeline (EDA) |
| **OneJS** | In-process (`__inkBridge`) | JSON string | Unity ↔ JS bridge (no network) |

See: [`ink-rsocket-transport.puml`](ink-rsocket-transport.puml)

### RSocket Transport Per Framework

| Framework | RSocket Transport | Why |
|-----------|------------------|-----|
| KT/JVM (server) | In-process (event bus origin) | Server-side, events originate here |
| C#/Unity + OneJS | In-process via `__inkBridge` | Same process, no network needed |
| JS/BabylonJS (WebXR) | WebSocket → Ktor `/rsocket` | Browser to server |
| JS/Electron (Inky) | WebSocket → Ktor `/rsocket` | Desktop to server |
| inkey PWA | WebSocket → Ktor `/rsocket` | Browser to server |

## MCP Server Architecture

### What is MCP?

[Model Context Protocol](https://modelcontextprotocol.io/) is a standard for connecting LLMs to external tools. The MCP server exposes ink compiler/runtime as tools that any MCP-compatible LLM (Claude, GPT, etc.) can invoke.

### Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.1.0 |
| Web framework | Ktor | 3.1.1 |
| JS engine | GraalJS (Oracle) | 25.0.2 |
| Serialization | kotlinx-serialization | 1.7.3 |
| LLM inference | JLama + LangChain4j | 0.8.4 / 1.11 |
| Diagram | PlantUML (MIT) | latest |
| Calendar | iCal4j | 4.0.7 |
| Contacts | ez-vcard | 0.12.1 |
| Auth | Keycloak OIDC + JWT | via Ktor |
| WebDAV client | Sardine | 5.12 |
| Collab | Yjs (HocusPocus) | via WebSocket |
| Pipeline | Apache Camel | 4.18 |
| EDA events | RSocket-Kotlin | 0.16.0 |
| Event encoding | jackson-dataformat-msgpack | 0.9.8 |
| Test data | faker-kotlin (serpro69) | 2.0.0-rc.7 |
| Build | Gradle | 9.3.1 |
| Launcher | JBang | latest |
| JDK | Oracle GraalVM 21 | via SDKMAN |

### 79 MCP Tools

| Group | Count | Engine |
|-------|-------|--------|
| Ink Core | 17 | InkEngine (GraalJS + inkjs) |
| Debug | 8 | InkDebugEngine |
| Edit | 6 | InkEditEngine |
| Ink+Markdown | 3 | InkMdEngine |
| PlantUML+TOC | 5 | Ink2PumlEngine (PlantUML MIT) |
| LLM Models | 8 | LlmEngine (JLama + LangChain4j) |
| Services | 2 | LlmServiceConfig (11 providers) |
| Collaboration | 2 | ColabEngine (Yjs WebSocket) |
| Calendar | 4 | InkCalendarEngine (iCal4j) |
| Principals | 4 | InkVCardEngine (ez-vcard) |
| Auth | 2 | InkAuthEngine (Keycloak OIDC) |
| WebDAV+Backup | 10 | InkWebDavEngine (Sardine + FS) |
| Asset Pipeline | 6 | InkAssetEventEngine + EmojiAssetManifest + InkFakerEngine |
| TTS | 2 | ChatterboxTtsEngine (ONNX) |
| **Total** | **79** | |

#### Ink Core (17 tools)

| Tool | Description |
|------|-------------|
| `compile_ink` | Compile .ink source to JSON |
| `start_story` | Compile + start interactive session |
| `start_story_json` | Start session from pre-compiled JSON |
| `continue_story` | Get next text segment |
| `choose` | Make a choice in the story |
| `get_variable` / `set_variable` | Read/write story variables |
| `save_state` / `load_state` | Save/restore story state |
| `reset_story` | Reset to beginning |
| `evaluate_function` | Call an ink function |
| `get_global_tags` | Get global ink tags |
| `list_sessions` / `end_session` | Session management |
| `bidify` / `strip_bidi` / `bidify_json` | RTL bidi markers |

#### Debug (8 tools)

| Tool | Description |
|------|-------------|
| `start_debug` | Begin debugging a story session |
| `add_breakpoint` / `remove_breakpoint` | Manage breakpoints |
| `debug_step` / `debug_continue` | Step/continue execution |
| `add_watch` | Watch variable changes |
| `debug_inspect` | Inspect current debug state |
| `debug_trace` | Get execution trace log |

#### Edit (6 tools)

| Tool | Description |
|------|-------------|
| `parse_ink` | Parse ink into sections |
| `get_section` / `replace_section` | Read/write sections |
| `insert_section` / `rename_section` | Add/rename sections |
| `ink_stats` | Script statistics + dead-end analysis |

#### Ink+Markdown (3), PlantUML+TOC (5), LLM (8), Services (2), Collab (2)

See [INK_SKILL_FOR_LLMS.md](../INK_SKILL_FOR_LLMS.md) for full tool descriptions.

#### Calendar (4 tools) — iCal4j

| Tool | Description |
|------|-------------|
| `create_event` | Create calendar event (milestone, session, deadline, quest) |
| `list_events` | List events with date range filter |
| `export_ics` / `import_ics` | ICS import/export |

#### Principals (4 tools) — ez-vcard

| Tool | Description |
|------|-------------|
| `create_principal` | Register user/LLM with vCard |
| `list_principals` / `get_principal` | Query principals |
| `delete_principal` | Remove a principal |

#### Auth (2 tools) — Keycloak OIDC

| Tool | Description |
|------|-------------|
| `auth_status` | Check auth config |
| `create_llm_credential` | Create LLM BasicAuth credential |

#### WebDAV + Backup (10 tools) — Sardine + FS

| Tool | Description |
|------|-------------|
| `webdav_list` / `webdav_get` / `webdav_put` / `webdav_delete` | CRUD operations |
| `webdav_mkdir` | Create directory |
| `webdav_sync` | Sync from remote WebDAV (Sardine) |
| `webdav_backup` / `webdav_list_backups` / `webdav_restore` | Timestamp backup sets |
| `webdav_working_copy` | LLM working copy for edit |

#### Asset Pipeline (6 tools) — EmojiAssetManifest + InkFakerEngine

| Tool | Description |
|------|-------------|
| `resolve_emoji` | Resolve emoji → AssetCategory (animset, grip, mesh) |
| `parse_asset_tags` | Parse ink tags → list of AssetRef |
| `emit_asset_event` | Manually emit an asset event to the bus |
| `list_asset_events` | List recent events for a session |
| `generate_ink_vars` | Generate ink VARs from emoji MD table data |
| `generate_story_md` | Generate story MD with characters + items |

#### TTS (2 tools) — ChatterboxTtsEngine (ONNX)

| Tool | Description |
|------|-------------|
| `synthesize_voice` | Synthesize speech from FLAC voice ref |
| `list_voices` | List available voice references |

### Transport

- **MCP SSE** (Server-Sent Events): `GET /sse` → event stream, `POST /message` → JSON-RPC
- **MCP REST**: Direct `POST /api/compile`, `POST /api/start`, etc.
- **MCP stdio** (planned): For local MCP client integration
- **Yjs WebSocket**: `WS /collab/{docId}` → HocusPocus CRDT sync
- **RSocket WebSocket**: `WS /rsocket` → msgpack asset events (fireAndForget, requestStream, requestChannel)

See: [`mcp-server-sequence.puml`](mcp-server-sequence.puml), [`ink-rsocket-transport.puml`](ink-rsocket-transport.puml)

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
    ├── InkDebugEngine.kt        # Breakpoints, step, trace
    ├── InkEditEngine.kt         # Ink section parser + editor
    ├── InkMdEngine.kt           # Ink+Markdown processor
    ├── Ink2PumlEngine.kt        # Ink → PlantUML/SVG converter
    ├── LlmEngine.kt             # JLama + LangChain4j chat models
    ├── ColabEngine.kt           # Yjs/HocusPocus WebSocket collab
    ├── InkCalendarEngine.kt     # iCal4j calendar events
    ├── InkVCardEngine.kt        # ez-vcard principal management
    ├── InkAuthEngine.kt         # Keycloak OIDC + LLM BasicAuth
    ├── InkWebDavEngine.kt       # Sardine WebDAV + FS + backups
    ├── McpTypes.kt              # MCP JSON-RPC types
    ├── McpTools.kt              # 79 tool definitions + handlers
    ├── McpRouter.kt             # Ktor SSE + REST + RSocket + WebDAV routing
    ├── EmojiAssetManifest.kt    # Emoji → asset category mapping
    ├── InkAssetEventEngine.kt   # Tag → asset event processor
    ├── AssetEventBus.kt         # RSocket + msgpack event bus
    ├── InkFakerEngine.kt        # faker-kotlin test data generator
    └── ChatterboxTtsEngine.kt   # ONNX voice cloning TTS

ink-electron/
├── renderer/
│   ├── editorView.js            # ACE editor for ink source
│   ├── ace-ink-mode/ace-ink.js  # ACE ink language mode
│   ├── controller.js            # UI orchestration
│   └── index.html               # ACE script tags
└── main-process/
    └── inklecate.js             # C# compiler subprocess

ink-js/inkey/packages/
├── ink-language/                # Shared grammar (ACE, CM6, Remirror)
│   └── src/ink-grammar.ts
├── codemirror-ink/              # @inky/codemirror-ink (CodeMirror 6)
│   └── src/
│       ├── index.ts
│       ├── ink-highlight.ts
│       ├── ink-complete.ts
│       ├── ink-fold.ts
│       └── ink-yjs.ts           # y-codemirror.next collab
├── remirror-ink/                # @inky/remirror-ink (ProseMirror)
│   └── src/
│       ├── InkExtension.ts
│       ├── ink-schema.ts
│       ├── ink-node-view.ts
│       └── ink-yjs.ts           # y-prosemirror collab
└── react-ink-editor/            # @inky/react-ink-editor
    └── src/
        ├── InkCodeEditor.tsx    # CodeMirror 6 component
        ├── InkProseEditor.tsx   # Remirror component
        ├── InkPlayer.tsx        # Play mode (inkjs)
        ├── InkEditorProvider.tsx # Yjs + runtime context
        ├── ModeToggle.tsx       # Edit ↔ Play toggle
        ├── InkRuntimeAdapter.ts # 3 backends (inkjs, OneJS, MCP)
        ├── AssetEventClient.ts  # RSocket WS client
        └── BabylonJsAssetLoader.ts # glTF mesh + anim

ink-unity/
├── InkOneJsBinding.cs           # 11-method OneJS bridge
├── InkAssetEventReceiver.cs     # Asset event consumer
├── InkTtsBridge.cs              # ONNX TTS for Unity
└── InkBabylonExporter.cs        # Unity → BabylonJS glTF export

ink-csharp/
├── compiler/Compiler.cs         # Native C# ink compiler
└── ink-engine-runtime/Story.cs  # Native C# ink runtime

docs/architecture/
├── INK_KMP_MCP.md               # This document
├── INK_ASSET_PIPELINE.md        # Asset event pipeline architecture
├── ink-mcp-tools.puml           # 79-tool architecture diagram
├── ink-per-framework-runtime.puml # Per-framework compiler/runtime
├── ink-asset-event-pipeline.puml  # Asset event flow diagram
├── ink-rsocket-transport.puml   # Multi-protocol transport sequence
├── mcp-server-sequence.puml     # MCP session sequence diagram
├── camel-llm-pipeline.puml      # Camel + JLama pipeline
├── ink-collab-yjs.puml          # Yjs collaboration + auth
├── sillytavern-ink-integration.puml  # SillyTavern integration
├── ink-kmp-architecture.puml    # Multi-protocol component diagram
└── ink-kmp-classes.puml         # KMP + asset pipeline class diagram
```

## References

- [inkle/ink](https://github.com/inkle/ink) — Ink language + inkjs
- [bladecoder/blade-ink-java](https://github.com/bladecoder/blade-ink-java) — Java runtime
- [bladecoder/blade-ink-rs](https://github.com/bladecoder/blade-ink-rs) — Rust runtime
- [bladecoder/blade-ink-template](https://github.com/bladecoder/blade-ink-template) — LibGDX template
- [GraalJS](https://www.graalvm.org/latest/reference-manual/js/) — Polyglot JS engine
- [Ktor](https://ktor.io/) — Kotlin web framework
- [MCP Spec](https://modelcontextprotocol.io/) — Model Context Protocol
- [RSocket](https://rsocket.io/) — Reactive streams over network
- [AsyncAPI](https://www.asyncapi.com/) — Event-driven API specification
- [Yjs](https://yjs.dev/) — CRDT for real-time collaboration
- [CodeMirror 6](https://codemirror.net/) — Extensible code editor
- [Remirror](https://remirror.io/) — ProseMirror React framework
- [BabylonJS](https://www.babylonjs.com/) — WebXR 3D engine
- [JBang](https://www.jbang.dev/) — Java/Kotlin script runner
- [SDKMAN](https://sdkman.io/) — JDK/tool manager
- [KMP](https://kotlinlang.org/docs/multiplatform/kmp-overview.html) — Kotlin Multiplatform
