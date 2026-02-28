package ink.mcp

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process event bus for the asset pipeline.
 *
 * Provides publish/subscribe for asset events across channels defined
 * in the AsyncAPI contract (ink-asset-events.yaml).
 *
 * Channels:
 *   - ink/story/tags          — tags emitted on story continue
 *   - ink/asset/load          — asset load requests
 *   - ink/asset/loaded        — asset loaded confirmations
 *   - ink/inventory/change    — inventory LIST changes
 *   - ink/voice/synthesize    — TTS requests
 *   - ink/voice/ready         — audio ready events
 *
 * Used directly for in-process consumers (KT/JVM, Unity+OneJS).
 * RSocket transport wraps this bus for network consumers (BabylonJS, Electron, inkey).
 */
class AssetEventBus {

    private val log = LoggerFactory.getLogger(AssetEventBus::class.java)

    /** Subscriber callback: receives channel name and event payload. */
    fun interface EventListener {
        fun onEvent(channel: String, event: Any)
    }

    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<EventListener>>()
    private val globalSubscribers = CopyOnWriteArrayList<EventListener>()
    private val eventLog = ConcurrentHashMap<String, CopyOnWriteArrayList<Any>>()

    /** Publish an event to a channel. */
    fun publish(channel: String, event: Any) {
        log.debug("Publishing to {}: {}", channel, event::class.simpleName)

        // Log event for replay
        eventLog.getOrPut(channel) { CopyOnWriteArrayList() }.add(event)

        // Notify channel subscribers
        subscribers[channel]?.forEach { listener ->
            try {
                listener.onEvent(channel, event)
            } catch (e: Exception) {
                log.warn("Subscriber error on {}: {}", channel, e.message)
            }
        }

        // Notify global subscribers
        globalSubscribers.forEach { listener ->
            try {
                listener.onEvent(channel, event)
            } catch (e: Exception) {
                log.warn("Global subscriber error: {}", e.message)
            }
        }
    }

    /** Subscribe to events on a specific channel. Returns unsubscribe function. */
    fun subscribe(channel: String, listener: EventListener): () -> Unit {
        val list = subscribers.getOrPut(channel) { CopyOnWriteArrayList() }
        list.add(listener)
        log.debug("Subscribed to {} ({} listeners)", channel, list.size)
        return { list.remove(listener) }
    }

    /** Subscribe to all events across all channels. Returns unsubscribe function. */
    fun subscribeAll(listener: EventListener): () -> Unit {
        globalSubscribers.add(listener)
        return { globalSubscribers.remove(listener) }
    }

    /** Get recent events for a channel (for replay on reconnect). */
    fun recentEvents(channel: String, limit: Int = 50): List<Any> {
        val events = eventLog[channel] ?: return emptyList()
        return if (events.size <= limit) events.toList()
        else events.subList(events.size - limit, events.size).toList()
    }

    /** Get all events for a session across all channels. */
    fun sessionEvents(sessionId: String): Map<String, List<Any>> {
        return eventLog.mapValues { (_, events) ->
            events.filter { event ->
                when (event) {
                    is InkAssetEventEngine.InkTagEvent -> event.sessionId == sessionId
                    is InkAssetEventEngine.AssetLoadRequest -> event.sessionId == sessionId
                    is InkAssetEventEngine.InventoryChangeEvent -> event.sessionId == sessionId
                    is InkAssetEventEngine.VoiceSynthRequest -> event.sessionId == sessionId
                    else -> false
                }
            }
        }.filterValues { it.isNotEmpty() }
    }

    /** Clear event log for a channel. */
    fun clearChannel(channel: String) {
        eventLog.remove(channel)
    }

    /** Clear all event logs. */
    fun clearAll() {
        eventLog.clear()
    }

    /** Number of subscribers on a channel. */
    fun subscriberCount(channel: String): Int =
        (subscribers[channel]?.size ?: 0) + globalSubscribers.size

    /** All channels that have received events. */
    fun activeChannels(): Set<String> = eventLog.keys.toSet()

    companion object {
        const val CHANNEL_STORY_TAGS = "ink/story/tags"
        const val CHANNEL_ASSET_LOAD = "ink/asset/load"
        const val CHANNEL_ASSET_LOADED = "ink/asset/loaded"
        const val CHANNEL_INVENTORY_CHANGE = "ink/inventory/change"
        const val CHANNEL_VOICE_SYNTHESIZE = "ink/voice/synthesize"
        const val CHANNEL_VOICE_READY = "ink/voice/ready"

        /** All 6 AsyncAPI contract channels. */
        val ALL_CHANNELS = listOf(
            CHANNEL_STORY_TAGS, CHANNEL_ASSET_LOAD, CHANNEL_ASSET_LOADED,
            CHANNEL_INVENTORY_CHANGE, CHANNEL_VOICE_SYNTHESIZE, CHANNEL_VOICE_READY
        )
    }
}
