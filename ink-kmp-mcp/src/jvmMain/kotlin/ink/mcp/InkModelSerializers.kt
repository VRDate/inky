package ink.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.squareup.moshi.Moshi
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireField
import com.squareup.wire.WireJsonAdapterFactory
import kotlinx.serialization.json.*
import org.msgpack.jackson.dataformat.MessagePackFactory

/**
 * Multi-format serialization for ink.model Wire messages.
 *
 * Drives all protocols from a single proto source of truth:
 *   - MCP JSON-RPC: Wire → JSON via Moshi
 *   - RSocket: Wire → msgpack via Jackson
 *   - WSS/SSE: Wire → JSON
 *   - Yjs: Wire field names = Y.Map keys
 *   - JSON Schema: Wire @WireField annotations → MCP inputSchema
 */
object InkModelSerializers {

    @PublishedApi
    internal val moshi: Moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory())
        .build()

    private val msgpackMapper: ObjectMapper = ObjectMapper(MessagePackFactory()).apply {
        registerModule(KotlinModule.Builder().build())
    }

    // ── MCP JSON-RPC ─────────────────────────────────────────────

    /** Serialize Wire message → JSON string (for MCP JSON-RPC, SSE, WSS) */
    fun <T : Message<T, *>> toJson(msg: T): String {
        @Suppress("UNCHECKED_CAST")
        val adapter = moshi.adapter(msg::class.java as Class<T>)
        return adapter.toJson(msg)
    }

    /** Deserialize JSON string → Wire message */
    inline fun <reified T : Message<T, *>> fromJson(json: String): T {
        val adapter = moshi.adapter(T::class.java)
        return adapter.fromJson(json) ?: throw IllegalArgumentException("Failed to parse JSON to ${T::class.simpleName}")
    }

    // ── RSocket msgpack ──────────────────────────────────────────

    /** Serialize Wire message → msgpack bytes (for RSocket transport) */
    fun <T : Message<T, *>> toMsgpack(msg: T): ByteArray =
        msgpackMapper.writeValueAsBytes(msgpackMapper.readTree(toJson(msg)))

    /** Deserialize msgpack bytes → JSON string (then parse to Wire message) */
    fun msgpackToJson(bytes: ByteArray): String =
        msgpackMapper.readTree(bytes).toString()

    // ── Binary protobuf ──────────────────────────────────────────

    /** Serialize Wire message → binary protobuf (for gRPC, compact storage) */
    fun <T : Message<T, *>> toBytes(msg: T): ByteArray = msg.encode()

    // ── JSON Schema (for MCP tool inputSchema) ───────────────────

    /** Generate JSON Schema object from Wire message class.
     *  Inspects @WireField annotations on constructor fields.
     *  Used to produce `inputSchema` for McpToolInfo. */
    fun toJsonSchema(type: Class<out Message<*, *>>): JsonObject {
        return buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                for (field in type.declaredFields) {
                    val wireField = field.getAnnotation(WireField::class.java) ?: continue
                    putJsonObject(field.name) {
                        put("type", wireAdapterToJsonSchemaType(wireField))
                        put("description", field.name.replace("_", " "))
                    }
                }
            }
        }
    }

    /** Convert Wire adapter string → JSON Schema type */
    private fun wireAdapterToJsonSchemaType(field: WireField): String {
        if (field.label == WireField.Label.REPEATED) return "array"
        val adapter = field.adapter
        return when {
            adapter.endsWith("#STRING") -> "string"
            adapter.endsWith("#BOOL") -> "boolean"
            adapter.endsWith("#INT32") || adapter.endsWith("#INT64") ||
            adapter.endsWith("#SINT32") || adapter.endsWith("#SINT64") ||
            adapter.endsWith("#UINT32") || adapter.endsWith("#UINT64") ||
            adapter.endsWith("#FIXED32") || adapter.endsWith("#FIXED64") ||
            adapter.endsWith("#SFIXED32") || adapter.endsWith("#SFIXED64") -> "integer"
            adapter.endsWith("#FLOAT") || adapter.endsWith("#DOUBLE") -> "number"
            adapter.endsWith("#BYTES") -> "string"
            else -> "object" // message or enum types
        }
    }

    // ── kotlinx.serialization bridge ─────────────────────────────

    /** Convert Wire message → kotlinx JsonElement (for Ktor content negotiation) */
    fun <T : Message<T, *>> toJsonElement(msg: T): JsonElement {
        val jsonString = toJson(msg)
        return Json.parseToJsonElement(jsonString)
    }
}
