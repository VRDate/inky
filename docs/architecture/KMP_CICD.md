# KMP CI/CD — GraalVM 25 + Gradle + GraalJS

> Kotlin Multiplatform CI/CD for ink.kt runtime + MCP server
> Oracle GraalVM 25 (`25.0.2-graal`) with GraalJS polyglot
> All versions from `gradle/libs.versions.toml` (single source of truth)
>
> **PlantUML diagram**: [`kmp-cicd.puml`](kmp-cicd.puml)
> **Related**: [`JBANG_LAUNCHER.md`](JBANG_LAUNCHER.md) | [`MAUI_CICD.md`](MAUI_CICD.md) | [`UNITY_CICD.md`](UNITY_CICD.md)

---

## 1. Version Catalog — Single Source of Truth

All SDK versions live in `gradle/libs.versions.toml`. GitHub Actions workflows parse them via grep/sed:

```yaml
- name: Read SDK versions from libs.versions.toml
  id: sdk
  run: |
    TOML="gradle/libs.versions.toml"
    get() { grep "^$1 = " "$TOML" | sed 's/.*= "\(.*\)"/\1/'; }
    echo "node=$(get node)" >> "$GITHUB_OUTPUT"
    echo "jdk=$(get jdk)" >> "$GITHUB_OUTPUT"
    echo "kotlin=$(get kotlin)" >> "$GITHUB_OUTPUT"
```

### Versions Used

| Key | Value | Purpose |
|-----|-------|---------|
| `jdk` | `25` | Gradle JVM toolchain |
| `jdk-sdkman` | `25.0.2-graal` | Oracle GraalVM via sdkman |
| `kotlin` | `2.3.0` | Kotlin compiler |
| `graal` | `25.0.2` | GraalJS polyglot library |
| `ktor` | `3.1.1` | Ktor server framework |
| `node` | `24` | Node.js for JS/TS builds |

---

## 2. SDK Stack

### Oracle GraalVM 25

Oracle GraalVM 25 bundles GraalJS natively — no separate polyglot install needed. The MCP server uses GraalJS to load and execute `inkjs` (the JavaScript ink compiler/runtime) inside the JVM.

```kotlin
// InkEngine.kt — GraalJS polyglot context
val context = Context.newBuilder("js")
    .allowAllAccess(true)
    .build()
context.eval("js", inkjsSource)  // loads ink-full.js
```

**Key constraint**: GraalVM 25 dropped macOS x64. Apple Silicon (aarch64) only on macOS.

### sdkman + `.sdkmanrc`

```
java=25.0.2-graal
kotlin=2.3.0
```

Run `sdk env` at repo root to auto-switch. Works on Ubuntu, WSL, macOS, Termux (partial).

---

## 3. Build Pipeline

### Gradle 9 Tasks

| Task | Description | Dependencies |
|------|-------------|-------------|
| `compileKotlin` | Compile commonMain + jvmMain + jsMain | — |
| `test` | JUnit5 tests (JVM) | compileKotlin |
| `testFast` | Skip slow GraalJS integration tests | compileKotlin |
| `runServer` | Run MCP server with JVM args | compileKotlin |
| `fatJar` | Fat JAR with all dependencies | compileKotlin |
| `jsBrowserProductionWebpack` | JS bundle for browser | compileKotlinJs |

### JVM Args

```
--add-modules jdk.incubator.vector --enable-preview
```

Required for JLama (SIMD-accelerated vector operations).

---

## 4. Test Matrix

| Test Suite | Runner | Tests | What It Covers |
|------------|--------|-------|---------------|
| **InkEngineTest** (GraalJS) | JUnit5 | 29 | inkjs loaded via GraalJS, compile/run/save/load |
| **McpToolsTest** | JUnit5 | 15 | 79 MCP tool invocations |
| **McpRouterTest** | Ktor testHost | 8 | SSE/REST/WebSocket routing |
| **InkEditEngineTest** | JUnit5 | 19 | Section parse/replace/rename |
| **ColabEngineTest** | JUnit5 | 14 | Yjs room create/join/leave |
| **InkWebDavEngineTest** | JUnit5 | 25 | WebDAV CRUD + backup/restore |
| **InkMdTableTest** | JUnit5 | 35 | Markdown table parsing |
| **Total** | | **145** | 7/20 modules (35% coverage) |

### CI Workflow (Planned)

```yaml
jobs:
  kmp-test:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v4
      - name: Read SDK versions
        id: sdk
        run: |
          TOML="gradle/libs.versions.toml"
          get() { grep "^$1 = " "$TOML" | sed 's/.*= "\(.*\)"/\1/'; }
          echo "jdk=$(get jdk)" >> "$GITHUB_OUTPUT"
      - uses: actions/setup-java@v4
        with:
          distribution: graalvm
          java-version: ${{ steps.sdk.outputs.jdk }}
      - uses: gradle/actions/setup-gradle@v4
      - run: gradle test
        working-directory: ink-kmp-mcp
```

---

## 5. KMP Targets

| Target | Output | Approach | Status |
|--------|--------|----------|--------|
| **JVM** (Android, Desktop) | `.jar` | Direct blade-ink-java dependency | ✅ Primary |
| **JS** (Browser, Node) | `.js` module | Kotlin/JS IR compiler | ✅ Compiles |
| **Native** (iOS, macOS, Linux) | `.dylib` / `.so` | Kotlin/Native + cinterop | ⏳ Planned |
| **WASM** (WasmJS, WASI) | `.wasm` module | Kotlin/Wasm | ⏳ Planned |

### Build Targets per Runner

| Runner | Targets Built |
|--------|--------------|
| ubuntu-22.04 | JVM, JS, Native (Linux), WASM |
| macos-latest | Native (macOS, iOS) |
| windows-latest | *(cross-compiled from Linux or skipped)* |

---

## 6. Server Portability

The MCP server is a JVM fat JAR — runs anywhere GraalVM 25 is available:

| Environment | SDK Source | Command |
|-------------|-----------|---------|
| **Docker** | sdkman in Dockerfile | `java --add-modules jdk.incubator.vector --enable-preview -jar inky-mcp.jar` |
| **Ubuntu** | sdkman `.sdkmanrc` | same |
| **WSL** | sdkman `.sdkmanrc` | same |
| **macOS** | sdkman `.sdkmanrc` | same (aarch64 only with GraalVM 25) |
| **Termux** | `pkg install openjdk-21` | same (JDK 25 when available) |

---

## 7. Integration with MAUI

ink.kt MCP server exposes:
- **MCP HTTP API** (`POST /message`, `GET /sse`) — MAUI consumes for ink tool invocation
- **gRPC** (planned via `InkModelSerializers.toBytes()`) — MagicOnion client in MAUI connects to KT server
- **Proto round-trip** — Kotlin `ink.model.*` ↔ C# `Ink.Model.*` via protobuf, JSON, msgpack

See: [`MAUI_CICD.md`](MAUI_CICD.md), [`INK_MAUI_UNITY_INTEGRATION.md`](INK_MAUI_UNITY_INTEGRATION.md)

---

## 8. Integration with Unity

GraalJS on the JVM server side ≈ OneJS on Unity side:
- **Same inkjs**: Both load `ink-full.js` into an embedded JS engine
- **MCP tools**: Unity OneJS bridge can invoke MCP tools via localhost HTTP
- **Asset events**: RSocket + msgpack (`AssetEventBus`) streams events to Unity `InkAssetEventReceiver.cs`

See: [`UNITY_CICD.md`](UNITY_CICD.md)

---

## 9. Docker — sdkman in CI

Dockerfile snippet for KMP CI (also used in Unity CI Dockerfile):

```dockerfile
SHELL ["/bin/bash", "-c"]

RUN curl -s "https://get.sdkman.io?ci=true&rcupdate=false" | bash \
    && sed -i 's/sdkman_auto_answer=false/sdkman_auto_answer=true/' $HOME/.sdkman/etc/config \
    && sed -i 's/sdkman_selfupdate_feature=true/sdkman_selfupdate_feature=false/' $HOME/.sdkman/etc/config \
    && sed -i 's/sdkman_colour_enable=true/sdkman_colour_enable=false/' $HOME/.sdkman/etc/config

ENV BASH_ENV="$HOME/.sdkman/bin/sdkman-init.sh"

ARG JDK_VERSION=25.0.2-graal
ARG KOTLIN_VERSION=2.3.0
RUN sdk install java ${JDK_VERSION} && \
    sdk install kotlin ${KOTLIN_VERSION} && \
    sdk flush && rm -rf $HOME/.sdkman/archives/* $HOME/.sdkman/tmp/*

ENV JAVA_HOME="$HOME/.sdkman/candidates/java/current"
ENV KOTLIN_HOME="$HOME/.sdkman/candidates/kotlin/current"
ENV PATH="$JAVA_HOME/bin:$KOTLIN_HOME/bin:$PATH"
```

---

## References

- [GraalVM 25](https://www.graalvm.org/) — Oracle GraalVM with GraalJS
- [GraalJS](https://www.graalvm.org/latest/reference-manual/js/) — Polyglot JS engine
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/kmp-overview.html) — KMP overview
- [SDKMAN](https://sdkman.io/) — SDK manager
- [`ink-kmp-mcp/build.gradle.kts`](../../ink-kmp-mcp/build.gradle.kts) — Gradle build
- [`INK_KMP_MCP.md`](INK_KMP_MCP.md) — Full MCP server architecture
