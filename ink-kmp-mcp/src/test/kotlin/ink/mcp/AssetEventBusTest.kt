package ink.mcp

import kotlin.test.*

class AssetEventBusTest {

    private val bus = AssetEventBus()

    @Test
    fun `publish delivers to channel subscriber`() {
        val received = mutableListOf<Any>()
        bus.subscribe("ink/story/tags") { _, event -> received.add(event) }

        bus.publish("ink/story/tags", "test-event")
        assertEquals(1, received.size)
        assertEquals("test-event", received[0])
    }

    @Test
    fun `publish does not deliver to other channels`() {
        val received = mutableListOf<Any>()
        bus.subscribe("ink/asset/load") { _, event -> received.add(event) }

        bus.publish("ink/story/tags", "test-event")
        assertTrue(received.isEmpty(), "Should not receive events from other channels")
    }

    @Test
    fun `subscribeAll receives events from all channels`() {
        val received = mutableListOf<Pair<String, Any>>()
        bus.subscribeAll { channel, event -> received.add(channel to event) }

        bus.publish("ink/story/tags", "tag-event")
        bus.publish("ink/asset/load", "load-event")

        assertEquals(2, received.size)
        assertEquals("ink/story/tags" to "tag-event", received[0])
        assertEquals("ink/asset/load" to "load-event", received[1])
    }

    @Test
    fun `unsubscribe stops delivery`() {
        val received = mutableListOf<Any>()
        val unsub = bus.subscribe("ink/story/tags") { _, event -> received.add(event) }

        bus.publish("ink/story/tags", "event-1")
        unsub()
        bus.publish("ink/story/tags", "event-2")

        assertEquals(1, received.size, "Should only receive event before unsubscribe")
    }

    @Test
    fun `recentEvents returns event log`() {
        bus.publish("ink/story/tags", "event-1")
        bus.publish("ink/story/tags", "event-2")
        bus.publish("ink/story/tags", "event-3")

        val recent = bus.recentEvents("ink/story/tags")
        assertEquals(3, recent.size)
        assertEquals("event-1", recent[0])
        assertEquals("event-3", recent[2])
    }

    @Test
    fun `recentEvents respects limit`() {
        repeat(100) { bus.publish("ink/story/tags", "event-$it") }

        val recent = bus.recentEvents("ink/story/tags", limit = 10)
        assertEquals(10, recent.size)
        assertEquals("event-90", recent[0])
        assertEquals("event-99", recent[9])
    }

    @Test
    fun `recentEvents returns empty for unknown channel`() {
        assertTrue(bus.recentEvents("unknown/channel").isEmpty())
    }

    @Test
    fun `activeChannels tracks published channels`() {
        bus.publish("ink/story/tags", "event")
        bus.publish("ink/asset/load", "event")

        val channels = bus.activeChannels()
        assertTrue("ink/story/tags" in channels)
        assertTrue("ink/asset/load" in channels)
        assertFalse("ink/voice/ready" in channels)
    }

    @Test
    fun `clearChannel removes event log`() {
        bus.publish("ink/story/tags", "event")
        bus.clearChannel("ink/story/tags")
        assertTrue(bus.recentEvents("ink/story/tags").isEmpty())
    }

    @Test
    fun `clearAll removes all event logs`() {
        bus.publish("ink/story/tags", "event")
        bus.publish("ink/asset/load", "event")
        bus.clearAll()
        assertTrue(bus.activeChannels().isEmpty())
    }

    @Test
    fun `subscriberCount counts correctly`() {
        assertEquals(0, bus.subscriberCount("ink/story/tags"))
        bus.subscribe("ink/story/tags") { _, _ -> }
        assertEquals(1, bus.subscriberCount("ink/story/tags"))
        bus.subscribe("ink/story/tags") { _, _ -> }
        assertEquals(2, bus.subscriberCount("ink/story/tags"))
    }

    @Test
    fun `multiple subscribers on same channel all receive events`() {
        val received1 = mutableListOf<Any>()
        val received2 = mutableListOf<Any>()
        bus.subscribe("ink/story/tags") { _, event -> received1.add(event) }
        bus.subscribe("ink/story/tags") { _, event -> received2.add(event) }

        bus.publish("ink/story/tags", "test")
        assertEquals(1, received1.size)
        assertEquals(1, received2.size)
    }

    @Test
    fun `subscriber error does not affect other subscribers`() {
        val received = mutableListOf<Any>()
        bus.subscribe("ink/story/tags") { _, _ -> throw RuntimeException("boom") }
        bus.subscribe("ink/story/tags") { _, event -> received.add(event) }

        bus.publish("ink/story/tags", "test")
        assertEquals(1, received.size, "Second subscriber should still receive event")
    }
}
