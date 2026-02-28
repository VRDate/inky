package ink.mcp

import org.slf4j.LoggerFactory

/**
 * Emoji + Unicode symbol → asset category manifest.
 *
 * Parses the official Unicode emoji-test.txt and UnicodeData.txt formats,
 * supporting emoji, IPA Extensions, and other UTF symbol blocks.
 *
 * Three-layer loading:
 *   1. Unicode emoji-test.txt (group/subgroup hierarchy, thousands of emoji)
 *   2. UnicodeData.txt blocks (IPA Extensions, Spacing Modifiers, Currency, etc.)
 *   3. Game asset overrides (10 curated categories with animset/grip/mesh bindings)
 *
 * Game overrides always load last and take precedence over Unicode-parsed data.
 *
 * Ink tags format: `# mesh:🗡️`, `# anim:sword_slash`, `# voice:gandalf_en`
 *
 * Permanent URLs:
 *   - https://unicode.org/Public/emoji/latest/emoji-test.txt
 *   - https://unicode.org/Public/UNIDATA/UnicodeData.txt
 */
class EmojiAssetManifest(
    private val loader: UnicodeDataLoader = UnicodeDataLoader(),
    private val parser: UnicodeSymbolParser = UnicodeSymbolParser(),
    private val unicodeBlocks: Map<String, IntRange> = UnicodeSymbolParser.DEFAULT_BLOCKS,
    /** If true, load emoji-test.txt data (thousands of entries). */
    private val loadFullEmoji: Boolean = false,
    /** If true, load UnicodeData.txt blocks (IPA, symbols, etc.). */
    private val loadUnicodeBlocks: Boolean = false
) {

    private val log = LoggerFactory.getLogger(EmojiAssetManifest::class.java)

    data class AssetCategory(
        val emoji: String,
        val name: String,
        val type: String,
        val animSet: String = "",
        val gripType: String = "none",
        val meshPrefix: String = "",
        val audioCategory: String = "",
        // Unicode-derived metadata
        val unicodeGroup: String = "",
        val unicodeSubgroup: String = "",
        val codePoints: List<Int> = emptyList(),
        val unicodeVersion: String = "",
        val generalCategory: String = "",
        val isGameAsset: Boolean = false
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

    // ── Indices ────────────────────────────────────────────────

    private val categories = mutableMapOf<String, AssetCategory>()
    private val byName = mutableMapOf<String, AssetCategory>()
    private val byMeshPrefix = mutableMapOf<String, AssetCategory>()
    private val byAnimSet = mutableMapOf<String, AssetCategory>()
    private val byGroup = mutableMapOf<String, MutableList<AssetCategory>>()
    private val bySubgroup = mutableMapOf<String, MutableList<AssetCategory>>()
    private val byCodePoint = mutableMapOf<Int, AssetCategory>()

    init {
        // Layer 1: Unicode emoji data (if enabled)
        if (loadFullEmoji) {
            loadEmojiTestData()
        }

        // Layer 2: Unicode block data — IPA, symbols, etc. (if enabled)
        if (loadUnicodeBlocks) {
            loadUnicodeBlockData()
        }

        // Layer 3: Game asset overrides (always loaded, override Unicode entries)
        for (cat in GAME_DEFAULTS) {
            registerCategory(cat)
        }
    }

    // ── Unicode data loading ──────────────────────────────────

    private fun loadEmojiTestData() {
        val lines = loader.loadEmojiTest()
        if (lines.isEmpty()) {
            log.warn("No emoji-test.txt data available")
            return
        }
        val result = parser.parseEmojiTest(lines)
        var count = 0
        for (entry in result.entries) {
            if (entry.status != UnicodeSymbolParser.Status.FULLY_QUALIFIED) continue
            val cat = AssetCategory(
                emoji = entry.symbol,
                name = entry.name.lowercase().replace(" ", "_"),
                type = mapGroupToType(entry.group),
                unicodeGroup = entry.group,
                unicodeSubgroup = entry.subgroup,
                codePoints = entry.codePoints,
                unicodeVersion = entry.version
            )
            registerCategory(cat)
            count++
        }
        log.info("Loaded {} fully-qualified emoji from emoji-test.txt", count)
    }

    private fun loadUnicodeBlockData() {
        val lines = loader.loadUnicodeData()
        if (lines.isEmpty()) {
            log.warn("No UnicodeData.txt data available")
            return
        }
        val result = parser.parseUnicodeData(lines, unicodeBlocks)
        for (entry in result.entries) {
            val cat = AssetCategory(
                emoji = entry.symbol,
                name = entry.name.lowercase().replace(" ", "_"),
                type = "symbol",
                unicodeGroup = entry.group,
                unicodeSubgroup = entry.subgroup,
                codePoints = entry.codePoints,
                generalCategory = entry.generalCategory
            )
            registerCategory(cat)
        }
        log.info("Loaded {} symbols from UnicodeData.txt ({} blocks)",
            result.entries.size, unicodeBlocks.size)
    }

    /** Map Unicode emoji group names to asset type categories */
    private fun mapGroupToType(group: String): String = when (group) {
        "Smileys & Emotion" -> "emoji_face"
        "People & Body" -> "emoji_person"
        "Animals & Nature" -> "emoji_nature"
        "Food & Drink" -> "emoji_food"
        "Travel & Places" -> "emoji_travel"
        "Activities" -> "emoji_activity"
        "Objects" -> "emoji_object"
        "Symbols" -> "emoji_symbol"
        "Flags" -> "emoji_flag"
        "Component" -> "emoji_component"
        else -> "symbol"
    }

    // ── Registration ──────────────────────────────────────────

    /** Register a category, updating all lookup indices */
    fun registerCategory(category: AssetCategory) {
        categories[category.emoji] = category
        byName[category.name] = category
        if (category.meshPrefix.isNotEmpty()) byMeshPrefix[category.meshPrefix] = category
        if (category.animSet.isNotEmpty()) byAnimSet[category.animSet] = category
        if (category.unicodeGroup.isNotEmpty()) {
            byGroup.getOrPut(category.unicodeGroup) { mutableListOf() }.add(category)
        }
        if (category.unicodeSubgroup.isNotEmpty()) {
            bySubgroup.getOrPut(category.unicodeSubgroup) { mutableListOf() }.add(category)
        }
        for (cp in category.codePoints) {
            byCodePoint[cp] = category
        }
    }

    // ── Resolution ────────────────────────────────────────────

    /** Resolve emoji string → AssetRef */
    fun resolve(emoji: String): AssetRef? {
        val cat = categories[emoji] ?: return null
        return makeAssetRef(cat)
    }

    /** Resolve by category name (e.g., "sword") → AssetRef */
    fun resolveByName(name: String): AssetRef? {
        val cat = byName[name] ?: return null
        return makeAssetRef(cat)
    }

    /** Resolve by Unicode code point → AssetRef */
    fun resolveByCodePoint(codePoint: Int): AssetRef? {
        val cat = byCodePoint[codePoint] ?: return null
        return makeAssetRef(cat)
    }

    /** Resolve by Unicode group name → all categories in that group */
    fun resolveByGroup(group: String): List<AssetCategory> =
        byGroup[group] ?: emptyList()

    /** Resolve by Unicode subgroup name → all categories in that subgroup */
    fun resolveBySubgroup(subgroup: String): List<AssetCategory> =
        bySubgroup[subgroup] ?: emptyList()

    /** Resolve an ink tag key-value pair → AssetRef */
    fun resolveTag(key: String, value: String): AssetRef? {
        return when (key) {
            "mesh" -> {
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
                val parts = value.split("_")
                if (parts.size >= 2) {
                    val characterId = parts.dropLast(1).joinToString("_")
                    val language = parts.last()
                    val wizardCat = byName["wizard"]
                    wizardCat?.let {
                        AssetRef(
                            emoji = it.emoji,
                            category = it,
                            meshPath = "${it.meshPrefix}_01.glb",
                            animSetId = it.animSet,
                            voiceRef = VoiceRef(characterId, language, "voices/$value.flac")
                        )
                    }
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

    // ── Queries ───────────────────────────────────────────────

    /** List all registered categories */
    fun allCategories(): List<AssetCategory> = categories.values.toList()

    /** Game asset categories only (curated with animset/grip/mesh bindings) */
    fun gameCategories(): List<AssetCategory> =
        categories.values.filter { it.isGameAsset }

    /** All known Unicode groups */
    fun allGroups(): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        for ((group, cats) in byGroup) {
            result[group] = cats.map { it.unicodeSubgroup }.distinct().toMutableList()
        }
        return result
    }

    /** Total number of registered symbols/emoji */
    fun size(): Int = categories.size

    // ── Private helpers ───────────────────────────────────────

    private fun makeAssetRef(cat: AssetCategory): AssetRef = AssetRef(
        emoji = cat.emoji,
        category = cat,
        meshPath = if (cat.meshPrefix.isNotEmpty()) "${cat.meshPrefix}_01.glb" else "",
        animSetId = cat.animSet
    )

    companion object {
        private val ASSET_TAG_KEYS = setOf("mesh", "anim", "voice")

        /** The 10 curated game asset categories with full 3D asset bindings */
        val GAME_DEFAULTS = listOf(
            AssetCategory("🗡️", "sword", "weapon", "sword_1h", "main_hand",
                "weapon_sword", "sfx_metal", isGameAsset = true),
            AssetCategory("🛡️", "shield", "armor", "shield_buckler", "off_hand",
                "armor_shield", "sfx_metal", isGameAsset = true),
            AssetCategory("🪄", "staff", "weapon", "staff_2h", "two_hand",
                "weapon_staff", "sfx_wood", isGameAsset = true),
            AssetCategory("🏹", "bow", "weapon", "bow_2h", "two_hand",
                "weapon_bow", "sfx_string", isGameAsset = true),
            AssetCategory("🧙", "wizard", "character", "cast", "none",
                "char_wizard", "voice", isGameAsset = true),
            AssetCategory("⚗️", "potion", "consumable", "drink", "main_hand",
                "item_potion", "sfx_glass", isGameAsset = true),
            AssetCategory("🗝️", "key", "quest", "use_item", "main_hand",
                "item_key", "sfx_metal", isGameAsset = true),
            AssetCategory("🗺️", "map", "quest", "read", "two_hand",
                "item_map", "sfx_paper", isGameAsset = true),
            AssetCategory("🪙", "coin", "currency", "none", "none",
                "item_coin", "sfx_coin", isGameAsset = true),
            AssetCategory("👑", "crown", "armor", "equip_head", "none",
                "armor_crown", "sfx_metal", isGameAsset = true)
        )
    }
}
