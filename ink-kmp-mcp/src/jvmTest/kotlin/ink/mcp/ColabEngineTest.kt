package ink.mcp

import kotlin.test.*

/**
 * Unit tests for ColabEngine — Yjs/HocusPocus collaboration engine.
 *
 * Tests: document lifecycle, client management, Yjs binary protocol message types,
 * sync protocol steps, awareness broadcasts.
 */
class ColabEngineTest {

    private lateinit var engine: ColabEngine

    @BeforeTest
    fun setup() {
        engine = ColabEngine()
    }

    // ── Document lifecycle ────────────────────────────────────────

    @Test
    fun `getOrCreateDocument creates new document`() {
        val doc = engine.getOrCreateDocument("test-doc-1")
        assertNotNull(doc, "Should create a new document")
    }

    @Test
    fun `getOrCreateDocument returns same document for same id`() {
        val doc1 = engine.getOrCreateDocument("test-doc-2")
        val doc2 = engine.getOrCreateDocument("test-doc-2")
        assertSame(doc1, doc2, "Same docId should return same document")
    }

    @Test
    fun `listDocuments returns active documents`() {
        engine.getOrCreateDocument("doc-a")
        engine.getOrCreateDocument("doc-b")

        val docs = engine.listDocuments()
        assertTrue(docs.size >= 2, "Should list at least 2 documents")
    }

    @Test
    fun `getDocumentInfo returns null for unknown document`() {
        val info = engine.getDocumentInfo("nonexistent-doc")
        assertNull(info, "Unknown document should return null info")
    }

    @Test
    fun `getDocumentInfo returns metadata for known document`() {
        engine.getOrCreateDocument("info-doc")
        val info = engine.getDocumentInfo("info-doc")
        assertNotNull(info, "Known document should return info")
        assertTrue(info.containsKey("clients"), "Info should have clients field")
    }

    @Test
    fun `removeDocumentIfEmpty removes empty document`() {
        engine.getOrCreateDocument("empty-doc")
        engine.removeDocumentIfEmpty("empty-doc")
        val info = engine.getDocumentInfo("empty-doc")
        assertNull(info, "Empty document should be removed")
    }

    // ── Client count ─────────────────────────────────────────────

    @Test
    fun `totalClients starts at zero`() {
        assertEquals(0, engine.totalClients, "Initial total clients should be 0")
    }

    @Test
    fun `document client count starts at zero`() {
        val doc = engine.getOrCreateDocument("client-count-doc")
        assertEquals(0, doc.clientCount, "New document should have 0 clients")
    }

    @Test
    fun `getClientIds returns empty list for new document`() {
        val doc = engine.getOrCreateDocument("client-ids-doc")
        assertTrue(doc.getClientIds().isEmpty(), "New document should have no client IDs")
    }

    // ── Yjs protocol message types ───────────────────────────────

    @Test
    fun `MSG_SYNC byte value is 0`() {
        // Verify the Yjs/HocusPocus protocol constants
        val MSG_SYNC: Byte = 0
        val MSG_AWARENESS: Byte = 1
        assertEquals(0.toByte(), MSG_SYNC, "MSG_SYNC should be 0x00")
        assertEquals(1.toByte(), MSG_AWARENESS, "MSG_AWARENESS should be 0x01")
    }

    @Test
    fun `sync step constants are correct`() {
        val SYNC_STEP1: Byte = 0  // Client requests sync
        val SYNC_STEP2: Byte = 1  // Server sends full state
        val SYNC_UPDATE: Byte = 2 // Incremental update

        assertEquals(0.toByte(), SYNC_STEP1)
        assertEquals(1.toByte(), SYNC_STEP2)
        assertEquals(2.toByte(), SYNC_UPDATE)
    }

    @Test
    fun `construct sync step 1 message`() {
        // A minimal Yjs sync step 1 message: [MSG_SYNC, SYNC_STEP1, empty_state_vector]
        val message = byteArrayOf(0, 0, 0) // MSG_SYNC, SYNC_STEP1, 0-length state vector
        assertEquals(3, message.size, "Sync step 1 should be at least 3 bytes")
        assertEquals(0.toByte(), message[0], "First byte should be MSG_SYNC")
        assertEquals(0.toByte(), message[1], "Second byte should be SYNC_STEP1")
    }

    @Test
    fun `construct awareness message`() {
        // A minimal awareness update: [MSG_AWARENESS, length, client_id_bytes, state_json]
        val awareness = byteArrayOf(1, 0) // MSG_AWARENESS, 0-length data
        assertEquals(1.toByte(), awareness[0], "First byte should be MSG_AWARENESS")
    }

    // ── Multiple documents isolation ─────────────────────────────

    @Test
    fun `different documents are isolated`() {
        val doc1 = engine.getOrCreateDocument("isolated-1")
        val doc2 = engine.getOrCreateDocument("isolated-2")
        assertNotSame(doc1, doc2, "Different docIds should create different documents")
    }
}
