# Ink KMP Runtime Port — Three-Way Implementation Comparison

> **Primary reference: C# (inkle/ink)** — the canonical implementation by the ink authors.
> Java (blade-ink) and JS (inkjs) are secondary references. Kotlin takes the best from each.

## Port Status

| # | Class | C# | Java | JS | KMP | Status | Notes |
|---|-------|----|------|----|-----|--------|-------|
| 1 | ValueType | enum | enum | const | enum class | ✅ | Identical across all |
| 2 | PushPopType | enum | enum | PushPopType | enum class | ✅ | |
| 3 | ErrorType | enum | enum | ErrorType | enum class | ✅ | |
| 4 | StoryException | Exception | Exception | Error | StoryException | ✅ | |
| 5 | INamedContent | interface | interface | — | interface | ✅ | JS has no interface |
| 6 | DebugMetadata | class | class | class | data class | ✅ | Kotlin data class |
| 7 | Path | class + Component | class + Component | class + Component | class + Component | ✅ | |
| 8 | InkObject (Object) | Object | RTObject | InkObject | InkObject | ✅ | Named to avoid clash |
| 9 | Pointer | struct | class + assign() | class + copy() | data class | ✅ | Kotlin value semantics |
| 10 | SearchResult | class | class | SearchResult | class | ✅ | |
| 11 | Glue | class | class | class | class | ✅ | Marker class |
| 12 | Void | class | class | class | class | ✅ | Marker class |
| 13 | Tag | class | class | class | class | ✅ | |
| 14 | ControlCommand | class + enum | class + enum | class + CommandType | class + enum class | ✅ | |
| 15 | Divert | class | class | class | class | ✅ | |
| 16 | ChoicePoint | class | class | class | class | ✅ | |
| 17 | VariableAssignment | class | class | class | class | ✅ | |
| 18 | VariableReference | class | class | class | class | ✅ | |
| 19 | Container | class | class | class | class | ✅ | Core container |
| 20 | Value\<T\> | abstract Value | abstract AbstractValue | Value | sealed class Value\<T\> | ✅ | **Kotlin sealed** |
| 21 | BoolValue | class | class | class | class | ✅ | C#-style "true"/"false" |
| 22 | IntValue | class | class | class | class | ✅ | |
| 23 | FloatValue | class | class | class | class | ✅ | |
| 24 | StringValue | class | class | class | class | ✅ | |
| 25 | DivertTargetValue | class | class | class | class | ✅ | |
| 26 | VariablePointerValue | class | class | class | class | ✅ | |
| 27 | ListValue | class | class | class | class | ✅ | |
| 28 | InkListItem | struct | class | InkListItem | data class | ✅ | **Kotlin data class** |
| 29 | InkList | Dictionary\<I,int\> | HashMap\<I,Integer\> | Map | LinkedHashMap delegation | ✅ | **operator +/-** |
| 30 | ListDefinition | class | class | class | class | ✅ | **lazy items** |
| 31 | ListDefinitionsOrigin | class | class | class | class | ✅ | |
| 32 | Choice | class | class | class | Comparable\<Choice\> | ✅ | **sorted + hashable** |
| 33 | NativeFunctionCall | class | class | class | fun interface BinaryOp/UnaryOp | ✅ | **SAM supplier** |
| 34 | CallStack | class | class | class | class | ✅ | **Fixed Java bug** |
| 35 | CallStack.Element | nested class | static inner | namespace prop | nested class | ✅ | LinkedHashMap temps |
| 36 | CallStack.Thread | nested class | static inner | namespace prop | nested class | ✅ | |
| 37 | Flow | class | class | class | class | ✅ | |
| 38 | StatePatch | class | class | class | class | ✅ | LinkedHashMap/Set |
| 39 | VariablesState | IEnumerable | Iterable | class | Iterable + operator[] | ✅ | **fun interface** |
| 40 | Json (stub) | — | — | — | internal object | ⚠️ | Stub for now |
| 41 | StoryState | class | class | class | OutputOp functional enum | ✅ | **LinkedHashMap state** |
| 42 | SimpleJson | class | class | class | object + Reader + Writer | ✅ | **LinkedHashMap**, fun interface InnerWriter, fixes Java bugs |
| 43 | JsonSerialisation | static class | class | class | — | ⏳ | |
| 44 | Story | class | class | class | — | ⏳ | Main entry |
| 45 | Profiler | class | class | class | — | ⏳ | |
| 46 | StopWatch | class | class | class | — | ⏳ | |

**Status**: ✅ = ported, ⚠️ = stub, ⏳ = pending

## Where C# Is Best (Primary Reference)

| Pattern | C# | Java Problem | JS Problem | Kotlin |
|---------|-----|-------------|------------|--------|
| **Value hierarchy** | All types in one file, clean cast() | Split across 8 files, verbose | Untyped, no generics | **sealed class** — exhaustive when |
| **Pointer** | struct (value semantics) | class + assign() (ref semantics) | class + copy() | **data class** (value semantics) |
| **InkListItem** | struct (value equality) | class, manual equals/hashCode | plain object | **data class** (auto equals/hash) |
| **NativeFunctionCall** | Static Create, orderly registration | 897 lines of if-chains | arguments[] hack | **fun interface** SAM supplier |
| **VariablesState indexer** | `this[name]` get/set | get()/set() methods | $()/$$() methods | **operator get/set** |
| **Event binding** | delegate + event keyword | Interface + setter | callback function | **fun interface** (SAM conversion) |
| **StatePatch.TryGet** | out-param TryGetValue | nullable return | has() + get() | **nullable return** (like Java but cleaner) |
| **CallStack temp vars** | Dictionary\<string, Object\> | HashMap (BUG: infinite recursion) | Map\<string, InkObject\> | **LinkedHashMap** (fixed bug) |

## Where Java Is Limited

| Issue | Java Code | Kotlin Fix |
|-------|-----------|------------|
| No sealed types | Abstract class + runtime instanceof | `sealed class Value<T>` — compile-time exhaustive |
| No data classes | Manual equals/hashCode/toString | `data class InkListItem` — free value semantics |
| No operator overloading | `list.union(other)` | `list + other`, `list - other` |
| No default params | 3 overloaded setTemporaryVariable() | 1 method with `contextIndex: Int = -1` |
| Infinite recursion bug | `setTemporaryVariable(name, value, boolean)` calls itself | Fixed with default params — no overload needed |
| HashMap only | No insertion order | LinkedHashMap everywhere |
| Verbose SAM | `new VariableChanged() { @Override void... }` | `VariableChanged { name, value -> ... }` |
| InnerWriter boilerplate | Anonymous inner class for JSON writers | Lambdas / deferred to JsonSerialisation |

## Where JS Is Not Type Safe

| Issue | JS Code | Kotlin Fix |
|-------|---------|------------|
| No type parameters | `Map<string, any>` | `LinkedHashMap<String, InkObject>` |
| arguments[] hack | `if (arguments[0] instanceof Story)` | Named constructors / companion factory |
| parseInt everywhere | `parseInt(jElementObj["type"])` | Type-safe enum `PushPopType.entries[ordinal]` |
| typeof checks | `typeof currentContainerPathStrToken !== "undefined"` | `?.let { }` null-safe chain |
| No interface | Classes only, duck typing | `interface INamedContent` |
| No enum classes | `const PushPopType = { Tunnel: 0 }` | `enum class PushPopType` |
| Untyped temporaryVariables | `new Map()` | `LinkedHashMap<String, InkObject>` |

## Kotlin-Unique Patterns (Not in Any Original)

| Pattern | Usage | Why |
|---------|-------|-----|
| `sealed class Value<T>` | Value hierarchy | Exhaustive `when`, no missed cases |
| `data class` | Pointer, InkListItem | Free value semantics, copy, destructuring |
| `fun interface` | BinaryOp, UnaryOp, VariableChanged | SAM conversion — lambdas auto-convert |
| `operator fun` | InkList +/-, VariablesState [] | Idiomatic Kotlin syntax |
| `by delegation` | InkList : MutableMap by _map | Composition over inheritance |
| `LinkedHashMap` | All collections | Insertion-order + O(1) (user directive) |
| `Comparable<Choice>` | Choice ordering | TreeSet / sorted collection support |
| `Flow<LinkedHashSet<Choice>>` | Reactive choices | Coroutine-based event streaming |
| `buildString {}` | callStackTrace | Kotlin stdlib builder pattern |

## File Map

```
ink-kmp-mcp/src/commonMain/kotlin/com/bladecoder/ink/runtime/
├── ValueType.kt              # Tier 0 — enum
├── PushPopType.kt            # Tier 0 — enum
├── ErrorType.kt              # Tier 0 — enum
├── StoryException.kt         # Tier 0 — exception
├── INamedContent.kt          # Tier 1 — interface
├── DebugMetadata.kt          # Tier 1 — data class
├── Path.kt                   # Tier 1 — path addressing
├── InkObject.kt              # Tier 1 — base class
├── Pointer.kt                # Tier 1 — data class
├── SearchResult.kt           # Tier 1 — result wrapper
├── Container.kt              # Tier 1 — content container
├── Glue.kt                   # Tier 2 — leaf
├── Void.kt                   # Tier 2 — leaf
├── Tag.kt                    # Tier 2 — leaf
├── ControlCommand.kt         # Tier 2 — leaf
├── Divert.kt                 # Tier 2 — leaf
├── ChoicePoint.kt            # Tier 2 — leaf
├── VariableAssignment.kt     # Tier 2 — leaf
├── VariableReference.kt      # Tier 2 — leaf
├── Value.kt                  # Tier 3 — sealed hierarchy (all value types)
├── InkListItem.kt            # Tier 4 — data class
├── InkList.kt                # Tier 4 — LinkedHashMap delegation
├── ListDefinition.kt         # Tier 4 — lazy items
├── ListDefinitionsOrigin.kt  # Tier 4 — origin resolver
├── Choice.kt                 # Tier 5 — Comparable
├── NativeFunctionCall.kt     # Tier 5 — fun interface BinaryOp/UnaryOp
├── CallStack.kt              # Tier 5 — Element, Thread nested classes
├── Flow.kt                   # Tier 5 — execution flow
├── StatePatch.kt             # Tier 5 — transactional overlay
├── VariablesState.kt         # Tier 5 — globals + fun interface VariableChanged
└── StoryState.kt             # Tier 6 — OutputOp functional enum, TreeMap<Choice> state
```

## Merged Ink+MD Grammar (Regex Only)

Both ink and markdown are **line-oriented text formats parseable by regex**. No AST parser needed — every line is classified by its leading characters. The fenced ` ```ink ` separator is eliminated: ink and markdown coexist naturally in a single file.

### Line Classification (Regex Dispatch Table)

| Leading Regex | Classification | Example |
|---|---|---|
| `^(#)\s+(.+)$` | **H1 section name** (file name = title) | `# The Dark Forest` |
| `^(#{2})\s+(.+)$` | **H2 chapter** | `## Characters` |
| `^(#{3})\s+(\w+)` | **H3 knot** (= `=== name ===`) | `### combat` |
| `^(#{4})\s+(\w+)` | **H4 stitch** (= `= name`) | `#### parry` |
| `^(#{5})\s+(\w+)` | **H5 thread** (= `<- name`) | `##### background_music` |
| `^(#{6})\s+(.+)$` | **H6 label / metadata** | `###### Author: inkle` |
| `^(\s*)(={2,})` | **ink knot** (classic syntax) | `=== chapter_1 ===` |
| `^(\s*)(=)(\s*)(\w+)` | **ink stitch** (classic syntax) | `= meeting` |
| `^(\s*)((?:[*+]\s?)+)` | **choice** (= `ul` / `*` list) | `* [Go north]` |
| `^(\s*)(\d+\.)\s+` | **numbered choice** (= `ol` list) | `1. [First option]` |
| `^(\s*)((?:-(?!>)\s*)+)` | **gather** | `- -` |
| `^\s*~` | **ink logic** | `~ x = x + 1` |
| `^(\s*)(VAR\|CONST)\b` | **ink var/const** (or `dl` `: value`) | `VAR health = 100` |
| `^(\s*)(LIST)\b` | **ink list** (or `dl` `: a, b, c`) | `LIST items = sword, potion` |
| `^(\s*):\s+(.+)$` | **definition value** (= VAR/LIST) | `: 100` |
| `^(\s*)(INCLUDE)\b` | **ink include** | `INCLUDE helpers.ink` |
| `^(\s*)(EXTERNAL)\b` | **ink external** | `EXTERNAL gameOver()` |
| `^(\s*)(TODO)\b` | **ink todo** | `TODO: fix this` |
| `^(\s*)(->)` | **ink divert** | `-> chapter_2` |
| `^(\s*)(//\|/\*)` | **ink comment** | `// note` |
| `^\|.*\|$` | **md table row** | `\| name \| health \|` |
| `(?<=\S\s*)#` | **ink tag** (inline only) | `text # tag1 # tag2` |
| `<>` | **ink glue** | `<>` |
| everything else | **prose text** (both) | `You enter the cave.` |

### List Unification: UL/OL/DL = Ink Choices

Markdown lists and ink choices are the **same structural concept** — a set of selectable options:

| Markdown | HTML | Ink | Merged AST Node |
|---|---|---|---|
| `* item` | `<ul><li>` | `* [choice text]` | `Choice(bullet="*")` |
| `+ item` | `<ul><li>` | `+ [sticky choice]` | `Choice(bullet="+", sticky=true)` |
| `1. item` | `<ol><li>` | `1. [numbered choice]` | `Choice(bullet="1.", ordered=true)` |
| `term : def` | `<dl><dt><dd>` | `* [label] content` | `Choice(label="label")` |

The `*` bullet is already shared — ink `*` choice and markdown `*` unordered list are the same character. In the merged grammar:
- `* [Go north]` = ink choice = clickable `<li>` in HTML
- `+ [Always visible]` = sticky ink choice = persistent `<li>`
- `1. [First option]` = ordered ink choice = numbered `<ol><li>`
- `* [Sword] A fine blade` = labeled choice = `<dl><dt>Sword</dt><dd>A fine blade</dd></dl>`

### Definition List = Ink VAR/LIST

The markdown definition list (`<dl>`) maps directly to ink variable declarations:

| Markdown DL | HTML | Ink | Merged AST |
|---|---|---|---|
| `name` + `\n: value` | `<dt>name</dt><dd>value</dd>` | `VAR name = value` | `VarDecl(name, value)` |
| `name` + `\n: "text"` | `<dt>name</dt><dd>"text"</dd>` | `VAR name = "text"` | `VarDecl(name, stringValue)` |
| `name` + `\n: true` | `<dt>name</dt><dd>true</dd>` | `VAR name = true` | `VarDecl(name, boolValue)` |
| `items` + `\n: a, b, c` | `<dt>items</dt><dd>a, b, c</dd>` | `LIST items = a, b, c` | `ListDecl(name, items)` |

Example — the same data as both markdown DL and ink VARs:
```
health
: 100

player_name
: "Arthur"

items
: sword, potion, key
```

The parser treats `term` + `: value` as `VAR term = value` (or `LIST` if comma-separated).

### Key Disambiguation

- **`# Section Name`** (H1 at line start) = section name — file name is the title
- **`text # tag1 # tag2`** (inline after content) = ink tags — only after text, never at line start
- **`-` (dash)** = ink gather (not `->`) — markdown lists use `*` which is ink choice, so `-` for lists is avoided
- **Prose text** is shared — works identically in ink (story output) and markdown (document body)
- **Tables** (`|...|`) are always markdown — ink has no table syntax

### AST Node Unification: Knots ARE Headings

The core innovation: **ink knots and markdown headings merge into one AST node**.
Both `=== knot_name ===` and `### knot_name` represent the same structural concept — a named section.

| Level | Ink Syntax | Markdown Syntax | HTML | Merged AST Node |
|---|---|---|---|---|
| Title | — | **file name** | `<title>` | Document title |
| H1 | — | `# Section` | `<h1>` | `Section(level=1)` — section name |
| H2 | (grouping) | `## Chapter` | `<h2>` | `Section(level=2)` — chapter |
| H3 | `=== knot ===` | `### knot` | `<h3>` | `Section(level=3, isKnot=true)` |
| H4 | `= stitch` | `#### stitch` | `<h4>` | `Section(level=4, isStitch=true)` |
| H5 | `<- thread` | `##### thread` | `<h5>` | `Thread(level=5)` — concurrent |
| H6 | (label) | `###### note` | `<h6>` | `Label(level=6)` — metadata |

The parser accepts **both syntaxes** for the same node — `=== combat ===` and `### combat` produce identical AST. This means:
- Markdown editors (Remirror, CodeMirror) render knots as headings natively
- Ink editors (ACE) render headings as knots natively
- TOC / outline / fold works identically for both
- Ink tags are **inline only**: `Some text # tag1 # tag2` (never at line start)
- `##### thread_name` = `<- thread_name` — concurrent thread declaration

### Merged File Format

```
## Characters

| name    | role   | health |
|---------|--------|--------|
| Arthur  | knight | 100    |
| Merlin  | wizard | 80     |

VAR player_name = "Arthur"
VAR player_role = "knight"

=== start ===
You are {player_name}, a {player_role}.
* [Draw sword] -> combat
* [Cast spell] -> magic

### combat
~ health = health - 20
You fight bravely.
-> END
```

Both `=== start ===` and `### combat` are valid knot declarations in the merged format. No ` ```ink ` fences needed. Each line is self-classifying by regex. The grammar parser (both TS `classifyLine()` and KT `InkMdEngine.parse()`) dispatches on the leading pattern.

### Grammar Source of Truth

The unified regex grammar lives in `@inky/ink-language` (TypeScript) with a KMP Kotlin mirror in commonMain. Both are pure regex, zero dependencies:

| Location | Language | Purpose |
|----------|----------|---------|
| `ink-js/inkey/packages/ink-language/src/ink-grammar.ts` | TypeScript | Editor tokenization (ACE, CM6, Remirror) |
| `ink-kmp-mcp/src/commonMain/.../InkMdGrammar.kt` | Kotlin | KMP runtime line classifier (pure regex) |

## Strategy: Verify with Old, Develop in KT Only

### Zero 3rd Party Dependencies

The KMP commonMain runtime uses **pure Kotlin stdlib only** — no kotlinx-serialization, no protobuf runtime, no Jackson. The `ink.model.*` proto layer sits above (in JVM-specific source sets), but commonMain has zero external dependencies.

### Old Code = Test Oracle

The existing C#, Java, and JS implementations serve as **verification targets**:

| Old Implementation | Role | How We Verify |
|-------------------|------|---------------|
| C# (inkle/ink) | Primary reference + test oracle | Run same .ink scripts, compare JSON output |
| Java (blade-ink) | Secondary oracle + regression | Run conformance test suite, compare StoryState |
| JS (inkjs) | Browser oracle + cross-platform | Run same stories in GraalJS, compare ContinueResult |

**Verification flow:**
```
.ink source → [C# compiler] → .ink.json
                                    ↓
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
              C# runtime       Java runtime      KT runtime
              (reference)      (blade-ink)        (KMP new)
                    ↓                ↓                ↓
              output text       output text      output text
                    └────────────────┼────────────────┘
                                    ↓
                            assert all equal
```

### New Features = KT Only

Once KT passes verification against all three oracles, new features are **KT-only**:
- Asset pipeline events (EmojiAssetManifest, AssetEventBus)
- Proto-annotated data classes (`ink.model.*`)
- Unicode symbol parsing
- Faker-based test data generation
- All future ink runtime extensions

Old C#/Java/JS remain as **backward-compatible fallbacks** until KMP targets fully replace them.

## Next Steps

1. **SimpleJson.kt** — JSON reader/writer (pure Kotlin, zero deps)
2. **JsonSerialisation.kt** — runtime object ↔ JSON conversion
3. **Story.kt** — main entry point (Continue, ChooseChoice, external functions)
4. **Profiler.kt, StopWatch.kt** — performance monitoring
5. **Verification** — run conformance tests against C#/Java/JS oracles
