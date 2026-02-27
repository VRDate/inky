package com.bladecoder.ink.runtime

/**
 * When looking up content within the story (e.g. in Container.contentAtPath),
 * the result is generally found, but if the story is modified, then when loading
 * up an old save state, some old paths may still exist. In this case we try to
 * recover by finding an approximate result.
 */
class SearchResult(
    var obj: InkObject? = null,
    var approximate: Boolean = false
) {
    constructor(sr: SearchResult) : this(sr.obj, sr.approximate)

    val correctObj: InkObject?
        get() = if (approximate) null else obj

    val container: Container?
        get() = obj as? Container
}
