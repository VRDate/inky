package ink.mcp

import org.slf4j.LoggerFactory

/**
 * JVM adapter implementing [McpAssetOps].
 *
 * Delegates to [EmojiAssetManifest], [InkFakerEngine], [InkAssetEventEngine],
 * and [AssetEventBus] for asset pipeline operations.
 */
class JvmAssetOps(
    private val manifest: EmojiAssetManifest,
    private val fakerEngine: InkFakerEngine,
    private val assetEventEngine: InkAssetEventEngine,
    private val bus: AssetEventBus
) : McpAssetOps {

    private val log = LoggerFactory.getLogger(JvmAssetOps::class.java)

    override fun resolveEmoji(emoji: String): Map<String, Any>? {
        val ref = manifest.resolve(emoji) ?: return null
        return assetRefToMap(ref)
    }

    override fun parseAssetTags(tags: List<String>): List<Map<String, Any>> {
        return manifest.parseInkTags(tags).map { assetRefToMap(it) }
    }

    override fun generateItems(seed: Long, count: Int, level: Int, categories: List<String>): Map<String, Any> {
        val config = InkFakerEngine.FakerConfig(
            seed = seed,
            count = count,
            level = level,
            categories = categories
        )
        val table = fakerEngine.generateItems(config)
        return mdTableToMap(table)
    }

    override fun generateCharacters(seed: Long, count: Int): Map<String, Any> {
        val config = InkFakerEngine.FakerConfig(
            seed = seed,
            count = count
        )
        val table = fakerEngine.generateCharacters(config)
        return mdTableToMap(table)
    }

    override fun generateStoryMd(seed: Long, level: Int, count: Int): String {
        val config = InkFakerEngine.FakerConfig(
            seed = seed,
            level = level,
            count = count
        )
        return fakerEngine.generateStoryMd(config)
    }

    override fun evaluateFormulas(tables: List<InkMdEngine.MdTable>): List<Map<String, Any>> {
        return tables.map { table ->
            val evaluated = fakerEngine.evaluateFormulas(table)
            mdTableToMap(evaluated)
        }
    }

    override fun listEmojiGroups(filter: String?): Map<String, Any> {
        val groups = manifest.allGroups()
        val filtered = if (filter != null) {
            groups.filterKeys { it.contains(filter, ignoreCase = true) }
        } else {
            groups
        }
        return mapOf(
            "groups" to filtered,
            "total_groups" to filtered.size,
            "total_categories" to manifest.size()
        )
    }

    override fun resolveUnicodeBlock(block: String): Map<String, Any> {
        val categories = manifest.resolveByGroup(block)
        return mapOf(
            "block" to block,
            "count" to categories.size,
            "entries" to categories.map { cat ->
                mapOf(
                    "emoji" to cat.emoji,
                    "name" to cat.name,
                    "type" to cat.type,
                    "unicode_group" to cat.unicodeGroup,
                    "unicode_subgroup" to cat.unicodeSubgroup
                )
            }
        )
    }

    override fun emitAssetEvent(sessionId: String, tags: List<String>, knot: String): Map<String, Any> {
        val resolved = assetEventEngine.processStoryState(sessionId, tags, knot)
        return mapOf(
            "session_id" to sessionId,
            "resolved_count" to resolved.size,
            "assets" to resolved.map { assetRefToMap(it) }
        )
    }

    override fun listAssetEvents(sessionId: String?, channel: String?, limit: Int): Map<String, Any> {
        return if (sessionId != null) {
            val events = bus.sessionEvents(sessionId)
            mapOf(
                "session_id" to sessionId,
                "channels" to events.keys.toList(),
                "total_events" to events.values.sumOf { it.size },
                "events" to events.mapValues { (_, evts) ->
                    evts.takeLast(limit).map { it.toString() }
                }
            )
        } else if (channel != null) {
            val events = bus.recentEvents(channel, limit)
            mapOf(
                "channel" to channel,
                "count" to events.size,
                "events" to events.map { it.toString() }
            )
        } else {
            val channels = bus.activeChannels()
            mapOf(
                "active_channels" to channels.toList(),
                "channel_count" to channels.size
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun assetRefToMap(ref: EmojiAssetManifest.AssetRef): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "emoji" to ref.emoji,
            "category" to ref.category.name,
            "type" to ref.category.type,
            "mesh_path" to ref.meshPath,
            "anim_set" to ref.animSetId
        )
        ref.voiceRef?.let {
            map["voice"] = mapOf(
                "character_id" to it.characterId,
                "language" to it.language,
                "flac_path" to it.flacPath
            )
        }
        if (ref.metadata.isNotEmpty()) {
            map["metadata"] = ref.metadata
        }
        return map
    }

    private fun mdTableToMap(table: InkMdEngine.MdTable): Map<String, Any> {
        return mapOf(
            "name" to table.name,
            "columns" to table.columns,
            "rows" to table.rows
        )
    }
}
