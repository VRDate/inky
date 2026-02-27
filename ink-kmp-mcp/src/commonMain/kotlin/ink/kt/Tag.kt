package ink.kt

class Tag(val text: String) : InkObject() {
    override fun toString(): String = "# $text"
}
