# Consolidated Plan: ink.kt Feature Parity + KMP + Event Pipeline + Auth

> **Single plan file** — merges:
> - `playful-imagining-petal.md` (Event-Driven Emoji Asset Pipeline + Three-Layer Architecture)
> - `quiet-tumbling-frog.md` (iCal4j + Auth + vCard Integration)
> - Feature Parity Merge analysis (Java/C# → ink.kt)
>
> **Copy to**: `docs/plan/purrfect-chasing-dewdrop.md`
> **Constraint**: NO JVM imports — pure Kotlin everywhere (commonMain, tests, all)

---

## 1. Context

The ink.kt package (60 files, ~9600 LOC) contains both the compiled runtime (from blade-ink Java) and the parser engine (from mica-ink). This plan covers:

1. **Complete feature parity** — every method from Java (blade-ink) and C# (inkle reference) in ink.kt
2. **Pure Kotlin** — zero `java.*` imports anywhere (commonMain AND tests) — no JVM imports at all
3. **Multi-format annotations** — proto/msgpack/openapi/asyncapi/graphql/JVM/KT/JS for code generation
4. **KMP tests** — blade-ink + mica tests ported to pure Kotlin (no JVM imports)
5. **PUML = our map** — ALL 70 classes in ink.[lang].puml diagrams with detailed doc notes and progress tracking. All 7 puml files (kt, cs, java, js, ts, proto, mica) in sync with cross-language notes to/from ink.kt. **The key for the full port is detailed class diagrams with docs notes and progress.**
6. **Event pipeline** — RSocket + msgpack + AsyncAPI for asset events
7. **Auth + Calendar + vCard** — Keycloak OIDC + iCal4j + ez-vcard

---

## 2. Three-Layer Architecture

**Single source of truth in Kotlin.** All API contracts derived from annotated Kotlin data classes.

| Layer | Package | Purpose | Annotations |
|-------|---------|---------|-------------|
| **1. ink.model** | `ink.model` | Kotlin data classes | `@Proto @OpenApi @Mcp @AsyncApi @KmpExport` |
| **2. ink.kt** | `ink.kt` | Runtime classes (extend model) | `@Serializable @SerialName @ProtoNumber` |
| **3. ink.kt.services** | `ink.kt.services` | Companion objects (ink logic) | Business logic, no annotations |

### Annotation-Driven Schema Generation

| Annotation | Output | Wire Format |
|------------|--------|-------------|
| `@Proto` | .proto files -> gRPC stubs | protobuf / msgpack / RSocket |
| `@OpenApi` | REST API spec (OpenAPI 3.1) | JSON |
| `@Mcp` | MCP tool schemas | JSON-RPC |
| `@AsyncApi` | Event streaming spec (AsyncAPI 3.0) | WSS / SSE / RSocket |
| `@GraphQL` | GraphQL schema | GraphQL |
| `@KmpExport` | JS/Native/WASM exports | platform-specific |

### Cross-Framework Consolidation

| Concept | KT (today) | C# (today) | TS (today) | ink.model (target) |
|---------|-----------|-----------|-----------|-------------------|
| Choice | `ChoiceInfo` | `ChoiceDto` | `InkChoice` | `ink.model.Choice` |
| Story output | `ContinueResult` | `StoryStateDto` | `InkStoryState` | `ink.model.StoryState` |
| Compilation | `CompileResult` | `CompileResult` | (via adapter) | `ink.model.CompileResult` |
| Variables | (via engine) | `VariablesState` | (via runtime) | `ink.model.Variable` |

### Proto Directory (14 files in `src/main/proto/ink/model/`)

`story.proto`, `structure.proto`, `debug.proto`, `table.proto`, `document.proto`, `asset.proto`, `faker.proto`, `event.proto`, `mcp.proto`, `llm.proto`, `principal.proto`, `calendar.proto`, `collab.proto`, `sillytavern.proto`

### Per-Framework Runtime

| Framework | Compiler | Runtime | JS Engine | Entry Point |
|-----------|----------|---------|-----------|-------------|
| **KT/JVM** | GraalJS + inkjs | GraalJS + inkjs | GraalVM Polyglot | `InkEngine.kt` |
| **C#/Unity** | ink-csharp | ink-csharp Runtime | OneJS | `InkOneJsBinding.cs` |
| **JS/Electron** | inklecate | inkjs | N/A | `inklecate.js` |
| **JS/BabylonJS** | inkjs | inkjs | N/A | `InkRuntimeAdapter.ts` |

---

## 3. Completed Steps

- [x] **Step 0** — 14 .proto files, Gradle protobuf plugin, InkModelSerializers.kt
- [x] **Step 1** — EmojiAssetManifest.kt, InkFakerEngine.kt, 6 MCP tools
- [x] **Step 1b** — UnicodeSymbolParser rewrite
- [x] **Step 2** — RSocket + msgpack + AsyncAPI event layer
- [x] **Step 5** — PlantUML diagrams + MD docs
- [x] **Step 5 fix** — ACE+Yjs, ProseMirror->Remirror rename
- [x] **Step 6a** — Parser fields added to ink.kt data classes (InkObject, Container, Choice, Story)
- [x] **Step 6b** — KMP-port mica (BigDecimal->Double, Jackson->kotlinx, reflection->fun interface)

---

## 4. Feature Parity Gap Analysis

### Classes at Feature Parity (30 — no method gaps)

InkObject, Container, Value (sealed, 7 types), NativeFunctionCall (30 operators via SAM), VariablesState, Path, Pointer, ChoicePoint, Divert, Tag, Glue, Void, ControlCommand, VariableAssignment, VariableReference, PushPopType, ValueType, ErrorType, StoryException, SearchResult, DebugMetadata, StatePatch, ListDefinition, ListDefinitionsOrigin, InkListItem, INamedContent, SimpleJson, JsonSerialisation, Profiler+ProfileNode, Stopwatch, Flow

### Actual Gaps (~16 methods, ~300 LOC)

| File | Gap | Details |
|------|-----|---------|
| **StoryState** | ~5 methods | `toJson()`, `loadJson()`, `toJsonObject()`, `fromJsonObject()`, `visitCountAtPathString()` |
| **JsonSerialisation** | ~3 methods | `encodeFlow()`, `encodeCallStackThread()`, `decodeFlow()` |
| **VariablesState** | ~1 method | `toJsonElement()` for state serialization |
| **InkList** | ~4 methods | Story constructor, `singleOriginListName`, `addItem` overload, `contains` alias |
| **Story** | ~1 method | `toJson(appendable: Appendable)` stream overload |
| NativeFunctionCall | 0 | Java's 94 anonymous classes = Kotlin SAM lambdas |
| CallStack | 0 | Java getters/setters = Kotlin properties |

---

## 5. Implementation Phases (Current Work)

### Phase 0: Verify Reference Files Unchanged
- `git diff` verify mica source (`jvmMain/kotlin/ink/kt/mica/`) synced to original
- `git diff` verify blade-ink Java (`jvmMain/java/com/bladecoder/ink/`) synced to original
- Both READONLY — never modify, only ink.kt classes
- **Files**: verification only

### Phase 1: Pure Kotlin TestResources (NO JVM imports)
- Remove `java.io.File`, `java.net.URI`, `classLoader` from `TestResources.kt`
- Use `kotlin.io` path APIs or `expect/actual` for resource loading
- All test files must have zero `java.*` imports
- **Files**: `src/test/kotlin/ink/kt/TestResources.kt`

### Phase 2: State Serialization (enables save/load, ~300 lines)

**Dependency order**: 2A -> 2B -> 2C -> 2D. All use `kotlinx.serialization.json` (KMP-compatible).

#### 2A. VariablesState (~20 lines)
- `toJsonElement(): JsonObject` — serialize global variables
- `fromJsonElement(json: JsonObject)` — deserialize
- **File**: `VariablesState.kt`
- **Source**: Java `VariablesState.java:129-148`, C# `VariablesState.cs:171-200`

#### 2B. JsonSerialisation Flow/CallStack (~80 lines)
- `encodeFlow(flow: Flow): JsonObject`
- `encodeCallStackThread(thread: CallStack.Thread): JsonObject`
- `decodeFlow(json: JsonObject, storyContext: Story): Flow`
- **File**: `JsonSerialisation.kt`
- **Source**: Java `Flow.java:40-119`, `CallStack.java:133-375`

#### 2C. StoryState toJson/loadJson (~180 lines)
- `toJson(): String` — kotlinx `Json.encodeToString()`
- `loadJson(json: String)` — kotlinx `Json.parseToJsonElement()`
- Serializes: flows, variables, eval stack, visit counts, turn indices, story seed
- **File**: `StoryState.kt`
- **Source**: Java `StoryState.java:927-1374`, C# `StoryState.cs:37-804`

#### 2D. visitCountAtPathString (~12 lines)
- `visitCountAtPathString(pathString: String): Int`
- **File**: `StoryState.kt`
- **Source**: C# `StoryState.cs:75-92`

### Phase 3: InkList Completeness (~30 lines)
- `InkList(singleOriginListName: String, story: Story)` constructor
- `singleOriginListName: String?` property
- `addItem(itemName: String, storyObject: Story? = null)` overload
- `contains(String)` alias for `containsItemNamed`
- **File**: `InkList.kt`

### Phase 4: Story Stream Overload (~5 lines)
- `toJson(appendable: Appendable)` — KMP-compatible (not `OutputStream`)
- **File**: `Story.kt`

### Phase 5: Multi-Format Annotation Foundation
- `@Serializable` (kotlinx) — already used by StoryLoader/StorySaver
- `@ProtoNumber` on fields for proto wire compatibility
- `@SerialName` for JSON/MessagePack field naming
- **Scope**: Public API surface: `InkRuntime`, `Choice`, `StoryState`
- **Files**: `InkRuntime.kt`, key data transfer classes

### Phase 6: Document Code + Tests in ALL ink.[lang].puml — Subagents Per Class Group

**This stage is about documenting code and tests.** Detailed class diagrams with doc notes and progress are the map for the full port. Use **subagents per class group** to read actual source files and generate complete PUML with all methods, constructors, fields. Every action synced in PUML.

#### Subagent Strategy: Read Source -> Generate PUML

Each class group gets a **dedicated subagent** that:
1. Reads the actual .kt source for each class (methods, constructors, fields)
2. Reads the C#, Java, JS equivalents for cross-lang notes
3. Generates complete PUML class definition with ALL members
4. Generates cross-language notes with method-level mappings
5. Identifies gaps and marks progress status
6. Updates ink.kt.puml AND corresponding ink.[lang].puml files

**11 subagent groups (parallel where possible):**

| # | Group | Classes | Files to Read |
|---|-------|---------|---------------|
| 1 | Core | InkObject, Container, Path, Path.Component, SearchResult, Pointer, DebugMetadata | InkObject.kt + C#/Java/JS equivalents |
| 2 | Content | Choice, ChoicePoint, Divert, Tag, Glue, Void, ControlCommand, VariableAssignment, VariableReference | 9 .kt files + equivalents |
| 3 | Values | Value (sealed), 7 subtypes | Value.kt + equivalents |
| 4 | Lists | InkList, InkListItem, ListDefinition, ListDefinitionsOrigin | 4 .kt files + equivalents |
| 5 | State | Story, StoryState, Flow, CallStack (+Element, +Thread) | 4 .kt files + equivalents |
| 6 | Variables | VariablesState, StatePatch | 2 .kt files + equivalents |
| 7 | Serialization | SimpleJson (+Reader, +Writer), JsonSerialisation, StoryLoader, StorySaver | 4 .kt files + equivalents |
| 8 | Parser | InkParser, Expression, Knot, Stitch, Gather, Conditional, ConditionalOption, Declaration, ParameterizedContainer | 9 .kt files |
| 9 | Support | StoryText, Operator, Symbol, StoryJson, StoryWrapper, StoryInterrupt, VariableMap, Function, VariableMapAdapter, NativeFunctionCall | 10 .kt files + equivalents |
| 10 | Infra | Profiler, ProfileNode, Stopwatch, InkClock, DateComponents, StoryException, 3 exceptions, ErrorHandler, INamedContent, InkRuntime | 8 .kt files |
| 11 | Tests | TestWrapper, TestResources, 17 test classes | 19 test .kt files |

#### Full Inventory: 91 Main + 19 Test = 110 Types

**Currently in ink.kt.puml: 45 classes. Missing: ~46 types to add.**

#### PUML = Synced Source of Truth — Method-Level + Line-Level References

PUML notes contain **exact line numbers and method signatures** from all language source files. This makes PUML the single source of truth for cross-language sync.

```plantuml
class InkObject {
    ' === Fields ===
    + parent : Container?
    + id : String              ' mica Content
    + text : String            ' mica Content
    + lineNumber : Int         ' mica Content
    ~ count : Int              ' mica Content
    - _debugMetadata : DebugMetadata?
    - _path : Path?
    ' === Properties ===
    + {readonly} path : Path
    + {readonly} rootContentContainer : Container?
    + {readonly} debugMetadata : DebugMetadata?
    ' === Methods ===
    + resolvePath(path: Path) : SearchResult
    + convertPathToRelative(globalPath: Path) : Path
    + compactPathString(otherPath: Path) : String
    + debugLineNumberOfPath(path: Path) : Int?
    + copy() : InkObject
    + getText(story: VariableMap) : String
}
note right of InkObject
  **ink.kt** : InkObject.kt:1-139
    parent:Container?          L:22
    resolvePath()              L:45
    copy()                     L:108
    id/text/lineNumber/count   L:28-35 (from mica)
    getText()                  L:130 (from mica)
  ----
  **C#** : Object.cs:1-98 (Ink.Runtime)
    parent                     L:14
    ResolvePath()              L:34
    Copy()                     L:85
  ----
  **Java** : RTObject.java:1-112
    getParent()/setParent()    L:18-22
    resolvePath()              L:42
    copy()                     L:95
  ----
  **JS** : Object.ts:1-87 (inkjs)
    parent                     L:10
    resolvePath()              L:28
    copy()                     L:72
  ----
  **mica** : see ink.kt.mica.puml Content
  **Proto** : -- (runtime-only)
  ----
  **Status**: COMPLETE + MICA_MERGED
end note
```

**Note format for each class:**
- File path + line range per language
- Each method/field with its line number
- Gap methods marked with `MISSING` or `TODO`
- Mica merge status with line references

#### 7 ink.[lang].puml Files (all under `docs/architecture/`)

| File | Lang | Theme | Types | Action |
|------|------|-------|-------|--------|
| `ink.kt.puml` | Kotlin | Blue | ALL 91+ | **UPDATE**: add 46 missing types, full method/field detail |
| `ink.cs.puml` | C# | Purple | ~36 | **UPDATE**: per-class notes to/from ink.kt |
| `ink.java.puml` | Java | Gold | ~46 | **UPDATE**: per-class notes to/from ink.kt |
| `ink.js.puml` | JS/TS | Teal | ~25 | **UPDATE**: per-class notes to/from ink.kt |
| `ink.ts.puml` | TS | Teal variant | NEW | **CREATE**: InkRuntimeAdapter.ts types |
| `ink.proto.puml` | Proto | Indigo | 44 msgs | **UPDATE**: per-message notes to/from ink.kt |
| `ink.kt.mica.puml` | KT mica | Orange | 28 | **UPDATE**: per-class merge status to/from ink.kt |

#### Sync Rule: Every Code Action Reflected in PUML

| Code Action | PUML Sync |
|-------------|-----------|
| Add `toJsonElement()` to VariablesState | Update VariablesState in ink.kt.puml, mark GAP resolved |
| Add `encodeFlow()` to JsonSerialisation | Update in ink.kt.puml + ink.cs.puml + ink.java.puml |
| Add `toJson()`/`loadJson()` to StoryState | Update StoryState, mark COMPLETE |
| Add InkList Story constructor | Update InkList in all lang puml files |
| Fix TestResources pure Kotlin | Update TestResources in ink.kt.puml test section |

#### Progress Indicators Per Class

- `Status: COMPLETE` — all methods/fields present, feature parity achieved
- `Status: GAP [method1, method2]` — specific missing methods listed
- `Status: IN_PROGRESS` — currently being ported
- `Status: MICA_MERGED` — mica parser fields integrated
- `Status: NEW` — ink.kt-only (not in C#/Java/JS)

### Phase 7: Commit + Push
- Copy plan to `docs/plan/purrfect-chasing-dewdrop.md`
- `./gradlew compileKotlin` — verify compilation
- `./gradlew test` — all tests pass
- Commit + push to `claude/sub-agents-code-review-7KAZW`

---

## 6. Pending Steps (from prior plans)

### Step 6c-f: Complete mica -> ink.kt Merge
- **6c**: Update ink.[lang].puml merge status
- **6d**: Copy plan to docs/plan
- **6e**: Move 22 non-colliding mica classes to ink.kt package
- **6f**: Delete ink/kt/mica/ directory, update references

### Step 3: Chatterbox TTS (deferred)
- `ChatterboxTtsEngine.kt` — ONNX voice cloning from FLAC
- `InkTtsBridge.cs` (Unity), `InkTtsAdapter.ts` (browser)
- MCP tools: `synthesize_voice`, `list_voices`

### Step 4: BabylonJS Unity Exporter (deferred)
- `BabylonJsAssetLoader.ts` — glTF mesh loading from AssetEvents
- `InkBabylonExporter.cs` — Unity scene -> BabylonJS glTF

### Auth + Calendar + vCard Integration (from quiet-tumbling-frog)
- **InkAuthEngine.kt** — Keycloak OIDC, JWT validation, LLM basicauth
- **InkCalendarEngine.kt** — iCal4j, 4 MCP tools (create/list/export/import events)
- **InkVCardEngine.kt** — ez-vcard principals, 4 MCP tools
- **McpTools.kt** — +10 tools (51->61)
- **McpRouter.kt** — Auth plugin + route protection
- Auth opt-in: when `KEYCLOAK_REALM_URL` unset, server runs open
- Dependencies already in build.gradle.kts: ktor-auth, ktor-auth-jwt, ical4j, ez-vcard

---

## 7. Kotlin Best Practices Applied

| Java/C# Pattern | Kotlin Idiom |
|---|---|
| `getValue()/setValue()` | `var property: Type` |
| `switch/case` | `when` expression |
| `interface + 4 abstract classes` | `fun interface` SAM |
| `ArrayList<>` / `HashMap<>` | `mutableListOf()` / `mutableMapOf()` |
| `null` checks with `if` | `?.let { }` / `?:` / `!!` |
| `StringBuilder` loops | `buildString { }` |
| Java `SimpleJson` Writer/Reader | `kotlinx.serialization.json` |
| `System.nanoTime()` | `kotlin.time.TimeSource.Monotonic` |
| `BigDecimal` | `Double` (KMP) |
| `Class<T>` type token | `inline reified T` |
| Java `equals/hashCode` | `data class` |
| C# `delegate` | `fun interface` |
| C# partial classes | Extension functions |
| `java.io.File` / `java.net.URI` | `kotlin.io.path` or pure Kotlin |

---

## 8. Critical Files

| Phase | File | Lines | Description |
|---|---|---|---|
| 0 | `jvmMain/` reference files | 0 | Verify synced, READONLY |
| 1 | `src/test/kotlin/ink/kt/TestResources.kt` | ~20 | Pure Kotlin resource loading |
| 2A | `src/commonMain/kotlin/ink/kt/VariablesState.kt` | ~20 | toJsonElement for state |
| 2B | `src/commonMain/kotlin/ink/kt/JsonSerialisation.kt` | ~80 | Flow/CallStack encode/decode |
| 2C | `src/commonMain/kotlin/ink/kt/StoryState.kt` | ~180 | toJson/loadJson core |
| 2D | `src/commonMain/kotlin/ink/kt/StoryState.kt` | ~12 | visitCountAtPathString |
| 3 | `src/commonMain/kotlin/ink/kt/InkList.kt` | ~30 | Story constructor, aliases |
| 4 | `src/commonMain/kotlin/ink/kt/Story.kt` | ~5 | toJson Appendable |
| 5 | `src/commonMain/kotlin/ink/kt/InkRuntime.kt` | ~20 | Annotation foundation |
| 6 | `docs/architecture/ink.kt.mica.puml` | ~100 | Updated diagram |
| 6 | `docs/architecture/ink.kt.puml` | ~200 | Full 70-class diagram |
| 7 | `docs/plan/purrfect-chasing-dewdrop.md` | -- | Copy plan |
| **Total** | | **~667** | |

---

## 9. Verification

1. **Pure Kotlin check**: `grep -r "import java\." src/` returns empty (commonMain AND tests)
2. **Compilation**: `./gradlew compileKotlin` passes
3. **Tests**: `./gradlew test` — all pass
4. **Feature parity**: Method-level comparison Java/C# vs Kotlin complete
5. **PUML completeness**: All 70 classes in diagrams with notes
6. **Reference files**: `git diff` confirms mica/blade-ink untouched
7. **Serialization**: `kotlinx.serialization.json` used everywhere (no SimpleJson for state)

---

## 10. Mica -> ink.kt Merge Details (from playful-imagining-petal Step 6)

### 6 Colliding Classes (merged into ink.kt)

1. **Content -> InkObject**: Parser fields (`id`, `text`, `lineNumber`, `count`) added to InkObject. `getText()` as extension.
2. **Container**: Parser traversal (`index`, `children` alias, `size`, `add`, `get`, `indexOf`) added.
3. **Choice**: Parser fields (`level`, `conditions`, `repeatable`) added. `evaluateConditions()` as extension.
4. **Divert**: `resolveDivert()` as extension. No field changes needed.
5. **Tag**: No changes — ink.kt Tag already has `text`. Uses InkObject fields from merge #1.
6. **Story**: Parser fields (`wrapper`, `parserContainer`, `parserContent`, `fileNames`, `parserVariables`, `parserFunctions`, `interrupts`, `parserText`, `parserChoices`) added. `next()`, `choose()`, etc. as extensions in `StoryParserExt.kt`.

### 22 Non-Colliding Classes (move from mica/ to ink.kt)

**AST nodes (6)**: Knot, Stitch, Gather, Conditional, ConditionalOption, Declaration
**Parser/serialization (5)**: InkParser, StoryLoader, StorySaver, StoryText, Expression
**Interfaces (3)**: VariableMap, Function, StoryWrapper
**Support types (5)**: Symbol, StoryJson, Operator, StoryInterrupt, ParameterizedContainer
**Exceptions (3)**: InkRunTimeException, InkParseException, InkLoadingException

### Extension Files Created

| File | Purpose | LOC |
|------|---------|-----|
| `InkObjectExt.kt` | `getText()`, `contentId()` | ~15 |
| `ChoiceExt.kt` | `evaluateConditions()`, `isFallBack()` | ~60 |
| `DivertExt.kt` | `resolveDivert()` | ~10 |
| `StoryParserExt.kt` | `next()`, `choose()`, `putVariable()`, 4 function classes | ~250 |

---

## 11. Event Pipeline Architecture (from playful-imagining-petal Steps 1-2)

### Full Pipeline Flow
```
faker-kotlin + k-random -> emoji MD tables (characters, items, stats)
  -> InkMdEngine.parse() -> ink VARs + EmojiAssetManifest
  -> LLM (LangChain4j) -> dialogs + ink storyline
  -> InkEngine.continueStory() -> tags (# anim:, # mesh:, # voice:)
  -> InkAssetEventEngine -> resolves tags via EmojiAssetManifest
  -> AssetEventBus (RSocket + msgpack) -> publishes (AsyncAPI contract)
  -> Consumers: KT/JVM, C#/Unity, JS/BabylonJS, JS/Electron, Inkey editor
```

### AsyncAPI Channels (6)
`ink/story/tags`, `ink/asset/load`, `ink/asset/loaded`, `ink/inventory/change`, `ink/voice/synthesize`, `ink/voice/ready`

### RSocket Transport Per Framework
| Framework | Transport | Why |
|-----------|----------|-----|
| KT/JVM | In-process | Server-side origin |
| C#/Unity + OneJS | In-process via `__inkBridge` | Same process |
| JS/BabylonJS | WebSocket -> `/rsocket` | Browser |
| JS/Electron | WebSocket -> `/rsocket` | Desktop |
| Inkey editor | WebSocket -> `/rsocket` | Browser |

---

## 12. Auth + Calendar + vCard (from quiet-tumbling-frog)

### New Engines
- **InkAuthEngine.kt**: Keycloak OIDC (JWT RS256), LLM BasicAuth, `InkPrincipal` data class, opt-in via env var
- **InkCalendarEngine.kt**: iCal4j in-memory store, `InkEvent` data class, 4 MCP tools
- **InkVCardEngine.kt**: ez-vcard principals, MCP URI format `mcp://model:token@host:port/tool`, 4 MCP tools

### +10 MCP Tools (51->61)
Calendar (4): `create_event`, `list_events`, `export_ics`, `import_ics`
vCard (4): `create_principal`, `list_principals`, `get_principal`, `delete_principal`
Auth (2): `create_llm_credential`, `validate_token`

### Execution Order
1. build.gradle.kts deps (already added)
2. InkAuthEngine.kt (foundation)
3. InkCalendarEngine.kt (independent)
4. InkVCardEngine.kt (uses auth)
5. McpTools.kt (+10 tools)
6. McpRouter.kt (wire, auth, protect)
7. ColabEngine.kt (edit role gate)
8. Main.kt (startup)
9. PUML diagrams

---

## 13. Emoji -> Asset Mapping (from playful-imagining-petal Step 1)

### Default 10 Game Categories

| Emoji | Name | Type | AnimSet | Grip | Mesh | Audio |
|-------|------|------|---------|------|------|-------|
| sword | weapon | sword_1h | main_hand | weapon_sword | sfx_metal |
| shield | armor | shield_buckler | off_hand | armor_shield | sfx_metal |
| staff | weapon | staff_2h | two_hand | weapon_staff | sfx_wood |
| bow | weapon | bow_2h | two_hand | weapon_bow | sfx_string |
| wizard | character | cast | none | char_wizard | voice |
| potion | consumable | drink | main_hand | item_potion | sfx_glass |
| key | quest | use_item | main_hand | item_key | sfx_metal |
| map | quest | read | two_hand | item_map | sfx_paper |
| coin | currency | none | none | item_coin | sfx_coin |
| crown | armor | equip_head | none | armor_crown | sfx_metal |

### UnicodeSymbolParser
Parses `emoji-test.txt` and `UnicodeData.txt`. Supports IPA Extensions, Mathematical Operators, and other UTF symbol blocks. Bundle + Fetch + Cache strategy.

---

## 14. Dependencies (all in build.gradle.kts)

| Package | Version | Purpose |
|---------|---------|---------|
| kotlinx-serialization-json | 1.7.3 | JSON (KMP) |
| kotlinx-datetime | 0.6.2 | Clock (KMP) |
| protobuf-kotlin | 4.28.3 | Proto codegen |
| rsocket-ktor-server/client | 0.16.0 | Event transport |
| jackson-dataformat-msgpack | 0.9.8 | msgpack |
| kotlin-faker | 2.0.0-rc.7 | Data generation |
| poi-ooxml | 5.2.5 | Formula eval |
| ktor-server-auth-jwt | 3.1.1 | Auth |
| ical4j | 4.0.7 | Calendar |
| ez-vcard | 0.12.1 | vCard |
