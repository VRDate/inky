package ink.mcp

import ink.kt.TestResources
import kotlin.test.*

class EmojiAssetManifestTest {

    private val manifest = EmojiAssetManifest()

    // ── Backward compatibility (original 10 game categories) ──

    @Test
    fun `resolve returns AssetRef for known emoji`() {
        val ref = manifest.resolve("🗡️")
        assertNotNull(ref, "Should resolve sword emoji")
        assertEquals("sword", ref.category.name)
        assertEquals("weapon", ref.category.type)
        assertEquals("sword_1h", ref.animSetId)
        assertEquals("weapon_sword_01.glb", ref.meshPath)
    }

    @Test
    fun `resolve returns null for unknown emoji`() {
        val ref = manifest.resolve("🦄")
        assertNull(ref, "Unknown emoji should return null")
    }

    @Test
    fun `resolveByName returns AssetRef`() {
        val ref = manifest.resolveByName("sword")
        assertNotNull(ref)
        assertEquals("🗡️", ref.emoji)
        assertEquals("weapon_sword_01.glb", ref.meshPath)
    }

    @Test
    fun `resolveByName returns null for unknown name`() {
        assertNull(manifest.resolveByName("unicorn"))
    }

    @Test
    fun `parseInkTags parses mesh emoji tags`() {
        val refs = manifest.parseInkTags(listOf("# mesh:🗡️"))
        assertEquals(1, refs.size)
        assertEquals("sword", refs[0].category.name)
    }

    @Test
    fun `parseInkTags parses anim tags`() {
        val refs = manifest.parseInkTags(listOf("# anim:sword_1h_slash"))
        assertEquals(1, refs.size)
        assertEquals("sword", refs[0].category.name)
        assertEquals("sword_1h_slash", refs[0].animSetId)
    }

    @Test
    fun `parseInkTags parses voice tags`() {
        val refs = manifest.parseInkTags(listOf("# voice:gandalf_en"))
        assertEquals(1, refs.size)
        assertNotNull(refs[0].voiceRef)
        assertEquals("gandalf", refs[0].voiceRef!!.characterId)
        assertEquals("en", refs[0].voiceRef!!.language)
        assertEquals("voices/gandalf_en.flac", refs[0].voiceRef!!.flacPath)
    }

    @Test
    fun `parseInkTags ignores non-asset tags`() {
        val refs = manifest.parseInkTags(listOf("# author: tolkien", "# version: 1"))
        assertTrue(refs.isEmpty(), "Non-asset tags should be ignored")
    }

    @Test
    fun `parseInkTags handles multiple tags`() {
        val refs = manifest.parseInkTags(listOf(
            "# mesh:🗡️",
            "# anim:sword_1h_equip",
            "# author: tolkien"
        ))
        assertEquals(2, refs.size, "Should resolve 2 asset tags, ignore 1 non-asset")
    }

    @Test
    fun `allCategories returns 10 defaults`() {
        val cats = manifest.allCategories()
        assertEquals(10, cats.size)
        val names = cats.map { it.name }
        assertTrue("sword" in names)
        assertTrue("shield" in names)
        assertTrue("wizard" in names)
        assertTrue("potion" in names)
        assertTrue("coin" in names)
        assertTrue("crown" in names)
    }

    @Test
    fun `registerCategory adds custom category`() {
        val custom = EmojiAssetManifest.AssetCategory(
            emoji = "🐉", name = "dragon", type = "creature",
            animSet = "fly", gripType = "none",
            meshPrefix = "creature_dragon", audioCategory = "sfx_roar"
        )
        manifest.registerCategory(custom)
        val ref = manifest.resolve("🐉")
        assertNotNull(ref)
        assertEquals("dragon", ref.category.name)
        assertEquals("creature_dragon_01.glb", ref.meshPath)
    }

    @Test
    fun `resolveTag mesh with prefix resolves to category`() {
        val ref = manifest.resolveTag("mesh", "weapon_sword_flame")
        assertNotNull(ref)
        assertEquals("sword", ref.category.name)
        assertEquals("weapon_sword_flame.glb", ref.meshPath)
    }

    // ── Game asset flag ───────────────────────────────────────

    @Test
    fun `default constructor game categories have isGameAsset true`() {
        for (cat in manifest.allCategories()) {
            assertTrue(cat.isGameAsset, "${cat.name} should be a game asset")
        }
    }

    @Test
    fun `gameCategories returns only game assets`() {
        val gameCats = manifest.gameCategories()
        assertEquals(10, gameCats.size)
        assertTrue(gameCats.all { it.isGameAsset })
    }

    // ── Extended manifest with emoji-test.txt ─────────────────

    @Test
    fun `full emoji manifest resolves standard emoji from snippet`() {
        val m = createSnippetManifest(loadFullEmoji = true)
        val ref = m.resolve("😀")
        assertNotNull(ref, "Should resolve grinning face from emoji-test-snippet.txt")
        assertEquals("Smileys & Emotion", ref.category.unicodeGroup)
        assertEquals("face-smiling", ref.category.unicodeSubgroup)
        assertEquals("emoji_face", ref.category.type)
    }

    @Test
    fun `full emoji manifest preserves group hierarchy`() {
        val m = createSnippetManifest(loadFullEmoji = true)
        val groups = m.allGroups()
        assertTrue(groups.containsKey("Smileys & Emotion"), "Should have Smileys group")
        assertTrue(groups.containsKey("Objects"), "Should have Objects group")
        assertTrue(groups.containsKey("Animals & Nature"), "Should have Animals group")
    }

    @Test
    fun `game overrides take precedence over Unicode data`() {
        val m = createSnippetManifest(loadFullEmoji = true)
        val ref = m.resolve("🗡️")
        assertNotNull(ref)
        assertEquals("sword", ref.category.name)
        assertTrue(ref.category.isGameAsset, "Game override should win")
        assertEquals("sword_1h", ref.category.animSet)
        assertEquals("weapon_sword_01.glb", ref.meshPath)
    }

    @Test
    fun `full emoji manifest has more entries than game-only`() {
        val gameOnly = EmojiAssetManifest()
        val full = createSnippetManifest(loadFullEmoji = true)
        assertTrue(full.size() > gameOnly.size(),
            "Full manifest (${full.size()}) should have more than game-only (${gameOnly.size()})")
    }

    @Test
    fun `resolveByGroup returns all entries in group`() {
        val m = createSnippetManifest(loadFullEmoji = true)
        val smileys = m.resolveByGroup("Smileys & Emotion")
        assertTrue(smileys.isNotEmpty(), "Should have entries in Smileys & Emotion")
        assertTrue(smileys.all { it.unicodeGroup == "Smileys & Emotion" })
    }

    @Test
    fun `resolveBySubgroup returns matching entries`() {
        val m = createSnippetManifest(loadFullEmoji = true)
        val faceSmiling = m.resolveBySubgroup("face-smiling")
        assertTrue(faceSmiling.isNotEmpty(), "Should have face-smiling entries")
    }

    // ── Extended manifest with UnicodeData.txt (IPA) ──────────

    @Test
    fun `unicode block loading parses IPA Extensions`() {
        val m = createSnippetManifest(
            loadUnicodeBlocks = true,
            unicodeBlocks = mapOf(UnicodeSymbolParser.IPA_EXTENSIONS)
        )
        val schwaRef = m.resolveByCodePoint(0x0259)
        assertNotNull(schwaRef, "Should resolve schwa from IPA Extensions")
        assertEquals("IPA Extensions", schwaRef.category.unicodeGroup)
        assertEquals("symbol", schwaRef.category.type)
    }

    @Test
    fun `IPA entries have correct general category`() {
        val m = createSnippetManifest(
            loadUnicodeBlocks = true,
            unicodeBlocks = mapOf(UnicodeSymbolParser.IPA_EXTENSIONS)
        )
        val schwaRef = m.resolveByCodePoint(0x0259)
        assertNotNull(schwaRef)
        assertEquals("Ll", schwaRef.category.generalCategory)
    }

    @Test
    fun `IPA entries are not game assets`() {
        val m = createSnippetManifest(
            loadUnicodeBlocks = true,
            unicodeBlocks = mapOf(UnicodeSymbolParser.IPA_EXTENSIONS)
        )
        val ipaEntries = m.resolveByGroup("IPA Extensions")
        assertTrue(ipaEntries.isNotEmpty())
        assertTrue(ipaEntries.none { it.isGameAsset }, "IPA entries should not be game assets")
    }

    @Test
    fun `currency symbols load from UnicodeData`() {
        val m = createSnippetManifest(
            loadUnicodeBlocks = true,
            unicodeBlocks = mapOf(UnicodeSymbolParser.CURRENCY_SYMBOLS)
        )
        val euroRef = m.resolveByCodePoint(0x20AC)
        assertNotNull(euroRef, "Should resolve Euro sign")
        assertEquals("Currency Symbols", euroRef.category.unicodeGroup)

        val sheqelRef = m.resolveByCodePoint(0x20AA)
        assertNotNull(sheqelRef, "Should resolve New Sheqel sign")
    }

    @Test
    fun `combined emoji and unicode blocks load together`() {
        val m = createSnippetManifest(
            loadFullEmoji = true,
            loadUnicodeBlocks = true,
            unicodeBlocks = mapOf(
                UnicodeSymbolParser.IPA_EXTENSIONS,
                UnicodeSymbolParser.CURRENCY_SYMBOLS
            )
        )
        assertNotNull(m.resolve("😀"), "Should resolve emoji")
        assertNotNull(m.resolveByCodePoint(0x0259), "Should resolve IPA schwa")
        assertNotNull(m.resolveByCodePoint(0x20AC), "Should resolve Euro")
        val sword = m.resolve("🗡️")
        assertNotNull(sword)
        assertTrue(sword.category.isGameAsset)
    }

    // ── helpers ───────────────────────────────────────────────

    private fun createSnippetManifest(
        loadFullEmoji: Boolean = false,
        loadUnicodeBlocks: Boolean = false,
        unicodeBlocks: Map<String, IntRange> = UnicodeSymbolParser.DEFAULT_BLOCKS
    ): EmojiAssetManifest {
        val loader = TestResourceLoader()
        return EmojiAssetManifest(
            loader = loader,
            loadFullEmoji = loadFullEmoji,
            loadUnicodeBlocks = loadUnicodeBlocks,
            unicodeBlocks = unicodeBlocks
        )
    }

    private class TestResourceLoader : UnicodeDataLoader(
        cacheDir = java.io.File(TestResources.tempDir("inky-test-unicode-cache"))
    ) {
        override fun loadEmojiTest(): List<String> =
            TestResources.loadTextOrNull("unicode/emoji-test-snippet.txt")?.lines() ?: emptyList()

        override fun loadUnicodeData(): List<String> =
            TestResources.loadTextOrNull("unicode/UnicodeData-snippet.txt")?.lines() ?: emptyList()
    }
}
