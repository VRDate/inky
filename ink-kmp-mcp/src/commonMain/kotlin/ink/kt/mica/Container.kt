package ink.kt.mica

open class Container(
    id: String,
    text: String,
    parent: Container?,
    lineNumber: Int
) : Content(id, text, parent, lineNumber) {

    internal var index: Int = 0
    internal var children: MutableList<Content> = mutableListOf()

    fun add(item: Content) {
        children.add(item)
    }

    fun get(i: Int): Content = children[i]

    fun indexOf(c: Content): Int = children.indexOf(c)

    val size: Int get() = children.size
}
