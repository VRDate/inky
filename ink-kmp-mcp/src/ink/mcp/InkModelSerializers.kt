package ink.mcp

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import kotlinx.serialization.json.*
import org.msgpack.jackson.dataformat.MessagePackFactory
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Multi-format serialization for ink.model proto messages.
 *
 * Drives all protocols from a single proto source of truth:
 *   - MCP JSON-RPC: proto → JSON via JsonFormat
 *   - RSocket: proto → msgpack via Jackson
 *   - WSS/SSE: proto → JSON
 *   - Yjs: proto field names = Y.Map keys
 *   - ink VARs: proto MdTable → generateVarDeclarations()
 *   - JSON Schema: proto descriptor → MCP inputSchema
 */
object InkModelSerializers {

    private val jsonPrinter: JsonFormat.Printer = JsonFormat.printer()
        .omittingInsignificantWhitespace()
        .preservingProtoFieldNames()

    private val jsonParser: JsonFormat.Parser = JsonFormat.parser()
        .ignoringUnknownFields()

    private val msgpackMapper: ObjectMapper = ObjectMapper(MessagePackFactory()).apply {
        registerModule(KotlinModule.Builder().build())
    }

    // ── MCP JSON-RPC ─────────────────────────────────────────────

    /** Serialize proto message → JSON string (for MCP JSON-RPC, SSE, WSS).
     *  Reverses Gson's HTML-safe escaping so that `=`, `<`, `>`, `&`
     *  appear as literal characters instead of `\\u003d` etc. */
    fun <T : Message> toJson(msg: T): String =
        jsonPrinter.print(msg).unescapeHtmlCharacters()

    /** Deserialize JSON string → proto message builder */
    fun <T : Message.Builder> fromJson(json: String, builder: T): T {
        jsonParser.merge(json, builder)
        return builder
    }

    // ── RSocket msgpack ──────────────────────────────────────────

    /** Serialize proto message → msgpack bytes (for RSocket transport) */
    fun <T : Message> toMsgpack(msg: T): ByteArray =
        msgpackMapper.writeValueAsBytes(msgpackMapper.readTree(toJson(msg)))

    /** Deserialize msgpack bytes → JSON string (then parse to proto) */
    fun msgpackToJson(bytes: ByteArray): String =
        msgpackMapper.readTree(bytes).toString()

    // ── Binary protobuf ──────────────────────────────────────────

    /** Serialize proto message → binary protobuf (for gRPC, compact storage) */
    fun <T : Message> toBytes(msg: T): ByteArray = msg.toByteArray()

    // ── JSON Schema (for MCP tool inputSchema) ───────────────────

    /** Generate JSON Schema object from proto message descriptor.
     *  Used to produce `inputSchema` for McpToolInfo. */
    fun toJsonSchema(descriptor: Descriptors.Descriptor): JsonObject {
        return buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                for (field in descriptor.fields) {
                    if (field.options.deprecated) continue
                    putJsonObject(field.name) {
                        put("type", protoTypeToJsonSchemaType(field))
                        put("description", field.name.replace("_", " "))
                    }
                }
            }
        }
    }

    /** Convert proto field type → JSON Schema type string */
    private fun protoTypeToJsonSchemaType(field: Descriptors.FieldDescriptor): String {
        if (field.isRepeated && !field.isMapField) return "array"
        if (field.isMapField) return "object"
        return when (field.type) {
            Descriptors.FieldDescriptor.Type.STRING -> "string"
            Descriptors.FieldDescriptor.Type.BOOL -> "boolean"
            Descriptors.FieldDescriptor.Type.INT32,
            Descriptors.FieldDescriptor.Type.INT64,
            Descriptors.FieldDescriptor.Type.SINT32,
            Descriptors.FieldDescriptor.Type.SINT64,
            Descriptors.FieldDescriptor.Type.UINT32,
            Descriptors.FieldDescriptor.Type.UINT64,
            Descriptors.FieldDescriptor.Type.FIXED32,
            Descriptors.FieldDescriptor.Type.FIXED64,
            Descriptors.FieldDescriptor.Type.SFIXED32,
            Descriptors.FieldDescriptor.Type.SFIXED64 -> "integer"
            Descriptors.FieldDescriptor.Type.FLOAT,
            Descriptors.FieldDescriptor.Type.DOUBLE -> "number"
            Descriptors.FieldDescriptor.Type.ENUM -> "string"
            Descriptors.FieldDescriptor.Type.MESSAGE -> "object"
            Descriptors.FieldDescriptor.Type.BYTES -> "string"
            else -> "string"
        }
    }

    // ── kotlinx.serialization bridge ─────────────────────────────

    /** Convert proto message → kotlinx JsonElement (for Ktor content negotiation) */
    fun <T : Message> toJsonElement(msg: T): JsonElement {
        val jsonString = toJson(msg)
        return Json.parseToJsonElement(jsonString)
    }

    // ── Internal ──────────────────────────────────────────────────

    /** Undo Gson HTML-safe escaping that protobuf's JsonFormat.Printer applies.
     *  Gson escapes `=` → `\u003d`, `<` → `\u003c`, `>` → `\u003e`, `&` → `\u0026`
     *  which breaks round-trip assertions on string values containing these chars
     *  (e.g. POI formulas like `=C2+C2*0.5`). */
    private fun String.unescapeHtmlCharacters(): String =
        replace("\\u003d", "=")
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")
            .replace("\\u0026", "&")
}
