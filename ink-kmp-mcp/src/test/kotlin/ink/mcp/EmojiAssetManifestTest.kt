package ink.mcp

import kotlin.test.*

class EmojiAssetManifestTest {

    private val manifest = EmojiAssetManifest()

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
}
