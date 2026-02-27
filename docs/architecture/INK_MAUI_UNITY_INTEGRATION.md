# ink.kt + MAUI + Unity Integration Architecture (2026)

## Vision

**ink KMP MAUI SteelToe multiplatform hybrid** — a single ink narrative runtime
that runs natively across Kotlin (KMP), C# (.NET MAUI), and Unity, with shared
game state and bi-directional communication.

## Architecture Stack

```
┌─────────────────────────────────────────────────────────┐
│                    ink Story (.ink)                       │
│              (authored in inky / VS Code)                 │
└───────────────────────┬─────────────────────────────────┘
                        │ compile (inklecate)
                        ▼
┌─────────────────────────────────────────────────────────┐
│                 compiled JSON bytecode                    │
└─────┬──────────┬──────────┬──────────┬──────────────────┘
      │          │          │          │
      ▼          ▼          ▼          ▼
┌──────────┐┌──────────┐┌──────────┐┌──────────────────┐
│  ink.kt  ││  inkjs   ││  ink C#  ││  ink.kt.mica     │
│  (KMP)   ││  (JS)    ││  (MAUI)  ││  (parser, ref)   │
│          ││          ││          ││                    │
│ JVM/     ││ Browser/ ││ .NET 9/  ││ Parses .ink       │
│ Android/ ││ Node/    ││ MAUI/    ││ directly at       │
│ iOS/     ││ Electron ││ SteelToe ││ runtime           │
│ JS/WASM  ││          ││          ││                    │
└──────────┘└──────────┘└──────────┘└──────────────────┘
      │          │          │
      └──────────┼──────────┘
                 │
      ┌──────────▼──────────┐
      │  InkRuntime (common │
      │  interface/contract) │
      └──────────┬──────────┘
                 │
      ┌──────────▼──────────┐
      │    ink-proof (e2e)   │
      │  135 ink + 7 bytecode│
      │  conformance tests   │
      └─────────────────────┘
```

## UnityUaal.Maui — The Missing Link

**[matthewrdev/UnityUaal.Maui](https://github.com/matthewrdev/UnityUaal.Maui)**
demonstrates embedding Unity as a Library (UAAL) inside .NET MAUI apps.

### How It Works

1. **Unity Project** → exports as native library (iOS framework / Android AAR)
2. **Native Bindings** → C# wrappers around the native Unity library
3. **MAUI App** → hosts Unity view alongside native MAUI UI
4. **Bridge** → bidirectional communication (MAUI ↔ Unity)

### Integration with ink

```
┌─────────────────────────────────────────────┐
│              .NET MAUI App                   │
│                                              │
│  ┌──────────────┐  ┌─────────────────────┐  │
│  │  MAUI UI     │  │  Unity View (UAAL)  │  │
│  │  (C# .NET 9) │  │  (3D/2D rendering)  │  │
│  │              │  │                      │  │
│  │  ink C#      │  │  ink Unity Runtime   │  │
│  │  runtime     │◄─┤  (asset events)     │  │
│  │              │  │                      │  │
│  │  SteelToe    │  │  OneJS + ink.kt      │  │
│  │  services    │  │  (Kotlin/JS bridge)  │  │
│  └──────────────┘  └─────────────────────┘  │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │  Asset Event Bus (RSocket/msgpack)   │   │
│  │  6 channels: tags, load, loaded,     │   │
│  │  inventory, voice-synth, voice-ready │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

### Platforms

| Platform | MAUI | Unity UAAL | ink Runtime |
|----------|------|-----------|-------------|
| Android  | ✅   | ✅ (AAR)  | ink.kt (KMP/JVM) |
| iOS      | ✅   | ✅ (framework) | ink.kt (KMP/Native) |
| Windows  | ✅   | ⬜ (Desktop) | ink C# (.NET) |
| macOS    | ✅   | ⬜ (Desktop) | ink C# (.NET) |
| Web      | —    | —         | inkjs / ink.kt (Kotlin/JS) |

## Priority Order

1. **KMP ready first** — ink.kt compiles to all KMP targets (JVM, JS, Native)
2. **ink-proof passing** — all 135+ conformance tests green
3. **MAUI integration** — ink C# runtime in .NET MAUI app
4. **Unity UAAL** — embed Unity view via UnityUaal.Maui pattern
5. **Asset event bridge** — RSocket/msgpack between MAUI and Unity
6. **SteelToe services** — cloud-native .NET microservices for ink state

## SteelToe Integration

[SteelToe](https://steeltoe.io/) provides cloud-native .NET patterns:
- **Service discovery** — ink sessions find each other
- **Config server** — centralized ink story configuration
- **Circuit breaker** — resilient asset loading
- **Distributed tracing** — trace ink story execution across services

## Related Projects Tracked

| Project | Role | Language |
|---------|------|----------|
| [inkle/ink](https://github.com/inkle/ink) | Reference C# runtime + compiler | C# |
| [y-lohse/inkjs](https://github.com/y-lohse/inkjs) | JS/TS port (npm inkjs) | TypeScript |
| [bladecoder/blade-ink](https://github.com/nickthecook/blade-ink) | Java port | Java |
| [chromy/ink-proof](https://github.com/chromy/ink-proof) | Conformance test suite | Python |
| [matthewrdev/UnityUaal.Maui](https://github.com/matthewrdev/UnityUaal.Maui) | Unity UAAL in MAUI | C# |
| ink.kt (this project) | KMP runtime | Kotlin |
| ink.kt.mica (this project) | Parser reference | Kotlin |

## PlantUML Tracking

Class diagrams for progress tracking:
- `docs/architecture/ink.kt.puml` — ink.kt runtime classes + cross-reference table
- `docs/architecture/ink.kt.mica.puml` — mica parser reference classes
- `docs/architecture/ink.js.puml` — inkjs TypeScript classes
- `docs/architecture/ink.java.puml` — blade-ink Java classes
- `docs/architecture/ink.cs.puml` — C# reference runtime classes
