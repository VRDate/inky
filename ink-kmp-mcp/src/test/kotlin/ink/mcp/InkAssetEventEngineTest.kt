package ink.mcp

import kotlin.test.*

class InkAssetEventEngineTest {

    private val manifest = EmojiAssetManifest()
    private val bus = AssetEventBus()
    private val engine = InkAssetEventEngine(manifest, bus)

    // ── processStoryState ─────────────────────────────────────

    @Test
    fun `processStoryState resolves mesh tags to assets`() {
        val assets = engine.processStoryState("session-1", listOf("# mesh:🗡️"))

        assertEquals(1, assets.size)
        assertEquals("sword", assets[0].category.name)
        assertEquals("weapon_sword_01.glb", assets[0].meshPath)
    }

    @Test
    fun `processStoryState publishes tag event to bus`() {
        val received = mutableListOf<Any>()
        bus.subscribe("ink/story/tags") { _, event -> received.add(event) }

        engine.processStoryState("session-1", listOf("# mesh:🗡️"), "tavern")

        assertEquals(1, received.size)
        val event = received[0] as InkAssetEventEngine.InkTagEvent
        assertEquals("session-1", event.sessionId)
        assertEquals("tavern", event.knot)
        assertEquals(1, event.tags.size)
        assertEquals(1, event.resolvedAssets.size)
    }

    @Test
    fun `processStoryState publishes asset load request`() {
        val received = mutableListOf<Any>()
        bus.subscribe("ink/asset/load") { _, event -> received.add(event) }

        engine.processStoryState("session-1", listOf("# mesh:🗡️"))

        assertEquals(1, received.size)
        val request = received[0] as InkAssetEventEngine.AssetLoadRequest
        assertEquals("session-1", request.sessionId)
        assertEquals("sword", request.asset.category.name)
        assertEquals(InkAssetEventEngine.AssetLoadRequest.Priority.IMMEDIATE, request.priority)
    }

    @Test
    fun `processStoryState handles multiple tags`() {
        val assets = engine.processStoryState("session-1", listOf(
            "# mesh:🗡️",
            "# anim:sword_1h_slash",
            "# author: tolkien"  // non-asset tag, ignored
        ))

        assertEquals(2, assets.size)
    }

    @Test
    fun `processStoryState returns empty for no tags`() {
        val assets = engine.processStoryState("session-1", emptyList())
        assertTrue(assets.isEmpty())
    }

    @Test
    fun `processStoryState returns empty for unresolvable tags`() {
        val assets = engine.processStoryState("session-1", listOf("# author: tolkien"))
        assertTrue(assets.isEmpty())
    }

    // ── processTag ────────────────────────────────────────────

    @Test
    fun `processTag resolves single tag`() {
        val asset = engine.processTag("session-1", "# mesh:🗡️")
        assertNotNull(asset)
        assertEquals("sword", asset.category.name)
    }

    @Test
    fun `processTag publishes load request`() {
        val received = mutableListOf<Any>()
        bus.subscribe("ink/asset/load") { _, event -> received.add(event) }

        engine.processTag("session-1", "# mesh:🗡️")
        assertEquals(1, received.size)
    }

    @Test
    fun `processTag returns null for non-asset tag`() {
        val asset = engine.processTag("session-1", "# author: tolkien")
        assertNull(asset)
    }

    // ── processInventoryChange ────────────────────────────────

    @Test
    fun `processInventoryChange detects equipped items`() {
        val previous = emptyMap<String, String>()
        val current = mapOf("🗡️" to "Frostbane")

        val events = engine.processInventoryChange("session-1", previous, current)

        assertEquals(1, events.size)
        assertEquals(InkAssetEventEngine.InventoryChangeEvent.Action.EQUIP, events[0].action)
        assertEquals("🗡️", events[0].emoji)
        assertEquals("Frostbane", events[0].itemName)
        assertNotNull(events[0].asset)
        assertEquals("sword", events[0].asset!!.category.name)
    }

    @Test
    fun `processInventoryChange detects unequipped items`() {
        val previous = mapOf("🗡️" to "Frostbane")
        val current = emptyMap<String, String>()

        val events = engine.processInventoryChange("session-1", previous, current)

        assertEquals(1, events.size)
        assertEquals(InkAssetEventEngine.InventoryChangeEvent.Action.UNEQUIP, events[0].action)
        assertEquals("🗡️", events[0].emoji)
    }

    @Test
    fun `processInventoryChange detects swap (equip + unequip)`() {
        val previous = mapOf("🗡️" to "OldSword")
        val current = mapOf("🏹" to "NewBow")

        val events = engine.processInventoryChange("session-1", previous, current)

        assertEquals(2, events.size)
        val actions = events.map { it.action }.toSet()
        assertTrue(InkAssetEventEngine.InventoryChangeEvent.Action.EQUIP in actions)
        assertTrue(InkAssetEventEngine.InventoryChangeEvent.Action.UNEQUIP in actions)
    }

    @Test
    fun `processInventoryChange publishes to bus`() {
        val received = mutableListOf<Any>()
        bus.subscribe("ink/inventory/change") { _, event -> received.add(event) }

        engine.processInventoryChange(
            "session-1",
            emptyMap(),
            mapOf("🗡️" to "Frostbane")
        )

        assertEquals(1, received.size)
    }

    @Test
    fun `processInventoryChange returns empty when no change`() {
        val same = mapOf("🗡️" to "Frostbane")
        val events = engine.processInventoryChange("session-1", same, same)
        assertTrue(events.isEmpty())
    }

    // ── processVoice ──────────────────────────────────────────

    @Test
    fun `processVoice publishes to voice channel`() {
        val received = mutableListOf<Any>()
        bus.subscribe("ink/voice/synthesize") { _, event -> received.add(event) }

        val voiceRef = EmojiAssetManifest.VoiceRef("gandalf", "en", "voices/gandalf_en.flac")
        engine.processVoice("session-1", "You shall not pass!", voiceRef)

        assertEquals(1, received.size)
        val request = received[0] as InkAssetEventEngine.VoiceSynthRequest
        assertEquals("session-1", request.sessionId)
        assertEquals("You shall not pass!", request.text)
        assertEquals("gandalf", request.voiceRef.characterId)
    }

    // ── eventBus ──────────────────────────────────────────────

    @Test
    fun `eventBus returns underlying bus`() {
        assertSame(bus, engine.eventBus())
    }

    // ── session event tracking ────────────────────────────────

    @Test
    fun `sessionEvents returns all events for a session`() {
        engine.processStoryState("session-1", listOf("# mesh:🗡️"))
        engine.processStoryState("session-2", listOf("# mesh:🛡️"))

        val session1Events = bus.sessionEvents("session-1")
        assertTrue(session1Events.isNotEmpty())

        // Should only contain session-1 events
        for ((_, events) in session1Events) {
            for (event in events) {
                when (event) {
                    is InkAssetEventEngine.InkTagEvent -> assertEquals("session-1", event.sessionId)
                    is InkAssetEventEngine.AssetLoadRequest -> assertEquals("session-1", event.sessionId)
                }
            }
        }
    }
}
