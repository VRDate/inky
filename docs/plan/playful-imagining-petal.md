# Plan: Event-Driven Emoji Asset Pipeline (RSocket + msgpack + AsyncAPI)

## Context

### Ink Compiler/Runtime Per Framework

Each framework has its own ink compiler/runtime. GraalJS is to KT what OneJS is to Unity — an embedded JS engine alongside the native engine.

| Framework | Compiler | Runtime | JS Engine | Entry Point |
|-----------|----------|---------|-----------|-------------|
| **KT/JVM** (MCP server) | GraalJS + inkjs | GraalJS + inkjs | GraalVM Polyglot | `InkEngine.kt` → `ink-full.js` |
| **C#/Unity** | ink-csharp `Ink.Compiler` | ink-csharp `Ink.Runtime.Story` | OneJS (bridges JS → C#) | `InkOneJsBinding.cs` |
| **JS/Electron** (Inky desktop) | inklecate subprocess | inkjs directly | N/A (native JS) | `inklecate.js` |
| **JS/BabylonJS** (inkey web) | inkjs directly | inkjs directly | N/A (native JS) | `InkRuntimeAdapter.ts` |

### Inky Editor Stack

**Inky = JS compiler + editor.** Two apps, shared grammar (`@inky/ink-language`):

| App | Editor | Content Type | Collaboration | Packages |
|-----|--------|-------------|---------------|----------|
| **ink-electron** (desktop) | ACE | Ink source only (.ink) | Yjs (`y-ace`) | `ace-ink-mode/ace-ink.js` |
| **ink-js/inkey** (web) | CodeMirror 6 | Ink source + embedded blocks | Yjs (`y-codemirror.next`) | `@inky/codemirror-ink` |
| **ink-js/inkey** (web) | Remirror | MD + ```ink blocks | Yjs (`y-remirror`) | `@inky/remirror-ink` |
| **ink-js/inkey** (web) | InkPlayer | Play mode (readonly) | — | `react-ink-editor/InkPlayer.tsx` |

**Key React components** (`@inky/react-ink-editor`):
- `InkCodeEditor.tsx` — CodeMirror 6 editor (ink source only, edit mode)
- `InkRemirrorEditor.tsx` (was InkProseEditor) — Remirror-only markdown editor (MD prose + embedded ```ink blocks, edit mode)
- `InkPlayer.tsx` — Story player (play mode, uses inkjs directly)
- `InkEditorProvider.tsx` — Context provider (Yjs doc + runtime)
- `ModeToggle.tsx` — Edit/Play mode toggle

### Existing Engines

- **InkMdEngine** (`InkMdEngine.kt`) — parses MD tables + ink blocks → ink VARs
- **InkEditEngine** (`InkEditEngine.kt`) — parses ink structure (knots, stitches, variables, diverts)
- **InkEngine** (`InkEngine.kt`) — GraalJS ink compiler/runtime, emits `ContinueResult` with `.tags`
- **InkOneJsBinding.cs** — 10-method OneJS bridge (`__inkBridge` global)
- **InkRuntimeAdapter.ts** — 10-method interface with `InkStoryState.tags`, 3 backends: `createInkJsAdapter()`, `createOneJsAdapter()`, `createMcpAdapter()`
- **CamelRoutes** — Apache Camel routes for LLM + ink pipeline
- **McpTools** — 60+ tools, constructor takes optional engines

### MCP Server = Multi-Tenant OIDC PWA UI Server

The MCP server isn't just an API — it's a multi-tenant application server:

| Concern | Technology | Description |
|---------|-----------|-------------|
| **Identity** | Keycloak OIDC + JWT | Multi-tenant authentication |
| **Principals** | ez-vcard per user | User/LLM identity cards |
| **Sessions** | Per-user folders | LLM model user sessions + ink story state |
| **Collaboration** | Yjs (HocusPocus WS) | Real-time shared editing |
| **Storage** | WebDAV (Sardine + FS) | domain/user/shared/ folder hierarchy |
| **PWA UI** | Ktor static + SPA | Serves inkey editor as PWA |

### Client Surfaces

| Client | Type | Purpose |
|--------|------|---------|
| **SillyTavern** | MCP user UI (localhost:8000) | Chat-based story interaction with LLM |
| **Electron** (Inky desktop) | Desktop app | ACE editor + inklecate compiler |
| **inkey PWA** | Progressive Web App | CodeMirror + Remirror + Yjs collab |
| **Chromium PWA extension** | Browser extension | Editor extension for Chrome/Edge |
| **Unity WebGL** | 3D rendering client | AssetBundle prefabs + animation |
| **BabylonJS WebXR** | 3D rendering client | glTF meshes + WebXR immersive |

### Faker Per Framework

faker-js in JS/OneJS/Electron, kotlin-faker at MCP server:

| Framework | Faker Library | Usage |
|-----------|--------------|-------|
| **KT/JVM** (MCP server) | `kotlin-faker` (serpro69) | MCP tools: `generate_ink_vars`, `generate_story_md` |
| **JS/Electron** (Inky) | `faker-js` (npm) | Client-side data generation |
| **JS/Browser** (inkey) | `faker-js` (npm) | Client-side data generation |
| **C#/Unity + OneJS** | `faker-js` via OneJS | In-process via `__inkBridge` |

Emoji categories map to faker methods (e.g., 🗡️ → `faker.game.weapon()`, 🧙 → `faker.name` + `faker.dnd`).

### 2D vs 3D: Text UI vs Rendering Clients

**inkjs = 2D emoji tags + text UI.** Emoji are text markers in ink tags — not 3D objects themselves. The 3D clients resolve these text markers to actual assets:

| Layer | Technology | What It Does |
|-------|-----------|-------------|
| **2D Text UI** | inkjs | Emits tags like `# mesh:🗡️` `# anim:sword_slash` — pure text |
| **3D Rendering** | BabylonJS (WebXR) | Resolves emoji tag → glTF mesh → 3D scene |
| **3D Rendering** | Unity (WebGL) | Resolves emoji tag → AssetBundle prefab → 3D scene |
| **2D Editor** | inkey PWA (CM6/Remirror) | Shows emoji as text indicators in story output |

The EmojiAssetManifest bridges 2D text tags → 3D asset references.

### What's New: Event-Driven Asset Pipeline

- **MD + ink are common contracts** — MD tables define data, ink defines logic
- **Emoji** identifies item categories (2D text tags) → resolves to 3D assets (animset, grip, mesh)
- **MD tables have POI XLSX formulas** — per-level modifiers, DnD traits, damage calc
- **faker + k-random** generate rows below formula headers (same seed = reproducible)
- **Voice = FLAC reference** for Chatterbox voice cloning (23 languages)
- **RSocket + msgpack** transport events between ink runtime (2D) and renderers (3D)
- **AsyncAPI** specifies the event contracts
- **Unity loads AssetBundles** mapped to emoji-tagged files
- **BabylonJS Unity Exporter** converts to WebXR for inkey editor
- **OneJS MCP tools** allow scripted objects and scripted animsets
- **Item inventory changes** trigger ink script blocks and fire events

### Full Pipeline

```
faker-kotlin + k-random → emoji MD tables (characters, items, stats)
        ↓
InkMdEngine.parse() → ink VARs + EmojiAssetManifest
        ↓
LLM (LangChain4j) → creates dialogs + ink storyline
        ↓
InkEngine.continueStory() → tags emitted (# anim:, # mesh:, # voice:)
        ↓
InkAssetEventEngine → resolves tags via EmojiAssetManifest
        ↓
AssetEventBus (RSocket + msgpack) → publishes to channels (AsyncAPI contract)
        ↓
Consumers (each has own ink runtime):
  ├─ KT/JVM: InkEngine (GraalJS) → Camel routes → in-process events
  ├─ C#/Unity: ink-csharp runtime → InkAssetEventReceiver.cs → AssetBundle load → anim
  │    └─ OneJS bridges JS → C# → in-process events (no network)
  ├─ JS/BabylonJS: inkjs runtime → AssetEventClient.ts → RSocket WS → glTF → WebXR
  ├─ JS/Electron: inklecate + inkjs → RSocket WS → asset events
  └─ Inkey editor: edit mode (CodeMirror) | play mode (inkjs → asset event indicators)
```

### RSocket Connection Per Framework

| Framework | RSocket Transport | Why |
|-----------|------------------|-----|
| KT/JVM (server) | In-process (event bus origin) | Server-side, events originate here |
| C#/Unity + OneJS | In-process via `__inkBridge` | Same process, no network needed |
| JS/BabylonJS | WebSocket → Ktor `/rsocket` | Browser to server |
| JS/Electron | WebSocket → Ktor `/rsocket` | Desktop to server |
| Inkey editor | WebSocket → Ktor `/rsocket` | Browser to server |

### Inventory → AnimSet → Ink Script Block Triggering

```
inventory += sword  (ink LIST change)
  → InventoryChangeEvent(equip, 🗡️, sword)
  → EmojiAssetManifest resolves 🗡️ → sword_1h animset + weapon_sword mesh
  → RSocket publishes to ink/inventory/change channel
  → Unity/BabylonJS plays equip animation, loads sword mesh
  → ink script block === equip_sword === triggered
  → ink tags emitted → more asset events
```

---

## Step 5 Fix: ACE + Yjs + Rename ProseMirror → Remirror

### Fix 1: ACE has Yjs too (y-ace binding)

As shown in `docs/architecture/ink-collab-yjs.puml`, ACE uses `y-ace`. Fix:

1. **`docs/architecture/INK_KMP_MCP.md`** line 176 — `| None |` → `| Yjs (y-ace) |`
2. **`docs/architecture/ink-per-framework-runtime.puml`** — Add `y-ace` Yjs binding to Electron package, update legend

### Fix 2: Remove all ProseMirror references → Remirror

ProseMirror is a paid product — not used. Replace all `ProseMirror` / `y-prosemirror` with `Remirror` / `y-remirror`:

| File | Line(s) | Change |
|------|---------|--------|
| `ink-per-framework-runtime.puml` | 101-102 | `Remirror/ProseMirror` → `Remirror`, `y-prosemirror` → `y-remirror` |
| `ink-rsocket-transport.puml` | 65 | `y-prosemirror` → `y-remirror` |
| `ink-asset-event-pipeline.puml` | 43 | `y-prosemirror` → `y-remirror` |
| `INK_KMP_MCP.md` | 178, 499, 504, 554 | All ProseMirror → Remirror |
| `INK_KMP_BLADE_INK.md` | 123 | `y-prosemirror` → `y-remirror` |
| `ink-collab-yjs.puml` | 22 | `y-prosemirror` → `y-remirror` |

### Fix 3: Rename InkProseEditor → InkRemirrorEditor

Remove "Prose" prefix since ProseMirror is not used. Rename class, file, and CSS class:

| Location | Old | New |
|----------|-----|-----|
| `react-ink-editor/src/InkProseEditor.tsx` | file rename | `InkRemirrorEditor.tsx` |
| Same file | `InkProseEditorProps` | `InkRemirrorEditorProps` |
| Same file | `function InkProseEditor` | `function InkRemirrorEditor` |
| Same file | `ink-prose-editor` CSS class | `ink-remirror-editor` |
| `react-ink-editor/src/index.ts` | `export { InkProseEditor }` | `export { InkRemirrorEditor }` |
| `ink-per-framework-runtime.puml` | `InkProseEditor.tsx` / `PROSE_ED` | `InkRemirrorEditor.tsx` / `REMIRROR_ED` |
| `INK_KMP_MCP.md` | `InkProseEditor.tsx` | `InkRemirrorEditor.tsx` |

---

## Step 0: Unified `ink.model` Proto Contract (Multi-Language Code Generation)

**Single source of truth.** All data classes across KT, C#, TS/JS, Python become `.proto` message definitions in the `ink.model` package. Gradle protobuf plugin generates typed code for every framework. No more hand-maintained duplicates.

**Eliminates cross-framework duplication:**

| Concept | KT (today) | C# (today) | TS (today) | Proto (unified) |
|---------|-----------|-----------|-----------|----------------|
| Choice | `ChoiceInfo` | `ChoiceDto` | `InkChoice` | `ink.model.Choice` |
| Story output | `ContinueResult` | `StoryStateDto` | `InkStoryState` | `ink.model.StoryState` |
| Compilation | `CompileResult` | `CompileResult` | (via adapter) | `ink.model.CompileResult` |
| Variables | (via engine) | `VariablesState` | (via runtime) | `ink.model.Variable` |
| Structure | `InkSection` | `Container` | (via adapter) | `ink.model.Section` |
| Table data | `MdTable` | — | — | `ink.model.MdTable` (flexible columns) |

**Drives every protocol — zero code duplication:**

| Protocol | Wire Format | How Proto Drives It |
|----------|------------|-------------------|
| **MCP** | JSON-RPC | `JsonFormat.printer()` → JSON, proto descriptor → `inputSchema` |
| **RSocket** | msgpack binary | `jackson-dataformat-msgpack` → `ByteArray` |
| **WSS** | JSON or binary | proto → JSON or `toByteArray()` |
| **SSE** | JSON stream | proto → JSON events |
| **WebDAV** | JSON properties | proto → JSON metadata |
| **Yjs** | CRDT + proto fields | proto field names = Y.Map keys |
| **ink VARs** | ink source text | proto `MdTable` → `generateVarDeclarations()` |

### 0.1 Proto Directory Structure

```
ink-kmp-mcp/
├── src/main/proto/
│   └── ink/model/
│       ├── story.proto           # StoryState, Choice, CompileResult, StorySession
│       ├── structure.proto       # Section, Variable, Structure, DivertRef, InkList
│       ├── debug.proto           # Breakpoint, WatchVariable, DebugSession, VisitEntry, StepResult
│       ├── table.proto           # MdTable, MdRow, MdCell, CellType (flexible columns)
│       ├── document.proto        # InkFile, ParseResult, EditorMode
│       ├── asset.proto           # AssetCategory, VoiceRef, AssetRef
│       ├── faker.proto           # FakerConfig, EmojiCategory
│       ├── event.proto           # AssetEvent, InventoryChangeEvent
│       ├── mcp.proto             # JsonRpcRequest/Response, McpToolInfo, McpToolResult, McpContentBlock
│       ├── llm.proto             # GgufModel, ServiceDef
│       ├── principal.proto       # InkPrincipal, InkPrincipalInfo (auth + vcard unified)
│       ├── calendar.proto        # InkEvent
│       ├── collab.proto          # ColabDocument, ColabClient, EditorContext
│       └── sillytavern.proto     # StCharacterCard
```

### 0.2 Cross-Framework Consolidation (44 unified messages)

**story.proto** — replaces KT `ContinueResult`/`ChoiceInfo`, C# `StoryStateDto`/`ChoiceDto`, TS `InkStoryState`/`InkChoice`:
```protobuf
syntax = "proto3";
package ink.model;
option java_package = "ink.model";
option csharp_namespace = "Ink.Model";

message Choice {
  int32 index = 1;
  string text = 2;
  repeated string tags = 3;
}
message StoryState {
  string text = 1;
  bool can_continue = 2;
  repeated Choice choices = 3;
  repeated string tags = 4;
}
message CompileResult {
  bool success = 1;
  string json = 2;
  repeated string errors = 3;
  repeated string warnings = 4;
}
message StorySession {
  string id = 1;
  string source = 2;
  string state_json = 3;
}
```

**structure.proto** — replaces KT `InkSection`/`InkVariable`/`InkStructure`/`DivertRef`, maps to C# `Container`/`Divert`/`Path`:
```protobuf
message Section {
  string name = 1;
  SectionType type = 2;
  int32 start_line = 3;
  int32 end_line = 4;
  string content = 5;
  string parent = 6;
  repeated string parameters = 7;
}
enum SectionType {
  KNOT = 0;
  STITCH = 1;
  FUNCTION = 2;
  PREAMBLE = 3;
}
message Variable {
  string name = 1;
  VariableType type = 2;
  string initial_value = 3;
  int32 line = 4;
}
enum VariableType {
  VAR = 0;
  CONST = 1;
  LIST = 2;
  TEMP = 3;
}
message DivertRef {
  string target = 1;
  int32 line = 2;
  int32 column = 3;
}
message Structure {
  repeated Section sections = 1;
  repeated Variable variables = 2;
  repeated string includes = 3;
  repeated DivertRef diverts = 4;
  int32 total_lines = 5;
}
```

**table.proto** — flexible columns with typed cells:
```protobuf
message MdTable {
  string name = 1;
  repeated string columns = 2;          // dynamic column names
  repeated MdRow rows = 3;
}
message MdRow {
  map<string, MdCell> cells = 1;        // column_name → typed cell
}
message MdCell {
  string value = 1;                     // raw text value
  string formula = 2;                   // POI formula (e.g. "=D2+E2*F2")
  string evaluated = 3;                 // computed result after POI eval
  CellType type = 4;
}
enum CellType {
  STRING = 0;
  INT = 1;
  FLOAT = 2;
  BOOL = 3;
  FORMULA = 4;                          // POI XLSX formula
  EMOJI = 5;                            // emoji category reference
  FAKER = 6;                            // faker method placeholder
}
```

**asset.proto:**
```protobuf
message AssetCategory {
  string emoji = 1;
  string name = 2;
  string type = 3;
  string anim_set = 4;
  string grip_type = 5;
  string mesh_prefix = 6;
  string audio_category = 7;
}
message VoiceRef {
  string character_id = 1;
  string language = 2;
  string flac_path = 3;
}
message AssetRef {
  string emoji = 1;
  AssetCategory category = 2;
  string mesh_path = 3;
  string anim_set_id = 4;
  VoiceRef voice_ref = 5;
  map<string, string> metadata = 6;
}
```

**faker.proto:**
```protobuf
message FakerConfig {
  int64 seed = 1;
  string locale = 2;
  int32 count = 3;
  int32 level = 4;
  repeated string categories = 5;
}
message EmojiCategory {
  string emoji = 1;
  string faker_provider = 2;
  string method_chain = 3;
  int32 range_min = 4;
  int32 range_max = 5;
}
```

**event.proto:**
```protobuf
message AssetEvent {
  string session_id = 1;
  string event_type = 2;
  AssetRef asset = 3;
  int64 timestamp = 4;
}
message InventoryChangeEvent {
  string session_id = 1;
  string action = 2;           // equip, unequip, use, drop
  string emoji = 3;
  AssetRef asset = 4;
  int64 timestamp = 5;
}
```

**debug.proto, mcp.proto, llm.proto, principal.proto, calendar.proto, collab.proto, sillytavern.proto** — follow same pattern, each consolidating the KT data classes into proto messages.

### 0.3 Gradle Protobuf Plugin Configuration

In `ink-kmp-mcp/build.gradle.kts`:

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.4"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.3"
    }
    plugins {
        create("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:2.0.0:jdk8@jar" }
        create("ts") { path = "node_modules/.bin/protoc-gen-ts" }
        create("grpc_csharp") { artifact = "Grpc.Tools:2.65.0" }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("python")
                create("js") { option("import_style=commonjs,binary") }
            }
            task.plugins {
                create("grpckt")
                create("ts") { outputSubDir = "ts" }
                create("grpc_csharp") { outputSubDir = "csharp" }
            }
        }
    }
}

dependencies {
    implementation("com.google.protobuf:protobuf-kotlin:4.28.3")
    implementation("com.google.protobuf:protobuf-java-util:4.28.3")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.8")
}
```

### 0.4 Multi-Format Serialization: `InkModelSerializers.kt`

```kotlin
object InkModelSerializers {
    val json = JsonFormat.printer().omittingInsignificantWhitespace()
    val parser = JsonFormat.parser().ignoringUnknownFields()
    val msgpack = ObjectMapper(MessagePackFactory()).apply {
        registerModule(KotlinModule.Builder().build())
    }

    // MCP JSON-RPC
    fun <T : Message> toJson(msg: T): String = json.print(msg)
    fun <T : Message.Builder> fromJson(json: String, builder: T): T {
        parser.merge(json, builder); return builder
    }
    // RSocket msgpack
    fun <T : Message> toMsgpack(msg: T): ByteArray = msgpack.writeValueAsBytes(msg)
    // MCP tool inputSchema from proto descriptor
    fun toJsonSchema(descriptor: Descriptors.Descriptor): JsonObject
    // ink VARs from MdTable proto
    fun toInkVars(table: MdTableOuterClass.MdTable): String
    // kotlinx.serialization bridge for Ktor
    fun <T : Message> toJsonElement(msg: T): JsonElement
}
```

### 0.5 Generated Output → Framework Targets

```
ink-kmp-mcp/build/generated/source/proto/main/
├── java/ink/model/           → KT/JVM (server) — used directly
├── grpckt/ink/model/         → KT gRPC stubs
├── ts/ink/model/             → copy to ink-js/inkey/packages/ink-model/
├── csharp/Ink.Model/         → copy to ink-unity/InkModel/
├── python/ink_model/         → copy to tools/ink-model-py/
└── js/ink/model/             → copy to ink-js/inkey/packages/ink-model/
```

Gradle `copyProto` tasks sync generated code to framework directories.

### 0.6 Migration: Existing Classes → Proto-Generated

Existing KT data classes become thin adapters with `toProto()` / `fromProto()` during migration. Once all consumers switch to proto-generated types, the adapters are removed.

Existing C# `ChoiceDto`/`StoryStateDto`/`CompileResult` in `InkOneJsBinding.cs` → replaced by `Ink.Model.Choice`/`Ink.Model.StoryState`/`Ink.Model.CompileResult`.

Existing TS `InkChoice`/`InkStoryState` in `InkRuntimeAdapter.ts` → replaced by `ink.model.Choice`/`ink.model.StoryState`.

### 0.7 Tests

- `InkModelSerializersTest.kt` — round-trip: proto → JSON → proto, proto → msgpack → proto
- `ProtoSchemaTest.kt` — verify JSON Schema output matches MCP inputSchema format
- `MdTableProtoTest.kt` — flexible columns: create MdTable with dynamic columns, MdCell types, formula cells
- Verify all 44 message types compile and serialize correctly across formats

---

## Step 1: EmojiAssetManifest + InkFakerEngine (Foundation)

### 1.1 `ink-kmp-mcp/src/ink/mcp/EmojiAssetManifest.kt` (NEW)

Core emoji → asset mapping. Data classes + resolution:

```kotlin
data class AssetCategory(
    val emoji: String,          // "🗡️"
    val name: String,           // "sword"
    val type: String,           // "weapon", "armor", "character", "consumable", "quest", "currency"
    val animSet: String,        // "sword_1h", "staff_2h", "cast"
    val gripType: String,       // "main_hand", "off_hand", "two_hand", "none"
    val meshPrefix: String,     // "weapon_sword", "char_wizard"
    val audioCategory: String   // "sfx_metal", "sfx_wood", "voice"
)

data class VoiceRef(
    val characterId: String,    // "gandalf"
    val language: String,       // "en", "he", "ar"
    val flacPath: String        // "voices/gandalf_en.flac"
)

data class AssetRef(
    val emoji: String,
    val category: AssetCategory,
    val meshPath: String,       // "weapon_sword_01.glb"
    val animSetId: String,      // "sword_1h"
    val voiceRef: VoiceRef? = null,
    val metadata: Map<String, String> = emptyMap()
)
```

Default categories (10):

| Emoji | Name | Type | AnimSet | Grip | Mesh Prefix | Audio |
|-------|------|------|---------|------|-------------|-------|
| 🗡️ | sword | weapon | sword_1h | main_hand | weapon_sword | sfx_metal |
| 🛡️ | shield | armor | shield_buckler | off_hand | armor_shield | sfx_metal |
| 🪄 | staff | weapon | staff_2h | two_hand | weapon_staff | sfx_wood |
| 🏹 | bow | weapon | bow_2h | two_hand | weapon_bow | sfx_string |
| 🧙 | wizard | character | cast | none | char_wizard | voice |
| ⚗️ | potion | consumable | drink | main_hand | item_potion | sfx_glass |
| 🗝️ | key | quest | use_item | main_hand | item_key | sfx_metal |
| 🗺️ | map | quest | read | two_hand | item_map | sfx_paper |
| 🪙 | coin | currency | none | none | item_coin | sfx_coin |
| 👑 | crown | armor | equip_head | none | armor_crown | sfx_metal |

Stored in `private val categories: Map<String, AssetCategory>` (keyed by emoji) + `private val byName: Map<String, AssetCategory>` (keyed by name).

Methods:
- `resolve(emoji: String): AssetRef?` — lookup by emoji string
- `resolveByName(name: String): AssetRef?` — lookup by name (e.g., "sword")
- `resolveTag(key: String, value: String): AssetRef?` — parse ink tag like `mesh:weapon_sword_01` → resolve by mesh prefix
- `parseInkTags(tags: List<String>): List<AssetRef>` — parse tags from `ContinueResult.tags` (format: `# mesh:🗡️`, `# anim:sword_slash`, `# voice:gandalf_en`)
- `allCategories(): List<AssetCategory>` — list all registered categories
- `registerCategory(category: AssetCategory)` — add custom category

### 1.2 `ink-kmp-mcp/src/ink/mcp/InkFakerEngine.kt` (NEW)

Uses **kotlin-faker 2.0** (modular) + `kotlin.random.Random(seed)` for deterministic generation + **Apache POI** for formula evaluation.

**Key design: POI requires workbook context**, not raw formula strings. Implementation:
1. Create in-memory `XSSFWorkbook`
2. Populate header row from MD table columns
3. Populate data rows with faker-generated values
4. Set formula cells (cells starting with `=`)
5. Evaluate with `FormulaEvaluator`
6. Read back evaluated values → new `MdTable`

**kotlin-faker 2.0 modular providers** — need separate Gradle deps:
- `kotlin-faker` (core) — `faker.name`, `faker.commerce`, `faker.science`
- `kotlin-faker-games` — `faker.dnd`, `faker.game` (weapon, armor)

**Emoji category → faker method mapping:**

| Emoji | Faker Provider | Method Chain | Random Range |
|-------|---------------|-------------|-------------|
| 🗡️ sword | `faker.game.elderScrolls.weapon()` | name | base_dmg: 8..20 |
| 🛡️ shield | `faker.game.elderScrolls.weapon()` | name + "Shield" | defense: 5..15 |
| 🪄 staff | `faker.game.elderScrolls.weapon()` | name + "Staff" | mana_cost: 10..30 |
| 🏹 bow | `faker.game.elderScrolls.weapon()` | name + "Bow" | range: 10..50 |
| 🧙 character | `faker.name.name()` + `faker.dnd.klass()` + `faker.dnd.races()` + `faker.dnd.alignments()` | composite | STR/DEX/CON/INT/WIS/CHA: 3..18 |
| ⚗️ potion | `faker.science.element()` | name | heal: 10..50 |
| 🗝️ key | `faker.ancient.god()` | name | rarity: 1..5 |
| 🗺️ map | `faker.address.city()` | name | distance: 1..100 |
| 🪙 coin | `faker.commerce.price()` | value | value: 1..1000 |
| 👑 crown | `faker.ancient.titan()` | name | prestige: 1..10 |

```kotlin
data class FakerConfig(
    val seed: Long = 42L,
    val locale: String = "en",
    val count: Int = 5,
    val level: Int = 1,
    val categories: List<String> = emptyList() // filter by emoji category names
)
```

Methods:
- `generateItems(config: FakerConfig): InkMdEngine.MdTable` — items table with formula row
- `generateCharacters(config: FakerConfig): InkMdEngine.MdTable` — DnD characters with formula row
- `generateStoryMd(config: FakerConfig): String` — full MD doc with formula headers + faker rows + ink blocks
- `evaluateFormulas(table: InkMdEngine.MdTable): InkMdEngine.MdTable` — POI workbook evaluation
- `randomInRange(random: Random, range: IntRange): Int` — seeded random for stat values

**POI formula flow:**
```
MdTable with formula row (row 0 has `=D+E*F` patterns)
  → XSSFWorkbook: header row + formula row + data rows
  → FormulaEvaluator.evaluateAll()
  → Read back: formula cells → computed numeric values
  → Return new MdTable with evaluated cells
```

**Seed metadata** in MD comments: `<!-- seed: 42, level: 1 -->`

### 1.3 Extend `ink-kmp-mcp/src/ink/mcp/InkMdEngine.kt`

Add methods to existing `InkMdEngine` class (250 lines):

- `resolveAssets(table: MdTable, manifest: EmojiAssetManifest): List<AssetRef>` — reads emoji column from table rows, resolves each via manifest
- `evaluateFormulas(table: MdTable): MdTable` — delegates to `InkFakerEngine.evaluateFormulas()`
- `renderWithFormulas(markdown: String, manifest: EmojiAssetManifest): Map<String, String>` — like `render()` but evaluates formulas first
- Update `parseTable()` to detect formula rows (cells starting with `=`) and preserve them
- Update `generateVarDeclarations()` to handle evaluated numeric formula results

### 1.4 `ink-kmp-mcp/build.gradle.kts` — Add dependencies

```kotlin
// kotlin-faker 2.0 (modular)
val fakerVersion = "2.0.0-rc.7"
implementation("io.github.serpro69:kotlin-faker:$fakerVersion")
implementation("io.github.serpro69:kotlin-faker-games:$fakerVersion")

// Apache POI for XLSX formula evaluation
implementation("org.apache.poi:poi-ooxml:5.2.5")
```

### 1.5 Tests

**`EmojiAssetManifestTest.kt`:**
- `resolve returns AssetRef for known emoji` — 🗡️ → sword category
- `resolve returns null for unknown emoji`
- `resolveByName returns AssetRef` — "sword" → sword category
- `parseInkTags parses mesh tags` — `["# mesh:🗡️"]` → `[AssetRef(sword)]`
- `parseInkTags parses anim tags` — `["# anim:sword_slash"]` → resolves animSet
- `parseInkTags parses voice tags` — `["# voice:gandalf_en"]`
- `parseInkTags ignores non-asset tags` — `["# author: tolkien"]` → empty
- `allCategories returns 10 defaults`
- `registerCategory adds custom category`

**`InkFakerEngineTest.kt`:**
- `generateItems produces MdTable with correct columns` — emoji, name, type, base_dmg, per_level, level, total_dmg
- `generateItems is deterministic with same seed` — seed=42 → same rows twice
- `generateItems different seed produces different data` — seed=42 ≠ seed=99
- `generateCharacters produces DnD stats` — STR, DEX, CON, INT, WIS, CHA, HP columns
- `generateCharacters stats in valid range` — all stats 3..18
- `evaluateFormulas computes arithmetic` — `=D2+E2*F2` → correct number
- `evaluateFormulas handles SUM` — `=SUM(E2:E5)` → correct total
- `evaluateFormulas handles IF` — `=IF(D2>10,"rare","common")` → string result
- `generateStoryMd produces valid markdown` — parseable by InkMdEngine
- `per-level modifiers with same seed` — level=1 vs level=5, same items, different total_dmg
- `seed metadata preserved in MD comments`

### 1.6 Register in `McpTools.kt`

Add constructor params:
```kotlin
private val assetManifest: EmojiAssetManifest = EmojiAssetManifest(),
private val fakerEngine: InkFakerEngine = InkFakerEngine(assetManifest),
```

Add to `tools` list: `addAll(assetTools)`

New tool group `assetTools` (6 tools):

| Tool | Description | Params |
|------|-------------|--------|
| `resolve_emoji` | Resolve emoji → AssetCategory (animset, grip, mesh, audio) | `emoji: String` |
| `parse_asset_tags` | Parse ink tags → list of AssetRef | `tags: List<String>` |
| `generate_items` | Generate items MD table with emoji categories + faker + formulas | `seed, count, level, categories` |
| `generate_characters` | Generate DnD characters MD table with faker + stat formulas | `seed, count` |
| `generate_story_md` | Generate full story MD with characters + items + formulas + ink blocks | `seed, level, characters, items` |
| `evaluate_formulas` | Evaluate POI XLSX formulas in MD table cells | `markdown: String` |

Add handler methods: `handleResolveEmoji`, `handleParseAssetTags`, `handleGenerateItems`, `handleGenerateCharacters`, `handleGenerateStoryMd`, `handleEvaluateFormulas`

Add to `callTool` `when` block.

---

## Step 2: RSocket + msgpack + AsyncAPI Event Layer

### 2.1 `docs/asyncapi/ink-asset-events.yaml` (NEW)

AsyncAPI 3.0 contract defining 6 channels:

| Channel | Direction | Payload | Purpose |
|---------|-----------|---------|---------|
| `ink/story/tags` | server → client | InkTagEvent | Tags emitted on story continue |
| `ink/asset/load` | server → client | AssetLoadRequest | Load mesh/animset/voice |
| `ink/asset/loaded` | client → server | AssetLoadedEvent | Confirm asset ready |
| `ink/inventory/change` | server → client | InventoryChangeEvent | Item equip/unequip/add/remove |
| `ink/voice/synthesize` | server → client | VoiceSynthRequest | TTS request |
| `ink/voice/ready` | client → server | VoiceReadyEvent | Audio ready |

All payloads: `contentType: application/x-msgpack`

### 2.2 `ink-kmp-mcp/src/ink/mcp/InkAssetEventEngine.kt` (NEW)

Watches ink story state (tags, variables) and emits asset events:
- `processStoryState(sessionId, tags, knot)` → resolves tags → emits events
- `processInventoryChange(sessionId, previous, current)` → detects LIST diff → emits equip/unequip events
- `processTag(sessionId, tag)` → single tag → asset event

### 2.3 `ink-kmp-mcp/src/ink/mcp/AssetEventBus.kt` (NEW)

In-process event bus (for tests + Unity OneJS same-process). RSocket transport optional.
- `publish(channel, event)`, `subscribe(channel): Flow<AssetEvent>`
- `fireAndForget(request)`, `requestStream(sessionId): Flow<AssetEvent>`
- msgpack serialization via `jackson-dataformat-msgpack`

### 2.4 `ink-kmp-mcp/build.gradle.kts` — Add dependencies

```kotlin
// RSocket-Kotlin
implementation("io.rsocket.kotlin:rsocket-ktor-server:0.16.0")
implementation("io.rsocket.kotlin:rsocket-ktor-client:0.16.0")
// msgpack
implementation("org.msgpack:jackson-dataformat-msgpack:0.9.8")
```

### 2.5 Extend `CamelRoutes.kt`

Add `direct:ink-asset-event` route that processes events through `InkAssetEventEngine` and publishes to `AssetEventBus`.

### 2.6 Extend `McpRouter.kt`

Add RSocket WebSocket endpoint (`/rsocket`) alongside existing SSE/WS routes. Supports:
- fireAndForget: asset load requests
- requestStream: subscribe to session events
- requestChannel: bidirectional voice synth

### 2.7 `ink-js/.../AssetEventClient.ts` (NEW)

Browser RSocket client: connects to Ktor `/rsocket`, decodes msgpack payloads, exposes `subscribe(sessionId, callback)`.

Dependencies in `react-ink-editor/package.json`:
```json
"rsocket-core": "^1.0.0-alpha.3",
"rsocket-websocket-client": "^1.0.0-alpha.3",
"@msgpack/msgpack": "^3.0.0"
```

### 2.8 Extend `InkRuntimeAdapter.ts`

Add to `InkStoryState`: `assetEvents?: AssetEvent[]`
Add to `InkRuntimeAdapter`: `onAssetEvent?(sessionId, callback): () => void`

### 2.9 `ink-unity/InkAssetEventReceiver.cs` (NEW)

Unity MonoBehaviour that receives asset events:
- In-process bridge for OneJS (no network): `ProcessEvent(msgpackBase64)`
- WebSocket RSocket for standalone: `ConnectRSocket(url)`
- Maps emoji → AssetBundle prefab path → instantiate + play animation
- `OnAssetEvent` C# event for Unity scripts

### 2.10 Extend `InkOneJsBinding.cs`

Add 11th method: `GetAssetEvents(sessionId)` — parses current tags through EmojiAssetManifest, returns JSON array of AssetRef objects.

### 2.11 `McpTools.kt` — Add 4 tools

`resolve_emoji`, `parse_asset_tags`, `emit_asset_event`, `list_asset_events`

### 2.12 Tests

- `InkAssetEventEngineTest.kt` — tag → event, inventory change detection (mocked bus)
- `AssetEventBusTest.kt` — in-process publish/subscribe round-trip with msgpack
- `InkBidiTdd.Tests/InkAssetEventReceiverTest.cs` — C# event receiver (mocked bridge)

---

## Step 3: Chatterbox ONNX TTS

### 3.1 `ink-kmp-mcp/src/ink/mcp/ChatterboxTtsEngine.kt` (NEW)

Voice cloning from FLAC reference files via ONNX Runtime:
- `synthesize(text, voiceRef, language): ByteArray` — returns WAV
- `listVoices(): List<VoiceRef>`
- `isAvailable(): Boolean`

Mock backend for CI (no real ONNX model downloads).

### 3.2 `ink-unity/InkTtsBridge.cs` (NEW)

C# wrapper around Microsoft.ML.OnnxRuntime for Unity.

### 3.3 `ink-js/.../InkTtsAdapter.ts` (NEW)

Browser: `onnxruntime-web` WASM, fallback Web Speech API.

### 3.4 Extend `CamelRoutes.kt`

Add `direct:voice-synthesize` route.

### 3.5 MCP Tools: `synthesize_voice`, `list_voices`

### 3.6 Dependencies (deferred until real inference)

```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime:1.18.0")
```

---

## Step 4: BabylonJS Unity Exporter Integration

### 4.1 `ink-js/.../BabylonJsAssetLoader.ts` (NEW)

Receives AssetEvents via `AssetEventClient`, loads glTF meshes (from BabylonJS Unity Exporter output), plays animations. Methods: `loadMesh(assetRef)`, `playAnimation(meshId, animSetId)`, `bindToEventClient(client, sessionId)`.

### 4.2 `ink-unity/InkBabylonExporter.cs` (NEW)

Utility for exporting Unity scenes to BabylonJS-compatible glTF. Scans for emoji-tagged GameObjects, exports with animation metadata.

### 4.3 Workspace config

Add `packages/babylon-ink` to inkey workspace if needed, or integrate into `react-ink-editor`.

---

## Step 5: PlantUML Diagrams + MD Docs Per Framework/Platform

### 5.1 `docs/architecture/ink-per-framework-runtime.puml` (NEW)

Per-framework compiler/runtime + editor component diagram:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Ink Language (.ink)                                │
│                 @inky/ink-language (shared grammar)                          │
└──────┬──────────────┬───────────────┬──────────────┬────────────────────────┘
       │              │               │              │
 ┌─────▼─────┐  ┌────▼────┐   ┌──────▼──────┐  ┌───▼──────────────────────┐
 │ KT / JVM  │  │C# Unity │   │ JS Electron │  │ JS Browser (inkey web)   │
 │           │  │         │   │ (Inky desk) │  │                          │
 │ GraalJS   │  │ink-csharp│  │ ACE editor  │  │ CodeMirror 6 (ink src)   │
 │ (≈OneJS)  │  │ +OneJS  │   │ inklecate   │  │ Remirror (MD+ink blocks) │
 │ + inkjs   │  │ +inkjs  │   │ + inkjs     │  │ Yjs collab               │
 │           │  │         │   │             │  │ inkjs (play mode)        │
 │           │  │         │   │             │  │ BabylonJS (3D/WebXR)     │
 └─────┬─────┘  └────┬────┘   └──────┬──────┘  └───┬──────────────────────┘
       │              │               │              │
       └──────────────┴───────────────┴──────────────┘
                          │
                   RSocket + msgpack
                   (AsyncAPI contract)
```

Diagram includes:
- Color coding: KT=blue, C#=purple, JS/Electron=green, JS/inkey=teal
- Each box shows: compiler, runtime, embedded JS engine, editor(s)
- GraalJS↔OneJS parallel annotated
- Editor stack: ACE (Electron) vs CodeMirror+Remirror+Yjs (inkey web)
- RSocket transport lines (in-process vs WebSocket)
- Shared `@inky/ink-language` grammar across ACE, CodeMirror, Remirror

### 5.2 `docs/architecture/ink-asset-event-pipeline.puml` (NEW)

Activity diagram showing the full event flow:

```
ink story continue → tags → EmojiAssetManifest → AssetEvent
  → RSocket publish → [Unity | BabylonJS | Inkey] consumer
  → AssetBundle/glTF load → animation → voice synth
```

Includes: inventory change detection, emoji→category resolution, FLAC voice ref.

### 5.3 `docs/architecture/ink-rsocket-transport.puml` (NEW)

Sequence diagram showing RSocket interactions per framework:

- KT server (event origin) → in-process publish
- C#/Unity + OneJS → in-process via `__inkBridge` (no network)
- JS/BabylonJS → WebSocket → Ktor `/rsocket` endpoint
- JS/Electron → WebSocket → Ktor `/rsocket` endpoint
- Inkey editor → WebSocket → Ktor `/rsocket` endpoint

Shows: fireAndForget, requestStream, requestChannel patterns.

### 5.4 Update `docs/architecture/ink-mcp-tools.puml`

Add new tool groups to the existing 71-tool diagram:

```
package "Asset Pipeline (6 tools)" as assetTools #B2DFDB {
    card "resolve_emoji"
    card "parse_asset_tags"
    card "emit_asset_event"
    card "list_asset_events"
    card "generate_ink_vars"
    card "generate_story_md"
}

package "TTS (2 tools)" as ttsTools #FFCCBC {
    card "synthesize_voice"
    card "list_voices"
}

rectangle "EmojiAssetManifest" as manifestEngine
rectangle "InkAssetEventEngine" as assetEventEngine
rectangle "AssetEventBus\n(RSocket+msgpack)" as eventBus
rectangle "ChatterboxTtsEngine\n(ONNX)" as ttsEngine
```

Update title: "Inky MCP Server -- 79 Tool Architecture"
Update legend total: 79 (71 + 6 asset + 2 TTS)

### 5.5 Update `docs/architecture/ink-kmp-classes.puml`

Add new classes to class diagram:
- `EmojiAssetManifest` with `AssetCategory`, `VoiceRef`, `AssetRef` data classes
- `InkAssetEventEngine` with `AssetEvent` data class
- `AssetEventBus` with `publish`/`subscribe` methods
- `InkFakerEngine` with `FakerConfig` data class
- Relationships: `InkMdEngine` → `EmojiAssetManifest` (resolves), `InkAssetEventEngine` → `AssetEventBus` (publishes)

### 5.6 Update `docs/architecture/ink-kmp-architecture.puml`

Add Inky editor layer (JS Ecosystem):
- Electron (desktop): ACE editor → ink source → inklecate → inkjs
- inkey (web): CodeMirror 6 → ink source, Remirror → MD+ink blocks, Yjs collab
- Shared: `@inky/ink-language` grammar feeds ACE, CodeMirror, Remirror

Add to Consumers section:
- Unity: `InkAssetEventReceiver.cs` → AssetBundle
- BabylonJS: `BabylonJsAssetLoader.ts` → glTF/WebXR
- inkey: edit mode (CodeMirror/Remirror) | play mode (InkPlayer + inkjs)

Add event bus layer between MCP Server and Consumers.

### 5.7 Update `docs/architecture/INK_KMP_MCP.md`

Add new sections:
- **Per-Framework Runtime Table** (compiler, runtime, JS engine, entry point)
- **Asset Pipeline** section (EmojiAssetManifest, InkFakerEngine, events)
- **Event Transport** section (RSocket, msgpack, AsyncAPI)
- **New MCP Tools** table (asset + TTS tools)
- **File Map** update (new engine files)
- Update **Stack** table (add RSocket, msgpack, faker-kotlin)
- Update tool count: 71 → 79

### 5.8 `docs/architecture/INK_ASSET_PIPELINE.md` (NEW)

Comprehensive architecture doc for the asset pipeline:
- Context: why event-driven (decoupled renderer, cross-platform)
- Per-framework runtime table
- Emoji category system (table of emoji → animset/grip/mesh)
- Voice FLAC reference system
- Event flow diagrams (references PUML files)
- AsyncAPI contract summary
- RSocket transport per framework
- Integration points (McpTools, CamelRoutes, McpRouter)
- Inventory → animset → ink script block triggering

---

## File Summary

### New Files (38)

| # | Path | Step |
|---|------|------|
| 1 | `ink-kmp-mcp/src/main/proto/ink/model/story.proto` | 0 |
| 2 | `ink-kmp-mcp/src/main/proto/ink/model/structure.proto` | 0 |
| 3 | `ink-kmp-mcp/src/main/proto/ink/model/debug.proto` | 0 |
| 4 | `ink-kmp-mcp/src/main/proto/ink/model/table.proto` | 0 |
| 5 | `ink-kmp-mcp/src/main/proto/ink/model/document.proto` | 0 |
| 6 | `ink-kmp-mcp/src/main/proto/ink/model/asset.proto` | 0 |
| 7 | `ink-kmp-mcp/src/main/proto/ink/model/faker.proto` | 0 |
| 8 | `ink-kmp-mcp/src/main/proto/ink/model/event.proto` | 0 |
| 9 | `ink-kmp-mcp/src/main/proto/ink/model/mcp.proto` | 0 |
| 10 | `ink-kmp-mcp/src/main/proto/ink/model/llm.proto` | 0 |
| 11 | `ink-kmp-mcp/src/main/proto/ink/model/principal.proto` | 0 |
| 12 | `ink-kmp-mcp/src/main/proto/ink/model/calendar.proto` | 0 |
| 13 | `ink-kmp-mcp/src/main/proto/ink/model/collab.proto` | 0 |
| 14 | `ink-kmp-mcp/src/main/proto/ink/model/sillytavern.proto` | 0 |
| 15 | `ink-kmp-mcp/src/ink/mcp/InkModelSerializers.kt` | 0 |
| 16 | `ink-kmp-mcp/src/test/kotlin/ink/mcp/InkModelSerializersTest.kt` | 0 |
| 17 | `ink-kmp-mcp/src/test/kotlin/ink/mcp/MdTableProtoTest.kt` | 0 |
| 18 | `ink-kmp-mcp/src/ink/mcp/EmojiAssetManifest.kt` | 1 |
| 19 | `ink-kmp-mcp/src/ink/mcp/InkFakerEngine.kt` | 1 |
| 20 | `ink-kmp-mcp/src/test/kotlin/ink/mcp/EmojiAssetManifestTest.kt` | 1 |
| 21 | `ink-kmp-mcp/src/test/kotlin/ink/mcp/InkFakerEngineTest.kt` | 1 |
| 5 | `docs/asyncapi/ink-asset-events.yaml` | 2 |
| 6 | `ink-kmp-mcp/src/ink/mcp/InkAssetEventEngine.kt` | 2 |
| 7 | `ink-kmp-mcp/src/ink/mcp/AssetEventBus.kt` | 2 |
| 8 | `ink-kmp-mcp/src/test/kotlin/ink/mcp/InkAssetEventEngineTest.kt` | 2 |
| 9 | `ink-kmp-mcp/src/test/kotlin/ink/mcp/AssetEventBusTest.kt` | 2 |
| 10 | `ink-js/inkey/packages/react-ink-editor/src/AssetEventClient.ts` | 2 |
| 11 | `ink-unity/InkAssetEventReceiver.cs` | 2 |
| 12 | `InkBidiTdd.Tests/InkAssetEventReceiverTest.cs` | 2 |
| 13 | `ink-kmp-mcp/src/ink/mcp/ChatterboxTtsEngine.kt` | 3 |
| 14 | `ink-unity/InkTtsBridge.cs` | 3 |
| 15 | `ink-js/inkey/packages/react-ink-editor/src/InkTtsAdapter.ts` | 3 |
| 16 | `ink-js/inkey/packages/react-ink-editor/src/BabylonJsAssetLoader.ts` | 4 |
| 17 | `docs/architecture/ink-per-framework-runtime.puml` | 5 |
| 18 | `docs/architecture/ink-asset-event-pipeline.puml` | 5 |
| 19 | `docs/architecture/ink-rsocket-transport.puml` | 5 |
| 20 | `docs/architecture/INK_ASSET_PIPELINE.md` | 5 |
| 21 | `ink-unity/InkBabylonExporter.cs` | 4 |
| 22 | `ink-kmp-mcp/src/test/kotlin/ink/mcp/ChatterboxTtsEngineTest.kt` | 3 |

### Modified Files (12)

| # | Path | Changes | Step |
|---|------|---------|------|
| 1 | `ink-kmp-mcp/build.gradle.kts` | faker-kotlin, RSocket-Kotlin, msgpack deps | 1-2 |
| 2 | `ink-kmp-mcp/src/ink/mcp/InkMdEngine.kt` | Add `resolveAssets()` method | 1 |
| 3 | `ink-kmp-mcp/src/ink/mcp/McpTools.kt` | Add assetEventEngine param, 6+ new tools | 1-2 |
| 4 | `ink-kmp-mcp/src/ink/mcp/CamelRoutes.kt` | Add asset event + voice routes | 2-3 |
| 5 | `ink-kmp-mcp/src/ink/mcp/McpRouter.kt` | Add RSocket WebSocket endpoint | 2 |
| 6 | `ink-unity/InkOneJsBinding.cs` | Add `GetAssetEvents()` (11th method) | 2 |
| 7 | `ink-js/.../InkRuntimeAdapter.ts` | Add `assetEvents` to state, `onAssetEvent` | 2 |
| 8 | `ink-js/.../react-ink-editor/package.json` | Add rsocket, msgpack deps | 2 |
| 9 | `docs/architecture/ink-mcp-tools.puml` | Add Asset Pipeline (6) + TTS (2) tool groups, update to 79 | 5 |
| 10 | `docs/architecture/ink-kmp-classes.puml` | Add EmojiAssetManifest, AssetEventBus classes | 5 |
| 11 | `docs/architecture/ink-kmp-architecture.puml` | Add event bus layer, per-framework consumers | 5 |
| 12 | `docs/architecture/INK_KMP_MCP.md` | Per-framework table, asset pipeline, 79 tools | 5 |

---

## Dependencies

### Step 0 (ink.model proto codegen)

| Ecosystem | Package | Scope | Version |
|-----------|---------|-------|---------|
| KT | `com.google.protobuf:protobuf-kotlin` | impl | 4.28.3 |
| KT | `com.google.protobuf:protobuf-java-util` | impl | 4.28.3 |
| KT | `org.msgpack:jackson-dataformat-msgpack` | impl | 0.9.8 |
| KT | `io.grpc:protoc-gen-grpc-kotlin` | protoc plugin | 2.0.0 |
| Gradle | `com.google.protobuf` plugin | plugin | 0.9.4 |
| TS | `protoc-gen-ts` | devDep | 0.8.7 |
| C# | `Grpc.Tools` | protoc plugin | 2.65.0 |

### Step 1 (faker + POI)

| Ecosystem | Package | Scope | Version |
|-----------|---------|-------|---------|
| KT | `io.github.serpro69:kotlin-faker` | impl | 2.0.0-rc.7 |
| KT | `io.github.serpro69:kotlin-faker-games` | impl | 2.0.0-rc.7 |
| KT | `org.apache.poi:poi-ooxml` | impl | 5.2.5 |

### Steps 2-4 (later)

| Ecosystem | Package | Scope | Version |
|-----------|---------|-------|---------|
| KT | `io.rsocket.kotlin:rsocket-ktor-server` | impl | 0.16.0 |
| KT | `io.rsocket.kotlin:rsocket-ktor-client` | impl | 0.16.0 |
| KT | `org.msgpack:jackson-dataformat-msgpack` | impl | 0.9.8 |
| KT | `com.microsoft.onnxruntime:onnxruntime` | impl | 1.18.0 |
| TS | `rsocket-core` | npm | 1.0.0-alpha.3 |
| TS | `rsocket-websocket-client` | npm | 1.0.0-alpha.3 |
| TS | `@msgpack/msgpack` | npm | 3.0.0 |
| C# | `Microsoft.ML.OnnxRuntime` | NuGet | 1.18.0 |

---

## Verification

```bash
# Step 0: Proto compilation + serializer tests
./gradlew :ink-kmp-mcp:generateProto
./gradlew :ink-kmp-mcp:test --tests "ink.mcp.ProtoSerializersTest"

# Step 5: Render PlantUML diagrams to SVG
./gradlew :ink-kmp-mcp:plantUml

# Step 1: Emoji manifest + faker
./gradlew :ink-kmp-mcp:test --tests "ink.mcp.EmojiAssetManifestTest"
./gradlew :ink-kmp-mcp:test --tests "ink.mcp.InkFakerEngineTest"

# Step 2: Event engine + bus
./gradlew :ink-kmp-mcp:test --tests "ink.mcp.InkAssetEventEngineTest"
./gradlew :ink-kmp-mcp:test --tests "ink.mcp.AssetEventBusTest"
dotnet test InkBidiTdd.Tests --filter "InkAssetEventReceiverTest"

# Step 3: TTS (mocked ONNX)
./gradlew :ink-kmp-mcp:test --tests "ink.mcp.ChatterboxTtsEngineTest"

# Full suite
./gradlew testAll
```

## Progress

- [x] **Step 5** — PlantUML diagrams + MD docs (committed + pushed)
- [x] **Step 5 fix** — ACE+Yjs, ProseMirror→Remirror, InkProseEditor→InkRemirrorEditor (committed + pushed)
- [x] **Step 0** — 14 .proto files in ink.model, Gradle protobuf plugin, InkModelSerializers.kt (committed + pushed)
- [x] **Step 1** — EmojiAssetManifest.kt, InkFakerEngine.kt, InkMdEngine extensions, 6 MCP tools in McpTools.kt (code + tests written)
- [ ] **Step 1 remaining** — Copy plan files to docs/plan/, commit all Step 1 files + push to `claude/sub-agents-code-review-7KAZW` ← **NEXT**
- [ ] **Step 2** — RSocket + msgpack + AsyncAPI event layer
- [ ] **Step 3** — Chatterbox TTS
- [ ] **Step 4** — BabylonJS loader
