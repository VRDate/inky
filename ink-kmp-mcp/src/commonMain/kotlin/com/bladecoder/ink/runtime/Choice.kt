package com.bladecoder.ink.runtime

/**
 * A generated Choice from the story. A single ChoicePoint in the Story
 * could potentially generate different Choices dynamically, so they're separated.
 *
 * All three implementations (C#, Java, JS) are structurally identical.
 *
 * Kotlin improvements:
 * - Comparable<Choice> for sorted collections (ordered by index)
 * - equals/hashCode based on index for LinkedHashSet<Choice> membership
 * - Story exposes Flow<LinkedHashSet<Choice>> for reactive choice event streaming
 *   (LinkedHashSet preserves insertion order + O(1) lookup + deduplication)
 */
class Choice : InkObject(), Comparable<Choice> {

    var text: String? = null
    var index: Int = 0
    var targetPath: Path? = null
    var sourcePath: String? = null
    var isInvisibleDefault: Boolean = false
    var originalThreadIndex: Int = 0
    var tags: List<String>? = null

    // C#: Thread property, Java: getter/setter, JS: untyped field
    // Kotlin: properly typed now that CallStack is ported
    var threadAtGeneration: CallStack.Thread? = null

    var pathStringOnChoice: String?
        get() = targetPath?.toString()
        set(value) { targetPath = if (value != null) Path(value) else null }

    /** Natural ordering by index — enables sorted set/list usage. */
    override fun compareTo(other: Choice): Int = index.compareTo(other.index)

    /** Identity by index — for LinkedHashSet deduplication. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Choice) return false
        return index == other.index
    }

    override fun hashCode(): Int = index

    fun clone(): Choice = Choice().also { copy ->
        copy.text = text
        copy.sourcePath = sourcePath
        copy.index = index
        copy.targetPath = targetPath
        copy.originalThreadIndex = originalThreadIndex
        copy.isInvisibleDefault = isInvisibleDefault
        copy.tags = tags
        copy.threadAtGeneration = threadAtGeneration?.copy()
    }
}
