# Ink Scripting Language — MCP Skill Reference

> **For MCP-capable LLMs**: This document teaches you the complete ink scripting language so you can write, debug, review, and play interactive fiction stories through the Inky MCP Server tools.

## What is Ink?

Ink is inkle's scripting language for writing interactive narrative. It compiles to JSON and runs in any runtime (JS, Java, C#, Rust). Ink powers games like *80 Days*, *Heaven's Vault*, and *Sorcery!*.

## Quick Start — Minimal Story

```ink
Hello, world!
* [Enter the cave] -> cave
* [Walk away] -> ending

=== cave ===
It's dark in here.
-> ending

=== ending ===
The end.
-> END
```

## Core Syntax

### Text Output

Plain text on a line is output directly:

```ink
This line is displayed to the reader.
So is this one.
```

### Choices

Choices use `*` (once-only) or `+` (sticky/repeatable):

```ink
* [Pick up the sword] You grab the rusty blade.
* [Leave it] You walk past.
+ [Look around] You examine the room. -> look_again
```

Square brackets `[...]` mark text shown only in the choice, not after selection:

```ink
* "Hello," I [said] said to her. "How are you?"
```

This shows `"Hello," I said to her. "How are you?"` after selection but `"Hello," I said` as the choice text.

### Knots (===)

Major sections, like chapters:

```ink
=== train_station ===
You arrive at the station.
* [Board the train] -> on_train
* [Wait on the platform] -> waiting

=== on_train ===
The train pulls away.
-> END
```

### Stitches (=)

Sub-sections within knots:

```ink
=== market ===
= entrance
The market bustles with life.
* [Visit the baker] -> baker
* [Visit the smith] -> smith

= baker
Fresh bread fills the air.
-> entrance

= smith
Sparks fly from the anvil.
-> entrance
```

### Diverts (->)

Navigate between knots/stitches:

```ink
-> knot_name
-> knot_name.stitch_name
-> END          // End the story
-> DONE         // End current flow (multi-flow)
```

### Glue (<>)

Join text across lines without a line break:

```ink
I looked at Monsieur Fogg <>
and I could see he was <>
irritated.
```

Output: `I looked at Monsieur Fogg and I could see he was irritated.`

## Variables and Logic

### Variable Declaration

```ink
VAR health = 100
VAR player_name = "Arthor"
VAR has_key = false
CONST MAX_HEALTH = 100
```

### Temporary Variables

```ink
~ temp x = health * 2
```

### Math and Assignment

```ink
~ health = health - 10
~ gold += 50
~ has_key = true
```

### Conditionals

Inline:
```ink
{health > 50: You feel strong.|You feel weak.}
```

Multi-line:
```ink
{
    - health > 80:
        You're in great shape!
    - health > 40:
        You've seen better days.
    - else:
        You're barely standing.
}
```

### Conditional Choices

```ink
* {has_key} [Unlock the door] -> unlocked_room
* {not has_key} [Try the door] It's locked.
+ [Go back] -> hallway
```

## Advanced Features

### Functions

```ink
=== function say_hello(name) ===
Hello, {name}!

=== function add(x, y) ===
~ return x + y
```

### Tunnels

Call a knot and return to where you left off:

```ink
-> flashback ->
We continued on our way.

=== flashback ===
I remembered the old days...
->->   // Return from tunnel
```

### Threads

Run content in parallel:

```ink
<- background_noise

=== background_noise ===
The wind howled outside.
-> DONE
```

### Lists

Enumerated types with state:

```ink
LIST mood = happy, sad, angry, confused
VAR current_mood = happy

~ current_mood = sad
{current_mood == sad: You look glum.}
```

### Sequences and Cycles

```ink
// Sequence (stopping): plays each once, then repeats last
{&First time|Second time|Every other time}

// Cycle: loops
{~Monday|Tuesday|Wednesday|Thursday|Friday}

// Shuffle: random each time
{!heads|tails}

// Once-only: plays each once, then nothing
{First visit|Second visit|Third and final}
```

### Tags

Metadata attached to lines:

```ink
# author: inkle
# theme: dark

=== intro ===
You enter the cave. # audio: drip.mp3
* [Light a torch] # mood: warm
```

### External Functions

Bind host functions:

```ink
EXTERNAL multiply(x, y)

~ temp result = multiply(3, 4)
The answer is {result}.
```

### Includes

Split story across files:

```ink
INCLUDE characters.ink
INCLUDE chapter_one.ink
INCLUDE utils.ink
```

## RTL Language Support (Bidi)

For Hebrew, Arabic, Persian content, use the bidify tools:

```ink
// Hebrew story text gets bidi markers for correct display
שלום עולם!
* [ברוך הבא] -> welcome
```

The MCP server provides `bidify`, `strip_bidi`, and `bidify_json` tools for proper RTL rendering without modifying the source.

## Common Patterns

### Hub Pattern (Revisitable Location)

```ink
=== tavern ===
You're in the tavern.
+ [Talk to barkeep] -> barkeep -> tavern
+ [Check the notice board] -> notices -> tavern
* [Leave] -> street

=== barkeep ===
"What'll it be?"
->->

=== notices ===
You read the posted jobs.
->->
```

### Inventory Pattern

```ink
LIST inventory = (nothing), sword, shield, potion

=== check_inventory ===
You have: <>
{inventory ? sword: a sword, }
{inventory ? shield: a shield, }
{inventory ? potion: a potion, }
{inventory == nothing: nothing at all.}
-> DONE
```

### Score/State Tracking

```ink
VAR trust = 0
VAR knowledge = 0

=== conversation ===
She asks about your journey.
* [Tell the truth] ~ trust += 2
  She nods approvingly.
* [Lie about it] ~ knowledge += 1
  She seems skeptical.
- -> next_scene
```

## MCP Tools Reference

### Ink Compilation & Playback

| Tool | Description |
|------|-------------|
| `compile_ink` | Compile ink source to JSON |
| `start_story` | Compile + start interactive session |
| `start_story_json` | Start session from pre-compiled JSON |
| `choose` | Make a choice in active session |
| `continue_story` | Continue reading story text |
| `get_variable` / `set_variable` | Read/write story variables |
| `save_state` / `load_state` | Save/restore story state |
| `reset_story` | Restart story from beginning |
| `evaluate_function` | Call an ink function |
| `get_global_tags` | Get story metadata tags |
| `list_sessions` / `end_session` | Session management |

### Script Editing

| Tool | Description |
|------|-------------|
| `parse_ink` | Parse ink into sections (knots, stitches, functions) |
| `get_section` | Get a specific knot/stitch by name |
| `replace_section` | Replace a section's content |
| `insert_section` | Insert new section after existing one |
| `rename_section` | Rename knot/stitch + update all diverts |
| `ink_stats` | Get script statistics and dead-end analysis |

### Debugging

| Tool | Description |
|------|-------------|
| `start_debug` | Begin debugging a story session |
| `add_breakpoint` | Set breakpoint on knot/pattern/variable change |
| `remove_breakpoint` | Remove a breakpoint |
| `debug_step` | Step to next output (checking breakpoints) |
| `debug_continue` | Continue until breakpoint hit or story ends |
| `add_watch` / `remove_watch` | Watch variable changes |
| `debug_inspect` | Inspect current debug state |
| `debug_trace` | Get execution trace log |

### RTL/Bidi

| Tool | Description |
|------|-------------|
| `bidify` | Add Unicode bidi markers for RTL display |
| `strip_bidi` | Remove bidi markers |
| `bidify_json` | Bidify compiled story JSON |

### LLM Services

| Tool | Description |
|------|-------------|
| `list_services` | List available LLM providers |
| `connect_service` | Connect to an LLM service |
| `llm_chat` | Send chat message to connected LLM |
| `generate_ink` | Generate ink code from description |
| `review_ink` | Review ink code for issues |
| `translate_ink_hebrew` | Translate ink story to Hebrew |
| `generate_compile_play` | Full pipeline: generate → compile → play |

### Collaboration

| Tool | Description |
|------|-------------|
| `collab_status` | List active collaboration documents |
| `collab_info` | Get document collaboration details |

## Writing Tips for LLMs

1. **Always end with `-> END` or `-> DONE`** — stories must terminate
2. **Every knot needs a divert out** — avoid dead ends
3. **Use `+` for hub choices** that players revisit, `*` for one-time choices
4. **Test by compiling** — use `compile_ink` before `start_story`
5. **Use tunnels `-> knot ->` for reusable scenes** that return to caller
6. **Validate with `ink_stats`** to find unreferenced knots and missing targets
7. **Debug with `start_debug` + `add_watch`** to trace variable changes
8. **For Hebrew/Arabic**: write plain text, apply `bidify` at display time only

## Example: Complete Mini-Game

```ink
VAR gold = 10
VAR has_map = false

-> market

=== market ===
The bazaar stretches before you. You have {gold} gold.
+ [Browse the stalls] -> stalls
+ {has_map} [Follow the map] -> treasure
* [Leave the city] -> ending

=== stalls ===
= gems
A merchant shows sparkling gems.
* {gold >= 5} [Buy a gem (5g)]
  ~ gold -= 5
  You pocket a ruby.
  -> market
* [Move on] -> maps

= maps
An old woman sells tattered maps.
* {gold >= 3} [Buy a treasure map (3g)]
  ~ gold -= 3
  ~ has_map = true
  She winks as she hands it over.
  -> market
* [Move on] -> market

=== treasure ===
Following the map, you find a hidden chest!
~ gold += 50
You found 50 gold! You now have {gold} gold.
-> ending

=== ending ===
Your adventure in the bazaar is over. <>
{gold > 20: You leave wealthy!|You leave with empty pockets.}
-> END
```
