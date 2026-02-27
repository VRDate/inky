package ink.kt

/**
 * Points to a specific position within a Container (container + index).
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.Pointer` — struct (value type)
 * - Java: `Pointer` — class (reference type, mutable)
 * - JS: `Pointer` — class
 *
 * Kotlin improvements:
 * - **`companion object { val Null }`** — null-object pattern
 * - **`resolve()` extension** — cleaner than Java's static method
 */
data class Pointer(
    var container: Container? = null,
    var index: Int = -1
) {
    constructor(p: Pointer) : this(p.container, p.index)

    fun assign(p: Pointer) {
        container = p.container
        index = p.index
    }

    fun resolve(): InkObject? {
        if (index < 0) return container
        val c = container ?: return null
        if (c.content.isEmpty()) return c
        if (index >= c.content.size) return null
        return c.content[index]
    }

    val isNull: Boolean get() = container == null

    val path: Path?
        get() {
            if (isNull) return null
            return if (index >= 0)
                container!!.path.pathByAppendingComponent(Path.Component(index))
            else
                container!!.path
        }

    override fun toString(): String =
        if (container == null) "Ink Pointer (null)"
        else "Ink Pointer -> ${container!!.path} -- index $index"

    companion object {
        val Null = Pointer(null, -1)

        fun startOf(container: Container): Pointer = Pointer(container, 0)
    }
}
