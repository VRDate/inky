package ink.kt

/**
 * Represents an addressable path within ink content.
 * Paths can be relative (starting with ".") or absolute.
 */
class Path {
    private val _components: MutableList<Component> = mutableListOf()
    var isRelative: Boolean = false
        private set
    private var _componentsString: String? = null

    constructor()

    constructor(head: Component, tail: Path) : this() {
        _components.add(head)
        _components.addAll(tail._components)
    }

    constructor(components: Collection<Component>, relative: Boolean = false) : this() {
        _components.addAll(components)
        isRelative = relative
    }

    constructor(componentsString: String) : this() {
        setComponentsString(componentsString)
    }

    fun getComponent(index: Int): Component = _components[index]

    val head: Component?
        get() = _components.firstOrNull()

    val tail: Path
        get() = if (_components.size >= 2) Path(_components.subList(1, _components.size))
                else self

    val length: Int
        get() = _components.size

    val lastComponent: Component?
        get() = _components.lastOrNull()

    fun containsNamedComponent(): Boolean =
        _components.any { !it.isIndex }

    fun pathByAppendingPath(pathToAppend: Path): Path {
        val p = Path()
        var upwardMoves = 0
        for (i in pathToAppend._components.indices) {
            if (pathToAppend._components[i].isParent) upwardMoves++
            else break
        }
        for (i in 0 until (_components.size - upwardMoves)) {
            p._components.add(_components[i])
        }
        for (i in upwardMoves until pathToAppend._components.size) {
            p._components.add(pathToAppend._components[i])
        }
        return p
    }

    fun pathByAppendingComponent(c: Component): Path {
        val p = Path()
        p._components.addAll(_components)
        p._components.add(c)
        return p
    }

    val componentsString: String
        get() {
            if (_componentsString == null) {
                _componentsString = buildString {
                    if (_components.isNotEmpty()) {
                        append(_components[0])
                        for (i in 1 until _components.size) {
                            append('.')
                            append(_components[i])
                        }
                    }
                    if (isRelative) {
                        _componentsString = ".$this"
                    }
                }
                if (isRelative) _componentsString = ".${_componentsString}"
            }
            return _componentsString!!
        }

    private fun setComponentsString(value: String) {
        _components.clear()
        _componentsString = value

        if (_componentsString.isNullOrEmpty()) return

        var str = _componentsString!!
        if (str[0] == '.') {
            isRelative = true
            str = str.substring(1)
            _componentsString = str
        } else {
            isRelative = false
        }

        for (part in str.split(".")) {
            val index = part.toIntOrNull()
            if (index != null) _components.add(Component(index))
            else _components.add(Component(part))
        }
    }

    override fun toString(): String = componentsString

    override fun equals(other: Any?): Boolean {
        if (other !is Path) return false
        if (other._components.size != _components.size) return false
        if (other.isRelative != isRelative) return false
        return _components.indices.all { _components[it] == other._components[it] }
    }

    override fun hashCode(): Int = toString().hashCode()

    companion object {
        private const val PARENT_ID = "^"

        val self: Path
            get() = Path().also { it.isRelative = true }
    }

    /**
     * Immutable path component — either a named component or an index.
     */
    class Component {
        val index: Int
        val name: String?

        constructor(index: Int) {
            this.index = index
            this.name = null
        }

        constructor(name: String) {
            this.name = name
            this.index = -1
        }

        val isIndex: Boolean get() = index >= 0
        val isParent: Boolean get() = name == PARENT_ID

        override fun toString(): String =
            if (isIndex) index.toString() else name!!

        override fun equals(other: Any?): Boolean {
            if (other !is Component) return false
            if (other.isIndex != isIndex) return false
            return if (isIndex) index == other.index else name == other.name
        }

        override fun hashCode(): Int =
            if (isIndex) index else name.hashCode()

        companion object {
            fun toParent(): Component = Component(PARENT_ID)
        }
    }
}
