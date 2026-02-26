package ink.mcp

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Authentication engine for Keycloak OIDC + LLM basic auth.
 *
 * Roles:
 *   "edit"  — Full access: HocusPocus/Yjs collaboration, all MCP tools
 *   "view"  — Read-only: ink.js player, read-only tools only
 *
 * LLM principals use basic auth: model_name:jwt_token
 * Human users authenticate via Keycloak OIDC (JWT bearer tokens).
 *
 * Auth is opt-in: when KEYCLOAK_REALM_URL is not set, server runs open.
 */
class InkAuthEngine {

    private val log = LoggerFactory.getLogger(InkAuthEngine::class.java)

    /** LLM credentials: model_name -> jwt_token */
    private val llmCredentials = ConcurrentHashMap<String, String>()

    /** Server-side JWT secret for LLM token generation */
    private val jwtSecret: String = System.getenv("MCP_JWT_SECRET")
        ?: UUID.randomUUID().toString()

    /** Keycloak configuration from environment */
    val keycloakRealmUrl: String? = System.getenv("KEYCLOAK_REALM_URL")
    val keycloakClientId: String = System.getenv("KEYCLOAK_CLIENT_ID") ?: "inky-mcp"
    val keycloakJwksUrl: String? = keycloakRealmUrl?.let {
        "$it/protocol/openid-connect/certs"
    }

    fun isConfigured(): Boolean = keycloakRealmUrl != null

    /**
     * Install Ktor auth plugins: JWT ("keycloak") + BasicAuth ("llm-basic").
     */
    fun installAuth(application: Application) {
        application.install(Authentication) {
            // JWT for Keycloak OIDC human users
            if (isConfigured()) {
                jwt("keycloak") {
                    realm = "inky-mcp"
                    verifier(
                        JWT.require(Algorithm.HMAC256(jwtSecret))
                            .withIssuer(keycloakRealmUrl)
                            .build()
                    )
                    validate { credential ->
                        val roles = extractRoles(credential)
                        if (roles.isNotEmpty()) {
                            InkPrincipal(
                                id = credential.payload.subject ?: "unknown",
                                name = credential.payload.getClaim("preferred_username")?.asString() ?: "user",
                                roles = roles,
                                isLlm = false,
                                email = credential.payload.getClaim("email")?.asString()
                            )
                        } else null
                    }
                }
            }

            // BasicAuth for LLM model principals
            basic("llm-basic") {
                realm = "inky-mcp-llm"
                validate { credentials ->
                    val storedToken = llmCredentials[credentials.name]
                    if (storedToken != null && storedToken == credentials.password) {
                        InkPrincipal(
                            id = credentials.name,
                            name = credentials.name,
                            roles = setOf("edit"),
                            isLlm = true
                        )
                    } else null
                }
            }
        }
        log.info("Auth installed: keycloak={}, llm-basic=enabled", isConfigured())
    }

    /** Create basicauth credentials for an LLM model, with jCard as JWT claim */
    fun createLlmCredential(
        modelName: String,
        host: String = "localhost",
        port: Int = 3001,
        jcard: String? = null
    ): Map<String, String> {
        val builder = JWT.create()
            .withIssuer("inky-mcp")
            .withSubject(modelName)
            .withClaim("role", "edit")
            .withClaim("is_llm", true)

        // Embed jCard (JSON vCard) as JWT claim for principal identity
        if (jcard != null) {
            builder.withClaim("jcard", jcard)
        }

        val token = builder.sign(Algorithm.HMAC256(jwtSecret))

        llmCredentials[modelName] = token

        val mcpUri = "mcp://$modelName:$token@$host:$port"
        log.info("Created LLM credential for model: {}", modelName)

        return mapOf(
            "model_name" to modelName,
            "token" to token,
            "mcp_uri" to mcpUri,
            "basic_auth" to "$modelName:$token"
        )
    }

    /** Get the latest JWT token for a model */
    fun getLatestToken(modelName: String): String? = llmCredentials[modelName]

    /** Extract roles from JWT claims (Keycloak format) */
    fun extractRoles(credential: JWTCredential): Set<String> {
        val roles = mutableSetOf<String>()

        // realm_access.roles
        credential.payload.getClaim("realm_access")?.asMap()?.let { realmAccess ->
            @Suppress("UNCHECKED_CAST")
            (realmAccess["roles"] as? List<String>)?.let { roles.addAll(it) }
        }

        // resource_access.{client_id}.roles
        credential.payload.getClaim("resource_access")?.asMap()?.let { resourceAccess ->
            @Suppress("UNCHECKED_CAST")
            (resourceAccess[keycloakClientId] as? Map<String, Any>)?.let { clientAccess ->
                @Suppress("UNCHECKED_CAST")
                (clientAccess["roles"] as? List<String>)?.let { roles.addAll(it) }
            }
        }

        // Map to our role set
        val result = mutableSetOf<String>()
        if (roles.any { it == "edit" || it == "editor" || it == "admin" }) result.add("edit")
        if (roles.any { it == "view" || it == "viewer" || it == "reader" }) result.add("view")
        if (result.isEmpty() && roles.isNotEmpty()) result.add("view") // default
        return result
    }

    /** Check if principal has a role */
    fun requireRole(principal: InkPrincipal?, role: String): Boolean {
        return principal != null && role in principal.roles
    }

    /** Get auth system status */
    fun getAuthStatus(): Map<String, Any> = mapOf(
        "keycloak_configured" to isConfigured(),
        "keycloak_realm_url" to (keycloakRealmUrl ?: "not set"),
        "keycloak_client_id" to keycloakClientId,
        "llm_credentials_count" to llmCredentials.size,
        "llm_models" to llmCredentials.keys.toList()
    )

    /** List LLM credentials (without tokens) */
    fun listLlmCredentials(): List<Map<String, String>> {
        return llmCredentials.keys.map { modelName ->
            mapOf("model_name" to modelName, "has_token" to "true")
        }
    }
}

/** Authenticated principal for Inky MCP */
data class InkPrincipal(
    val id: String,
    val name: String,
    val roles: Set<String>,
    val isLlm: Boolean,
    val email: String? = null
) : Principal
