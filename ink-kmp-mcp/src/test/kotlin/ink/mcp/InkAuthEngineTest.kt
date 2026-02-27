package ink.mcp

import kotlin.test.*

/**
 * Unit tests for InkAuthEngine — Keycloak OIDC + LLM basic auth engine.
 *
 * Tests: configuration detection, auth status, LLM credential lifecycle,
 * role checking, and credential listing.
 *
 * Note: Without KEYCLOAK_REALM_URL env var, isConfigured() returns false
 * but LLM basic-auth credential operations still work.
 */
class InkAuthEngineTest {

    private lateinit var engine: InkAuthEngine

    @BeforeTest
    fun setup() {
        engine = InkAuthEngine()
    }

    // -- Configuration ------------------------------------------------

    @Test
    fun `isConfigured returns false without keycloak env`() {
        assertFalse(engine.isConfigured(),
            "isConfigured should be false when KEYCLOAK_REALM_URL is not set")
    }

    // -- Auth status --------------------------------------------------

    @Test
    fun `getAuthStatus returns status map with expected fields`() {
        val status = engine.getAuthStatus()
        assertNotNull(status, "getAuthStatus should return a non-null map")
        assertEquals(false, status["keycloak_configured"],
            "keycloak_configured should be false without env vars")
        assertEquals("not set", status["keycloak_realm_url"],
            "keycloak_realm_url should be 'not set' without env vars")
        assertEquals("inky-mcp", status["keycloak_client_id"],
            "keycloak_client_id should default to 'inky-mcp'")
        assertEquals(0, status["llm_credentials_count"],
            "llm_credentials_count should be 0 initially")
        assertTrue((status["llm_models"] as List<*>).isEmpty(),
            "llm_models should be empty initially")
    }

    // -- LLM credential creation --------------------------------------

    @Test
    fun `createLlmCredential returns credential map with required keys`() {
        val cred = engine.createLlmCredential("test-model")
        assertEquals("test-model", cred["model_name"],
            "model_name should match the provided name")
        assertNotNull(cred["token"], "Credential should include a JWT token")
        assertTrue(cred["token"]!!.isNotBlank(), "Token should not be blank")
        assertNotNull(cred["mcp_uri"], "Credential should include mcp_uri")
        assertTrue(cred["mcp_uri"]!!.startsWith("mcp://test-model:"),
            "mcp_uri should start with mcp://model_name:")
        assertNotNull(cred["basic_auth"], "Credential should include basic_auth")
        assertEquals("test-model:${cred["token"]}", cred["basic_auth"],
            "basic_auth should be model_name:token")
    }

    // -- getLatestToken -----------------------------------------------

    @Test
    fun `getLatestToken returns null for unknown model`() {
        assertNull(engine.getLatestToken("nonexistent-model"),
            "getLatestToken should return null for a model with no credential")
    }

    // -- listLlmCredentials -------------------------------------------

    @Test
    fun `listLlmCredentials returns empty list initially`() {
        val creds = engine.listLlmCredentials()
        assertTrue(creds.isEmpty(),
            "listLlmCredentials should return empty list before any credentials are created")
    }

    @Test
    fun `listLlmCredentials returns credentials after creation`() {
        engine.createLlmCredential("model-a")
        engine.createLlmCredential("model-b")

        val creds = engine.listLlmCredentials()
        assertEquals(2, creds.size,
            "Should list 2 credentials after creating 2")

        val names = creds.map { it["model_name"] }.toSet()
        assertTrue("model-a" in names, "Should contain model-a")
        assertTrue("model-b" in names, "Should contain model-b")
        assertTrue(creds.all { it["has_token"] == "true" },
            "Each credential entry should have has_token=true")
    }

    // -- requireRole --------------------------------------------------

    @Test
    fun `requireRole returns false for null principal`() {
        assertFalse(engine.requireRole(null, "edit"),
            "requireRole should return false when principal is null")
    }

    // -- Full lifecycle -----------------------------------------------

    @Test
    fun `lifecycle - create credential then list and retrieve token`() {
        // Initially empty
        assertTrue(engine.listLlmCredentials().isEmpty())
        assertNull(engine.getLatestToken("llama-3"))

        // Create credential
        val cred = engine.createLlmCredential("llama-3", host = "10.0.0.1", port = 8080)
        val token = cred["token"]!!

        // Token retrievable
        assertEquals(token, engine.getLatestToken("llama-3"),
            "getLatestToken should return the token that was just created")

        // Listed in credentials
        val list = engine.listLlmCredentials()
        assertEquals(1, list.size, "Should have exactly 1 credential")
        assertEquals("llama-3", list[0]["model_name"])

        // Auth status reflects the credential
        val status = engine.getAuthStatus()
        assertEquals(1, status["llm_credentials_count"],
            "Auth status should show 1 credential")
        assertTrue((status["llm_models"] as List<*>).contains("llama-3"),
            "Auth status llm_models should contain the created model name")

        // mcp_uri uses custom host and port
        assertTrue(cred["mcp_uri"]!!.contains("@10.0.0.1:8080"),
            "mcp_uri should reflect the custom host and port")
    }
}
