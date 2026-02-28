package ink.mcp

import kotlin.test.*

/**
 * Unit tests for InkVCardEngine -- vCard-based principal management.
 *
 * Tests: createPrincipal (human & LLM), listPrincipals (all & filtered),
 * getPrincipal (found & not found), deletePrincipal (existing & unknown),
 * hasRole, getFolderPath, and full create-get-delete lifecycle.
 *
 * These tests use no authEngine (null) so LLM credential/JWT paths are
 * not exercised -- only the in-memory principal store is tested.
 */
class InkVCardEngineTest {

    private lateinit var engine: InkVCardEngine

    @BeforeTest
    fun setup() {
        engine = InkVCardEngine()
    }

    // ═══════════════════════════════════════════════════════════════
    // CREATE PRINCIPAL
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `createPrincipal creates human principal`() {
        val result = engine.createPrincipalFull("user1", "Alice Smith", email = "alice@example.com", role = "edit")

        assertTrue(result["ok"] as Boolean, "createPrincipal should succeed")
        assertEquals("user1", result["id"])
        assertEquals("Alice Smith", result["name"])
        assertEquals("edit", result["role"])
        assertEquals(false, result["is_llm"])
        // Email alice@example.com maps to domain/user/ filesystem layout
        assertEquals("./ink-scripts/example.com/alice/", result["folder_path"],
            "Email alice@example.com should map to ./ink-scripts/example.com/alice/")
        assertNotNull(result["vcard"], "Should include vCard text")
        assertNotNull(result["jcard"], "Should include jCard JSON")
        assertFalse(result.containsKey("mcp_uri"),
            "Human principal without authEngine should not have mcp_uri")
    }

    @Test
    fun `createPrincipal creates LLM principal with isLlm true`() {
        val result = engine.createPrincipalFull(
            "claude-3", "Claude Sonnet",
            role = "view", isLlm = true
        )

        assertTrue(result["ok"] as Boolean)
        assertEquals("claude-3", result["id"])
        assertEquals("Claude Sonnet", result["name"])
        assertEquals(true, result["is_llm"])
        assertEquals("view", result["role"])
        // No email provided, so folder falls back to id-based path
        assertEquals("./ink-scripts/claude-3/", result["folder_path"])
        // No authEngine, so no mcp_uri even for LLM
        assertFalse(result.containsKey("mcp_uri"))
    }

    @Test
    fun `createPrincipal with explicit folderPath uses provided path`() {
        val result = engine.createPrincipalFull(
            "user2", "Bob", folderPath = "/custom/path/"
        )

        assertTrue(result["ok"] as Boolean)
        assertEquals("/custom/path/", result["folder_path"],
            "Should use the explicitly provided folderPath")
    }

    // ═══════════════════════════════════════════════════════════════
    // LIST PRINCIPALS
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `listPrincipals lists all principals`() {
        engine.createPrincipalFull("human1", "Alice", isLlm = false)
        engine.createPrincipalFull("llm1", "Claude", isLlm = true)
        engine.createPrincipalFull("human2", "Bob", isLlm = false)

        val all = engine.listPrincipals()
        assertEquals(3, all.size, "Should list all 3 principals")

        val ids = all.map { it["id"] as String }.toSet()
        assertTrue("human1" in ids)
        assertTrue("llm1" in ids)
        assertTrue("human2" in ids)
    }

    @Test
    fun `listPrincipals with isLlm filter returns only matching type`() {
        engine.createPrincipalFull("human1", "Alice", isLlm = false)
        engine.createPrincipalFull("llm1", "Claude", isLlm = true)
        engine.createPrincipalFull("human2", "Bob", isLlm = false)
        engine.createPrincipalFull("llm2", "GPT", isLlm = true)

        val humans = engine.listPrincipals(isLlm = false)
        assertEquals(2, humans.size, "Should list 2 human principals")
        assertTrue(humans.all { it["is_llm"] == false },
            "All entries in human-filtered list should have is_llm=false")

        val llms = engine.listPrincipals(isLlm = true)
        assertEquals(2, llms.size, "Should list 2 LLM principals")
        assertTrue(llms.all { it["is_llm"] == true },
            "All entries in LLM-filtered list should have is_llm=true")
    }

    // ═══════════════════════════════════════════════════════════════
    // GET PRINCIPAL
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `getPrincipal returns details for known principal`() {
        engine.createPrincipalFull("user1", "Alice Smith", email = "alice@example.com", role = "edit")

        val principal = engine.getPrincipal("user1")
        assertNotNull(principal, "Should find existing principal")
        assertEquals("user1", principal["id"])
        assertEquals("Alice Smith", principal["name"])
        assertEquals("edit", principal["role"])
        assertEquals(false, principal["is_llm"])
        assertEquals("./ink-scripts/example.com/alice/", principal["folder_path"])
        assertEquals("alice@example.com", principal["email"])
        // getPrincipal adds extra detail fields beyond what listPrincipals returns
        assertNotNull(principal["vcard_text"], "Should include raw vCard text")
        assertNotNull(principal["jcard"], "Should include jCard JSON")
        assertNotNull(principal["script_path"], "Should resolve script_path")
        assertEquals("./ink-scripts/example.com/alice/script.ink", principal["script_path"])
        assertNotNull(principal["vcf_path"], "Should resolve vcf_path")
        assertEquals("./ink-scripts/example.com/alice.vcf", principal["vcf_path"])
    }

    @Test
    fun `getPrincipal returns null for unknown principal`() {
        val principal = engine.getPrincipal("nonexistent-id")
        assertNull(principal, "Should return null for unknown principal")
    }

    // ═══════════════════════════════════════════════════════════════
    // DELETE PRINCIPAL
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `deletePrincipal removes existing principal`() {
        engine.createPrincipalFull("user1", "Alice")

        val result = engine.deletePrincipal("user1")
        assertTrue(result["ok"] as Boolean, "Deleting existing principal should return ok=true")
        assertEquals("user1", result["id"])
        assertEquals("Principal deleted", result["message"])

        // Verify it is gone from all stores
        assertNull(engine.getPrincipal("user1"), "Principal should no longer exist")
        assertNull(engine.getFolderPath("user1"), "Folder mapping should be removed")
        assertFalse(engine.hasRole("user1", "view"), "Role mapping should be removed")
    }

    @Test
    fun `deletePrincipal for unknown id returns ok false`() {
        val result = engine.deletePrincipal("ghost")
        assertFalse(result["ok"] as Boolean, "Deleting nonexistent principal should return ok=false")
        assertEquals("Principal not found", result["message"])
    }

    // ═══════════════════════════════════════════════════════════════
    // HAS ROLE
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `hasRole checks assigned role correctly`() {
        engine.createPrincipalFull("editor", "Alice", role = "edit")
        engine.createPrincipalFull("viewer", "Bob", role = "view")

        assertTrue(engine.hasRole("editor", "edit"), "editor should have 'edit' role")
        assertFalse(engine.hasRole("editor", "view"), "editor should not have 'view' role")
        assertTrue(engine.hasRole("viewer", "view"), "viewer should have 'view' role")
        assertFalse(engine.hasRole("viewer", "edit"), "viewer should not have 'edit' role")
        assertFalse(engine.hasRole("unknown", "view"), "unknown principal should not match any role")
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE: CREATE -> GET -> DELETE
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `lifecycle create then get then delete`() {
        // 1) Create
        val createResult = engine.createPrincipalFull(
            "lifecycle1", "Lifecycle User",
            email = "lifecycle@test.org", role = "edit"
        )
        assertTrue(createResult["ok"] as Boolean)

        // 2) Get -- principal exists with correct data
        val principal = engine.getPrincipal("lifecycle1")
        assertNotNull(principal)
        assertEquals("Lifecycle User", principal["name"])
        assertEquals("edit", principal["role"])
        assertEquals("lifecycle@test.org", principal["email"])
        assertEquals("./ink-scripts/test.org/lifecycle/", principal["folder_path"])

        // Verify role and folder helpers
        assertTrue(engine.hasRole("lifecycle1", "edit"))
        assertEquals("./ink-scripts/test.org/lifecycle/", engine.getFolderPath("lifecycle1"))

        // Verify it appears in list
        val list = engine.listPrincipals()
        assertTrue(list.any { it["id"] == "lifecycle1" })

        // 3) Delete
        val deleteResult = engine.deletePrincipal("lifecycle1")
        assertTrue(deleteResult["ok"] as Boolean)

        // 4) Verify full cleanup
        assertNull(engine.getPrincipal("lifecycle1"))
        assertNull(engine.getFolderPath("lifecycle1"))
        assertFalse(engine.hasRole("lifecycle1", "edit"))
        assertTrue(engine.listPrincipals().none { it["id"] == "lifecycle1" })
    }
}
