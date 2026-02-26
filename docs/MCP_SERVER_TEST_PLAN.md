# Inky MCP Server — Comprehensive Test Plan

> **71 tools across 12 engines** — Ktor + GraalJS + inkjs + JLama + iCal4j + ez-vcard + Keycloak + Sardine + Yjs

## 1. Current Test Coverage

### Existing Tests

| Location | Type | Count | Covers |
|----------|------|-------|--------|
| `app/test/bidify.test.js` | Unit | 29 | `bidify()`, `stripBidi()`, `bidifyJson()` — 8 scripts |
| `app/test/test.js` | E2E (Playwright) | 7 | Electron editor: window, title, menu, hello world, choices, TODO |
| `app/test/bidi-e2e.test.js` | E2E (Playwright) | — | Bidi rendering in Electron player |
| `app/test/incremental-e2e.test.js` | E2E (Playwright) | — | Incremental compilation |
| `docs/E2E_TEST_PLAN.md` | Plan | 80 | Electron editor E2E (10 implemented / 70 not) |

### Gap: MCP Server (0 tests)

**No tests exist for the MCP server (71 tools, 12 engines).** This plan covers that gap.

---

## 2. Test Infrastructure

### Recommended Stack

| Component | Technology | Notes |
|-----------|------------|-------|
| Framework | JUnit 5 | Kotlin/JVM, already in Gradle ecosystem |
| HTTP client | Ktor test client | `testApplication {}` for route testing |
| SSE testing | `ktor-client-sse` | MCP SSE protocol verification |
| Assertions | kotlin.test + assertk | Fluent assertions |
| Mocking | mockk | Kotlin-native mocking |
| Test data | `src/test/resources/` | .ink, .ics, .vcf fixtures |

### Test Directory Structure

```
mcp-server/src/test/kotlin/ink/mcp/
├── InkEngineTest.kt           # Ink Core (17 tools)
├── InkDebugEngineTest.kt      # Debug (8 tools)
├── InkEditEngineTest.kt       # Edit (6 tools)
├── InkMdEngineTest.kt         # Ink+Markdown (3 tools)
├── Ink2PumlEngineTest.kt      # PlantUML+TOC (5 tools)
├── LlmEngineTest.kt           # LLM Models (8 tools)
├── LlmServiceConfigTest.kt   # Services (2 tools)
├── ColabEngineTest.kt         # Collab (2 tools)
├── InkCalendarEngineTest.kt   # Calendar (4 tools)
├── InkVCardEngineTest.kt      # Principals (4 tools)
├── InkAuthEngineTest.kt       # Auth (2 tools)
├── InkWebDavEngineTest.kt     # WebDAV+Backup (10 tools)
├── McpToolsTest.kt            # Tool dispatch + integration
├── McpRouterTest.kt           # SSE/REST route tests
└── resources/
    ├── hello.ink               # Minimal ink
    ├── choices.ink             # Story with choices
    ├── variables.ink           # Variables + functions
    ├── hebrew.ink              # RTL content
    ├── complex.ink             # Threads, tunnels, lists
    ├── sample.ics              # Calendar fixture
    └── sample.vcf              # vCard fixture
```

---

## 3. Ink Core Engine Tests (17 tools)

### TC-IC-001: compile_ink — Valid Source
- **Priority**: P0
- **Tool**: `compile_ink`
- **Input**: `{"source": "Hello World!\n-> END"}`
- **Expected**: `{success: true, json: "..."}`; JSON is valid ink JSON

### TC-IC-002: compile_ink — Invalid Source
- **Priority**: P0
- **Tool**: `compile_ink`
- **Input**: `{"source": "-> nonexistent"}`
- **Expected**: `{success: false, errors: [...]}`

### TC-IC-003: start_story — Basic Playback
- **Priority**: P0
- **Tool**: `start_story`
- **Input**: `{"source": "Hello!\n* [Option A] A\n* [Option B] B\n-> END"}`
- **Expected**: `{session_id: "...", text: "Hello!\n", choices: [{index: 0, text: "Option A"}, ...]}`

### TC-IC-004: start_story_json — Pre-compiled JSON
- **Priority**: P0
- **Tool**: `start_story_json`
- **Input**: Pre-compiled JSON from `compile_ink`
- **Expected**: Valid session with text and choices

### TC-IC-005: choose — Make Choice
- **Priority**: P0
- **Tool**: `choose`
- **Input**: `{"session_id": "...", "choice_index": 0}`
- **Precondition**: Active session with choices
- **Expected**: New text + updated choices or END

### TC-IC-006: continue_story — Multi-Segment
- **Priority**: P1
- **Tool**: `continue_story`
- **Input**: `{"session_id": "..."}`
- **Precondition**: Story with multiple text segments
- **Expected**: Next text segment returned

### TC-IC-007: get_variable
- **Priority**: P1
- **Tool**: `get_variable`
- **Input**: `{"session_id": "...", "name": "health"}`
- **Precondition**: Story with `VAR health = 100`
- **Expected**: `{name: "health", value: 100}`

### TC-IC-008: set_variable
- **Priority**: P1
- **Tool**: `set_variable`
- **Input**: `{"session_id": "...", "name": "health", "value": 50}`
- **Expected**: `{ok: true}`; subsequent `get_variable` returns 50

### TC-IC-009: save_state / load_state Round-Trip
- **Priority**: P1
- **Tools**: `save_state`, `load_state`
- **Steps**: Start story → make choice → save → reset → load
- **Expected**: After load, story position matches saved state

### TC-IC-010: reset_story
- **Priority**: P1
- **Tool**: `reset_story`
- **Input**: `{"session_id": "..."}`
- **Precondition**: Story with progress
- **Expected**: Story returns to initial state

### TC-IC-011: evaluate_function
- **Priority**: P1
- **Tool**: `evaluate_function`
- **Input**: `{"session_id": "...", "function_name": "add", "arguments": [3, 4]}`
- **Precondition**: Story with `=== function add(x, y) === ~ return x + y`
- **Expected**: `{result: 7}`

### TC-IC-012: get_global_tags
- **Priority**: P2
- **Tool**: `get_global_tags`
- **Input**: `{"session_id": "..."}`
- **Precondition**: Story with `# author: test\n# theme: dark`
- **Expected**: `{tags: ["author: test", "theme: dark"]}`

### TC-IC-013: list_sessions
- **Priority**: P1
- **Tool**: `list_sessions`
- **Steps**: Start 2 sessions, then list
- **Expected**: Returns 2 session entries with IDs

### TC-IC-014: end_session
- **Priority**: P1
- **Tool**: `end_session`
- **Input**: `{"session_id": "..."}`
- **Expected**: Session removed; subsequent calls with that ID fail

### TC-IC-015: bidify
- **Priority**: P1
- **Tool**: `bidify`
- **Input**: `{"text": "שלום World"}`
- **Expected**: Text with RLI/LRI/PDI Unicode markers

### TC-IC-016: strip_bidi
- **Priority**: P1
- **Tool**: `strip_bidi`
- **Input**: `{"text": "⁧שלום⁩ ⁦World⁩"}`
- **Expected**: `"שלום World"` (markers removed)

### TC-IC-017: bidify_json
- **Priority**: P1
- **Tool**: `bidify_json`
- **Input**: Compiled JSON containing Hebrew text
- **Expected**: JSON with bidi markers in `^` prefixed strings

---

## 4. Debug Engine Tests (8 tools)

### TC-DB-001: start_debug
- **Priority**: P1
- **Tool**: `start_debug`
- **Input**: `{"session_id": "..."}`
- **Expected**: Debug session created with initial state

### TC-DB-002: add_breakpoint — Knot Name
- **Priority**: P1
- **Tool**: `add_breakpoint`
- **Input**: `{"session_id": "...", "type": "knot", "target": "cave"}`
- **Expected**: Breakpoint added; `debug_continue` stops at cave knot

### TC-DB-003: add_breakpoint — Pattern Match
- **Priority**: P2
- **Tool**: `add_breakpoint`
- **Input**: `{"session_id": "...", "type": "pattern", "target": "treasure"}`
- **Expected**: Breakpoint triggers when text contains "treasure"

### TC-DB-004: add_breakpoint — Variable Change
- **Priority**: P1
- **Tool**: `add_breakpoint`
- **Input**: `{"session_id": "...", "type": "variable", "target": "health"}`
- **Expected**: Breakpoint triggers when `health` variable changes

### TC-DB-005: remove_breakpoint
- **Priority**: P2
- **Tool**: `remove_breakpoint`
- **Input**: Breakpoint ID from add_breakpoint
- **Expected**: Breakpoint no longer triggers

### TC-DB-006: debug_step
- **Priority**: P1
- **Tool**: `debug_step`
- **Expected**: Advances to next output, returns current state

### TC-DB-007: debug_continue
- **Priority**: P1
- **Tool**: `debug_continue`
- **Expected**: Runs until breakpoint hit or story ends

### TC-DB-008: debug_inspect
- **Priority**: P1
- **Tool**: `debug_inspect`
- **Expected**: Returns current knot, choices, variables, call stack

### TC-DB-009: debug_trace
- **Priority**: P2
- **Tool**: `debug_trace`
- **Expected**: Returns ordered list of executed knots/stitches

### TC-DB-010: add_watch
- **Priority**: P1
- **Tool**: `add_watch`
- **Input**: `{"session_id": "...", "variable": "gold"}`
- **Expected**: Watch added; debug output includes gold value changes

---

## 5. Edit Engine Tests (6 tools)

### TC-ED-001: parse_ink — Basic Structure
- **Priority**: P0
- **Tool**: `parse_ink`
- **Input**: Story with 3 knots, 2 stitches
- **Expected**: Section tree with correct hierarchy

### TC-ED-002: get_section
- **Priority**: P1
- **Tool**: `get_section`
- **Input**: `{"source": "...", "name": "cave"}`
- **Expected**: Section content for the `cave` knot

### TC-ED-003: replace_section
- **Priority**: P1
- **Tool**: `replace_section`
- **Input**: Replace cave content with new text
- **Expected**: Updated source with new cave content, rest unchanged

### TC-ED-004: insert_section
- **Priority**: P1
- **Tool**: `insert_section`
- **Input**: Insert new knot after `cave`
- **Expected**: New knot appears after cave in source

### TC-ED-005: rename_section — Diverts Updated
- **Priority**: P1
- **Tool**: `rename_section`
- **Input**: Rename `cave` to `dark_cave`
- **Expected**: All `-> cave` diverts updated to `-> dark_cave`

### TC-ED-006: ink_stats
- **Priority**: P1
- **Tool**: `ink_stats`
- **Input**: Story with unreferenced knot + missing target
- **Expected**: Stats include dead-end warnings, knot/stitch/word counts

---

## 6. Ink+Markdown Engine Tests (3 tools)

### TC-MD-001: parse_ink_md
- **Priority**: P1
- **Tool**: `parse_ink_md`
- **Input**: Markdown with ` ```ink ` code blocks
- **Expected**: Extracted ink blocks with positions

### TC-MD-002: render_ink_md
- **Priority**: P1
- **Tool**: `render_ink_md`
- **Expected**: Combined markdown + playable ink output

### TC-MD-003: compile_ink_md
- **Priority**: P1
- **Tool**: `compile_ink_md`
- **Input**: Markdown doc with embedded ink
- **Expected**: Compiled ink JSON from extracted blocks

---

## 7. PlantUML + TOC Engine Tests (5 tools)

### TC-PU-001: ink2puml
- **Priority**: P1
- **Tool**: `ink2puml`
- **Input**: Story with knots and choices
- **Expected**: Valid PlantUML source with `@startuml`/`@enduml`

### TC-PU-002: ink2svg
- **Priority**: P1
- **Tool**: `ink2svg`
- **Input**: Same story
- **Expected**: SVG string (starts with `<svg`)

### TC-PU-003: puml2svg
- **Priority**: P1
- **Tool**: `puml2svg`
- **Input**: PlantUML source from ink2puml
- **Expected**: SVG rendering of the diagram

### TC-PU-004: ink_toc
- **Priority**: P1
- **Tool**: `ink_toc`
- **Input**: Story with nested knots/stitches
- **Expected**: Hierarchical TOC array

### TC-PU-005: ink_toc_puml
- **Priority**: P2
- **Tool**: `ink_toc_puml`
- **Input**: Same story
- **Expected**: PlantUML mindmap or tree of TOC

---

## 8. LLM Models Engine Tests (8 tools)

> These tests require JLama or are mocked in unit tests.

### TC-LM-001: list_models
- **Priority**: P1
- **Tool**: `list_models`
- **Input**: `{"vram_gb": 8}`
- **Expected**: Array of model objects with id, size, quant info

### TC-LM-002: load_model (mocked)
- **Priority**: P1
- **Tool**: `load_model`
- **Input**: `{"model_id": "test-model"}`
- **Expected**: `{ok: true}` or appropriate error

### TC-LM-003: model_info
- **Priority**: P2
- **Tool**: `model_info`
- **Expected**: Current model name, parameters, quant level

### TC-LM-004: llm_chat
- **Priority**: P1
- **Tool**: `llm_chat`
- **Input**: `{"message": "Hello"}`
- **Precondition**: Model loaded
- **Expected**: Non-empty response text

### TC-LM-005: generate_ink
- **Priority**: P1
- **Tool**: `generate_ink`
- **Input**: `{"prompt": "mystery in a cave"}`
- **Expected**: Valid ink source code

### TC-LM-006: review_ink
- **Priority**: P2
- **Tool**: `review_ink`
- **Input**: Ink source with intentional issues
- **Expected**: Review feedback with suggestions

### TC-LM-007: translate_ink_hebrew
- **Priority**: P1
- **Tool**: `translate_ink_hebrew`
- **Input**: English ink source
- **Expected**: Hebrew translation preserving ink syntax

### TC-LM-008: generate_compile_play
- **Priority**: P1
- **Tool**: `generate_compile_play`
- **Input**: `{"prompt": "short mystery"}`
- **Expected**: Session with text + choices from generated story

---

## 9. Services Tests (2 tools)

### TC-SV-001: list_services
- **Priority**: P1
- **Tool**: `list_services`
- **Expected**: Array of 11 provider configs

### TC-SV-002: connect_service
- **Priority**: P2
- **Tool**: `connect_service`
- **Input**: Service name + API key
- **Expected**: `{ok: true, provider: "..."}`

---

## 10. Collaboration Engine Tests (2 tools)

### TC-CL-001: collab_status
- **Priority**: P1
- **Tool**: `collab_status`
- **Expected**: List of active Yjs documents (empty if none)

### TC-CL-002: collab_info
- **Priority**: P1
- **Tool**: `collab_info`
- **Input**: Document ID
- **Expected**: Client count, awareness states, doc metadata

---

## 11. Calendar Engine Tests (4 tools)

### TC-CA-001: create_event
- **Priority**: P0
- **Tool**: `create_event`
- **Input**: `{"calendar_id": "test", "summary": "Quest start", "dtStart": "2026-03-01T10:00:00Z", "dtEnd": "2026-03-01T11:00:00Z", "category": "quest"}`
- **Expected**: `{ok: true, event_uid: "...", summary: "Quest start"}`

### TC-CA-002: list_events
- **Priority**: P0
- **Tool**: `list_events`
- **Input**: `{"calendar_id": "test"}`
- **Precondition**: At least 1 event created
- **Expected**: Array with event objects

### TC-CA-003: list_events — Date Range Filter
- **Priority**: P1
- **Tool**: `list_events`
- **Input**: `{"calendar_id": "test", "from": "2026-03-01", "to": "2026-03-02"}`
- **Expected**: Only events within the range

### TC-CA-004: export_ics
- **Priority**: P1
- **Tool**: `export_ics`
- **Input**: `{"calendar_id": "test"}`
- **Expected**: Valid iCalendar string starting with `BEGIN:VCALENDAR`

### TC-CA-005: import_ics
- **Priority**: P1
- **Tool**: `import_ics`
- **Input**: Valid .ics content
- **Expected**: Events imported into calendar; `list_events` returns them

### TC-CA-006: import_ics — Round-Trip
- **Priority**: P1
- **Steps**: Create event → export ICS → import into new calendar → list
- **Expected**: Event data preserved through export/import

---

## 12. Principals Engine Tests (4 tools)

### TC-PR-001: create_principal — Human User
- **Priority**: P0
- **Tool**: `create_principal`
- **Input**: `{"id": "alice", "name": "Alice Smith", "email": "alice@example.com", "type": "human"}`
- **Expected**: `{vcard: "...", jcard: [...], folder_path: "example.com/alice"}`

### TC-PR-002: create_principal — LLM Model
- **Priority**: P0
- **Tool**: `create_principal`
- **Input**: `{"id": "claude", "name": "Claude", "type": "llm"}`
- **Expected**: Principal with LLM credentials, model.vcf created

### TC-PR-003: list_principals
- **Priority**: P1
- **Tool**: `list_principals`
- **Precondition**: 2+ principals created
- **Expected**: Array with all registered principals

### TC-PR-004: get_principal
- **Priority**: P1
- **Tool**: `get_principal`
- **Input**: `{"id": "alice"}`
- **Expected**: Full principal details with vCard, jCard, folder info

### TC-PR-005: delete_principal
- **Priority**: P1
- **Tool**: `delete_principal`
- **Input**: `{"id": "alice"}`
- **Expected**: `{ok: true}`; subsequent `get_principal` returns not found

---

## 13. Auth Engine Tests (2 tools)

### TC-AU-001: auth_status — Auth Disabled
- **Priority**: P0
- **Tool**: `auth_status`
- **Precondition**: No `KEYCLOAK_REALM_URL` env var
- **Expected**: `{enabled: false, message: "Auth disabled"}`

### TC-AU-002: auth_status — Auth Enabled
- **Priority**: P1
- **Tool**: `auth_status`
- **Precondition**: Keycloak env vars configured
- **Expected**: `{enabled: true, realm_url: "...", client_id: "..."}`

### TC-AU-003: create_llm_credential
- **Priority**: P0
- **Tool**: `create_llm_credential`
- **Input**: `{"model_name": "claude"}`
- **Expected**: `{model_name: "claude", token: "...", mcp_uri: "mcp://claude:token@host:port"}`

### TC-AU-004: Auth Backward Compatibility
- **Priority**: P0
- **Precondition**: Auth disabled (no Keycloak config)
- **Steps**: Access all routes without auth headers
- **Expected**: All routes accessible; no 401/403 errors

---

## 14. WebDAV + Backup Engine Tests (10 tools)

### TC-WD-001: webdav_list — Root
- **Priority**: P0
- **Tool**: `webdav_list`
- **Input**: `{"path": "example.com/alice/"}`
- **Precondition**: Principal alice with files
- **Expected**: Array of file entries with name, size, type

### TC-WD-002: webdav_put + webdav_get — Round-Trip
- **Priority**: P0
- **Tools**: `webdav_put`, `webdav_get`
- **Steps**: Put file → get file
- **Expected**: Retrieved content matches put content

### TC-WD-003: webdav_put — Allowed Extensions
- **Priority**: P1
- **Tool**: `webdav_put`
- **Input**: `.ink`, `.puml`, `.svg`, `.vcf`, `.ics`, `.json`, `.md`, `.txt`
- **Expected**: All allowed extensions succeed

### TC-WD-004: webdav_put — Rejected Extension
- **Priority**: P1
- **Tool**: `webdav_put`
- **Input**: `{"path": "example.com/alice/script.exe", "content": "..."}`
- **Expected**: Rejected with error

### TC-WD-005: webdav_delete
- **Priority**: P1
- **Tool**: `webdav_delete`
- **Input**: `{"path": "example.com/alice/temp.ink"}`
- **Expected**: File removed; subsequent `webdav_get` fails

### TC-WD-006: webdav_mkdir
- **Priority**: P1
- **Tool**: `webdav_mkdir`
- **Input**: `{"path": "example.com/alice/chapter2/"}`
- **Expected**: Directory created; `webdav_list` shows it

### TC-WD-007: webdav_sync (mocked remote)
- **Priority**: P2
- **Tool**: `webdav_sync`
- **Input**: Remote URL + local path
- **Expected**: Files synced from remote to local

### TC-WD-008: webdav_backup — Create Backup Set
- **Priority**: P0
- **Tool**: `webdav_backup`
- **Input**: `{"path": "example.com/alice/story"}`
- **Precondition**: story.ink + story.puml + story.svg exist
- **Expected**: 3 files backed up with ISO timestamp prefix

### TC-WD-009: webdav_list_backups
- **Priority**: P1
- **Tool**: `webdav_list_backups`
- **Input**: `{"path": "example.com/alice/story"}`
- **Precondition**: At least 1 backup exists
- **Expected**: Array of backup entries with timestamps

### TC-WD-010: webdav_restore
- **Priority**: P1
- **Tool**: `webdav_restore`
- **Input**: `{"path": "example.com/alice/story", "timestamp": "2026-02-26_14-30-00.000000000"}`
- **Expected**: Master files restored from backup

### TC-WD-011: webdav_working_copy
- **Priority**: P0
- **Tool**: `webdav_working_copy`
- **Input**: `{"origin": "example.com/alice/", "model_id": "claude"}`
- **Expected**: `{origin: "...", working_copy: "example.com/claude/alice/", copied_files: [...]}`

### TC-WD-012: Backup Retention — Purge Old
- **Priority**: P1
- **Steps**: Create backups with old timestamps → purge with 14-day retention
- **Expected**: Backups older than 14 days removed, recent ones kept

---

## 15. Access Control Tests

### TC-AC-001: Shared Folder — Public Read
- **Priority**: P0
- **Steps**: Put file in `example.com/alice/shared/story.ink`
- **Expected**: `canAccess(null, "example.com/alice/shared/story.ink", false)` → true

### TC-AC-002: Private Folder — Owner Only
- **Priority**: P0
- **Steps**: Put file in `example.com/alice/private.ink`
- **Expected**: `canAccess("alice", ..., false)` → true; `canAccess("bob", ..., false)` → false

### TC-AC-003: Private Folder — Org Member Access
- **Priority**: P1
- **Steps**: alice and bob in same domain
- **Expected**: `canAccess("bob", "example.com/alice/story.ink", false)` → true

### TC-AC-004: Write — Owner Only
- **Priority**: P0
- **Steps**: Try write with different principals
- **Expected**: Only owner can write; others get access denied

### TC-AC-005: LLM Access via model.vcf
- **Priority**: P0
- **Steps**: Create `example.com/claude.vcf` → LLM tries to read alice's files
- **Expected**: `canAccess("claude", "example.com/alice/story.ink", false)` → true

### TC-AC-006: LLM Without model.vcf — Denied
- **Priority**: P1
- **Steps**: No model.vcf → LLM tries to read
- **Expected**: Access denied

### TC-AC-007: Path Traversal Prevention
- **Priority**: P0
- **Steps**: Try `webdav_get` with `"../../../etc/passwd"`
- **Expected**: Rejected; no path traversal allowed

---

## 16. MCP Protocol Tests (McpRouter)

### TC-MR-001: SSE Connection — /sse
- **Priority**: P0
- **Steps**: `GET /sse`
- **Expected**: SSE stream opened; receives `event: endpoint` with session URL

### TC-MR-002: Initialize Handshake
- **Priority**: P0
- **Steps**: POST initialize message
- **Expected**: Response with `serverInfo`, `capabilities`, 71 tools

### TC-MR-003: tools/list
- **Priority**: P0
- **Steps**: POST `{"method": "tools/list"}`
- **Expected**: 71 tool definitions with names, descriptions, schemas

### TC-MR-004: tools/call — Valid Tool
- **Priority**: P0
- **Steps**: POST `{"method": "tools/call", "params": {"name": "compile_ink", "arguments": {"source": "Hello"}}}`
- **Expected**: Successful tool result

### TC-MR-005: tools/call — Unknown Tool
- **Priority**: P1
- **Steps**: POST `{"method": "tools/call", "params": {"name": "nonexistent"}}`
- **Expected**: Error response with "unknown tool"

### TC-MR-006: WebDAV REST — GET /dav/
- **Priority**: P1
- **Steps**: `GET /dav/example.com/alice/`
- **Expected**: JSON file listing

### TC-MR-007: WebDAV REST — PUT /dav/
- **Priority**: P1
- **Steps**: `PUT /dav/example.com/alice/story.ink` with body
- **Expected**: File created/updated

### TC-MR-008: Health Endpoint
- **Priority**: P0
- **Steps**: `GET /health` or `/api/health`
- **Expected**: Server status with tool count (71)

---

## 17. Integration Tests

### TC-IT-001: Full Story Lifecycle
- **Priority**: P0
- **Steps**: compile → start → choose → continue → save → load → reset → end
- **Expected**: Full lifecycle completes without errors

### TC-IT-002: Debug + Edit Pipeline
- **Priority**: P1
- **Steps**: parse_ink → get_section → replace_section → compile → start_debug → step
- **Expected**: Edited story runs in debugger

### TC-IT-003: WebDAV + Backup + Restore Lifecycle
- **Priority**: P1
- **Steps**: put → backup → modify → list_backups → restore → verify original
- **Expected**: Full backup/restore cycle works

### TC-IT-004: Principal + Auth + WebDAV ACL
- **Priority**: P1
- **Steps**: create_principal → create_llm_credential → webdav_put → verify access control
- **Expected**: Access rules enforced correctly

### TC-IT-005: Calendar + Export/Import Round-Trip
- **Priority**: P1
- **Steps**: create_event → export_ics → import_ics to new calendar → verify
- **Expected**: Event data preserved

### TC-IT-006: LLM Generate + Compile + Play Pipeline
- **Priority**: P2
- **Steps**: generate_ink → compile_ink → start_story → choose
- **Expected**: Generated story is playable

### TC-IT-007: Ink → PlantUML → SVG → WebDAV
- **Priority**: P2
- **Steps**: Write ink → ink2puml → puml2svg → webdav_put (.ink + .puml + .svg)
- **Expected**: All three artifact files stored

---

## 18. Test Coverage Summary

| Category | Tests | P0 | P1 | P2 | Tools Covered |
|----------|-------|----|----|-----|---------------|
| Ink Core | 17 | 5 | 9 | 3 | 17 |
| Debug | 10 | 0 | 7 | 3 | 8 |
| Edit | 6 | 1 | 5 | 0 | 6 |
| Ink+Markdown | 3 | 0 | 3 | 0 | 3 |
| PlantUML+TOC | 5 | 0 | 4 | 1 | 5 |
| LLM Models | 8 | 0 | 5 | 3 | 8 |
| Services | 2 | 0 | 1 | 1 | 2 |
| Collaboration | 2 | 0 | 2 | 0 | 2 |
| Calendar | 6 | 2 | 4 | 0 | 4 |
| Principals | 5 | 2 | 3 | 0 | 4 |
| Auth | 4 | 3 | 1 | 0 | 2 |
| WebDAV+Backup | 12 | 4 | 7 | 1 | 10 |
| Access Control | 7 | 4 | 3 | 0 | — |
| MCP Protocol | 8 | 4 | 4 | 0 | — |
| Integration | 7 | 1 | 4 | 2 | — |
| **TOTAL** | **102** | **26** | **66** | **14** | **71** |

### Comparison with Existing Coverage

| Area | Existing Tests | Planned Tests | Gap |
|------|---------------|---------------|-----|
| Electron Editor (E2E) | 10/80 implemented | 80 total (E2E_TEST_PLAN.md) | 70 not implemented |
| Bidify (Unit) | 29 tests | Covered by TC-IC-015..017 | Good coverage |
| MCP Server (All) | **0 tests** | **102 tests planned** | **Full gap** |
| **Total** | **39 tests** | **182 tests planned** | **143 gap** |

### Implementation Priority

1. **Phase 1 (P0)**: 26 tests — Core compilation, story playback, access control, MCP protocol
2. **Phase 2 (P1)**: 66 tests — Full tool coverage, integration tests, debug/edit workflows
3. **Phase 3 (P2)**: 14 tests — Edge cases, mocked LLM tests, advanced features

### Notes

- **LLM tests**: Mock JLama for unit tests; use real model only in integration/CI with GPU
- **WebDAV sync**: Mock Sardine remote client; test local filesystem operations directly
- **Auth tests**: Test with and without `KEYCLOAK_REALM_URL` to verify backward compatibility
- **Steeltoe C# Unity integration**: Not yet implemented — test plan TBD when integration is built
