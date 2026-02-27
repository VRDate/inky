package ink.mcp

import org.slf4j.LoggerFactory

/**
 * Emoji → asset category manifest.
 *
 * Bridges 2D emoji text tags (from ink story output) to 3D asset references
 * (animset, grip, mesh, audio) for Unity/BabylonJS renderers.
 *
 * Default categories: 🗡️ sword, 🛡️ shield, 🪄 staff, 🏹 bow, 🧙 wizard,
 * ⚗️ potion, 🗝️ key, 🗺️ map, 🪙 coin, 👑 crown.
 *
 * Ink tags format: `# mesh:🗡️`, `# anim:sword_slash`, `# voice:gandalf_en`
 */
class EmojiAssetManifest {

    private val log = LoggerFactory.getLogger(EmojiAssetManifest::class.java)

    data class AssetCategory(
        val emoji: String,
        val name: String,
        val type: String,
        val animSet: String,
        val gripType: String,
        val meshPrefix: String,
        val audioCategory: String
    )

    data class VoiceRef(
        val characterId: String,
        val language: String,
        val flacPath: String
    )

    data class AssetRef(
        val emoji: String,
        val category: AssetCategory,
        val meshPath: String,
        val animSetId: String,
        val voiceRef: VoiceRef? = null,
        val metadata: Map<String, String> = emptyMap()
    )

    private val categories = mutableMapOf<String, AssetCategory>()
    private val byName = mutableMapOf<String, AssetCategory>()
    private val byMeshPrefix = mutableMapOf<String, AssetCategory>()
    private val byAnimSet = mutableMapOf<String, AssetCategory>()

    init {
        val defaults = listOf(
            AssetCategory("🗡️", "sword", "weapon", "sword_1h", "main_hand", "weapon_sword", "sfx_metal"),
            AssetCategory("🛡️", "shield", "armor", "shield_buckler", "off_hand", "armor_shield", "sfx_metal"),
            AssetCategory("🪄", "staff", "weapon", "staff_2h", "two_hand", "weapon_staff", "sfx_wood"),
            AssetCategory("🏹", "bow", "weapon", "bow_2h", "two_hand", "weapon_bow", "sfx_string"),
            AssetCategory("🧙", "wizard", "character", "cast", "none", "char_wizard", "voice"),
            AssetCategory("⚗️", "potion", "consumable", "drink", "main_hand", "item_potion", "sfx_glass"),
            AssetCategory("🗝️", "key", "quest", "use_item", "main_hand", "item_key", "sfx_metal"),
            AssetCategory("🗺️", "map", "quest", "read", "two_hand", "item_map", "sfx_paper"),
            AssetCategory("🪙", "coin", "currency", "none", "none", "item_coin", "sfx_coin"),
            AssetCategory("👑", "crown", "armor", "equip_head", "none", "armor_crown", "sfx_metal")
        )
        for (cat in defaults) {
            registerCategory(cat)
        }
    }

    /** Register a custom asset category */
    fun registerCategory(category: AssetCategory) {
        categories[category.emoji] = category
        byName[category.name] = category
        byMeshPrefix[category.meshPrefix] = category
        byAnimSet[category.animSet] = category
    }

    /** Resolve emoji string → AssetRef */
    fun resolve(emoji: String): AssetRef? {
        val cat = categories[emoji] ?: return null
        return AssetRef(
            emoji = emoji,
            category = cat,
            meshPath = "${cat.meshPrefix}_01.glb",
            animSetId = cat.animSet
        )
    }

    /** Resolve by category name (e.g., "sword") → AssetRef */
    fun resolveByName(name: String): AssetRef? {
        val cat = byName[name] ?: return null
        return AssetRef(
            emoji = cat.emoji,
            category = cat,
            meshPath = "${cat.meshPrefix}_01.glb",
            animSetId = cat.animSet
        )
    }

    /** Resolve an ink tag key-value pair → AssetRef */
    fun resolveTag(key: String, value: String): AssetRef? {
        return when (key) {
            "mesh" -> {
                // value is emoji or mesh prefix
                resolve(value) ?: run {
                    val cat = byMeshPrefix.entries.find { value.startsWith(it.key) }?.value
                    cat?.let {
                        AssetRef(
                            emoji = it.emoji,
                            category = it,
                            meshPath = if (value.endsWith(".glb")) value else "$value.glb",
                            animSetId = it.animSet
                        )
                    }
                }
            }
            "anim" -> {
                val cat = byAnimSet.entries.find { value.startsWith(it.key) }?.value
                cat?.let {
                    AssetRef(
                        emoji = it.emoji,
                        category = it,
                        meshPath = "${it.meshPrefix}_01.glb",
                        animSetId = value
                    )
                }
            }
            "voice" -> {
                // value format: "characterId_language" e.g., "gandalf_en"
                val parts = value.split("_")
                if (parts.size >= 2) {
                    val characterId = parts.dropLast(1).joinToString("_")
                    val language = parts.last()
                    val wizardCat = byName["wizard"]!!
                    AssetRef(
                        emoji = wizardCat.emoji,
                        category = wizardCat,
                        meshPath = "${wizardCat.meshPrefix}_01.glb",
                        animSetId = wizardCat.animSet,
                        voiceRef = VoiceRef(characterId, language, "voices/$value.flac")
                    )
                } else null
            }
            else -> null
        }
    }

    /**
     * Parse ink tags from ContinueResult.tags into AssetRefs.
     *
     * Tag format: `# mesh:🗡️`, `# anim:sword_slash`, `# voice:gandalf_en`
     * Non-asset tags (e.g., `# author: tolkien`) are ignored.
     */
    fun parseInkTags(tags: List<String>): List<AssetRef> {
        return tags.mapNotNull { tag ->
            val cleaned = tag.removePrefix("#").trim()
            val colonIndex = cleaned.indexOf(':')
            if (colonIndex < 0) return@mapNotNull null

            val key = cleaned.substring(0, colonIndex).trim()
            val value = cleaned.substring(colonIndex + 1).trim()

            if (key in ASSET_TAG_KEYS) {
                resolveTag(key, value)
            } else null
        }
    }

    /** List all registered categories */
    fun allCategories(): List<AssetCategory> = categories.values.toList()

    companion object {
        private val ASSET_TAG_KEYS = setOf("mesh", "anim", "voice")
    }
}
