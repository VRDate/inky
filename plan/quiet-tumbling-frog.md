# iCal4j + ical.js + ez-vcard + Keycloak Auth Integration

## Context

The Inky MCP server (51 tools, Ktor/Kotlin) needs authentication, calendar event management, and user/LLM principal management. Currently ColabEngine has no auth (anyone can connect), and there's no calendar or vCard support. This adds 10 new MCP tools (51→61), 3 new engine files, and role-based access control with Keycloak OIDC + LLM basicauth credentials.

## Dependencies to Add

**build.gradle.kts** (after line 29):
```kotlin
implementation("io.ktor:ktor-server-auth:$ktorVersion")
implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
implementation("org.mnode.ical4j:ical4j:4.0.7")
implementation("com.googlecode.ez-vcard:ez-vcard:0.12.1")
```

**InkyMcp.kt** (after line 10):
```
//DEPS io.ktor:ktor-server-auth:3.1.1
//DEPS io.ktor:ktor-server-auth-jwt:3.1.1
//DEPS org.mnode.ical4j:ical4j:4.0.7
//DEPS com.googlecode.ez-vcard:ez-vcard:0.12.1
//SOURCES src/ink/mcp/InkCalendarEngine.kt
//SOURCES src/ink/mcp/InkVCardEngine.kt
//SOURCES src/ink/mcp/InkAuthEngine.kt
```

## New Files

### 1. InkAuthEngine.kt — Auth foundation (must come first)

- Keycloak OIDC via env vars: `KEYCLOAK_REALM_URL`, `KEYCLOAK_CLIENT_ID`
- JWT validation (RS256 against Keycloak JWKS)
- BasicAuth for LLM models: `model_name:jwt_token`
- Role extraction: "edit" (full YJS collab) vs "view" (ink.js player only)
- `InkPrincipal` data class: id, name, roles, isLlm, email
- `installAuth(Application)` — installs Ktor jwt("keycloak") + basic("llm-basic") providers
- `createLlmCredential(modelName)` → `{ model_name, token, mcp_uri }`
- **Auth is opt-in**: when `KEYCLOAK_REALM_URL` unset, server runs open (backward compatible)

### 2. InkCalendarEngine.kt — iCal4j game events

- In-memory `ConcurrentHashMap<String, Calendar>` store
- `InkEvent` data class: uid, summary, description, dtStart, dtEnd, category, status
- Categories: milestone, session, deadline, quest
- 4 methods → 4 MCP tools: `create_event`, `list_events`, `export_ics`, `import_ics`

### 3. InkVCardEngine.kt — ez-vcard principals

- In-memory `ConcurrentHashMap<String, VCard>` + folder mappings
- Human users: vCard from Keycloak OIDC profile → mapped to ink script folder
- LLM models: vCard with MCP URI in NOTE field, basicauth credentials
- MCP URI format: `mcp://model_name:jwt_token@host:port/tool_name`
- 4 methods → 4 MCP tools: `create_principal`, `list_principals`, `get_principal`, `delete_principal`

## Modified Files

### 4. McpTools.kt — +10 tools (51→61)

- Add `calendarEngine`, `vcardEngine`, `authEngine` constructor params
- Add `calendarTools` (4), `vcardTools` (4), `authTools` (2) lists
- Add 10 dispatch cases in `callTool()` when block
- Add 10 handler methods delegating to engines

### 5. McpRouter.kt — Auth plugin + route protection

- Add `authEngine` param to `startServer()`
- Create `InkCalendarEngine` + `InkVCardEngine` instances
- Pass all 3 new engines to McpTools constructor
- `authEngine?.installAuth(this)` after `install(WebSockets)`
- Wrap `/collab/{docId}` and `/api/` routes in `authenticate()` when auth configured
- Add REST endpoints: `/api/calendar/*`, `/api/principals/*`

### 6. ColabEngine.kt — Auth gate on WebSocket

- `installColabRoutes(colabEngine, authEngine?)` — overloaded signature
- Before creating client: check `InkPrincipal` has "edit" role
- Close WebSocket with VIOLATED_POLICY if no edit role
- Extend `ColabClient` with optional `principal: InkPrincipal?`

### 7. Main.kt + InkyMcp.kt — Startup wiring

- Create `InkAuthEngine()`, pass to `startServer()`
- Banner: show auth status (Keycloak URL or "disabled")
- Update tool count 51→61, add calendar/vcard/auth categories

### 8. Architecture diagrams

- `ink-mcp-tools.puml`: +3 tool groups (Calendar 4, vCard 4, Auth 2), +3 engines, total 61
- `ink-collab-yjs.puml`: add auth layer, LLM actor with basicauth WebSocket

## Execution Order

```
1. build.gradle.kts + InkyMcp.kt DEPS
2. InkAuthEngine.kt (foundation — others depend on it)
3. InkCalendarEngine.kt (independent of auth)
4. InkVCardEngine.kt (uses authEngine for LLM creds)
5. McpTools.kt (+10 tools, +10 handlers)
6. McpRouter.kt (wire engines, install auth, protect routes)
7. ColabEngine.kt (edit role gate)
8. Main.kt + InkyMcp.kt (startup wiring)
9. PUML diagrams (final state)
```

## Verification

1. `gradle compileKotlin` — must pass
2. Start server with `--no-llm` — health endpoint shows 61 tools (or 53 in pwa mode)
3. Test `create_event` / `list_events` / `export_ics` / `import_ics` via MCP JSON-RPC
4. Test `create_principal` / `list_principals` for human + LLM principals
5. Test `create_llm_credential` — returns `mcp://model:token@host:port` URI
6. Without `KEYCLOAK_REALM_URL`: all routes open (backward compatible)
7. WebSocket `/collab/{docId}` accessible without auth when auth disabled
