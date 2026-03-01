# TODO — Inky Multiplatform Monorepo

> Effort estimates: **S** = small (< 1 day), **M** = medium (1–3 days), **L** = large (3–7 days), **XL** = extra-large (1–2 weeks)

---

## Current Test Coverage Snapshot

| Ecosystem | Files | Tests | Source Modules Covered | Shared Fixtures | Coverage |
|-----------|-------|-------|------------------------|-----------------|----------|
| **Kotlin** (ink-kmp-mcp) | 7 + KtTestFixtures | 145 | InkEngine, McpTools, McpRouter, InkEditEngine, ColabEngine, InkWebDavEngine, BIDI_TDD_ISSUES.md | `KtTestFixtures.kt` companion object | 7/20 (35%) |
| **C#** (InkBidiTdd.Tests) | 4 + InkTestFixtures + InkStorySession | 42 | Ink.Compiler, Ink.Runtime, 10-method OneJS bridge | `InkTestFixtures.cs` + `InkStorySession.cs` fluent API | production |
| **TypeScript** (ink-editor) | 1 | 48 | ink-grammar.ts, BIDI_TDD_ISSUES.md | inline | 1/16 (6%) |
| **JavaScript** (ink-electron) | 4 + e2e-helpers | 64 | bidify.js + E2E via Playwright | `e2e-helpers.js` module | 1/29 (3%) |
| **PlantUML** | — | — | 24 diagrams across 3 dirs (+ink.ts.puml, ink.py.puml, ink.magiconion.puml) | — | — |
| **Markdown** | — | — | 15 docs (~280K lines) | — | — |
| **Total** | **16+** | **299** | | | |

### Test Infrastructure: Companion object pattern across all ecosystems
- **KT**: `KtTestFixtures.kt` — shared projectRoot, engine, bidiTddSource, mdSource
- **C#**: `InkTestFixtures.cs` — shared ProjectRoot, BidiTddSource + `InkStorySession.cs` fluent API with SemaphoreSlim(1,1) thread-safe compile
- **JS**: `e2e-helpers.js` — shared Electron/Playwright helpers (launchApp, editor, story interaction)
- **TS**: inline fixtures (single test file, no cross-file duplication)

### Coverage Tools: None configured (no Jacoco/Kover, no c8/istanbul, no Coverlet)

---

## 1. Test Coverage Gaps — MCP Server (Kotlin)

> 13 of 20 source modules have **zero** test coverage (was 18).
> 7 modules now tested: InkEngine(29), McpTools(15), McpRouter(8), InkEditEngine(19), ColabEngine(14), InkWebDavEngine(25), InkMdTable(35).
> Shared infra: `KtTestFixtures.kt` companion object eliminates projectRoot/engine/fixture duplication across 4 test files.

### P0 — Critical (must-have for CI) — DONE

| # | Task | Status | Tests |
|---|------|--------|-------|
| 1.1 | ~~McpRouterTest.kt~~ | **DONE** | 8 |
| 1.2 | ~~McpToolsTest.kt~~ | **DONE** | 15 |
| 1.3 | ~~BidiTddInkTest.kt~~ (InkEngine via GraalJS) | **DONE** | 29 |
| 1.4 | ~~ColabEngineTest.kt~~ | **DONE** | 14 |

### P1 — High (full tool coverage)

| # | Task | Effort | Modules | Tests |
|---|------|--------|---------|-------|
| 1.5 | **InkDebugEngineTest.kt** — start_debug, breakpoints (knot/pattern/variable), step, continue, inspect, trace | **L** | InkDebugEngine.kt | ~10 |
| 1.6 | ~~InkEditEngineTest.kt~~ | **DONE** | 19 |
| 1.7 | **InkMdEngineTest.kt** — parse_ink_md, render_ink_md, compile_ink_md | **S** | InkMdEngine.kt | ~3 |
| 1.8 | **Ink2PumlEngineTest.kt** — ink2puml, ink2svg, puml2svg, ink_toc, ink_toc_puml | **M** | Ink2PumlEngine.kt | ~5 |
| 1.9 | **InkCalendarEngineTest.kt** — create/list/export/import events, date range, round-trip | **M** | InkCalendarEngine.kt | ~6 |
| 1.10 | **InkVCardEngineTest.kt** — create/list/get/delete principal, human + LLM types | **M** | InkVCardEngine.kt | ~5 |
| 1.11 | **InkAuthEngineTest.kt** — auth_status (enabled/disabled), create_llm_credential, backward compat | **M** | InkAuthEngine.kt | ~4 |

### P2 — Medium (edge cases + mocked)

| # | Task | Effort | Modules | Tests |
|---|------|--------|---------|-------|
| 1.12 | **LlmEngineTest.kt** — list_models, load_model (mocked), llm_chat, generate_ink | **M** | LlmEngine.kt, LmStudioEngine.kt | ~8 |
| 1.13 | **CamelRoutesTest.kt** — route validation, pipeline config | **S** | CamelRoutes.kt | ~3 |
| 1.14 | **LlmServiceConfigTest.kt** — list_services, connect_service | **S** | LlmServiceConfig.kt, SillyTavernConfig.kt, DictaLmConfig.kt | ~3 |
| 1.15 | **Add Jacoco or Kover** to build.gradle.kts for coverage reporting | **S** | build.gradle.kts | — |

**Remaining: ~47 new KT tests across 8 files | Effort: ~2–4 weeks**

---

## 2. Test Coverage Gaps — JavaScript (Electron app)

> 1 of 29 source modules has unit tests (bidify.js).
> E2E tests cover some modules indirectly via Playwright.
> See: `docs/E2E_TEST_PLAN.md` for 80 planned test cases (10 implemented / 70 remaining).

### P0 — Critical

| # | Task | Effort | Modules | Tests |
|---|------|--------|---------|-------|
| 2.1 | **liveCompiler unit tests** — compile lifecycle, debounce, error collection, recompile | **M** | liveCompiler.js | ~8 |
| 2.2 | **inkFile unit tests** — path resolution, relative/absolute, file watching, INCLUDE parsing | **M** | inkFile.js, inkFileSymbols.js | ~10 |
| 2.3 | **inkProject unit tests** — multi-file project, file add/remove/rename, save race condition (#508) | **L** | inkProject.js | ~12 |

### P1 — High

| # | Task | Effort | Modules | Tests |
|---|------|--------|---------|-------|
| 2.4 | **controller unit tests** — editor↔player coordination, IPC message handling | **L** | controller.js | ~10 |
| 2.5 | **playerView unit tests** — story display, choice rendering, scroll behavior (#506, #514) | **M** | playerView.js | ~8 |
| 2.6 | **navView unit tests** — file tree, error highlighting, include hierarchy | **M** | navView.js, navHistory.js | ~6 |
| 2.7 | **E2E: complete remaining 70 Playwright tests** from E2E_TEST_PLAN.md | **XL** | All Electron modules | ~70 |
| 2.8 | **Add c8/istanbul** coverage to package.json test scripts | **S** | package.json | — |

### P2 — Medium

| # | Task | Effort | Modules | Tests |
|---|------|--------|---------|-------|
| 2.9 | **ace-ink-mode unit tests** — syntax highlighting, token types, conditional `\|\|` (#442) | **M** | ace-ink-mode/ace-ink.js | ~8 |
| 2.10 | **editorView unit tests** — Ace editor integration, find/replace, goto | **M** | editorView.js, goto.js | ~6 |
| 2.11 | **i18n unit tests** — locale loading, string resolution, fallback behavior | **S** | i18n.js | ~4 |
| 2.12 | **inklecate unit tests** — process spawning, IPC, timeout, zombie cleanup | **M** | inklecate.js | ~6 |

**Subtotal: ~148 new JS tests across 11 areas | Effort: ~6–8 weeks**

---

## 3. Test Coverage Gaps — TypeScript (ink-editor packages)

> 1 of 16 TS source modules tested (ink-grammar.ts + BIDI_TDD_ISSUES.md).
> 48 tests in ink-md-table.test.ts covering ink-grammar regex, md table schema, cross-references.
> 5 packages, 4 have zero tests.

| # | Task | Effort | Modules | Tests |
|---|------|--------|---------|-------|
| 3.1 | **codemirror-ink tests** — ink-highlight, ink-fold, ink-complete, ink-language, ink-yjs | **L** | 6 modules | ~15 |
| 3.2 | **remirror-ink tests** — InkExtension, ink-node-view, ink-schema, ink-yjs | **L** | 5 modules | ~12 |
| 3.3 | **react-ink-editor tests** — InkRuntimeAdapter (3 adapters: Browser, MCP, Unity) | **M** | 2 modules | ~8 |
| 3.4 | **ink-keywords tests** — keyword list completeness, category validation | **S** | 1 module | ~4 |
| 3.5 | **Add tsx + c8** coverage to all ink-editor packages | **S** | package.json files | — |

**Subtotal: ~39 new TS tests across 4 packages | Effort: ~3–4 weeks**

---

## 4. Test Coverage Gaps — C# (InkBidiTdd.Tests)

> 42 tests across 2 test suites + fluent API + companion fixtures.
> ink-csharp cloned and integrated. Thread-safe compilation via SemaphoreSlim(1,1).
> Shared infra: `InkTestFixtures.cs` (companion object) + `InkStorySession.cs` (fluent API).

### DONE

| # | Task | Status | Tests |
|---|------|--------|-------|
| 4.1 | ~~Clone inkle/ink C# runtime~~ | **DONE** | — |
| 4.4 | ~~InkOneJsBindingTest.cs~~ — 10-method bridge contract | **DONE** | 16 |
| 4.5 | ~~BidiTddInkTest.cs~~ — compile, play, choices, variables, save/load, 28 syntax features | **DONE** | 26 |

### Remaining

| # | Task | Effort | Modules | Tests |
|---|------|--------|---------|-------|
| 4.2 | **Clone ink-unity-integration** into ink-unity/ | **S** | — | — |
| 4.3 | **InkOneJsBinding.cs** — implement Unity bridge (production MonoBehaviour) | **L** | InkOneJsBinding.cs | — |
| 4.6 | **Add Coverlet** to .csproj for coverage reporting | **S** | .csproj | — |

**Remaining: Unity bridge + coverage tooling | Effort: ~1–2 weeks**

---

## 5. Documentation & Diagram Gaps

> 15 markdown docs (280K lines), 24 PlantUML diagrams.
> Architecture and bidi docs are excellent; feature/API docs are missing.
> Plan files consolidated into roadmap.md (originals in bak/). Interop plan documented as Section 5b.

### DONE

| # | Task | Status | Notes |
|---|------|--------|-------|
| 5.a | ~~Merge plan files into roadmap.md~~ | **DONE** | Event-Driven + iCal4j plans → roadmap Section 2 (originals → bak/) |
| 5.b | ~~Interop Plan in roadmap.md~~ | **DONE** | Wire/Moshi/Camel/gRPC/RSocket/MessagePack/JSON/Unity/MagicOnion → Section 5b |
| 5.c | ~~ink.ts.puml~~ — 16 packages, 57 interfaces, 12 enums (protoc-gen-ts_proto) | **DONE** | Teal/cyan theme, MessageFns pattern, cross-language mapping notes |
| 5.d | ~~ink.py.puml~~ — 16 packages, 65 messages, 20 enums (protoc v5.28.3) | **DONE** | Amber/orange theme, descriptor pool pattern, .pyi stub notes |
| 5.e | ~~Update all 6 existing puml diagrams~~ — dates, cross-refs, progress, interop | **DONE** | ink.proto/kt/java/cs/js/kt.mica.puml → 2026-03-01, 8-diagram cross-refs |
| 5.f | ~~Proto count 14→16~~ in roadmap + puml files | **DONE** | ical.proto + vcard.proto added |

### Remaining

| # | Task | Effort | Type | Notes |
|---|------|--------|------|-------|
| 5.1 | **MCP API Reference** — document all 71 tools with input/output schemas | **L** | MD | No API docs exist |
| 5.2 | **Developer Onboarding Guide** — codebase tour, module breakdown, contribution guide | **M** | MD | No onboarding docs |
| 5.3 | **Keyboard Shortcut Reference** — document all shortcuts + planned shortcuts | **S** | MD | Referenced in original TODO |
| 5.4 | **RTL/Bidi User Guide** — "How to write RTL stories in Inky" | **M** | MD | 96K arch doc exists, no user guide |
| 5.5 | **Export to Web Guide** — customization, styles, limitations | **S** | MD | Export exists, undocumented |
| 5.6 | **PUML: Compiler pipeline diagram** — inklecate integration flow | **S** | PUML | Missing |
| 5.7 | **PUML: Save/autosave state machine** — race condition prevention (#508) | **S** | PUML | Missing |
| 5.8 | **PUML: Error handling flow** — error browser, file I/O errors | **S** | PUML | Missing |
| 5.9 | **PUML: Player/runtime interaction** — story lifecycle sequence | **S** | PUML | Missing |

**Remaining: 9 docs/diagrams | Effort: ~3–4 weeks**

---

## 6. BIDI_TDD_ISSUES.md Test Matrix Gaps

> 42% feature coverage (8/19 features fully tested across all ecosystems).
> RTL/Bidi: 100% coverage. Ink syntax features: partial.

| # | Feature | bidi_and_tdd.ink | E2E JS | KT | bidify.js | Gap | Effort |
|---|---------|-----------------|--------|----|-----------|----|--------|
| 6.1 | Glue `<>` | yes | **NO** | yes | — | E2E glue assertion | **S** |
| 6.2 | Tags `#` | yes | **NO** | yes | — | E2E tag value test | **S** |
| 6.3 | Tunnels `->->` | yes | **NO** | yes | — | E2E tunnel flow test | **S** |
| 6.4 | Threads `<-` | yes | **NO** | yes | — | E2E merged choice test | **S** |
| 6.5 | Functions | yes | **NO** | yes | — | E2E return value test | **S** |
| 6.6 | Variables | yes | partial | yes | — | E2E value assertion | **S** |
| 6.7 | Lists | yes | **NO** | **NO** | — | KT + E2E list ops | **M** |
| 6.8 | Conditionals | yes | **NO** | **NO** | — | KT + E2E branch test | **M** |
| 6.9 | Alternatives | yes | **NO** | **NO** | — | KT + E2E variation test | **M** |
| 6.10 | State save/load | — | **NO** | yes | — | E2E reload assertion | **S** |
| 6.11 | INCLUDE | display | **NO** | — | — | Multi-file E2E + KT | **M** |
| 6.12 | EXTERNAL | — | **NO** | — | — | **Critical: no tests at all** | **M** |

**Subtotal: 12 feature gaps | Effort: ~2–3 weeks**

---

## 7. Build & Infrastructure

| # | Task | Effort | Notes |
|---|------|--------|-------|
| 7.1 | **Unified Gradle 9 root build** — orchestrate npm/dotnet/gradle via Exec tasks | **L** | Planned but not committed |
| 7.2 | ~~GraalVM 25 + SDKMAN~~ | **DONE** | System OpenJDK 21 + GraalJS via Gradle |
| 7.3 | **KMP migration** — add `js(IR)` and `native` targets to mcp-server | **XL** | Multiplatform comparison goal |
| 7.4 | **CI/CD pipeline** — GitHub Actions for testAll across KT, JS, TS, C# | **L** | No CI exists |
| 7.5 | ~~Directory rename~~ — app→ink-electron, mcp-server→ink-kmp-mcp, ink-editor→ink-js/inkey | **DONE** | Completed |
| 7.6 | ~~ink-csharp cloned~~ + ink-unity/onejs TBD | **PARTIAL** | ink-csharp vendored, ink-unity not yet |
| 7.7 | **Create `.sdkmanrc`** — pin `java=25.0.2-graal`, `kotlin=2.3.0` at repo root | **S** | Dev/WSL/Termux SDK pinning |
| 7.8 | **Create `Dockerfile.ci`** — extend `unityci/editor` + sdkman (GraalVM 25) + Node 24 + Python 3.11 + .NET 9 + Vulkan | **L** | Replaces `singtaa/unity-ci-node` |
| 7.9 | **Create `docker-build-ci.sh`** — multi-target builder reading versions from TOML | **S** | linux-il2cpp, android, webgl, windows-mono |
| 7.10 | **Unity CI multi-target matrix** — expand workflow for Android, WebGL, Windows cross-compile | **M** | Extends current StandaloneLinux64-only build |
| 7.11 | **Add software Vulkan to CI** — `mesa-vulkan-drivers` for UI automation tests without GPU | **S** | Enables UI Toolkit testing in headless CI |
| 7.12 | **CI/CD pipeline — GitHub Actions for testAll** across KT, JS, TS, C# | **L** | See [`KMP_CICD.md`](docs/architecture/KMP_CICD.md) [`MAUI_CICD.md`](docs/architecture/MAUI_CICD.md) [`UNITY_CICD.md`](docs/architecture/UNITY_CICD.md) |
| 7.13 | **TOML version sync check** — CI step that verifies TOML versions match Dockerfile/workflow | **S** | Drift detection |
| 7.14 | **GHCR image publishing workflow** — build + push CI images on version bump | **M** | `ghcr.io/vrdate/inky-ci` |

**Remaining: 11 infra tasks | Effort: ~6–8 weeks**

---

## 8. Original Inky TODO (from inkle)

> Preserved from the original project. Effort estimates added.

### Features

| # | Task | Effort | Notes |
|---|------|--------|-------|
| 8.1 | **Keyboard shortcuts** — Ctrl-(shift)-tab, back/forward, follow symbol | **M** | appmenus.js + editorView.js |
| 8.2 | **Find in project** — Ace Search API across multiple files | **L** | ace.c9.io/#nav=api&api=search |
| 8.3 | **Go to symbol in project** | **M** | inkFileSymbols.js |
| 8.4 | **Include rename/delete** with automatic INCLUDE line update | **M** | inkFile.js + inkProject.js |
| 8.5 | **File watcher rename behavior** — subtitle "Included from xyz.ink" | **M** | inkProject.js |
| 8.6 | **Hierarchy view for includes** — tree instead of flat groups | **L** | navView.js |
| 8.7 | **Drag/drop includes between groups** | **L** | navView.js (tricky) |
| 8.8 | **Highlight files with errors** in nav | **S** | navView.js |
| 8.9 | **Filenames in issue browser** | **S** | controller.js |
| 8.10 | **Switch to related ink file** when opening externally | **M** | main.js + projectWindow.js |
| 8.11 | **File system error checking** for open/save | **M** | inkProject.js |
| 8.12 | **Toolbar: jump to path** at story start | **M** | toolbarView.js + playerView.js |
| 8.13 | **Debug: query/list variables** | **M** | expressionWatchView.js |
| 8.14 | **Step back buttons** on each turn chunk | **L** | playerView.js |
| 8.15 | **Hide/show editor/player views** + focus mode margins | **M** | split.js |
| 8.16 | **Pause live compilation** | **S** | liveCompiler.js |
| 8.17 | **Dynamic menu titles** — "Save jolly.ink" | **S** | appmenus.js |
| 8.18 | **Menu item enabling** — save only when needed | **M** | appmenus.js |
| 8.19 | **Export full web player** using ink.js | **L** | export-for-web-template/ |
| 8.20 | **Load & play JSON file** with editing hidden | **M** | controller.js + playerView.js |

### Engineering Fixes

| # | Task | Effort | Notes |
|---|------|--------|-------|
| 8.21 | **FIX: Ace highlights not removed** on file switch | **M** | editorView.js |
| 8.22 | **FIX: /tmp never cleared** — stale compiled files | **S** | liveCompiler.js |
| 8.23 | **FIX: Replay fade transition** on last turn | **S** | playerView.js + jQuery |
| 8.24 | **FIX: Cmd-D "don't save"** | **S** | main.js |
| 8.25 | **VERIFY: Multiple windows** flakiness | **M** | projectWindow.js |
| 8.26 | **Refactor: InkFile.path** → always relative, add absolutePath() | **M** | inkFile.js |
| 8.27 | **Refactor: EventEmitter** migration from ad-hoc events | **L** | All modules |

---

## 9. MagicOnion Integration (Unity/.NET gRPC)

> MagicOnion (Cysharp) = gRPC + MessagePack for Unity/.NET.
> Replaces WebSocket+msgpack (MAUI) and enables networked Unity clients.
> Keeps OneJS for local Unity (no network needed).
>
> **Architecture diagram**: `docs/architecture/ink.magiconion.puml`

### Prerequisites (already done)

| # | What | Status | Location |
|---|------|--------|----------|
| — | Proto messages (16 files, 65 messages) | Done | `src/proto/ink/model/` |
| — | Binary protobuf encoding (`msg.encode()`) | Done | `InkModelSerializers.toBytes()` |
| — | MessagePack serialization (Jackson) | Done | `InkModelSerializers.toMsgpack()` |
| — | IInkRuntime interface (11 methods) | Done | `src/csMain/ink/cs/IInkRuntime.cs` |
| — | InkRuntimeService (platform-agnostic) | Done | `src/csMain/ink/cs/InkRuntimeService.cs` |
| — | IAssetEventTransport (6 channels) | Done | `src/csMain/ink/cs/transport/IAssetEventTransport.cs` |
| — | Proto-generated C# model classes | Done | `src/csMain/ink/cs/model/` |

### P0 — Service Interfaces

| # | Task | Effort | Notes |
|---|------|--------|-------|
| 9.1 | **IInkRuntimeService : IService\<IInkRuntimeService\>** — 11 unary RPC methods wrapping IInkRuntime (Compile, StartStory, ContinueStory, Choose, Get/SetVariable, Save/LoadState, Reset, EndSession, GetAssetEvents) | **M** | MagicOnion shared interface (NuGet: MagicOnion.Abstractions) |
| 9.2 | **IInkEventHub : IStreamingHub\<IInkEventHub, IInkEventReceiver\>** — streaming hub: JoinSession, LeaveSession, SendEvent (fire-and-forget) | **M** | Maps to AssetEventBus 6 channels |
| 9.3 | **IInkEventReceiver** — client callback interface: OnTagEvent, OnAssetLoad, OnAssetLoaded, OnInventoryChange, OnVoiceSynthesize, OnVoiceReady | **S** | Mirrors IAssetEventTransport events |

### P1 — Server Implementation

| # | Task | Effort | Notes |
|---|------|--------|-------|
| 9.4 | **InkRuntimeServiceImpl** — MagicOnion server wrapping InkRuntimeService | **M** | NuGet: MagicOnion.Server, hosted in ASP.NET or Kestrel |
| 9.5 | **InkEventHubImpl** — StreamingHub server bridging AssetEventBus | **L** | Pub/sub relay: AssetEventBus → StreamingHub broadcast |
| 9.6 | **MessagePack-CSharp config** — ContractlessStandardResolver to match Jackson string-key format | **S** | NuGet: MessagePack, must match `InkModelSerializers.toMsgpack()` |

### P2 — Client Implementation

| # | Task | Effort | Notes |
|---|------|--------|-------|
| 9.7 | **MagicOnionInkClient (Unity)** — replaces in-process OneJS for networked Unity clients | **M** | NuGet: MagicOnion.Client + grpc-dotnet |
| 9.8 | **MagicOnionEventReceiver (Unity)** — implements IInkEventReceiver, replaces InkAssetEventReceiver WebSocket path | **M** | Main-thread dispatch via Unity Update() |
| 9.9 | **MagicOnionInkClient (MAUI)** — replaces MsgpackAssetEventClient WebSocket transport | **M** | Same interfaces, different DI registration |
| 9.10 | **Proto service definitions** — add `service InkStoryService` + `service InkEventService` to .proto files | **S** | Enables grpc-dotnet codegen alongside MagicOnion |

### P3 — KT Server Bridge

| # | Task | Effort | Notes |
|---|------|--------|-------|
| 9.11 | **gRPC endpoint in McpRouter.kt** — serve binary protobuf over HTTP/2 | **L** | grpc-kotlin or Ktor gRPC plugin, bridges to InkEngine |
| 9.12 | **Streaming bridge** — AssetEventBus → gRPC server-side streaming | **M** | Converts publish/subscribe to gRPC streams |

**Subtotal: 12 tasks | Effort: ~6–8 weeks**

---

## Summary by Effort

| Category | Done | Remaining | Total Items |
|----------|------|-----------|-------------|
| **1. KT test gaps** | 6 (P0 + InkEdit + InkMdTable + InkWebDav) | 8 | 14 |
| **2. JS test gaps** | 0 | 9 | 9 |
| **3. TS test gaps** | 0 (48 tests exist but only 1 module) | 5 | 5 |
| **4. C# test gaps** | 3 (ink-csharp + OneJS + BidiTdd) | 3 | 6 |
| **5. Docs/diagrams** | 6 (plan merge, interop, ts/py puml, puml updates, proto count) | 9 | 15 |
| **6. BIDI matrix gaps** | 0 | 12 | 12 |
| **7. Build/infra** | 3 (GraalVM, dir rename, ink-csharp) | 3 | 6 |
| **8. Original Inky TODO** | 0 | 24 | 24 |
| **9. MagicOnion** | 0 | 12 | 12 |
| **Grand Total** | **18** | **85** | **103** |

### Current Test Counts: KT 145 + C# 42 + TS 48 + JS 64 = **299 total**

### Estimated Remaining Effort: ~28–38 weeks (1 developer)

### Priority Order (recommended)
1. ~~KT P0 tests (1.1–1.4)~~ — **DONE** (145 tests passing)
2. **Build infra** (7.1, 7.4) — unified build + CI pipeline
3. **JS P0 tests** (2.1–2.3) — prevent data loss bugs (#508, #515)
4. **BIDI matrix EXTERNAL** (6.12) — only fully-untested feature
5. **C# Unity bridge** (4.3) — enable Unity integration
6. **MagicOnion P0** (9.1–9.3) — service interfaces (unlocks all MagicOnion work)
7. Everything else by priority tier
