package ink.kt

/**
 * Result of searching for content by path. Holds found object + approximate flag.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.SearchResult` — struct with Container property
 * - Java: `SearchResult` — class with mutable fields
 * - JS: `SearchResult` — class
 *
 * Kotlin: Mutable result class, same as Java. C# version is a value-type struct.
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
