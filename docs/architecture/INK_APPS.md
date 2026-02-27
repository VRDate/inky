# Ink Narrative Scripting Language - Open Source Resources

A curated list of open source projects, tools, and example stories for inkle's
[ink](https://www.inklestudios.com/ink/) narrative scripting language. Focused on
projects with actual `.ink` source files useful as test fixtures for ink runtime ports.

---

## 1. Official Ink Resources

| Project | Description | URL |
|---------|-------------|-----|
| **ink** | The official ink compiler and C# runtime. The core narrative engine written in C#. Contains test `.ink` files in the source tree. | https://github.com/inkle/ink |
| **inky** | The official desktop editor/IDE for ink. Includes a `test.ink` file at `app/renderer/test.ink`. Lets you play stories as you write them, with error/warning browser and JSON export. | https://github.com/inkle/inky |
| **ink-unity-integration** | Official Unity package for integrating ink. Includes the full ink engine, auto-compilation of `.ink` to JSON, an Ink Player debugging window, and demo projects. | https://github.com/inkle/ink-unity-integration |
| **ink-library** | Official collection of ink samples, stories, snippets, tools, and a catalogue of community projects. MIT licensed sample content. **Best single starting point for `.ink` files.** | https://github.com/inkle/ink-library |
| **Writing with Ink** | The official ink language documentation and tutorial. | https://github.com/inkle/ink/blob/master/Documentation/WritingWithInk.md |
| **Running Your Ink** | Official guide on integrating ink into your game. | https://github.com/inkle/ink/blob/master/Documentation/RunningYourInk.md |
| **inkle Studios Ink Page** | Official ink landing page with downloads and links. | https://www.inklestudios.com/ink/ |

---

## 2. Community Open Source Ink Scripts

These repositories contain actual `.ink` story files that can be used for testing.

| Project | Description | URL |
|---------|-------------|-----|
| **ink-library (Stories)** | The official community collection includes entire playable stories written in ink, plus reusable snippet functions. All contributed content is MIT licensed. | https://github.com/inkle/ink-library |
| **ink-proof** | Conformance testing tool for ink compilers and runtimes. Contains structured `.ink` and `.json` test cases, each with source, input, and expected transcript output. **Best resource for runtime test fixtures.** | https://github.com/chromy/ink-proof |
| **ink_roguelike** | A narrative mini-roguelike written entirely in ink. Features level generation, hit detection, and permadeath, all within `.ink` files. | https://github.com/nbush/ink_roguelike |
| **The Intercept (ink source)** | The full `.ink` story script from inkle's demo game. A real-world example of how inkle structures their ink files. | https://github.com/inkle/the-intercept/blob/master/Assets/Ink/TheIntercept.ink |
| **Ink-Games** | A collection of games and experiments created with ink and Binksi (bitsy + ink) by manonamora. Interactive fiction in English and French. | https://github.com/manonamora/Ink-Games |
| **Unofficial Ink Cookbook** | An open textbook with numerous ink examples and tutorials across 12+ chapters. Contains example `.ink` files for each concept taught. | https://github.com/videlais/Unofficial-Ink-Cookbook |
| **Ink-Tester** | A testing framework for ink stories. Uses ink's parser and runtime to validate story content. | https://github.com/wildwinter/Ink-Tester |

---

## 3. Unity + Ink Projects

| Project | Description | URL |
|---------|-------------|-----|
| **ink-unity-integration** | The official Unity plugin by inkle. Includes C# runtime API, Ink Player debug window, auto-compilation, custom inspectors, and demo scenes. Supports Unity 2020 LTS+. MIT licensed. | https://github.com/inkle/ink-unity-integration |
| **The Intercept** | A small demo game by inkle built with ink and Unity. Built in a couple of days for a game jam. Shows real-world ink file structuring and Unity plugin usage. Full source available. | https://github.com/inkle/the-intercept |
| **The Intercept (AccessKit fork)** | A fork of The Intercept demonstrating screen reader accessibility integration in Unity with ink. | https://github.com/AccessKit/the-intercept |

---

## 4. Ink Tools and Utilities

### Editors and IDEs

| Project | Description | URL |
|---------|-------------|-----|
| **Inky** | The official ink editor by inkle. Electron-based IDE with live preview, error browser, JSON export, and web export. | https://github.com/inkle/inky |
| **vscode-ink** | Ink language support for Visual Studio Code. Includes syntax highlighting and an experimental Language Server Protocol (LSP) integration. | https://github.com/ephread/vscode-ink |
| **Borogove** | Online tool to write, play, and share interactive fiction entirely in the browser. Supports ink among other IF formats. | https://borogove.app |
| **Calico** | A web engine/framework for interactive fiction, designed for use with ink. MIT licensed. No programming experience required. | https://github.com/elliotherriman/calico |
| **binksi** | A bitsy-like tool for making lo-fi graphical adventure games, powered by ink for narration. Browser-based editor with single-HTML export. | https://github.com/smwhr/binksi |

### Runtime Ports and Implementations

| Project | Description | URL |
|---------|-------------|-----|
| **inkjs** | The official JavaScript port of ink (runtime + compiler). Zero dependencies, works in all browsers and Node.js. TypeScript support included. | https://github.com/y-lohse/inkjs |
| **inkgd** | Pure GDScript implementation of the ink runtime for Godot Engine. Feature-complete, passes the test suite. Godot 4.2+ support on the `godot4` branch. | https://github.com/ephread/inkgd |
| **inkcpp** | Ink runtime in C++ with a JSON-to-binary compiler. No external dependencies for the runtime. Supports C++, C, UE Blueprints, and Python bindings. | https://github.com/JBenda/inkcpp |
| **Inkpot** | Ink integration for Unreal Engine 5.4+ by The Chinese Room. Contains a full C++ port of the ink engine runtime (InkPlusPlus module). Includes 180 unit tests. Blueprint support. | https://github.com/The-Chinese-Room/Inkpot |
| **blade-ink-java** | Ink runtime implementation in Java. Fully compatible with reference version. Available on Maven Central. | https://github.com/bladecoder/blade-ink-java |
| **blade-ink-rs** | Ink runtime implementation in Rust by the same team as blade-ink-java. Fully compatible with the reference version. Includes a terminal story runner. | https://github.com/bladecoder/blade-ink-rs |
| **inkrs** | An independent port/rewrite of ink in Rust. Parses story `.json` files into typed structures. | https://github.com/clembu/inkrs |
| **Tinta** | Ink runtime in Lua, targeting Playdate, Love2D, and Picotron. Runtime only (requires external compiler for `.ink` to JSON). Supports async continuation. | https://github.com/smwhr/tinta |
| **Narrator** | An ink parser and runtime implementation in Lua. Also works with the Defold game engine. | Listed in [ink-library](https://github.com/inkle/ink-library) |

### Testing and Deployment

| Project | Description | URL |
|---------|-------------|-----|
| **ink-proof** | Conformance testing for ink compilers and runtimes. Structured test cases with `.ink` source, input, and expected output. Supports pluggable compiler/runtime drivers. Results published as a webpage. | https://github.com/chromy/ink-proof |
| **Ink-Tester** | A testing framework for validating stories written in ink. Uses ink's own parser and runtime internally. | https://github.com/wildwinter/Ink-Tester |
| **PalimpsestNW** | Template for deploying an ink game as a standalone desktop app using NW.js and inkjs. | https://github.com/ladyisak/PalimpsestNW |
| **blade-ink-template** | A LibGDX + Java template for creating multi-platform interactive fiction games (Android, Desktop, iOS) with ink. | https://github.com/bladecoder/blade-ink-template |
| **calico-starter** | A starter project template for Calico and inkjs web-based ink games. | https://github.com/CMCRobotics/calico-starter |
| **InkpotDemo** | A small Unreal Engine demo project showing how to use Inkpot. | https://github.com/The-Chinese-Room/InkpotDemo |

---

## 5. Ink Game Examples

| Project | Description | URL |
|---------|-------------|-----|
| **The Intercept** | Official demo game by inkle. A complete Unity + ink game built in two days for a game jam. Full source including `.ink` story files. MIT licensed. | https://github.com/inkle/the-intercept |
| **ink_roguelike** | A narrative mini-roguelike written completely in ink. Demonstrates level generation, hit detection, and permadeath entirely within `.ink` scripting. | https://github.com/nbush/ink_roguelike |
| **Ink-Games** | Collection of experimental interactive fiction games built with ink and Binksi by manonamora. | https://github.com/manonamora/Ink-Games |
| **binksi** | A tool (and games made with it) combining bipsi graphical adventure-making with ink-driven narration. Exports single-HTML playable games. | https://github.com/smwhr/binksi |

### Notable Commercial/Published Games Using Ink (source not fully available)

- **80 Days** by inkle - BAFTA-winning narrative game
- **Heaven's Vault** by inkle - Archaeological adventure game
- **Sorcery!** by inkle - Four-part adventure game series
- **Neocab** - Cyberpunk emotional survival game
- **Sable** - Open-world exploration game
- **Wayward Strand** - Interactive story set on a flying hospital

---

## Key Repositories for Test Fixtures

If you are porting the ink runtime and need `.ink` files for testing, these are the
highest-priority repositories:

1. **[chromy/ink-proof](https://github.com/chromy/ink-proof)** - Purpose-built conformance
   test suite with `.ink` source files, inputs, and expected outputs. This is the gold
   standard for testing ink compiler/runtime implementations.

2. **[inkle/ink](https://github.com/inkle/ink)** - The official repo contains test files
   used during development of the reference C# implementation.

3. **[inkle/ink-library](https://github.com/inkle/ink-library)** - Community-contributed
   stories and snippets. Good for integration testing with real-world content.

4. **[inkle/the-intercept](https://github.com/inkle/the-intercept)** - A complete,
   non-trivial `.ink` story file from a real game. Good for end-to-end testing.

5. **[The-Chinese-Room/Inkpot](https://github.com/The-Chinese-Room/Inkpot)** - Contains
   180 unit tests ported alongside the C++ runtime. Useful as a reference for what tests
   a port should pass.

6. **[nbush/ink_roguelike](https://github.com/nbush/ink_roguelike)** - An entire game
   written purely in ink, useful for stress-testing advanced language features.
