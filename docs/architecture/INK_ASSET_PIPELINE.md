# Ink Asset Pipeline — Event-Driven Architecture

## Overview

The asset pipeline connects ink story runtime to six client surfaces and 3D renderers via four protocols:

### Client Surfaces

| Client | Type | Purpose |
|--------|------|---------|
| **SillyTavern** | MCP user UI (localhost:8000) | Chat-based story interaction with LLM |
| **Electron** (Inky desktop) | Desktop app | ACE editor + inklecate compiler |
| **inkey PWA** | Progressive Web App | CodeMirror + Remirror + Yjs collab |
| **Chromium PWA extension** | Browser extension | Editor extension for Chrome/Edge |
| **Unity WebGL** | 3D rendering client | AssetBundle prefabs + animation |
| **BabylonJS WebXR** | 3D rendering client | glTF meshes + WebXR immersive |

### Protocols

| Protocol | Transport | Encoding | Purpose |
|----------|-----------|----------|---------|
| **MCP** | SSE / stdio / REST | JSON-RPC | LLM + user ↔ ink tools (79 tools) |
| **Yjs** | HocusPocus WebSocket | CRDT Y.Doc | Real-time collaborative editing |
| **RSocket** | WebSocket (`/rsocket`) | msgpack | Event-driven asset pipeline (EDA) |
| **OneJS** | In-process (`__inkBridge`) | JSON string | Unity ↔ JS bridge (no network) |

See: [`ink-rsocket-transport.puml`](ink-rsocket-transport.puml)

## Per-Framework Ink Runtime

Each framework has its own ink compiler/runtime. GraalJS is to KT what OneJS is to Unity.

| Framework | Compiler | Runtime | Editor | Transport |
|-----------|----------|---------|--------|-----------|
| **KT/JVM** (MCP) | GraalJS + inkjs | GraalJS + inkjs | — | In-process |
| **C#/Unity** (WebGL) | ink-csharp | ink-csharp + OneJS | — | In-process |
| **JS/Electron** (Inky) | inklecate | inkjs | ACE | WebSocket |
| **JS/Browser** (inkey) | inkjs | inkjs | CM6 + Remirror + Yjs | WebSocket |

See: [`ink-per-framework-runtime.puml`](ink-per-framework-runtime.puml)

## 2D Text UI vs 3D Rendering Clients

**inkjs = 2D emoji tags + text UI.** Emoji are text markers in ink tags — not 3D objects. The 3D rendering clients resolve these text markers to actual 3D assets:

| Layer | Technology | What It Does |
|-------|-----------|-------------|
| **2D Text UI** | inkjs (all frameworks) | Emits tags like `# mesh:🗡️` `# anim:sword_slash` — pure text |
| **3D Rendering** | BabylonJS (WebXR) | Resolves emoji tag → glTF mesh → 3D scene in browser |
| **3D Rendering** | Unity (WebGL) | Resolves emoji tag → AssetBundle prefab → 3D scene |
| **2D Editor** | inkey PWA (CM6/Remirror) | Shows emoji as text indicators in story output |

The `EmojiAssetManifest` bridges 2D text tags → 3D asset references.

## Emoji Category System

Emoji tags identify item categories. Each emoji maps to an animset, grip type, mesh prefix, and audio category:

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
| 🎤 | voice | voice_ref | — | — | — | voice |

`EmojiAssetManifest.kt` stores this mapping and resolves emoji → `AssetCategory` → `AssetRef`.

## Faker Per Framework

faker-js in JS/OneJS/Electron, kotlin-faker at MCP server:

| Framework | Faker Library | Usage |
|-----------|--------------|-------|
| **KT/JVM** (MCP server) | `kotlin-faker` (serpro69) | MCP tools: `generate_ink_vars`, `generate_story_md` |
| **JS/Electron** (Inky) | `faker-js` (npm `@faker-js/faker`) | Client-side data generation |
| **JS/Browser** (inkey) | `faker-js` (npm `@faker-js/faker`) | Client-side data generation |
| **C#/Unity + OneJS** | `faker-js` via OneJS bridge | In-process via `__inkBridge` |

Emoji kategories map to faker methods — same mapping in both kotlin-faker and faker-js.

## Formula-Driven MD Tables (POI + faker + k-random)

MD tables in ink documents aren't static — they're **spreadsheet-like with formulas**. The first rows define column headers and formulas (POI XLSX style), and MCP tools generate data rows below using faker (kotlin-faker on server, faker-js on clients) + k-random seeds.

### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  MD Table (formula headers)                                     │
│                                                                 │
│  | emoji | name   | type   | base_dmg | modifier | level_dmg  │
│  |-------|--------|--------|----------|----------|------------|│
│  |       |        |        |          |          | =D2+E2*F1  │ ← POI formula
│  |       |        |        |          |          |            │
│                                                                 │
│  ↓ MCP tool: generate_ink_vars(seed=42, count=5)               │
│                                                                 │
│  | 🗡️  | Frostbane | sword | 12 | 3 | =D2+E2*1 |            │ ← faker fills
│  | 🪄  | Oakstaff  | staff | 8  | 5 | =D3+E3*1 |            │ ← k-random range
│  | 🏹  | Windbow   | bow   | 10 | 4 | =D4+E4*1 |            │
│  | 🛡️  | Ironwall  | shield| 0  | 8 | =D5+E5*1 |            │
│  | ⚗️  | Elixir    | potion| 0  | 0 | 0         |            │
└─────────────────────────────────────────────────────────────────┘
```

### Emoji Kategories → Faker Methods

Each emoji category maps to specific faker-kotlin methods for generating contextually appropriate random data:

| Emoji | Faker Kategory | Methods | Range Examples |
|-------|---------------|---------|----------------|
| 🗡️ sword | `faker.game.weapon()` | name, prefix, suffix | base_dmg: 8-20 |
| 🛡️ shield | `faker.game.armor()` | name, material | defense: 5-15 |
| 🪄 staff | `faker.fantasy.tolkien()` | name, adjective | mana_cost: 10-30 |
| 🏹 bow | `faker.game.weapon()` | name, range_type | range: 10-50 |
| 🧙 character | `faker.name`, `faker.dnd` | name, class, race, alignment | STR/DEX/CON: 3-18 |
| ⚗️ potion | `faker.science.element()` | name, color, effect | heal: 10-50 |
| 🗝️ key | `faker.ancient.god()` | name, origin | rarity: 1-5 |
| 🗺️ map | `faker.address.city()` | location, region | distance: 1-100 |
| 🪙 coin | `faker.commerce.price()` | value, currency | value: 1-1000 |
| 👑 crown | `faker.ancient.titan()` | name, power | prestige: 1-10 |

### Per-Level Item Modifiers

MD tables support **level-indexed modifiers**. The formula row references a level column:

```markdown
## items.ink

| emoji | name | type | base_dmg | per_level | level | total_dmg |
|-------|------|------|----------|-----------|-------|-----------|
|       |      |      |          |           |       | =D+E*F    |

## Level 1 items (seed=42)

| 🗡️ | Frostbane | sword | 12 | 3 | 1 | 15 |
| 🪄 | Oakstaff  | staff | 8  | 5 | 1 | 13 |

## Level 5 items (seed=42, level=5)

| 🗡️ | Frostbane | sword | 12 | 3 | 5 | 27 |
| 🪄 | Oakstaff  | staff | 8  | 5 | 5 | 33 |
```

Same seed = same items, different level = different computed stats. The POI formula engine evaluates `=D+E*F` at render time.

### Character DnD Traits

```markdown
## characters.ink

| emoji | name | class | race | STR | DEX | CON | INT | WIS | CHA | HP |
|-------|------|-------|------|-----|-----|-----|-----|-----|-----|-----|
|       |      |       |      |     |     |     |     |     |     | =10+E*2 |

## Party (seed=7)

| 🧙 | Merlin   | wizard   | human    | 8  | 12 | 10 | 18 | 16 | 14 | 26 |
| 🧙 | Aragorn  | ranger   | dunedain | 16 | 14 | 15 | 12 | 14 | 16 | 42 |
```

### POI XLSX Formula Evaluation

Apache POI evaluates formulas in the MD table cells. Supported formula patterns:

| Pattern | Example | Meaning |
|---------|---------|---------|
| `=COL+COL*COL` | `=D2+E2*F2` | Column arithmetic |
| `=SUM(range)` | `=SUM(E2:E10)` | Sum of range |
| `=IF(cond,t,f)` | `=IF(D2>10,"rare","common")` | Conditional |
| `=VLOOKUP(...)` | VLOOKUP across tables | Cross-table reference |
| `=RAND()*N` | `=RAND()*20` | Random (seeded via k-random) |
| `=MIN/MAX` | `=MAX(D2,E2)` | Aggregation |

### k-random Seeds

k-random provides deterministic random number generation:
- Same seed → same characters, items, stats across all frameworks
- Seed stored in MD table metadata: `<!-- seed: 42, level: 1 -->`
- Reproducible across KT, C#, JS (cross-platform determinism)

### MCP Tools for Table Generation

| Tool | Description |
|------|-------------|
| `generate_ink_vars` | Generate ink VARs from emoji MD table + seed + level |
| `generate_story_md` | Generate full story MD with characters + items + formulas |
| `resolve_emoji` | Look up emoji → AssetCategory (animset, grip, mesh) |
| `parse_asset_tags` | Parse ink tags → list of AssetRef |

The LLM or user calls `generate_story_md(seed=42, level=3, characters=4, items=8)` and gets a complete MD document with formula headers and faker-generated rows.

## Event Flow

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
Consumers:
  ├─ KT/JVM: InkEngine (GraalJS) → Camel routes → in-process events
  ├─ C#/Unity: ink-csharp → InkAssetEventReceiver.cs → AssetBundle → anim
  │    └─ OneJS bridges JS → C# → in-process events (no network)
  ├─ JS/BabylonJS: inkjs → AssetEventClient.ts → RSocket WS → glTF → WebXR
  ├─ JS/Electron: inklecate + inkjs → RSocket WS → asset events
  └─ Inkey editor: edit mode (CM6/Remirror) | play mode (inkjs → asset indicators)
```

See: [`ink-asset-event-pipeline.puml`](ink-asset-event-pipeline.puml)

## Asset Event Types

Events are serialized as msgpack and published via RSocket:

| Event Type | Fields | Trigger |
|------------|--------|---------|
| `MeshLoadEvent` | emoji, meshPath, transform | `# mesh:weapon_sword_01` tag |
| `AnimPlayEvent` | animSetId, clipName, loop | `# anim:sword_slash` tag |
| `VoiceSynthEvent` | voiceRef, text, language | `# voice:gandalf_en` tag |
| `SfxPlayEvent` | audioCategory, clipId | `# sfx:metal_clang` tag |
| `InventoryChangeEvent` | action, emoji, itemName | ink LIST change detected |

## Inventory → AnimSet → Ink Script Block

```
inventory += sword  (ink LIST change)
  → InventoryChangeEvent(equip, 🗡️, sword)
  → EmojiAssetManifest resolves 🗡️ → sword_1h animset + weapon_sword mesh
  → RSocket publishes to ink/inventory/change channel
  → Unity/BabylonJS plays equip animation, loads sword mesh
  → ink script block === equip_sword === triggered
  → ink tags emitted → more asset events (recursive)
```

## Voice Reference System

Each character has a FLAC voice reference file for Chatterbox ONNX voice cloning:

```markdown
| emoji | character | language | flac_path |
|-------|-----------|----------|-----------|
| 🎤 | gandalf | en | voices/gandalf_en.flac |
| 🎤 | gandalf | he | voices/gandalf_he.flac |
| 🎤 | aragorn | en | voices/aragorn_en.flac |
```

23 languages supported. `VoiceSynthEvent` carries the `VoiceRef` + text → ChatterboxTtsEngine produces WAV audio streamed via RSocket `requestChannel`.

## AsyncAPI Contract

Event channels defined in `docs/asyncapi/ink-asset-events.yaml`:

| Channel | Pattern | Description |
|---------|---------|-------------|
| `ink/session/{id}/assets` | requestStream | All asset events for a session |
| `ink/inventory/change` | fireAndForget | Inventory mutations |
| `ink/voice/synthesize` | requestChannel | Bidirectional voice synth |
| `ink/mesh/load` | fireAndForget | Mesh load requests |
| `ink/anim/play` | fireAndForget | Animation play requests |

## Dependencies

| Ecosystem | Package | Purpose |
|-----------|---------|---------|
| KT | `io.github.serpro69:kotlin-faker` | Faker kategories → random data |
| KT | `org.apache.poi:poi-ooxml` | XLSX formula evaluation in MD tables |
| KT | `io.rsocket.kotlin:rsocket-ktor-server` | RSocket server |
| KT | `org.msgpack:jackson-dataformat-msgpack` | msgpack serialization |
| TS | `rsocket-core` + `rsocket-websocket-client` | RSocket browser client |
| TS | `@msgpack/msgpack` | msgpack decoding |
| C# | `Microsoft.ML.OnnxRuntime` | Voice cloning TTS |

## Diagrams

- [`ink-per-framework-runtime.puml`](ink-per-framework-runtime.puml) — Compiler/runtime per framework
- [`ink-asset-event-pipeline.puml`](ink-asset-event-pipeline.puml) — Full event flow
- [`ink-rsocket-transport.puml`](ink-rsocket-transport.puml) — Multi-protocol transport sequence
- [`ink-mcp-tools.puml`](ink-mcp-tools.puml) — 79-tool architecture
- [`ink-kmp-classes.puml`](ink-kmp-classes.puml) — Class diagram with asset pipeline
- [`ink-kmp-architecture.puml`](ink-kmp-architecture.puml) — Multi-protocol component diagram
