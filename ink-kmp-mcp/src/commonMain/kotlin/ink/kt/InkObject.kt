package ink.kt

/**
 * Base class for all ink runtime objects in the content tree.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.InkObject` (renamed from Object to avoid System.Object collision)
 * - Java: `RTObject` (renamed to avoid java.lang.Object collision)
 * - JS: `InkObject` (same name as C#)
 *
 * Kotlin improvements:
 * - **Named `InkObject`** — matches C#/JS naming, clearer than Java's `RTObject`
 * - **Nullable parent** — `var parent: Container?` vs Java's null-checked getter
 * - **Package**: `ink.kt` vs `com.bladecoder.ink.runtime` (Java) vs `Ink.Runtime` (C#)
 */
open class InkObject {
    var parent: Container? = null

    private var _debugMetadata: DebugMetadata? = null
    private var _path: Path? = null

    var ownDebugMetadata: DebugMetadata?
        get() = _debugMetadata
        set(value) { _debugMetadata = value }

    val debugMetadata: DebugMetadata?
        get() = _debugMetadata ?: parent?.debugMetadata

    fun debugLineNumberOfPath(path: Path?): Int? {
        if (path == null || path.isRelative) return null

        val root = rootContentContainer ?: return null
        val targetContent = root.contentAtPath(path).obj ?: return null
        return targetContent._debugMetadata?.startLineNumber
    }

    val path: Path
        get() {
            if (_path == null) {
                if (parent == null) {
                    _path = Path()
                } else {
                    val comps = mutableListOf<Path.Component>()
                    var child: InkObject = this
                    var container = child.parent
                    while (container != null) {
                        if (child is Container && child.hasValidName) {
                            comps.add(Path.Component(child.name!!))
                        } else {
                            comps.add(Path.Component(container.content.indexOf(child)))
                        }
                        child = container
                        container = container.parent
                    }
                    comps.reverse()
                    _path = Path(comps)
                }
            }
            return _path!!
        }

    fun resolvePath(path: Path): SearchResult {
        if (path.isRelative) {
            var nearestContainer = this as? Container
            if (nearestContainer == null) {
                nearestContainer = parent
                return nearestContainer!!.contentAtPath(path.tail)
            }
            return nearestContainer.contentAtPath(path)
        } else {
            return rootContentContainer!!.contentAtPath(path)
        }
    }

    fun convertPathToRelative(globalPath: Path): Path {
        val ownPath = this.path
        val minPathLength = minOf(globalPath.length, ownPath.length)
        var lastSharedPathCompIndex = -1

        for (i in 0 until minPathLength) {
            val ownComp = ownPath.getComponent(i)
            val otherComp = globalPath.getComponent(i)
            if (ownComp == otherComp) lastSharedPathCompIndex = i
            else break
        }

        if (lastSharedPathCompIndex == -1) return globalPath

        val numUpwardsMoves = (ownPath.length - 1) - lastSharedPathCompIndex
        val newPathComps = mutableListOf<Path.Component>()

        repeat(numUpwardsMoves) { newPathComps.add(Path.Component.toParent()) }

        for (down in (lastSharedPathCompIndex + 1) until globalPath.length) {
            newPathComps.add(globalPath.getComponent(down))
        }

        return Path(newPathComps, relative = true)
    }

    fun compactPathString(otherPath: Path): String {
        val globalPathStr: String
        val relativePathStr: String

        if (otherPath.isRelative) {
            relativePathStr = otherPath.componentsString
            globalPathStr = path.pathByAppendingPath(otherPath).componentsString
        } else {
            val relativePath = convertPathToRelative(otherPath)
            relativePathStr = relativePath.componentsString
            globalPathStr = otherPath.componentsString
        }

        return if (relativePathStr.length < globalPathStr.length) relativePathStr else globalPathStr
    }

    val rootContentContainer: Container?
        get() {
            var ancestor: InkObject = this
            while (ancestor.parent != null) {
                ancestor = ancestor.parent!!
            }
            return ancestor as? Container
        }

    open fun copy(): InkObject {
        throw UnsupportedOperationException("${this::class.simpleName} doesn't support copying")
    }
}
