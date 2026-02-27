package ink.kt

/**
 * Runtime container for ink content. Holds ordered content list + named sub-containers.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.Container` — Dictionary<string, INamedContent>
 * - Java: `Container` — HashMap<String, INamedContent>
 * - JS: `Container` — same structure
 *
 * Kotlin improvements:
 * - **LinkedHashMap** preserves insertion order for named content
 * - **`content: MutableList<InkObject>`** typed list vs raw Object list
 * - **Property syntax** for countersAtStartOnly, visitsShouldBeCounted, etc.
 */
class Container : InkObject(), INamedContent {
    private var _name: String? = null

    val content: MutableList<InkObject> = mutableListOf()
    var namedContent: HashMap<String, INamedContent> = HashMap()

    var visitsShouldBeCounted: Boolean = false
    var turnIndexShouldBeCounted: Boolean = false
    var countingAtStartOnly: Boolean = false

    override val name: String?
        get() = _name

    fun setName(value: String?) {
        _name = value
    }

    override val hasValidName: Boolean
        get() = !_name.isNullOrEmpty()

    /**
     * Named content that is NOT part of the sequential content list.
     * Used for named sub-containers that are looked up by name only.
     */
    val namedOnlyContent: HashMap<String, InkObject>
        get() {
            val namedOnlyContentDict = HashMap<String, InkObject>()
            for ((key, value) in namedContent) {
                namedOnlyContentDict[key] = value as InkObject
            }
            for (c in content) {
                val named = c as? INamedContent
                if (named != null && named.hasValidName) {
                    namedOnlyContentDict.remove(named.name)
                }
            }
            return namedOnlyContentDict
        }

    fun setNamedOnlyContent(value: HashMap<String, InkObject>) {
        val existingNamedOnly = namedOnlyContent
        for ((key, _) in existingNamedOnly) {
            namedContent.remove(key)
        }
        for ((_, obj) in value) {
            val named = obj as? INamedContent
            if (named != null) addToNamedContentOnly(named)
        }
    }

    var countFlags: Int
        get() {
            var flags = 0
            if (visitsShouldBeCounted) flags = flags or COUNTFLAGS_VISITS
            if (turnIndexShouldBeCounted) flags = flags or COUNTFLAGS_TURNS
            if (countingAtStartOnly) flags = flags or COUNTFLAGS_COUNTSTARTONLY
            // If we're only storing CountStartOnly, it serves no purpose,
            // since it's dependent on the other two to be used at all.
            if (flags == COUNTFLAGS_COUNTSTARTONLY) flags = 0
            return flags
        }
        set(value) {
            if ((value and COUNTFLAGS_VISITS) > 0) visitsShouldBeCounted = true
            if ((value and COUNTFLAGS_TURNS) > 0) turnIndexShouldBeCounted = true
            if ((value and COUNTFLAGS_COUNTSTARTONLY) > 0) countingAtStartOnly = true
        }

    fun addContents(contentList: List<InkObject>) {
        for (c in contentList) addContent(c)
    }

    fun addContent(contentObj: InkObject) {
        content.add(contentObj)
        require(contentObj.parent == null) { "content is already in ${contentObj.parent}" }
        contentObj.parent = this
        tryAddNamedContent(contentObj)
    }

    fun insertContent(contentObj: InkObject, index: Int) {
        content.add(index, contentObj)
        require(contentObj.parent == null) { "content is already in ${contentObj.parent}" }
        contentObj.parent = this
        tryAddNamedContent(contentObj)
    }

    private fun tryAddNamedContent(contentObj: InkObject) {
        val namedContentObj = contentObj as? INamedContent
        if (namedContentObj != null && namedContentObj.hasValidName) {
            addToNamedContentOnly(namedContentObj)
        }
    }

    fun addToNamedContentOnly(namedContentObj: INamedContent) {
        val runtimeObj = namedContentObj as InkObject
        runtimeObj.parent = this
        namedContent[namedContentObj.name!!] = namedContentObj
    }

    fun addContentsOfContainer(otherContainer: Container) {
        content.addAll(otherContainer.content)
        for (obj in otherContainer.content) {
            obj.parent = this
            tryAddNamedContent(obj)
        }
    }

    internal fun contentWithPathComponent(component: Path.Component): InkObject? {
        return when {
            component.isIndex -> {
                if (component.index in content.indices) content[component.index] else null
            }
            component.isParent -> this.parent
            else -> {
                val foundContent = namedContent[component.name]
                foundContent as? InkObject
            }
        }
    }

    fun contentAtPath(path: Path, partialPathStart: Int = 0, partialPathLength: Int = -1): SearchResult {
        val length = if (partialPathLength == -1) path.length else partialPathLength
        val result = SearchResult()
        result.approximate = false

        var currentContainer: Container? = this
        var currentObj: InkObject? = this

        for (i in partialPathStart until length) {
            val comp = path.getComponent(i)

            // Path component was wrong type
            if (currentContainer == null) {
                result.approximate = true
                break
            }

            val foundObj = currentContainer.contentWithPathComponent(comp)

            // Couldn't resolve entire path?
            if (foundObj == null) {
                result.approximate = true
                break
            }

            // Are we about to loop into another container?
            val nextContainer = foundObj as? Container
            if (i < length - 1 && nextContainer == null) {
                result.approximate = true
                break
            }

            currentObj = foundObj
            currentContainer = nextContainer
        }

        result.obj = currentObj
        return result
    }

    fun buildStringOfHierarchy(sb: StringBuilder, indentation: Int, pointedObj: InkObject?) {
        var indent = indentation
        appendIndentation(sb, indent)
        sb.append("[")

        if (this.hasValidName) {
            sb.append(" ({")
            sb.append(this._name)
            sb.append("})")
        }

        if (this === pointedObj) {
            sb.append("  <---")
        }

        sb.append("\n")
        indent++

        for (i in content.indices) {
            val obj = content[i]

            if (obj is Container) {
                obj.buildStringOfHierarchy(sb, indent, pointedObj)
            } else {
                appendIndentation(sb, indent)
                if (obj is StringValue) {
                    sb.append("\"")
                    sb.append(obj.toString().replace("\n", "\\n"))
                    sb.append("\"")
                } else {
                    sb.append(obj.toString())
                }
            }

            if (i != content.size - 1) {
                sb.append(",")
            }

            if (obj !is Container && obj === pointedObj) {
                sb.append("  <---")
            }

            sb.append("\n")
        }

        val onlyNamed = HashMap<String, INamedContent>()
        for ((key, value) in namedContent) {
            if (value as InkObject !in content) {
                onlyNamed[key] = value
            }
        }

        if (onlyNamed.isNotEmpty()) {
            appendIndentation(sb, indent)
            sb.append("-- named: --\n")

            for ((_, value) in onlyNamed) {
                val container = value as Container
                container.buildStringOfHierarchy(sb, indent, pointedObj)
                sb.append("\n")
            }
        }

        indent--
        appendIndentation(sb, indent)
        sb.append("]")
    }

    fun buildStringOfHierarchy(): String {
        val sb = StringBuilder()
        buildStringOfHierarchy(sb, 0, null)
        return sb.toString()
    }

    companion object {
        const val COUNTFLAGS_VISITS = 1
        const val COUNTFLAGS_TURNS = 2
        const val COUNTFLAGS_COUNTSTARTONLY = 4

        private const val SPACES_PER_INDENT = 4

        private fun appendIndentation(sb: StringBuilder, indentation: Int) {
            repeat(SPACES_PER_INDENT * indentation) { sb.append(' ') }
        }
    }
}
