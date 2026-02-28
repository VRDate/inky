# Roadmap: ink.kt KMP Runtime + Multi-Format Annotations + Test Porting

> **Consolidated roadmap** — merges:
> - Event-Driven Emoji Asset Pipeline + Three-Layer Architecture
> - iCal4j + Auth + vCard Integration
> - Feature Parity Merge analysis (Java/C# → ink.kt)
> - KMP Multiplatform Build Migration + JS/JVM Annotations
> - Test Porting Strategy (C#/Java/JS → commonTest/jvmTest/jsTest)
>
> **Constraint**: NO JVM imports in commonMain — pure Kotlin everywhere

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

### Per-Framework Runtime — 3 KMP Ink Engines

| Framework | Engine (default) | Engine (legacy) | Entry Point |
|-----------|-----------------|-----------------|-------------|
| **KT/JVM** | **ink.kt** (pure Kotlin) | blade-ink Java | `InkKtEngine` / `InkJavaEngine` |
| **KT/JS** | **ink.kt/JS** (compiled) | inkjs (native) | `InkKtEngine` / `InkJsEngine` |
| **C#/Unity** | ink-csharp Runtime | — | `InkOneJsBinding.cs` |
| **JS/Electron** | inkjs | — | `inklecate.js` |
| **JS/BabylonJS** | inkjs | — | `InkRuntimeAdapter.ts` |

> **`legacy` flag**: When `legacy=true`, use the official engine (blade-ink Java on JVM, inkjs on JS) instead of ink.kt. Used for parity tests to verify ink.kt produces identical output.

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
- [x] **Phase 2** — State serialization: toJson/loadJson/writeJson (~555 LOC added)
- [x] **Phase 2A** — VariablesState.toJsonElement() + fromJsonElement()
- [x] **Phase 2B** — JsonSerialisation.encodeFlow/encodeThread/decodeFlow
- [x] **Phase 2C** — StoryState.toJson/loadJson/writeJson/loadJsonObj (~340 LOC)
- [x] **Phase 2D** — StoryState.visitCountAtPathString
- [x] **Phase 3** — InkList completeness: Story constructor, singleOriginListName, addItem, contains (~30 LOC)
- [x] **Phase 4** — Story.toJson(Appendable) stream overload
- [x] **InkFlow reactive** — InkFlow implements Flow\<InkObject\> via MutableSharedFlow + EmittingList bridge
- [x] **Ink prefix renames** — Flow→InkFlow, Thread→InkThread, Void→InkVoid (avoid Java clashes)
- [x] **Auth + Calendar + vCard** — InkAuthEngine (Keycloak OIDC), InkCalendarEngine (iCal4j), InkVCardEngine (ez-vcard), InkWebDavEngine (Sardine). **79 MCP tools total.**
- [x] **Camel routes** — 10 direct: routes (llm-chat, llm-langchain4j, llm-generate-ink, llm-review-ink, llm-translate-he, ink-compile, ink-play, ink-choose, llm-compile-chain, ink-asset-event, voice-synthesize)
- [x] **ink.kt.puml: threads/flows/reactive** — N_THREADS_FLOWS note: four-way comparison (C#/Java/JS/KT), Kotlin Flow vs Reactor table, back pressure design, threading per platform
- [x] **ink.kt.puml: Camel/RSocket/MCP** — N_CAMEL_RSOCKET_MCP note: 3 protocol layers, 10 routes, data flow, 6 AsyncAPI channels, EIP patterns, Kotlin Flow ↔ Camel bridge
- [x] **ink-proof** — 7 bytecode (B001-B007) + 135 ink source (I001-I135) test resources

---

## 4. Feature Parity Gap Analysis — ALL RESOLVED

### All 46 Classes at Feature Parity

InkObject, Container, Value (sealed, 7 types), NativeFunctionCall (30 operators via SAM), VariablesState, Path, Pointer, ChoicePoint, Divert, Tag, Glue, InkVoid, ControlCommand, VariableAssignment, VariableReference, PushPopType, ValueType, ErrorType, StoryException, SearchResult, DebugMetadata, StatePatch, ListDefinition, ListDefinitionsOrigin, InkListItem, INamedContent, SimpleJson, JsonSerialisation, Profiler+ProfileNode, Stopwatch, InkFlow, Choice, CallStack (+Element, +InkThread), StoryState, Story, InkList, InkClock, DateComponents

### ~~Actual Gaps~~ — ALL DONE

| File | Gap | Status |
|------|-----|--------|
| ~~**StoryState**~~ | ~~toJson/loadJson/writeJson/loadJsonObj/visitCountAtPathString~~ | ✅ Done (~340 LOC) |
| ~~**JsonSerialisation**~~ | ~~encodeFlow/encodeThread/decodeFlow~~ | ✅ Done (~80 LOC) |
| ~~**VariablesState**~~ | ~~toJsonElement/fromJsonElement~~ | ✅ Done (~20 LOC) |
| ~~**InkList**~~ | ~~Story constructor, singleOriginListName, addItem, contains~~ | ✅ Done (~30 LOC) |
| ~~**Story**~~ | ~~toJson(Appendable)~~ | ✅ Done (~5 LOC) |
| ~~**InkFlow**~~ | ~~writeJson(writer)~~ | ✅ Done (~40 LOC) |
| ~~**CallStack**~~ | ~~writeJson(writer), InkThread.writeJson(writer)~~ | ✅ Done (~40 LOC) |

---

## 5. Implementation Phases

### Phases 0–6: COMPLETED (see Section 3)

Phases 0–6 covered feature parity, state serialization, InkList completeness, stream overloads, annotation foundation, and PUML documentation. All completed — see section 3 for full details.

### Phase 7: KMP Multiplatform Build Migration

Migrate from JVM-only to Kotlin Multiplatform with JS target.

- [ ] Migrate `build.gradle.kts` from `kotlin("jvm")` to `kotlin("multiplatform")`
- [ ] Add JS(IR) target: `js(IR) { nodejs(); binaries.library(); generateTypeScriptDefinitions() }`
- [ ] Fix one JVM blocker: `Expression.kt` lines 486-508 (`obj::class.java` reflection) — replace with `expect/actual` or default to null
- [ ] Create source sets: `commonMain`, `commonTest`, `jvmMain`, `jvmTest`, `jsMain`, `jsTest`
- [ ] `expect/actual` for `TestResources` — JVM: classloader, JS: `fs.readFileSync`
- [ ] Verify `./gradlew compileKotlinJs` passes (all 60 commonMain files)
- [ ] **Files**: `build.gradle.kts`, `Expression.kt`, `TestResources.kt`

### Phase 8: JS Annotations (@JsExport, @JsName)

Match inkjs PascalCase API surface so ink.kt/JS is a drop-in replacement.

- [ ] `@JsExport` on public API classes: Story, Choice, InkList, StoryState, VariablesState, InkFlow, CallStack, Value, Container, InkObject, Path, Pointer, ChoicePoint, Divert, Tag, NativeFunctionCall
- [ ] `@JsName` for inkjs PascalCase methods:
  - `Story`: Continue, ChooseChoiceIndex, ChoosePathString, ToJson, LoadJson, BindExternalFunction, ContinueMaximally, ResetState, ResetErrors, ResetCallstack
  - `Choice`: Text, Index, Tags, SourcePath
  - `InkList`: ToJson, Add, Contains, Count
  - `StoryState`: ToJson, LoadJson, VisitCountAtPathString
  - `VariablesState`: ToJson, FromJson
- [ ] `@JsName("Flow")` on InkFlow class (match inkjs naming)
- [ ] JS facade in `jsMain/` for `@JsExport` limitations (sealed classes, fun interface)
- [ ] Module name: `@inky/ink-kt` → TypeScript `.d.ts` generation
- [ ] **Files**: all 16 public API `.kt` files + `jsMain/kotlin/ink/kt/JsFacade.kt`

### Phase 9: JVM Annotations (@JvmName, @JvmOverloads, @JvmField, @JvmStatic)

Enable blade-ink Java tests to call ink.kt directly without Kotlin-specific syntax.

- [ ] `@JvmOverloads` on methods with defaults:
  - `Story.choosePathString(path, resetCallstack, args)`
  - `Story.bindExternalFunction(name, lookaheadSafe, fn)`
  - `InkList.addItem(itemName, storyObject)`
- [ ] `@JvmStatic` on companion object factories:
  - `Value.create(obj)`, `Pointer.startOf(container)`, `Path.parse(str)`
- [ ] `@JvmField` on frequently accessed properties:
  - `Choice.text`, `Choice.index`, `Choice.tags`
  - `Story.currentChoices`, `Story.currentText`
- [ ] `@JvmName` where Kotlin mangles names (property getters, inline functions)
- [ ] **Files**: `Story.kt`, `Choice.kt`, `InkList.kt`, `Value.kt`, `Pointer.kt`, `Path.kt`

### Phase 10: Schema Annotations (@Proto, @OpenApi, @AsyncApi, @GraphQL)

Custom annotation definitions + alignment with existing proto files and API specs.

- [ ] Create `ink/model/Annotations.kt` with annotation definitions:
  - `@ProtoMessage(number)` — align with existing 14 `.proto` files
  - `@OpenApiSchema(path, method)` — REST API spec generation
  - `@AsyncApiChannel(name, protocol)` — 6 event channels
  - `@GraphQLType(name)` / `@GraphQLQuery` / `@GraphQLMutation`
- [ ] `@ProtoNumber` alignment with existing proto messages in `src/main/proto/ink/model/`
- [ ] `@OpenApi` on MCP tool request/response types for OpenAPI 3.1 spec generation
- [ ] `@AsyncApi` on 6 event channels: `ink/story/tags`, `ink/asset/load`, `ink/asset/loaded`, `ink/inventory/change`, `ink/voice/synthesize`, `ink/voice/ready`
- [ ] `@GraphQL` schema annotations on Story, Choice, StoryState, InkList, Variable types
- [ ] Future: KSP processors for code generation from annotations
- [ ] **Files**: `ink/model/Annotations.kt`, `ink/model/*.kt` data classes

### Phase 11: Test Porting

Three-tier test strategy: commonTest (pure Kotlin, runs on JVM + JS), jvmTest (JVM-only), jsTest (JS verification).

- [ ] **ink-proof (I001-I135)**: Wire up 135 ink-source tests already in `src/test/resources/ink-proof/` — currently only 7 bytecode (B001-B007) are wired in `InkProofTest.kt`
- [ ] **C# Tests.cs → kotlin.test**: Port ~130 integration tests (4259 lines) from `ink-csharp/tests/Tests.cs` to `commonTest/kotlin/ink/kt/` — covers: arithmetic, conditionals, tunnels, external binding, lists, multi-flow, tags, variables, save/load
- [ ] **Move 14 pure-Kotlin tests to commonTest**: existing ink.kt tests (ChoiceTest, StoryTest, etc.) that have zero JVM imports — run on both JVM + JS targets
- [ ] **jsTest: ink.kt/JS vs inkjs verification**: Same JSON story → drive both ink.kt/JS and inkjs → compare output transcript (proves compilation correctness)
- [ ] **Keep MCP tests in jvmTest**: GraalJS, Ktor, WireMock — JVM-only dependencies
- [ ] **Files**: `commonTest/kotlin/ink/kt/`, `jvmTest/kotlin/ink/kt/`, `jsTest/kotlin/ink/kt/`

### Phase 12: Ink Engine Selection Framework

Three KMP ink engines coexist — `legacy` boolean selects between ink.kt and the official (Java/JS) engine.

- [ ] Define `InkEngineProvider` interface:
  ```kotlin
  interface InkEngineProvider {
      fun createEngine(json: String, legacy: Boolean = false): InkEngine
  }
  ```
- [ ] Three implementations:
  - `InkKtEngine` — pure ink.kt runtime (commonMain, works on JVM + JS) — **default**
  - `InkJavaEngine` — blade-ink Java runtime (jvmMain only) — `legacy=true` on JVM
  - `InkJsEngine` — inkjs via GraalJS (jvmMain) or native JS (jsMain) — `legacy=true` on JS
- [ ] `legacy` flag behavior:
  - **`legacy=false` (default)**: use ink.kt on all platforms
  - **`legacy=true`**: use official engine (blade-ink Java on JVM, inkjs on JS)
- [ ] `expect/actual` for `defaultInkEngine(legacy: Boolean)` per platform
- [ ] Parity tests use `legacy` flag: run same story JSON through both engines, assert identical output
  ```kotlin
  @Test fun parityTest() {
      val kt = provider.createEngine(json, legacy = false)
      val official = provider.createEngine(json, legacy = true)
      assertEquals(official.continueMaximally(), kt.continueMaximally())
  }
  ```
- [ ] **Electron settings UI**: ink-electron app exposes engine selector in settings (ink.kt/JS vs inkjs)
- [ ] **Playwright/Spectron E2E tests**: drive ink-electron app, toggle engine setting, run same story, verify identical output
  ```typescript
  test('engine parity via electron settings', async ({ page }) => {
      await page.goto('settings');
      await page.selectOption('#ink-engine', 'ink-kt');   // ink.kt/JS
      const ktOutput = await runStory(page, storyJson);
      await page.selectOption('#ink-engine', 'inkjs');     // legacy
      const jsOutput = await runStory(page, storyJson);
      expect(ktOutput).toEqual(jsOutput);
  });
  ```
- [ ] **Files**: `commonMain/kotlin/ink/kt/engine/InkEngineProvider.kt`, `jvmMain/kotlin/ink/kt/engine/`, `jsMain/kotlin/ink/kt/engine/`, `ink-electron/test/engine-parity.spec.ts`

---

## 6. Deferred Steps

### Step 6c-f: Complete mica -> ink.kt Merge
- **6e**: Move 22 non-colliding mica classes to ink.kt package
- **6f**: Delete ink/kt/mica/ directory, update references

### Chatterbox TTS (deferred)
- `ChatterboxTtsEngine.kt` — ONNX voice cloning from FLAC
- `InkTtsBridge.cs` (Unity), `InkTtsAdapter.ts` (browser)
- MCP tools: `synthesize_voice`, `list_voices`

### BabylonJS Unity Exporter (deferred)
- `BabylonJsAssetLoader.ts` — glTF mesh loading from AssetEvents
- `InkBabylonExporter.cs` — Unity scene -> BabylonJS glTF

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

| Phase | File | Description |
|---|---|---|
| 7 | `build.gradle.kts` | KMP multiplatform migration |
| 7 | `Expression.kt` lines 486-508 | JVM blocker: `obj::class.java` reflection |
| 7 | `TestResources.kt` | `expect/actual` for JVM/JS resource loading |
| 8 | 16 public API `.kt` files | `@JsExport` + `@JsName` annotations |
| 8 | `jsMain/kotlin/ink/kt/JsFacade.kt` | JS facade for sealed class / fun interface |
| 9 | `Story.kt`, `Choice.kt`, `Value.kt`, etc. | `@JvmOverloads` / `@JvmStatic` / `@JvmField` |
| 10 | `ink/model/Annotations.kt` | Custom annotation definitions |
| 10 | `ink/model/*.kt` | Schema annotations on data classes |
| 11 | `InkProofTest.kt` | Wire I001-I135 ink-source tests |
| 11 | `commonTest/kotlin/ink/kt/` | Ported C# tests + moved pure-Kotlin tests |
| 11 | `jsTest/kotlin/ink/kt/` | ink.kt/JS vs inkjs cross-verification |
| 12 | `ink/kt/engine/InkEngineProvider.kt` | Engine selection interface |
| 12 | `jvmMain/kotlin/ink/kt/engine/` | Java + KT engine providers |
| 12 | `jsMain/kotlin/ink/kt/engine/` | JS + KT/JS engine providers |

---

## 9. Verification

1. **Pure Kotlin check**: `grep -r "import java\." src/commonMain/` returns empty
2. **JVM compilation**: `./gradlew compileKotlinJvm` passes
3. **JS compilation**: `./gradlew compileKotlinJs` passes
4. **JVM tests**: `./gradlew jvmTest` — all pass
5. **JS tests**: `./gradlew jsTest` — all pass
6. **Engine parity**: all 3 engines (ink.kt, blade-ink Java, inkjs) produce identical output for same story JSON — `legacy=true` flag selects official engine for comparison
7. **Feature parity**: Method-level comparison Java/C# vs Kotlin complete (ALL 46 classes)
8. **PUML completeness**: All 91+ classes in diagrams with notes
9. **Reference files**: `git diff` confirms mica/blade-ink untouched
10. **TypeScript defs**: `.d.ts` generated, matches inkjs public API surface

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

## 12. Auth + Calendar + vCard (COMPLETED)

### Engines (all implemented)
- **InkAuthEngine.kt**: Keycloak OIDC (JWT RS256), LLM BasicAuth, `InkPrincipal` data class, opt-in via env var
- **InkCalendarEngine.kt**: iCal4j in-memory store, `InkEvent` data class, 4 MCP tools
- **InkVCardEngine.kt**: ez-vcard principals, MCP URI format `mcp://model:token@host:port/tool`, 4 MCP tools
- **InkWebDavEngine.kt**: Sardine WebDAV client

### +18 MCP Tools (61->79)
Calendar (4): `create_event`, `list_events`, `export_ics`, `import_ics`
vCard (4): `create_principal`, `list_principals`, `get_principal`, `delete_principal`
Auth (2): `create_llm_credential`, `validate_token`
WebDAV + additional: 8 more tools — **79 total MCP tools**

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
