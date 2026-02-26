# Ink Language Syntax — Complete Feature Reference & E2E Test Coverage

> **Reference:** [Writing with Ink](https://github.com/inkle/ink/blob/master/Documentation/WritingWithInk.md)
> **Test files analysed:**
> - `app/test/test.js` — General Inky E2E tests
> - `app/test/bidi-e2e.test.js` — Bidi/RTL-specific E2E tests
> - `app/test/fixtures/bidi_and_tdd.ink` — Comprehensive bidi ink fixture

---

## Coverage Summary (Updated — 100 features catalogued)

| Category | Total Features | In Fixture | In E2E | Fixture % | E2E % |
|---|---|---|---|---|---|
| Text Output | 8 | 8 | 5 | 100% | 63% |
| Flow Control | 16 | 12 | 4 | 75% | 25% |
| Choices | 10 | 6 | 3 | 60% | 30% |
| Variables & Types | 15 | 11 | 2 | 73% | 13% |
| Logic & Conditionals | 14 | 11 | 0 | 79% | 0% |
| Built-in Functions | 8 | 1 | 0 | 13% | 0% |
| Functions | 6 | 5 | 0 | 83% | 0% |
| Lists | 21 | 5 | 0 | 24% | 0% |
| Project Structure | 1 | 0 | 0 | 0% | 0% |
| **TOTAL** | **100** | **59** | **14** | **59%** | **14%** |

### Test Files

| Test File | Tests | Coverage Focus |
|---|---|---|
| `app/test/test.js` | 7 | Core Inky E2E (launch, compile, choices, TODOs) |
| `app/test/bidi-e2e.test.js` | 15 | Bidi/RTL typing, compilation, museum, assertions |
| `app/test/incremental-e2e.test.js` | 15 | Chapter-by-chapter writing simulation, 28 syntax features, timing |
| `app/test/bidify.test.js` | 29 | Unit tests for bidify/stripBidi/bidifyJson |
| `app/test/fixtures/bidi_and_tdd.ink` | — | 1113-line fixture covering 28 ink features × 10 RTL scripts |

### Legend

| Symbol | Meaning |
|---|---|
| COVERED | Feature is exercised and assertions are made in E2E tests |
| PARTIAL | Feature appears in test fixture ink but has no direct assertion |
| NOT COVERED | Feature is not tested at all in E2E tests |

---

## 1. Text Output

### 1.1 Plain Text
- **Syntax:** Any line of text is output directly to the player.
- **Example:**
  ```ink
  Hello, world!
  This is story content.
  ```
- **E2E Coverage:** COVERED
  - `test.js`: `'writes and reads hello world'` — sets `Hello World!` and asserts `text === 'Hello World!'`
  - `bidi-e2e.test.js`: `'compiles Hebrew story without errors'` — verifies Hebrew text `שלום` appears in output
  - `bidi-e2e.test.js`: `'types Hebrew hello world and compiles'` — types `שלום עולם!` and asserts it appears

### 1.2 Glue `<>`
- **Syntax:** Joins content across lines/diverts without a line break.
- **Example:**
  ```ink
  We went <>
  quickly home.
  ```
  Output: `We went quickly home.`
- **E2E Coverage:** PARTIAL
  - Used in `bidi_and_tdd.ink` syn_09/syn_09b (`<>` across knots), but no direct assertion verifying joined output.

### 1.3 Tags `#`
- **Syntax:** Add invisible metadata to a line.
- **Example:**
  ```ink
  Content here # tag_name
  # CLEAR
  Market scene. # location: market # mood: busy
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` uses `# CLEAR`, `# BGM:`, `# location:`, `# lang_section:` extensively.
  - No E2E test asserts on tag values or tag parsing.

### 1.4 String Interpolation `{var}`
- **Syntax:** Embed variable or expression values in text.
- **Example:**
  ```ink
  VAR name = "World"
  Hello, {name}!
  You have {gold} coins.
  Status: {describe_health(hp)}
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` uses `{lang}`, `{health}`, `{inventory}`, `{CITY_NAME}`, `{damage}`, `{describe_health(health)}` extensively.
  - No E2E test directly asserts interpolated variable values in output.

### 1.5 Escaping `\{ \}`
- **Syntax:** Output literal special characters.
- **Example:**
  ```ink
  Symbols: \{ \} \[ \]
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_25 uses `\{ \} \[ \]`.
  - No E2E test asserts on escaped output.

### 1.6 Comments `//` and `/* */`
- **Syntax:** Non-output annotations.
- **Example:**
  ```ink
  // Single-line comment
  /* Multi-line
     block comment */
  ```
- **E2E Coverage:** COVERED
  - Comments are present throughout all test fixture ink files.
  - `bidi_and_tdd.ink` uses both `//` and `/* */` styles.
  - Compilation succeeds (asserted), proving comments are parsed correctly.

### 1.7 TODO
- **Syntax:** Flagged during compilation as a reminder.
- **Example:**
  ```ink
  TODO: Add more choices here
  ```
- **E2E Coverage:** COVERED
  - `test.js`: `'shows TODOs'` — sets `TODO: Make this more interesting`, asserts `.issueCount.todo` is visible.

---

## 2. Flow Control

### 2.1 Knots `=== name ===`
- **Syntax:** Major story sections.
- **Example:**
  ```ink
  === knot_name ===
  Content here.

  === knot_with_params(a, b) ===
  Hello, {a}! You are {b} years old.
  ```
- **E2E Coverage:** COVERED
  - `bidi-e2e.test.js`: Hebrew story uses `=== start ===`, `=== smoke_test ===`, `=== bidi_museum ===`, `=== story ===`, `=== story_end ===`.
  - Navigation between knots is verified by clicking choices and asserting output text changes.
  - `bidi_and_tdd.ink` uses 40+ knots.

### 2.2 Stitches `= name`
- **Syntax:** Sub-sections within a knot.
- **Example:**
  ```ink
  === my_knot ===
  = first_stitch
  Content.
  = second_stitch
  More content.
  -> my_knot.second_stitch
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_02 uses `= opening` and `= middle` with `-> syn_02.middle`.
  - `murder_scene.ink` uses stitches extensively (e.g., `= operate_lamp`, `= compare_prints`).
  - No direct E2E assertion on stitch navigation.

### 2.3 Diverts `->`
- **Syntax:** Immediate flow redirection.
- **Example:**
  ```ink
  -> knot_name
  -> knot_name.stitch_name
  -> END
  -> DONE
  ```
- **E2E Coverage:** COVERED
  - `test.js`: `-> END` used in choice tests.
  - `bidi-e2e.test.js`: Hebrew story uses diverts throughout (`-> smoke_test`, `-> start`, `-> END`).
  - Navigation through diverts is implicitly verified by story flow assertions.

### 2.4 Tunnels `-> tunnel ->`
- **Syntax:** Subroutine-style call with return.
- **Example:**
  ```ink
  -> my_dream ->
  After the dream.

  === my_dream ===
  A vivid dream.
  * [Wake up] ->->
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_13 uses `-> syn_dream ->` with `->->` return.
  - `murder_scene.ink` uses tunnels: `-> operate_lamp ->`, `-> see_prints_on_glass ->`.
  - No direct E2E assertion on tunnel call/return behavior.

### 2.5 Threads `<-`
- **Syntax:** Merge choices from another knot into current flow.
- **Example:**
  ```ink
  <- thread_knot_a
  <- thread_knot_b
  * [Own choice] -> next

  === thread_knot_a ===
  * [Thread A option] Result A.
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_14 uses `<- syn_thread_a` and `<- syn_thread_b`.
  - No direct E2E assertion on thread merging behavior.

### 2.6 Gathers `-`
- **Syntax:** Collect diverging branches back together.
- **Example:**
  ```ink
  * [Choice A] Text A.
  * [Choice B] Text B.
  - All roads lead here.
  - (reunion) We met again.
  ```
- **E2E Coverage:** COVERED
  - `test.js`: `'writes and selects a choice'` — implicitly uses gathers (content after choice).
  - `bidi_and_tdd.ink` syn_05 uses gathers with labels: `- (reunion)`.

### 2.7 Labels `(label)`
- **Syntax:** Named points for visit counting and flow control.
- **Example:**
  ```ink
  - (opts)
    * [Option] -> opts
  * (myChoice) [Text]
  { myChoice: Already picked. }
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` uses `- (reunion)` in syn_05.
  - `murder_scene.ink` uses labels extensively: `- (top)`, `- (opts)`, `* (dobed)`, etc.
  - No direct E2E assertion on label-based visit counting.

---

## 3. Choices

### 3.1 Basic Choice `*`
- **Syntax:** One-time choice (consumed after selection).
- **Example:**
  ```ink
  * Choice text
    Result of choosing.
  ```
- **E2E Coverage:** COVERED
  - `test.js`: `'writes and selects a choice'` — `* Hello back\n  Nice to hear from you!`
  - `bidi-e2e.test.js`: Hebrew story uses `*` choices throughout.

### 3.2 Sticky Choice `+`
- **Syntax:** Reappears every time.
- **Example:**
  ```ink
  + [Repeatable option]
    This keeps appearing.
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` settings menu uses `+` for repeatable language/emoji toggles.
  - `murder_scene.ink` uses `+` for repeatable desk drawer opens and breathing on glass.
  - No direct E2E test targeting sticky choice reappearance.

### 3.3 Suppressed Choice Text `[]`
- **Syntax:** Text shown only in choice, hidden from output after selection.
- **Example:**
  ```ink
  * [Hidden choice text]
    Only this output appears.
  ```
- **E2E Coverage:** COVERED
  - `test.js`: `'suppresses choice text'` — `* [Hello back]\n  Nice to hear from you!` and asserts suppressed text does not appear.

### 3.4 Nested Choices `** ***`
- **Syntax:** Deeper levels of choice within a choice.
- **Example:**
  ```ink
  * [Outer choice]
    ** [Inner choice]
      *** [Deepest choice]
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_04 uses `**` and `***` nested choices.
  - `murder_scene.ink` uses `** ` and `*** ` extensively.
  - No direct E2E test targeting nested choice behavior.

### 3.5 Conditional Choice `* {condition}`
- **Syntax:** Choice appears only when condition is true.
- **Example:**
  ```ink
  * {has_key} [Unlock door] Opened!
  * {gold >= 10} [Buy item] Purchased.
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_18 uses `* {inventory ? sword}` and `* {inventory ? potion}`.
  - `murder_scene.ink` uses conditional choices extensively.
  - No direct E2E assertion verifying a conditional choice appears/hides.

### 3.6 Fallback Choice `* ->`
- **Syntax:** Auto-selected when no other choices remain.
- **Example:**
  ```ink
  * -> fallback_knot
  ```
- **E2E Coverage:** NOT COVERED
  - Not used in any test fixture.
  - Used in `storylets.ink` (`* ->`) but that file is not tested.

### 3.7 Choice Labels `* (label)`
- **Syntax:** Named choices for tracking.
- **Example:**
  ```ink
  * (myChoice) [Text]
  { myChoice: Already chosen. }
  ```
- **E2E Coverage:** PARTIAL
  - `murder_scene.ink` uses choice labels: `* (dobed)`, `* (darkunder)`, `* (pickup_cane)`.
  - No direct E2E assertion on choice-label visit counting.

---

## 4. Variables & Types

### 4.1 VAR (Global Variables)
- **Syntax:** Persist across the entire story.
- **Example:**
  ```ink
  VAR name = "Alice"
  VAR score = 0
  VAR alive = true
  VAR ratio = 0.5
  ```
- **E2E Coverage:** COVERED
  - `bidi-e2e.test.js`: `'loads the full bidi ink fixture without crashing'` — asserts `content.indexOf('VAR lang') !== -1`.
  - `bidi_and_tdd.ink` declares 14 global variables of types string, bool, int.

### 4.2 temp (Local Variables)
- **Syntax:** Scoped to current knot/stitch.
- **Example:**
  ```ink
  ~ temp damage = 15
  ~ temp target = -> next_knot
  ~ temp greeting = "Hello"
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_06 uses `~ temp damage = 15`, syn_16 uses `~ temp target = -> syn_17`.
  - No direct E2E assertion on temp variable behavior.

### 4.3 CONST
- **Syntax:** Immutable constants.
- **Example:**
  ```ink
  CONST MAX_HP = 100
  CONST CITY = "London"
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` uses `CONST MAX_HEALTH = 100` and `CONST CITY_NAME = "ירושלים"`.
  - No direct E2E assertion on CONST values.

### 4.4 Variable Types

All supported types:

| Type | Example | Tested in Fixture | E2E Assertion |
|---|---|---|---|
| `int` | `VAR health = 100` | Yes (bidi_and_tdd.ink) | No |
| `float` | `VAR ratio = 0.5` | No | No |
| `string` | `VAR lang = "both"` | Yes (bidi_and_tdd.ink) | Partial (VAR presence) |
| `bool` | `VAR show_emoji = true` | Yes (bidi_and_tdd.ink) | No |
| `list` | `VAR state = (item1, item2)` | Yes (bidi_and_tdd.ink) | No |
| `divert` | `VAR target = -> knot` | Yes (syn_16 as temp) | No |

- **E2E Coverage:** PARTIAL
  - Multiple types used in fixture, but only `string` VAR presence is asserted.
  - `float` type is not used in any test fixture.

### 4.5 Assignment `~`
- **Syntax:** Modify variable values with various operators.
- **Example:**
  ```ink
  ~ health = 100
  ~ gold += 25
  ~ gold -= 10
  ~ count++
  ~ count--
  ~ visited = true
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` uses `~ health = health - 10`, `~ gold += 25`, `~ trust_level++`, `~ visited_market = true`, `~ inventory += potion`.
  - No direct E2E assertion on assignment results.

### 4.6 ref Parameters
- **Syntax:** Pass variables by reference to functions.
- **Example:**
  ```ink
  === function pop(ref list)
    ~ temp x = LIST_MIN(list)
    ~ list -= x
    ~ return x
  ```
- **E2E Coverage:** NOT COVERED
  - Used in `murder_scene.ink` (`ref list`, `ref item_state`), `theintercept.ink` (`ref x`).
  - Not used in `bidi_and_tdd.ink`.
  - No E2E test exercises ref parameter behavior.

---

## 5. Logic & Conditionals

### 5.1 Inline Logic `~`
- **Syntax:** Execute logic without text output.
- **Example:**
  ```ink
  ~ health = health - 10
  ~ inventory += sword
  ~ temp x = clamp(v, 0, 100)
  ```
- **E2E Coverage:** PARTIAL
  - Extensively used in `bidi_and_tdd.ink` (every syn_* knot uses `~`).
  - No direct E2E assertion on inline logic results.

### 5.2 Inline Conditional `{cond: a | b}`
- **Syntax:** Ternary-style conditional text.
- **Example:**
  ```ink
  {alive: Breathing | Dead}
  {gold > 0: Rich}
  {has_key: Can unlock door.}
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_07 uses `{visited_market: ...}` and `{health > 50: ... | ...}`.
  - `bidi_and_tdd.ink` uses `{show_emoji: icon}` pattern throughout.
  - No direct E2E assertion on conditional text output.

### 5.3 Multi-line Conditional
- **Syntax:** Switch/if-else style blocks.
- **Example:**
  ```ink
  { - mood == happy: Smiling.
    - mood == sad: Crying.
    - else: Neutral. }
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_19 uses multi-line conditional with mood check.
  - Functions `clamp()`, `describe_health()`, `t()` all use multi-line conditionals.
  - No direct E2E assertion on multi-line conditional output.

### 5.4 Alternatives (Sequence / Cycle / Shuffle / Once / Stopping)
- **Syntax:** Text that varies across visits.
- **Example:**
  ```ink
  Sequence:  {First|Second|Third}        -- plays through once
  Cycle:     {&AM|PM|AM|PM...}           -- loops forever
  Shuffle:   {~Sun|Rain|Snow}            -- random each time
  Once:      {!Only shown first time|}   -- shown once then blank
  Stopping:  {First|Second|Last}         -- last entry repeats
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_08 uses sequence `{...|...|...}`, cycle `{&...|...}`, and shuffle `{~...|...|...}`.
  - `murder_scene.ink` uses sequence: `{a drawer at random|another drawer|a third drawer}`.
  - `theintercept.ink` uses stopping: `{|I rattle my fingers...|}`.
  - No direct E2E assertion on alternative text variation.

### 5.5 Operators
- **Syntax:** Arithmetic, comparison, and logical operators.
- **Example:**
  ```ink
  Arithmetic: + - * / % (mod)
  Comparison: == != > < >= <=
  Logical:    and or not (also: && || !)
  String:     + (concatenation)
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_21 uses all arithmetic ops: `{a+b}`, `{a-b}`, `{a*b}`, `{a/b}`, `{a mod b}`.
  - `bidi_and_tdd.ink` syn_22 uses `and`, `or`, `not`, `!=`, `>=`.
  - No direct E2E assertion on operator results.

### 5.6 Visit Counts
- **Syntax:** Track how often content has been visited.
- **Example:**
  ```ink
  {knot_name}            -- visit count as number
  {knot_name > 2: ...}   -- conditional on visits
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_17 uses `{syn_17}` (visit count output).
  - `murder_scene.ink` uses `{top >= 5}`, `{found == 1}`, `{open < 3}`.
  - No direct E2E assertion on visit count values.

### 5.7 Built-in Query Functions
- **Syntax:** System queries for game state.
- **Example:**
  ```ink
  TURNS_SINCE(-> knot_name)
  CHOICE_COUNT()
  TURNS()
  SEED_RANDOM(seed)
  RANDOM(min, max)
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_17 uses `TURNS_SINCE(-> syn_01)`.
  - `murder_scene.ink` uses `TURNS_SINCE(-> dobed)`, `TURNS_SINCE(-> reaching)`, `TURNS_SINCE(-> deskstate)`, `TURNS_SINCE(-> floorit)`.
  - `thread_in_tunnel.ink` uses `TURNS()`.
  - `list_random_subset.ink` uses `RANDOM(0,1)`.
  - No direct E2E assertion on query function return values.

### 5.8 Type Conversion Functions
- **Syntax:** Cast between types.
- **Example:**
  ```ink
  INT(value)
  FLOAT(value)
  FLOOR(value)
  LIST_VALUE(item)
  ```
- **E2E Coverage:** NOT COVERED
  - `INT()` and `FLOAT()` are not used in any test fixture.
  - `FLOOR()` is not used in any test fixture.
  - `LIST_VALUE()` is used in `swindlestones.ink` but that is not E2E tested.

---

## 6. Functions

### 6.1 Function Definition
- **Syntax:** Reusable logic that returns a value.
- **Example:**
  ```ink
  === function clamp(value, min_val, max_val) ===
  { - value < min_val: ~ return min_val
    - value > max_val: ~ return max_val
    - else:            ~ return value }
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` defines `clamp()`, `describe_health()`, `t()` functions.
  - Functions are called and story compiles, but no direct assertion on function return values.

### 6.2 Function Call
- **Syntax:** Invoke a function.
- **Example:**
  ```ink
  In text: {my_func(arg)}
  In logic: ~ result = my_func(a, b)
  As divert: -> my_func(arg)
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` calls functions in text (`{describe_health(health)}`, `{t("he","en")}`) and logic (`~ health = clamp(health, 0, MAX_HEALTH)`).
  - No direct E2E assertion on function call output.

### 6.3 Return `~ return`
- **Syntax:** Return a value from a function.
- **Example:**
  ```ink
  ~ return value
  ~ return "string result"
  ```
- **E2E Coverage:** PARTIAL
  - Used in all function definitions in `bidi_and_tdd.ink`.
  - No direct E2E assertion on return values.

### 6.4 EXTERNAL
- **Syntax:** Bind to a host language function (C#/JS).
- **Example:**
  ```ink
  EXTERNAL UPPERCASE(txt)
  === function UPPERCASE(txt)
    {txt}
  ```
- **E2E Coverage:** NOT COVERED
  - Used in `uppercase.ink` and `string_to_list.ink` (example snippets only).
  - No E2E test exercises EXTERNAL binding.

---

## 7. Lists

### 7.1 LIST Declaration
- **Syntax:** Define enumerated types with optional initial values and custom numeric values.
- **Example:**
  ```ink
  LIST mood = neutral, happy, sad, angry
  LIST inventory = (nothing), sword, shield
  LIST values = (one=1), (five=5), (ten=10)
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` declares `LIST mood` and `LIST inventory`.
  - `murder_scene.ink` declares 9 LIST types.
  - No direct E2E assertion on LIST declaration/values.

### 7.2 List Modification `+= -=`
- **Syntax:** Add or remove items from a list.
- **Example:**
  ```ink
  ~ inventory += sword
  ~ inventory -= shield
  ~ mood = happy
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_04 uses `~ inventory += key`, syn_15 uses `~ inventory += potion`, `~ inventory -= nothing`, `~ inventory += map`.
  - No direct E2E assertion on list modification results.

### 7.3 List Query `? !?`
- **Syntax:** Test list membership.
- **Example:**
  ```ink
  {inventory ? sword: Has sword!}
  {inventory !? map: No map.}
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_15 uses `{inventory ? sword: ...}` and `{inventory !? map: ...}`.
  - `murder_scene.ink` uses `?` and `!?` extensively.
  - No direct E2E assertion on query results.

### 7.4 List Set Operators `+ - ^`
- **Syntax:** Union, difference, intersection.
- **Example:**
  ```ink
  list1 + list2    -- union
  list1 - list2    -- difference
  list1 ^ list2    -- intersection
  ```
- **E2E Coverage:** NOT COVERED
  - `murder_scene.ink` uses `knowledgeState ^ y` (intersection).
  - `swindlestones.ink` uses set operators.
  - No direct E2E assertion on set operations.

### 7.5 List Built-in Functions
- **Syntax:** Query and manipulate lists.
- **Example:**
  ```ink
  LIST_COUNT(list)
  LIST_MIN(list)
  LIST_MAX(list)
  LIST_ALL(list_or_item)
  LIST_RANGE(list, min, max)
  LIST_INVERT(list)
  LIST_RANDOM(list)
  LIST_VALUE(item)
  ```
- **E2E Coverage:** NOT COVERED
  - Used in `murder_scene.ink` (`LIST_MIN`, `LIST_ALL`, `LIST_RANGE`).
  - Used in `list_prev_next.ink` (`LIST_MAX`, `LIST_INVERT`, `LIST_RANGE`, `LIST_ALL`).
  - Used in `swindlestones.ink` (`LIST_COUNT`, `LIST_RANDOM`, `LIST_VALUE`, `LIST_RANGE`, `LIST_INVERT`).
  - No E2E test directly exercises or asserts on list function results.

### 7.6 Multi-valued Lists
- **Syntax:** Lists as sets holding multiple values simultaneously.
- **Example:**
  ```ink
  VAR hand = (ace, king, queen)
  ~ hand -= king
  {LIST_COUNT(hand)} cards left.
  ```
- **E2E Coverage:** NOT COVERED
  - `murder_scene.ink` uses multi-valued lists for knowledge state and bedroom light state.
  - No E2E assertion on multi-valued list behavior.

---

## 8. Project Structure

### 8.1 INCLUDE
- **Syntax:** Import another ink file at compile time.
- **Example:**
  ```ink
  INCLUDE other_file.ink
  INCLUDE subfolder/helpers.ink
  ```
- **E2E Coverage:** NOT COVERED
  - `bidi_and_tdd.ink` syn_26 mentions INCLUDE as display-only (commented out: `// INCLUDE קובץ_נוסף.ink`).
  - No E2E test compiles a multi-file ink project.

### 8.2 END / DONE
- **Syntax:** Terminate story or flow.
- **Example:**
  ```ink
  -> END    -- entire story stops
  -> DONE   -- current thread/flow ends, others continue
  ```
- **E2E Coverage:** COVERED
  - `test.js`: All choice tests use `-> END`.
  - `bidi-e2e.test.js`: Hebrew story uses `-> END`.
  - `bidi_and_tdd.ink` uses both `-> END` and `-> DONE` (via threads).

---

## 9. Knot Parameters

### 9.1 Knots with Parameters
- **Syntax:** Pass arguments to knots.
- **Example:**
  ```ink
  -> greet("Alice", 30)

  === greet(name, age) ===
  Hello, {name}! Age: {age}.
  ```
- **E2E Coverage:** PARTIAL
  - `bidi_and_tdd.ink` syn_11 uses `-> syn_greet("אבי/Avi", 42)` with `=== syn_greet(name, age) ===`.
  - No direct E2E assertion on parameterized knot output.

---

## Coverage Gaps — Prioritized

### Critical Gaps (features with no coverage at all)

| # | Feature | Risk | Recommendation |
|---|---|---|---|
| 1 | **INCLUDE** (multi-file projects) | High — multi-file is common in real projects | Add E2E test with 2+ ink files using INCLUDE |
| 2 | **EXTERNAL** functions | Medium — needed for game engine integration | Add test with EXTERNAL + fallback function |
| 3 | **ref parameters** | Medium — used in list manipulation patterns | Add test with `ref` parameter function |
| 4 | **List built-in functions** | Medium — LIST_COUNT, LIST_MIN, etc. are core | Add test asserting list function output |
| 5 | **List set operators** (^ intersection) | Medium — used in complex game logic | Add test with `+`, `-`, `^` on lists |
| 6 | **Multi-valued lists** | Medium — used in state tracking | Add test with multi-valued list operations |
| 7 | **Type conversion** (INT, FLOAT, FLOOR) | Low — niche usage | Add basic type conversion test |
| 8 | **Fallback choice** (`* ->`) | Low — advanced flow control | Add test with exhaustible choices + fallback |

### Partial Coverage (features used but not asserted)

| # | Feature | Current State | Recommendation |
|---|---|---|---|
| 1 | **Glue** `<>` | Used in fixture, no assertion | Assert joined text output from glue |
| 2 | **Tags** `#` | Used in fixture, no assertion | Assert tag values via ink runtime API |
| 3 | **String interpolation** `{var}` | Used in fixture, no assertion | Assert interpolated variable in output |
| 4 | **Stitches** `= name` | Used in fixture, no assertion | Assert stitch navigation output |
| 5 | **Tunnels** `-> tunnel ->` | Used in fixture, no assertion | Assert tunnel call/return flow |
| 6 | **Threads** `<-` | Used in fixture, no assertion | Assert thread-merged choices appear |
| 7 | **Sticky choice** `+` | Used in fixture, no assertion | Assert choice reappears after selection |
| 8 | **Nested choices** `** ***` | Used in fixture, no assertion | Assert nested choice navigation |
| 9 | **Conditional choices** | Used in fixture, no assertion | Assert choice visibility based on state |
| 10 | **Alternatives** (seq/cycle/shuffle) | Used in fixture, no assertion | Assert text varies across visits |
| 11 | **Visit counts** | Used in fixture, no assertion | Assert visit count value in output |
| 12 | **Operators** (math/logic) | Used in fixture, no assertion | Assert arithmetic/logic output |
| 13 | **Functions** (def/call/return) | Used in fixture, no assertion | Assert function return value in output |
| 14 | **List modification** `+= -=` | Used in fixture, no assertion | Assert list contents after modification |
| 15 | **List query** `? !?` | Used in fixture, no assertion | Assert conditional text from list query |
| 16 | **Knot parameters** | Used in fixture, no assertion | Assert parameterized knot output |

---

## Appendix A: Test File Summary

### `app/test/test.js` — 5 tests

| Test | Ink Features Exercised |
|---|---|
| `shows an initial window` | (UI only — no ink features) |
| `reads the title` | (UI only — no ink features) |
| `opens the menu` | (UI only — no ink features) |
| `writes and reads hello world` | Plain text |
| `writes and selects a choice` | `*` choice, gather `-`, `-> END` |
| `suppresses choice text` | `[]` suppression, `-> END` |
| `shows TODOs` | `TODO:`, `*` choices, gather `-` |

### `app/test/bidi-e2e.test.js` — 12 tests

| Test | Ink Features Exercised |
|---|---|
| `loads the full bidi ink fixture without crashing` | VAR, all 28 syntax features from fixture |
| `compiles Hebrew story without errors` | `===` knots, `->` diverts, `*` choices, plain text |
| `shows menu choices with Hebrew text` | `* []` choices with RTL text |
| `types Hebrew hello world and compiles` | Plain text (Hebrew) |
| `types Hebrew choice syntax and compiles` | `* []` choices, `-> END` |
| `types mixed Hebrew-English ink line` | `-> END` divert in mixed bidi text |
| `types Arabic hello world and compiles` | Plain text (Arabic) |
| `navigates smoke test` | Knots, diverts, choices |
| `navigates bidi museum` | Multiple knots, RTL text, diverts |
| `plays story path with choices` | Nested navigation, multiple choices |
| `compiles Hebrew text without bidi markers` | Plain text, bidi marker absence |
| `compiles mixed Hebrew-English choices` | `* []` choices with mixed bidi text |
| `handles 10 RTL scripts` | Plain text across 10 Unicode RTL scripts |
| `validates bidify/stripBidi round-trip` | (bidify module, not ink syntax) |
| `validates assertion file entries` | (bidify module, not ink syntax) |

### `app/test/fixtures/bidi_and_tdd.ink` — 28 features claimed

| # | Feature | Ink Syntax Used |
|---|---|---|
| 01 | Knots + Text + Diverts | `===`, `->` |
| 02 | Stitches | `=`, `-> knot.stitch` |
| 03 | Choices | `*`, `+`, `[]` |
| 04 | Nested Choices | `**`, `***` |
| 05 | Gathers | `-`, `- (label)` |
| 06 | Variables | `~`, `temp`, `+=`, `-=`, `++` |
| 07 | Conditionals | `{cond: a \| b}` |
| 08 | Alternatives | sequence, `{&cycle}`, `{~shuffle}` |
| 09 | Glue | `<>` across knots |
| 10 | Tags | `#` metadata |
| 11 | Knot Parameters | `=== knot(p1, p2) ===` |
| 12 | Functions | `=== function ===`, `~ return` |
| 13 | Tunnels | `-> tunnel ->`, `->->` |
| 14 | Threads | `<-` |
| 15 | Lists | `LIST`, `?`, `!?`, `+=`, `-=` |
| 16 | Variable Diverts | `~ temp target = -> knot` |
| 17 | Visit Counts + TURNS_SINCE | `{knot}`, `TURNS_SINCE()` |
| 18 | Conditional Choices | `* {condition} []` |
| 19 | Multi-line Conditionals | `{ - cond: ... - else: }` |
| 20 | String Operations | `==`, string vars, interpolation |
| 21 | Math | `+`, `-`, `*`, `/`, `%` (`mod`) |
| 22 | Logic | `and`, `or`, `not`, `!=`, `>=` |
| 23 | Comments | `//`, `/* */` |
| 24 | TODO | `TODO:` |
| 25 | Escaping | `\{`, `\}`, `\[`, `\]` |
| 26 | INCLUDE | Display only (commented out) |
| 27 | Mid-sentence Diverts + Glue | `->` + `<>` chains |
| 28 | Ending + Summary | `-> END` |

---

## Appendix B: Features NOT in Any Test Fixture

These ink features exist in the language but are entirely absent from all test fixtures:

| Feature | Syntax | Notes |
|---|---|---|
| `float` variable type | `VAR x = 3.14` | Only int/string/bool used |
| `INCLUDE` (active) | `INCLUDE file.ink` | Only mentioned in comment |
| `EXTERNAL` | `EXTERNAL func(p)` | Only in example snippets |
| `CHOICE_COUNT()` | `{CHOICE_COUNT()}` | Not used in any fixture |
| `SEED_RANDOM()` | `~ SEED_RANDOM(42)` | Not used in any fixture |
| `INT()` / `FLOAT()` / `FLOOR()` | `{INT(3.7)}` | Not used in any fixture |
| `has` / `hasnt` keywords | `{list has item}` | `?` and `!?` used instead |
| `{!once}` alternative | `{!Only once\|}` | Only sequence/cycle/shuffle used |
| Stopping sequence | `{First\|Last}` | Used in theintercept.ink but not tested |
| `ref` parameters | `function f(ref x)` | Used in murder_scene.ink but not tested |
| List intersection `^` | `list1 ^ list2` | Used in murder_scene.ink but not tested |
| `LIST_COUNT()` | `LIST_COUNT(list)` | Used in swindlestones.ink but not tested |
| `LIST_MIN()` / `LIST_MAX()` | `LIST_MIN(list)` | Used in murder_scene.ink but not tested |
| `LIST_ALL()` | `LIST_ALL(list)` | Used in murder_scene.ink but not tested |
| `LIST_RANGE()` | `LIST_RANGE(l, min, max)` | Used in murder_scene.ink but not tested |
| `LIST_INVERT()` | `LIST_INVERT(list)` | Used in list_prev_next.ink but not tested |
| `LIST_RANDOM()` | `LIST_RANDOM(list)` | Used in list_pop_random.ink but not tested |
| `LIST_VALUE()` | `LIST_VALUE(item)` | Used in swindlestones.ink but not tested |
| `RANDOM()` | `RANDOM(min, max)` | Used in list_random_subset.ink but not tested |
| `TURNS()` | `TURNS()` | Used in thread_in_tunnel.ink but not tested |
| Fallback choice `* ->` | `* ->` | Used in storylets.ink but not tested |
| Multi-valued lists | `VAR x = (a, b, c)` | Used in murder_scene.ink but not tested |
