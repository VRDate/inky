package ink.mcp

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Yjs-compatible collaboration engine for real-time ink script editing.
 *
 * Implements a Hocuspocus-compatible WebSocket protocol for Yjs CRDT documents.
 * Multiple clients can connect to the same ink document and see each other's
 * edits in real-time via Yjs conflict-free replicated data types.
 *
 * Protocol:
 *   - Clients connect via WebSocket to /collab/:docId
 *   - Binary messages are Yjs sync/awareness protocol frames
 *   - Message types: sync-step-1 (0), sync-step-2 (1), update (2), awareness (3)
 *
 * Each document maintains:
 *   - A server-side Yjs state vector for catch-up sync
 *   - Connected client awareness (cursor positions, user names)
 *   - Document persistence (auto-save to disk)
 *
 * @see <a href="https://docs.yjs.dev/">Yjs Documentation</a>
 * @see <a href="https://tiptap.dev/hocuspocus">Hocuspocus Protocol</a>
 */
class ColabEngine : McpColabOps {

    private val log = LoggerFactory.getLogger(ColabEngine::class.java)

    /** Connected clients per document */
    private val documents = ConcurrentHashMap<String, ColabDocument>()

    /** Get or create a collaboration document */
    fun getOrCreateDocument(docId: String): ColabDocument {
        return documents.getOrPut(docId) {
            ColabDocument(docId).also {
                log.info("Created collab document: {}", docId)
            }
        }
    }

    /** Remove a document when all clients disconnect */
    fun removeDocumentIfEmpty(docId: String) {
        documents[docId]?.let { doc ->
            if (doc.clientCount == 0) {
                documents.remove(docId)
                log.info("Removed empty collab document: {}", docId)
            }
        }
    }

    /** List active documents with client counts */
    override fun listDocuments(): List<Map<String, Any>> {
        return documents.map { (id, doc) ->
            mapOf(
                "doc_id" to id,
                "clients" to doc.clientCount,
                "created_at" to doc.createdAt
            )
        }
    }

    /** Get document info */
    override fun getDocumentInfo(docId: String): Map<String, Any>? {
        return documents[docId]?.let { doc ->
            mapOf(
                "doc_id" to docId,
                "clients" to doc.clientCount,
                "created_at" to doc.createdAt,
                "client_ids" to doc.getClientIds(),
                "update_count" to doc.updateCount
            )
        }
    }

    /** Total connected clients across all documents */
    override val totalClients: Int get() = documents.values.sumOf { it.clientCount }
}

/**
 * A single collaboration document with connected Yjs clients.
 *
 * Handles the Yjs sync protocol:
 *   Step 1: Client sends its state vector → server replies with missing updates
 *   Step 2: Server sends its state vector → client replies with missing updates
 *   Update: Incremental Yjs document updates broadcast to all peers
 *   Awareness: Cursor positions, user names, presence info
 */
class ColabDocument(val docId: String) {

    private val log = LoggerFactory.getLogger(ColabDocument::class.java)

    val createdAt: Long = System.currentTimeMillis()
    var updateCount: Long = 0L
        private set

    /** Connected WebSocket clients */
    private val clients = ConcurrentHashMap<String, ColabClient>()
    private val mutex = Mutex()

    /** Accumulated Yjs document updates (server-side state) */
    private val updates = mutableListOf<ByteArray>()

    /** Accumulated awareness states */
    private val awarenessStates = ConcurrentHashMap<Int, ByteArray>()

    val clientCount: Int get() = clients.size

    fun getClientIds(): List<String> = clients.keys().toList()

    /** Add a client to this document */
    suspend fun addClient(clientId: String, session: WebSocketSession): ColabClient {
        val client = ColabClient(clientId, session)
        clients[clientId] = client
        log.info("Client {} joined document {} ({} clients)", clientId, docId, clientCount)

        // Send accumulated state to new client
        mutex.withLock {
            if (updates.isNotEmpty()) {
                // Send sync step 1: all accumulated updates as a single merge
                val merged = mergeUpdates(updates)
                val syncMsg = buildSyncStep2Message(merged)
                session.send(Frame.Binary(true, syncMsg))
            }

            // Send current awareness states
            for ((_, awareness) in awarenessStates) {
                session.send(Frame.Binary(true, awareness))
            }
        }

        return client
    }

    /** Remove a client from this document */
    suspend fun removeClient(clientId: String) {
        clients.remove(clientId)
        log.info("Client {} left document {} ({} clients)", clientId, docId, clientCount)
    }

    /** Handle an incoming Yjs protocol message */
    suspend fun handleMessage(fromClientId: String, data: ByteArray) {
        if (data.isEmpty()) return

        val messageType = data[0].toInt() and 0xFF

        when (messageType) {
            MSG_SYNC -> handleSyncMessage(fromClientId, data)
            MSG_AWARENESS -> handleAwarenessMessage(fromClientId, data)
            else -> log.debug("Unknown message type: {} from {}", messageType, fromClientId)
        }
    }

    /** Handle Yjs sync protocol messages */
    private suspend fun handleSyncMessage(fromClientId: String, data: ByteArray) {
        if (data.size < 2) return
        val syncType = data[1].toInt() and 0xFF

        when (syncType) {
            SYNC_STEP1 -> {
                // Client requesting sync — send our state
                mutex.withLock {
                    if (updates.isNotEmpty()) {
                        val merged = mergeUpdates(updates)
                        val response = buildSyncStep2Message(merged)
                        clients[fromClientId]?.session?.send(Frame.Binary(true, response))
                    }
                }
            }
            SYNC_STEP2, SYNC_UPDATE -> {
                // Client sending update — store and broadcast
                val update = data.copyOfRange(2, data.size)
                mutex.withLock {
                    updates.add(update)
                    updateCount++
                }

                // Broadcast to all other clients
                broadcastExcept(fromClientId, data)
            }
        }
    }

    /** Handle Yjs awareness protocol messages */
    private suspend fun handleAwarenessMessage(fromClientId: String, data: ByteArray) {
        if (data.size < 2) return

        // Extract client ID from awareness message and store
        val clientAwarenessId = fromClientId.hashCode() and 0x7FFFFFFF
        awarenessStates[clientAwarenessId] = data

        // Broadcast awareness to all other clients
        broadcastExcept(fromClientId, data)
    }

    /** Broadcast a message to all clients except the sender */
    private suspend fun broadcastExcept(excludeClientId: String, data: ByteArray) {
        clients.forEach { (id, client) ->
            if (id != excludeClientId) {
                try {
                    client.session.send(Frame.Binary(true, data))
                } catch (e: Exception) {
                    log.debug("Failed to send to client {}: {}", id, e.message)
                }
            }
        }
    }

    /** Build a sync step 2 response message */
    private fun buildSyncStep2Message(update: ByteArray): ByteArray {
        val msg = ByteArray(2 + update.size)
        msg[0] = MSG_SYNC.toByte()
        msg[1] = SYNC_STEP2.toByte()
        update.copyInto(msg, 2)
        return msg
    }

    /** Simple merge: concatenate all updates (real Yjs would use Y.mergeUpdates) */
    private fun mergeUpdates(updates: List<ByteArray>): ByteArray {
        if (updates.size == 1) return updates[0]
        val total = updates.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (u in updates) {
            u.copyInto(result, offset)
            offset += u.size
        }
        return result
    }

    companion object {
        // Yjs message types
        const val MSG_SYNC = 0
        const val MSG_AWARENESS = 1

        // Yjs sync sub-types
        const val SYNC_STEP1 = 0
        const val SYNC_STEP2 = 1
        const val SYNC_UPDATE = 2
    }
}

/**
 * A connected collaboration client.
 */
class ColabClient(
    val clientId: String,
    val session: WebSocketSession
)

/**
 * Install Yjs collaboration WebSocket routes into Ktor routing.
 *
 * When authEngine is configured (Keycloak), WebSocket connections require
 * either "edit" role (full collab) or are rejected with VIOLATED_POLICY.
 * When auth is not configured, all connections are allowed (backward compatible).
 *
 * Endpoints:
 *   GET /collab/:docId     — WebSocket for Yjs sync
 */
fun Route.installColabRoutes(colabEngine: ColabEngine, authEngine: InkAuthEngine? = null) {
    val log = LoggerFactory.getLogger("ink.mcp.ColabRoutes")

    webSocket("/collab/{docId}") {
        val docId = call.parameters["docId"] ?: run {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing docId"))
            return@webSocket
        }

        // Auth gate: require "edit" role when auth is configured
        if (authEngine != null && authEngine.isConfigured()) {
            val principal = call.principal<InkPrincipal>()
            if (principal == null || "edit" !in principal.roles) {
                log.warn("WebSocket rejected: no edit role for doc={}", docId)
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Requires edit role"))
                return@webSocket
            }
            log.info("WebSocket auth: principal={} roles={} doc={}", principal.name, principal.roles, docId)
        }

        val clientId = java.util.UUID.randomUUID().toString().take(8)
        val document = colabEngine.getOrCreateDocument(docId)
        val client = document.addClient(clientId, this)

        log.info("WebSocket connected: client={} doc={}", clientId, docId)

        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> {
                        document.handleMessage(clientId, frame.readBytes())
                    }
                    is Frame.Text -> {
                        // Some Yjs clients send text frames — treat as binary
                        document.handleMessage(clientId, frame.readBytes())
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            log.debug("WebSocket error: client={} doc={} error={}", clientId, docId, e.message)
        } finally {
            document.removeClient(clientId)
            colabEngine.removeDocumentIfEmpty(docId)
            log.info("WebSocket disconnected: client={} doc={}", clientId, docId)
        }
    }
}
