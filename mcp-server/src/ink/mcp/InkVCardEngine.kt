package ink.mcp

import ezvcard.VCard
import ezvcard.Ezvcard
import ezvcard.property.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * vCard-based principal management engine.
 *
 * Manages vCards for two types of principals:
 *   1. Human users (from Keycloak OIDC) — mapped to folders with ink scripts
 *   2. LLM models (virtual users) — participate in Yjs collaboration
 *
 * Identity mapping:
 *   - Keycloak UUID → vCard UID (1:1 identity mapping)
 *   - jCard (JSON vCard) → JWT "jcard" claim (portable identity in token)
 *   - X-AUTH → latest JWT token stored in vCard extended property
 *
 * MCP URI format: mcp://model_name:jwt_token@host:port/tool_name
 */
class InkVCardEngine(
    private val authEngine: InkAuthEngine? = null
) {

    private val log = LoggerFactory.getLogger(InkVCardEngine::class.java)

    /** Principal store: id -> VCard */
    private val principals = ConcurrentHashMap<String, VCard>()

    /** Folder mappings: id -> folder path */
    private val folderMappings = ConcurrentHashMap<String, String>()

    /** Role mappings: id -> role */
    private val roleMappings = ConcurrentHashMap<String, String>()

    /** LLM flag: id -> isLlm */
    private val llmFlags = ConcurrentHashMap<String, Boolean>()

    data class InkPrincipalInfo(
        val id: String,
        val displayName: String,
        val email: String? = null,
        val role: String,
        val isLlm: Boolean,
        val folderPath: String,
        val mcpUri: String? = null
    )

    /**
     * Create a user or LLM principal with vCard.
     *
     * For Keycloak users: the id should be the Keycloak UUID (becomes vCard UID).
     * For LLM models: creates basicauth credentials with jCard embedded in JWT.
     * The latest JWT is stored in the vCard X-AUTH extended property.
     */
    fun createPrincipal(
        id: String,
        name: String,
        email: String? = null,
        role: String = "view",
        isLlm: Boolean = false,
        folderPath: String? = null,
        host: String = "localhost",
        port: Int = 3001
    ): Map<String, Any> {
        // Map email user@domain → domain/user/ filesystem layout
        val resolvedFolder = folderPath ?: emailToFolderPath(email, id)
        var mcpUri: String? = null
        var jwtToken: String? = null

        // Build vCard first (without X-AUTH — will add after credential)
        val vcard = buildVCard(id, name, email, role, isLlm, null, null)

        // For LLM principals, create auth credentials with jCard as JWT claim
        if (isLlm && authEngine != null) {
            // Generate jCard (JSON vCard) to embed in JWT claim
            val jcard = Ezvcard.writeJson(vcard).go()

            val cred = authEngine.createLlmCredential(id, host, port, jcard)
            mcpUri = cred["mcp_uri"]
            jwtToken = cred["token"]

            // Store latest JWT in X-AUTH extended property
            vcard.setExtendedProperty("X-AUTH", jwtToken)
        }

        // Store note with metadata
        vcard.notes.clear()
        vcard.addNote(buildString {
            append("role=$role")
            append(";type=${if (isLlm) "llm" else "human"}")
            if (mcpUri != null) append(";mcp_uri=$mcpUri")
            append(";folder=$resolvedFolder")
        })

        principals[id] = vcard
        folderMappings[id] = resolvedFolder
        roleMappings[id] = role
        llmFlags[id] = isLlm

        log.info("Created {} principal: {} (uid={}, role={}, folder={})",
            if (isLlm) "LLM" else "user", name, id, role, resolvedFolder)

        val result = mutableMapOf<String, Any>(
            "ok" to true,
            "id" to id,
            "name" to name,
            "role" to role,
            "is_llm" to isLlm,
            "folder_path" to resolvedFolder,
            "vcard" to Ezvcard.write(vcard).go(),
            "jcard" to Ezvcard.writeJson(vcard).go()
        )
        mcpUri?.let { result["mcp_uri"] = it }
        return result
    }

    /** List all principals */
    fun listPrincipals(isLlm: Boolean? = null): List<Map<String, Any>> {
        return principals.entries
            .filter { (id, _) ->
                isLlm == null || llmFlags[id] == isLlm
            }
            .map { (id, vcard) -> vcardToMap(id, vcard) }
    }

    /** Get full details of a principal including jCard and X-AUTH */
    fun getPrincipal(id: String): Map<String, Any>? {
        val vcard = principals[id] ?: return null
        val map = vcardToMap(id, vcard).toMutableMap()
        map["vcard_text"] = Ezvcard.write(vcard).go()
        map["jcard"] = Ezvcard.writeJson(vcard).go()
        // Include X-AUTH (latest JWT) if present
        vcard.getExtendedProperty("X-AUTH")?.let {
            map["x_auth"] = it.value
        }
        return map
    }

    /** Delete a principal */
    fun deletePrincipal(id: String): Map<String, Any> {
        val existed = principals.remove(id) != null
        folderMappings.remove(id)
        roleMappings.remove(id)
        llmFlags.remove(id)

        return mapOf(
            "ok" to existed,
            "id" to id,
            "message" to if (existed) "Principal deleted" else "Principal not found"
        )
    }

    /** Get folder path for a principal */
    fun getFolderPath(id: String): String? = folderMappings[id]

    /** Check if principal has a role */
    fun hasRole(id: String, role: String): Boolean = roleMappings[id] == role

    /**
     * Refresh the JWT for an LLM principal.
     * Generates new credentials, updates X-AUTH in vCard, embeds new jCard in JWT.
     */
    fun refreshLlmCredential(
        id: String,
        host: String = "localhost",
        port: Int = 3001
    ): Map<String, Any>? {
        val vcard = principals[id] ?: return null
        if (llmFlags[id] != true || authEngine == null) return null

        // Generate jCard without X-AUTH to avoid circular embedding
        val cleanVcard = VCard(vcard)
        cleanVcard.removeExtendedProperty("X-AUTH")
        val jcard = Ezvcard.writeJson(cleanVcard).go()

        val cred = authEngine.createLlmCredential(id, host, port, jcard)

        // Update X-AUTH with new JWT
        vcard.setExtendedProperty("X-AUTH", cred["token"])

        log.info("Refreshed credential for LLM principal: {}", id)
        return mapOf(
            "ok" to true,
            "id" to id,
            "mcp_uri" to (cred["mcp_uri"] ?: ""),
            "jcard" to jcard
        )
    }

    /**
     * Map email user@domain to domain/user/ filesystem path.
     * Example: alice@example.com → ./ink-scripts/example.com/alice/
     * Fallback to ./ink-scripts/{id}/ if no email provided.
     */
    private fun emailToFolderPath(email: String?, fallbackId: String): String {
        if (email != null && email.contains("@")) {
            val parts = email.split("@", limit = 2)
            val user = parts[0]
            val domain = parts[1]
            return "./ink-scripts/$domain/$user/"
        }
        return "./ink-scripts/$fallbackId/"
    }

    private fun buildVCard(
        id: String,
        name: String,
        email: String?,
        role: String,
        isLlm: Boolean,
        mcpUri: String?,
        jwtToken: String?
    ): VCard {
        return VCard().apply {
            // Keycloak UUID = vCard UID (1:1 identity)
            uid = Uid(id)
            setFormattedName(name)
            setStructuredName(StructuredName().apply {
                family = if (isLlm) "LLM" else name.split(" ").lastOrNull() ?: name
                given = if (isLlm) name else name.split(" ").firstOrNull() ?: ""
            })
            email?.let { addEmail(it) }
            setOrganization(Organization().apply {
                values.add(if (isLlm) "LLM Model" else "Inky User")
                values.add(role)
            })
            addNote(buildString {
                append("role=$role")
                append(";type=${if (isLlm) "llm" else "human"}")
                if (mcpUri != null) append(";mcp_uri=$mcpUri")
                append(";folder=${folderMappings[id] ?: "./ink-scripts/$id/"}")
            })
            setCategories(Categories().apply {
                values.add(if (isLlm) "llm" else "user")
                values.add(role)
            })
            // X-AUTH: latest JWT stored in vCard extended property
            if (jwtToken != null) {
                setExtendedProperty("X-AUTH", jwtToken)
            }
        }
    }

    private fun vcardToMap(id: String, vcard: VCard): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "name" to (vcard.formattedName?.value ?: id),
            "role" to (roleMappings[id] ?: "view"),
            "is_llm" to (llmFlags[id] ?: false),
            "folder_path" to (folderMappings[id] ?: "")
        )
        vcard.emails.firstOrNull()?.let { map["email"] = it.value }
        // Extract MCP URI from note
        vcard.notes.firstOrNull()?.value?.let { note ->
            val mcpUri = note.split(";").find { it.startsWith("mcp_uri=") }
                ?.removePrefix("mcp_uri=")
            mcpUri?.let { map["mcp_uri"] = it }
        }
        // Include X-AUTH presence (not the actual token, for list view)
        vcard.getExtendedProperty("X-AUTH")?.let {
            map["has_auth"] = true
        }
        return map
    }
}
