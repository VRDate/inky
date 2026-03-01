# MAUI CI/CD — .NET 9 + SteelToe + MagicOnion

> .NET MAUI CI/CD for ink C# runtime + cross-platform app
> Builds for Android, iOS, Windows, macOS
>
> **PlantUML diagram**: [`maui-cicd.puml`](maui-cicd.puml)
> **Related**: [`JBANG_LAUNCHER.md`](JBANG_LAUNCHER.md) | [`KMP_CICD.md`](KMP_CICD.md) | [`UNITY_CICD.md`](UNITY_CICD.md) | [`INK_MAUI_UNITY_INTEGRATION.md`](INK_MAUI_UNITY_INTEGRATION.md)

---

## 1. SDK Stack

| Tool | Version | Purpose |
|------|---------|---------|
| .NET SDK | 9.0 | Build + test + pack |
| MAUI workload | `maui` | Cross-platform UI |
| Android workload | `android` | Android target |
| iOS workload | `ios` | iOS target |
| macOS workload | `maccatalyst` | macOS Catalyst target |
| xunit | 2.7.x | Test framework |
| Google.Protobuf | 4.28.x | Proto codegen (match KT) |
| MessagePack | 2.5+ | Binary serialization |
| MagicOnion | latest | gRPC real-time framework |
| SteelToe | 4.x | Cloud-native .NET patterns |

---

## 2. Build Pipeline

```
dotnet restore → dotnet test → dotnet build → dotnet pack
```

### Steps

1. **Restore** — download NuGet packages + install workloads
2. **Test** — xunit (42 C# tests) + ink-proof conformance (135 stories)
3. **Build** — platform-specific binaries (Android/iOS/Windows/macOS)
4. **Pack** — NuGet packages for shared libraries

---

## 3. Platform Build Matrix

| Platform | Runner | .NET Workload | UAAL Export | Transport |
|----------|--------|---------------|-------------|-----------|
| **Android** | ubuntu-22.04 | `android` | AAR (ARM64) | In-process bridge |
| **iOS** | macos-latest | `ios` | .framework (ARM64) | In-process bridge |
| **Windows** | windows-latest | *(WinUI3)* | N/A (no UAAL) | WebSocket to standalone Unity |
| **macOS** | macos-latest | `maccatalyst` | N/A (no UAAL) | WebSocket to standalone Unity |

### CI Workflow (Planned)

```yaml
jobs:
  maui-build:
    strategy:
      matrix:
        include:
          - os: ubuntu-22.04
            target: net9.0-android
            workload: android
          - os: macos-latest
            target: net9.0-ios
            workload: ios
          - os: macos-latest
            target: net9.0-maccatalyst
            workload: maccatalyst
          - os: windows-latest
            target: net9.0-windows10.0.19041.0
            workload: ""
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-dotnet@v4
        with:
          dotnet-version: '9.0.x'
      - run: dotnet workload install ${{ matrix.workload }}
        if: matrix.workload != ''
      - run: dotnet test ink-maui/InkMaui.Tests/
      - run: dotnet build ink-maui/InkMaui/ -f ${{ matrix.target }}
```

---

## 4. Test Strategy

### Unit Tests (xunit)

| Test Suite | Tests | What It Covers |
|------------|-------|---------------|
| InkBidiTdd.Tests | 42 | Ink.Compiler, Ink.Runtime, 10-method OneJS bridge |

### Conformance Tests (Planned)

`ink-maui/InkMaui.ConformanceTests/` — runs 135 ink-proof stories through 3 runtimes:

1. **C# ink runtime** — directly via `InkStorySession`
2. **Kotlin ink.kt** — via MCP server HTTP API
3. **inkjs** — via Node.js subprocess

Assert identical output transcripts across all 3.

### Proto Round-Trip

- C# `Ink.Model.StoryState` ↔ Kotlin `ink.model.StoryState` via protobuf, JSON, msgpack
- Verify field-by-field equality in both directions

---

## 5. NuGet Packages

Three packages produced by `dotnet pack`:

| Package | Dependencies | Purpose |
|---------|-------------|---------|
| `Inky.Ink.Model` | Google.Protobuf | Proto-generated C# data classes |
| `Inky.Ink.AssetEvents` | Inky.Ink.Model, MessagePack | Transport client (6 channels) |
| `Inky.Ink.Unity` | Inky.Ink.Model | Non-MonoBehaviour event parsing |

---

## 6. Integration with KMP

MAUI consumes the KMP MCP server for ink operations:

| Protocol | Transport | Encoding | Use Case |
|----------|-----------|----------|----------|
| **MCP REST** | HTTP `POST /message` | JSON-RPC | Tool invocation from MAUI |
| **MCP SSE** | HTTP `GET /sse` | Server-Sent Events | Event stream to MAUI |
| **gRPC** (planned) | HTTP/2 | Binary protobuf | MagicOnion client → KT server |
| **RSocket** | WebSocket `/rsocket` | msgpack | Asset events |

### Serialization Interop

| Format | Kotlin Side | C# Side | Match Strategy |
|--------|-------------|---------|----------------|
| **protobuf** | Wire 5.4.0 (`msg.encode()`) | Google.Protobuf 4.28.x | Pin versions |
| **msgpack** | Jackson + MessagePackFactory | MessagePack (neuecc) | `ContractlessStandardResolver` |
| **JSON** | Moshi (Wire adapter) | System.Text.Json | Standard JSON |

---

## 7. Integration with Unity (UAAL)

Unity as a Library embedded in MAUI app:

```
┌──────────────────────────────────────┐
│           .NET MAUI App              │
│                                      │
│  ┌──────────────┐  ┌─────────────┐  │
│  │  MAUI UI     │  │ Unity View  │  │
│  │  (C# .NET 9) │  │ (UAAL)     │  │
│  │              │  │             │  │
│  │  ink C#      │  │ ink Unity   │  │
│  │  runtime     │◄─┤ runtime    │  │
│  └──────────────┘  └─────────────┘  │
│                                      │
│  ┌──────────────────────────────┐   │
│  │  Asset Event Bus (6 channels)│   │
│  │  RSocket + msgpack           │   │
│  └──────────────────────────────┘   │
└──────────────────────────────────────┘
```

- **Android**: AAR (ARM64) — in-process bridge via `UnitySendMessage`
- **iOS**: .framework (ARM64) — in-process bridge
- **Windows/macOS**: No UAAL — WebSocket fallback to standalone Unity

See: [`INK_MAUI_UNITY_INTEGRATION.md`](INK_MAUI_UNITY_INTEGRATION.md)

---

## 8. SteelToe Services

[SteelToe](https://steeltoe.io/) provides cloud-native .NET patterns:

| Service | Package | Purpose |
|---------|---------|---------|
| **Service Discovery** | Steeltoe.Discovery.Eureka | Ink sessions find each other |
| **Config Server** | Steeltoe.Extensions.Configuration.ConfigServerCore | Centralized ink story config |
| **Circuit Breaker** | Polly | Resilient asset loading |
| **Distributed Tracing** | Steeltoe.Management.TracingCore (OpenTelemetry) | Trace ink execution across services |

**Note**: SteelToe 4.x targets .NET 8. Keep API project on `net8.0`, MAUI on `net9.0`.

---

## 9. MagicOnion — gRPC Real-Time

MagicOnion (Cysharp) sits between proto messages and Unity/MAUI clients:

### Service Interfaces (Shared)

| Interface | Pattern | Methods |
|-----------|---------|---------|
| `IInkRuntimeService` | Unary RPC | 11 (Compile, StartStory, ContinueStory, Choose, Get/SetVariable, Save/LoadState, Reset, EndSession, GetAssetEvents) |
| `IInkEventHub` | StreamingHub | JoinSession, LeaveSession, SendEvent |
| `IInkEventReceiver` | Client callback | OnTagEvent, OnAssetLoad, OnAssetLoaded, OnInventoryChange, OnVoiceSynthesize, OnVoiceReady |

### Architecture

```
MAUI Client → MagicOnion Client → gRPC → MagicOnion Server → InkRuntimeService.cs
                                                            → AssetEventBus (RSocket relay)

Unity Client → MagicOnion Client → gRPC → same server
           or → OneJS Bridge (local, no network)
```

---

## References

- [.NET MAUI](https://learn.microsoft.com/en-us/dotnet/maui/) — Cross-platform UI
- [SteelToe](https://steeltoe.io/) — Cloud-native .NET
- [MagicOnion](https://github.com/Cysharp/MagicOnion) — gRPC for Unity/.NET
- [UnityUaal.Maui](https://github.com/matthewrdev/UnityUaal.Maui) — UAAL pattern
- [`INK_MAUI_UNITY_INTEGRATION.md`](INK_MAUI_UNITY_INTEGRATION.md) — Integration architecture
- [`KMP_CICD.md`](KMP_CICD.md) — KMP server CI/CD
