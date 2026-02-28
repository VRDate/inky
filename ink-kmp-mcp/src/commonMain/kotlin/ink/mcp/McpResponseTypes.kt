package ink.mcp

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * MCP tool response types — `@Serializable` data classes matching the JSON wire format
 * returned by each of the 81 MCP tools in `McpTools.kt`.
 *
 * These are the *client-side* parse targets. The JVM server builds JSON from engine types
 * (`InkEngine.ContinueResult`, etc.) — the client deserializes that JSON into these types.
 */

// ════════════════════════════════════════════════════════════════════
// INK COMPILATION & PLAYBACK
// ════════════════════════════════════════════════════════════════════

@Serializable
data class CompileInkResponse(
    val success: Boolean,
    val json: String? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
data class ChoiceResponse(
    val index: Int,
    val text: String,
    val tags: List<String> = emptyList()
)

@Serializable
data class ContinueResponse(
    @SerialName("session_id") val sessionId: String,
    val text: String,
    @SerialName("can_continue") val canContinue: Boolean,
    val choices: List<ChoiceResponse> = emptyList(),
    val tags: List<String> = emptyList()
)

@Serializable
data class VariableResponse(
    val name: String,
    val value: JsonElement? = null
)

@Serializable
data class SetVariableResponse(
    val ok: Boolean,
    val name: String
)

@Serializable
data class SaveStateResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("state_json") val stateJson: String
)

@Serializable
data class LoadStateResponse(
    val ok: Boolean,
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class EvalFunctionResponse(
    val function: String,
    val result: JsonElement? = null
)

@Serializable
data class GlobalTagsResponse(
    val tags: List<String>
)

@Serializable
data class ListSessionsResponse(
    val sessions: List<String>
)

@Serializable
data class EndSessionResponse(
    val ok: Boolean,
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class BidiResponse(
    val result: String
)

// ════════════════════════════════════════════════════════════════════
// DEBUG
// ════════════════════════════════════════════════════════════════════

@Serializable
data class BreakpointResponse(
    val id: String,
    val type: String,
    val target: String,
    val enabled: Boolean
)

@Serializable
data class RemoveBreakpointResponse(
    val ok: Boolean,
    @SerialName("breakpoint_id") val breakpointId: String
)

@Serializable
data class DebugStepResponse(
    val text: String,
    @SerialName("can_continue") val canContinue: Boolean,
    val choices: List<ChoiceResponse> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("step_number") val stepNumber: Int,
    @SerialName("is_paused") val isPaused: Boolean,
    @SerialName("hit_breakpoint") val hitBreakpoint: BreakpointResponse? = null,
    @SerialName("watch_changes") val watchChanges: JsonObject? = null
)

@Serializable
data class AddWatchResponse(
    val variable: String,
    @SerialName("current_value") val currentValue: JsonElement? = null
)

// start_debug, debug_inspect, debug_trace use mapToJson() → JsonObject

// ════════════════════════════════════════════════════════════════════
// EDIT
// ════════════════════════════════════════════════════════════════════

@Serializable
data class SectionInfo(
    val name: String,
    val type: String,
    @SerialName("start_line") val startLine: Int,
    @SerialName("end_line") val endLine: Int,
    @SerialName("line_count") val lineCount: Int = 0,
    val content: String = "",
    val parent: String? = null,
    val parameters: List<String> = emptyList()
)

@Serializable
data class VariableInfo(
    val name: String,
    val type: String,
    @SerialName("initial_value") val initialValue: String,
    val line: Int
)

@Serializable
data class DivertInfo(
    val target: String,
    val line: Int
)

@Serializable
data class InkStructureResponse(
    val sections: List<SectionInfo>,
    val variables: List<VariableInfo>,
    val includes: List<String>,
    @SerialName("total_lines") val totalLines: Int,
    @SerialName("divert_count") val divertCount: Int,
    val diverts: List<DivertInfo> = emptyList()
)

@Serializable
data class EditResponse(
    val ok: Boolean,
    val source: String
)

// ink_stats uses mapToJson() → JsonObject

// ════════════════════════════════════════════════════════════════════
// INK+MARKDOWN
// ════════════════════════════════════════════════════════════════════

@Serializable
data class InkFileResponse(
    val name: String,
    @SerialName("ink_source") val inkSource: String,
    @SerialName("header_level") val headerLevel: Int
)

@Serializable
data class MdTableResponse(
    val name: String,
    val columns: List<String>,
    val rows: List<JsonObject>
)

@Serializable
data class ParseInkMdResponse(
    val files: List<InkFileResponse>,
    val tables: List<MdTableResponse> = emptyList()
)

@Serializable
data class RenderInkMdResponse(
    val files: Map<String, String>
)

@Serializable
data class CompileInkMdResultEntry(
    val file: String,
    val success: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
data class CompileInkMdResponse(
    val results: List<CompileInkMdResultEntry>
)

// ════════════════════════════════════════════════════════════════════
// PLANTUML
// ════════════════════════════════════════════════════════════════════

@Serializable
data class PumlResponse(
    val puml: String,
    val mode: String? = null
)

@Serializable
data class SvgResponse(
    val svg: String,
    val puml: String? = null,
    val mode: String? = null
)

@Serializable
data class TocResponse(
    val toc: String
)

@Serializable
data class TocPumlResponse(
    val puml: String
)

// ════════════════════════════════════════════════════════════════════
// LLM
// ════════════════════════════════════════════════════════════════════

@Serializable
data class ChatResponse(
    val response: String
)

@Serializable
data class GenerateInkResponse(
    @SerialName("ink_source") val inkSource: String,
    val compiles: Boolean,
    @SerialName("compile_errors") val compileErrors: List<String> = emptyList()
)

@Serializable
data class ReviewInkResponse(
    val review: String
)

@Serializable
data class TranslateInkResponse(
    @SerialName("translated_ink") val translatedInk: String
)

@Serializable
data class GenerateCompilePlayResponse(
    val stage: String,
    @SerialName("ink_source") val inkSource: String,
    @SerialName("session_id") val sessionId: String? = null,
    val text: String? = null,
    @SerialName("can_continue") val canContinue: Boolean? = null,
    val choices: List<ChoiceResponse> = emptyList(),
    val errors: List<String> = emptyList()
)

@Serializable
data class ModelEntry(
    val id: String,
    val parameters: String,
    val quantization: String,
    val architecture: String,
    @SerialName("size_gb") val sizeGb: Double,
    @SerialName("min_vram_gb") val minVramGb: Int,
    @SerialName("jlama_compatible") val jlamaCompatible: Boolean,
    val description: String,
    val url: String
)

@Serializable
data class ListModelsResponse(
    val models: List<ModelEntry>,
    val recommended: String? = null,
    @SerialName("vram_filter_gb") val vramFilterGb: Int? = null
)

@Serializable
data class LoadModelResponse(
    val ok: Boolean,
    @SerialName("model_id") val modelId: String,
    val message: String
)

// model_info uses mapToJson() → JsonObject

// ════════════════════════════════════════════════════════════════════
// SERVICES
// ════════════════════════════════════════════════════════════════════

@Serializable
data class ListServicesResponse(
    val services: List<JsonObject>,
    @SerialName("connected_service") val connectedService: String? = null
)

@Serializable
data class ConnectServiceResponse(
    val ok: Boolean,
    val service: String,
    val model: String,
    val message: String
)

// ════════════════════════════════════════════════════════════════════
// COLLABORATION
// ════════════════════════════════════════════════════════════════════

@Serializable
data class CollabStatusResponse(
    val documents: List<JsonObject>,
    @SerialName("total_clients") val totalClients: Int
)

// collab_info uses mapToJson() → JsonObject

// ════════════════════════════════════════════════════════════════════
// CALENDAR
// ════════════════════════════════════════════════════════════════════

@Serializable
data class ListEventsResponse(
    val events: List<JsonObject>
)

@Serializable
data class ExportIcsResponse(
    val ics: String
)

// create_event, import_ics use mapToJson() → JsonObject

// ════════════════════════════════════════════════════════════════════
// VCARD / PRINCIPALS
// ════════════════════════════════════════════════════════════════════

@Serializable
data class ListPrincipalsResponse(
    val principals: List<JsonObject>
)

// create_principal, get_principal, delete_principal use mapToJson() → JsonObject

// ════════════════════════════════════════════════════════════════════
// AUTH
// ════════════════════════════════════════════════════════════════════

// auth_status, create_llm_credential use mapToJson() → JsonObject

// ════════════════════════════════════════════════════════════════════
// WEBDAV
// ════════════════════════════════════════════════════════════════════

@Serializable
data class WebDavListResponse(
    val path: String,
    @SerialName("is_shared") val isShared: Boolean = false,
    val files: List<JsonObject>
)

@Serializable
data class WebDavListBackupsResponse(
    val path: String,
    val backups: List<JsonObject>
)

// webdav_get, webdav_put, webdav_delete, webdav_mkdir, webdav_sync,
// webdav_backup, webdav_restore, webdav_working_copy use mapToJson() → JsonObject

// ════════════════════════════════════════════════════════════════════
// ASSETS
// ════════════════════════════════════════════════════════════════════

@Serializable
data class EmojiCategoryResponse(
    val name: String,
    val type: String,
    val animSet: String,
    val gripType: String,
    val meshPrefix: String,
    val audioCategory: String
)

@Serializable
data class ResolveEmojiResponse(
    val emoji: String,
    val category: EmojiCategoryResponse,
    val meshPath: String,
    val animSetId: String
)

@Serializable
data class VoiceRefResponse(
    @SerialName("character_id") val characterId: String,
    val language: String,
    @SerialName("flac_path") val flacPath: String
)

@Serializable
data class ParsedAssetTagResponse(
    val emoji: String,
    val category: String,
    val meshPath: String,
    val animSetId: String,
    @SerialName("voice_ref") val voiceRef: VoiceRefResponse? = null
)

@Serializable
data class AssetEventResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("tags_processed") val tagsProcessed: Int,
    @SerialName("assets_resolved") val assetsResolved: Int,
    val assets: List<ParsedAssetTagResponse>,
    @SerialName("channels_published") val channelsPublished: List<String>
)

@Serializable
data class ListAssetEventsResponse(
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("total_events") val totalEvents: Int? = null,
    val channels: JsonObject? = null,
    val channel: String? = null,
    val count: Int? = null,
    val events: List<String>? = null,
    @SerialName("active_channels") val activeChannels: List<String>? = null,
    @SerialName("event_counts") val eventCounts: JsonObject? = null,
    @SerialName("subscriber_counts") val subscriberCounts: JsonObject? = null
)

@Serializable
data class UnicodeBlockEntry(
    val emoji: String,
    val name: String,
    val type: String,
    val unicodeGroup: String,
    val unicodeSubgroup: String,
    val codePoints: String,
    val isGameAsset: Boolean,
    val generalCategory: String? = null,
    val animSet: String? = null,
    val meshPrefix: String? = null
)

@Serializable
data class ResolveUnicodeBlockResponse(
    val block: String,
    val count: Int,
    val entries: List<UnicodeBlockEntry>
)

@Serializable
data class ListEmojiGroupsResponse(
    val message: String? = null,
    val gameCategories: Int? = null,
    val totalGroups: Int? = null,
    val groups: JsonObject? = null
)

@Serializable
data class GenerateTableResponse(
    val name: String,
    val columns: List<String>,
    val rows: List<JsonObject>
)

// generate_story_md returns raw markdown text (not JSON)

// ════════════════════════════════════════════════════════════════════
// GENERIC OK RESPONSE
// ════════════════════════════════════════════════════════════════════

@Serializable
data class OkResponse(
    val ok: Boolean
)
