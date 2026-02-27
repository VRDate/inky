package ink.mcp

import org.slf4j.LoggerFactory

/**
 * Watches ink story state (tags, variables) and emits asset events.
 *
 * Bridges InkEngine's ContinueResult.tags to the asset event pipeline:
 *   - Resolves tags via EmojiAssetManifest → AssetRef
 *   - Detects inventory LIST changes → InventoryChangeEvent
 *   - Publishes events to AssetEventBus
 *
 * Used by both internal (Camel direct routes) and external (RSocket) consumers.
 */
class InkAssetEventEngine(
    private val manifest: EmojiAssetManifest = EmojiAssetManifest(),
    private val bus: AssetEventBus = AssetEventBus()
) {
    private val log = LoggerFactory.getLogger(InkAssetEventEngine::class.java)

    // ── Event data classes (AsyncAPI contract) ────────────────

    data class InkTagEvent(
        val sessionId: String,
        val knot: String = "",
        val tags: List<String>,
        val resolvedAssets: List<EmojiAssetManifest.AssetRef>,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class AssetLoadRequest(
        val sessionId: String,
        val asset: EmojiAssetManifest.AssetRef,
        val priority: Priority = Priority.IMMEDIATE,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        enum class Priority { IMMEDIATE, PRELOAD, LAZY }
    }

    data class InventoryChangeEvent(
        val sessionId: String,
        val action: Action,
        val emoji: String,
        val itemName: String,
        val asset: EmojiAssetManifest.AssetRef?,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        enum class Action { EQUIP, UNEQUIP, ADD, REMOVE, USE, DROP }
    }

    data class VoiceSynthRequest(
        val sessionId: String,
        val text: String,
        val voiceRef: EmojiAssetManifest.VoiceRef,
        val timestamp: Long = System.currentTimeMillis()
    )

    // ── Processing ────────────────────────────────────────────

    /**
     * Process story state after Continue() — resolve tags and emit events.
     *
     * @param sessionId ink story session ID
     * @param tags raw tags from ContinueResult.tags
     * @param knot current knot/stitch path
     * @return list of resolved AssetRefs
     */
    fun processStoryState(
        sessionId: String,
        tags: List<String>,
        knot: String = ""
    ): List<EmojiAssetManifest.AssetRef> {
        if (tags.isEmpty()) return emptyList()

        val resolved = manifest.parseInkTags(tags)
        if (resolved.isEmpty()) return emptyList()

        // Publish tag event
        val tagEvent = InkTagEvent(
            sessionId = sessionId,
            knot = knot,
            tags = tags,
            resolvedAssets = resolved
        )
        bus.publish("ink/story/tags", tagEvent)

        // Publish individual asset load requests
        for (asset in resolved) {
            val loadRequest = AssetLoadRequest(
                sessionId = sessionId,
                asset = asset
            )
            bus.publish("ink/asset/load", loadRequest)

            // If voice ref present, also request TTS
            asset.voiceRef?.let { voice ->
                // Voice synth would need the story text, which we don't have here.
                // The caller should use processVoice() separately with the text.
                log.debug("Voice ref detected: {} ({})", voice.characterId, voice.language)
            }
        }

        log.debug("Processed {} tags → {} assets for session {}",
            tags.size, resolved.size, sessionId)
        return resolved
    }

    /**
     * Process a single ink tag → asset event.
     */
    fun processTag(sessionId: String, tag: String): EmojiAssetManifest.AssetRef? {
        val refs = manifest.parseInkTags(listOf(tag))
        val ref = refs.firstOrNull() ?: return null

        bus.publish("ink/asset/load", AssetLoadRequest(
            sessionId = sessionId,
            asset = ref
        ))
        return ref
    }

    /**
     * Detect inventory LIST changes and emit equip/unequip events.
     *
     * @param sessionId ink story session ID
     * @param previous previous LIST items (emoji → name)
     * @param current current LIST items (emoji → name)
     */
    fun processInventoryChange(
        sessionId: String,
        previous: Map<String, String>,
        current: Map<String, String>
    ): List<InventoryChangeEvent> {
        val events = mutableListOf<InventoryChangeEvent>()

        // Added items
        for ((emoji, name) in current) {
            if (emoji !in previous) {
                val asset = manifest.resolve(emoji)
                val event = InventoryChangeEvent(
                    sessionId = sessionId,
                    action = InventoryChangeEvent.Action.EQUIP,
                    emoji = emoji,
                    itemName = name,
                    asset = asset
                )
                events.add(event)
                bus.publish("ink/inventory/change", event)
            }
        }

        // Removed items
        for ((emoji, name) in previous) {
            if (emoji !in current) {
                val asset = manifest.resolve(emoji)
                val event = InventoryChangeEvent(
                    sessionId = sessionId,
                    action = InventoryChangeEvent.Action.UNEQUIP,
                    emoji = emoji,
                    itemName = name,
                    asset = asset
                )
                events.add(event)
                bus.publish("ink/inventory/change", event)
            }
        }

        if (events.isNotEmpty()) {
            log.debug("Inventory change: {} events for session {}", events.size, sessionId)
        }
        return events
    }

    /**
     * Request voice synthesis for story text.
     */
    fun processVoice(
        sessionId: String,
        text: String,
        voiceRef: EmojiAssetManifest.VoiceRef
    ) {
        val request = VoiceSynthRequest(
            sessionId = sessionId,
            text = text,
            voiceRef = voiceRef
        )
        bus.publish("ink/voice/synthesize", request)
    }

    /** Get the underlying event bus for direct subscription. */
    fun eventBus(): AssetEventBus = bus
}
