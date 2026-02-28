package ink.kt

/**
 * KMP-safe case-insensitive string-keyed map.
 * Replaces `sortedMapOf(String.CASE_INSENSITIVE_ORDER)` which is JVM-only.
 */
internal class CaseInsensitiveMap<V> : MutableMap<String, V> {
    private val delegate = mutableMapOf<String, V>()

    override val size: Int get() = delegate.size
    override val entries: MutableSet<MutableMap.MutableEntry<String, V>> get() = delegate.entries
    override val keys: MutableSet<String> get() = delegate.keys
    override val values: MutableCollection<V> get() = delegate.values

    override fun containsKey(key: String): Boolean = delegate.containsKey(key.lowercase())
    override fun containsValue(value: V): Boolean = delegate.containsValue(value)
    override fun get(key: String): V? = delegate[key.lowercase()]
    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun clear() = delegate.clear()
    override fun put(key: String, value: V): V? = delegate.put(key.lowercase(), value)
    override fun putAll(from: Map<out String, V>) {
        for ((k, v) in from) delegate[k.lowercase()] = v
    }
    override fun remove(key: String): V? = delegate.remove(key.lowercase())
}
