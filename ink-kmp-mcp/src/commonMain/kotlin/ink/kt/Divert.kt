package ink.kt

/**
 * Ink divert instruction (->). Redirects story flow to target path.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.Divert` — same structure
 * - Java: `Divert` — same, extends RTObject
 * - JS: `Divert` — same
 *
 * Kotlin improvements:
 * - **`when` expression** in `toString()` vs if-else chain
 * - **String templates** for debug output
 */
class Divert(var stackPushType: PushPopType = PushPopType.Function) : InkObject() {
    private var _targetPath: Path? = null
    private var _targetPointer: Pointer = Pointer.Null

    var pushesToStack: Boolean = false
    var isExternal: Boolean = false
    var externalArgs: Int = 0
    var isConditional: Boolean = false
    var variableDivertName: String? = null

    // ── Parser constructor ────────────────────────────────────────────

    internal constructor(lineNumber: Int, text: String, parent: Container)
        : this(PushPopType.Function) {
        this.id = contentId(parent)
        this.text = text
        this.parent = parent
        this.lineNumber = lineNumber
    }

    fun resolveDivert(story: Story): Container {
        var d = text.trim()
        if (d.contains(Symbol.BRACE_LEFT))
            d = d.substring(0, d.indexOf(Symbol.BRACE_LEFT))
        d = story.resolveInterrupt(d)
        return story.getDivert(d)
    }

    val hasVariableTarget: Boolean
        get() = variableDivertName != null

    var targetPath: Path?
        get() {
            if (_targetPath != null && _targetPath!!.isRelative) {
                val targetObj = targetPointer.resolve()
                if (targetObj != null) {
                    _targetPath = targetObj.path
                }
            }
            return _targetPath
        }
        set(value) {
            _targetPath = value
            _targetPointer = Pointer.Null
        }

    val targetPointer: Pointer
        get() {
            if (_targetPointer.isNull) {
                val targetObj = resolvePath(_targetPath!!).obj
                if (_targetPath!!.lastComponent?.isIndex == true) {
                    _targetPointer.container = targetObj?.parent
                    _targetPointer.index = _targetPath!!.lastComponent!!.index
                } else {
                    _targetPointer = Pointer.startOf(targetObj as? Container ?: targetObj?.parent!!)
                }
            }
            return _targetPointer
        }

    var targetPathString: String?
        get() = _targetPath?.let { compactPathString(it) }
        set(value) {
            if (value == null) _targetPath = null
            else _targetPath = Path(value)
        }

    override fun toString(): String = buildString {
        if (hasVariableTarget) {
            append("Divert(variable: $variableDivertName)")
        } else if (targetPath == null) {
            append("Divert(null)")
        } else {
            append("Divert")
            if (isConditional) append("?")
            if (pushesToStack) {
                if (stackPushType == PushPopType.Function) append(" function")
                else append(" tunnel")
            }
            append(" -> ${targetPath!!.componentsString}")
            append(" ($targetPathString)")
        }
        return@buildString
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Divert) return false
        return if (hasVariableTarget == other.hasVariableTarget && hasVariableTarget)
            variableDivertName == other.variableDivertName
        else targetPath == other.targetPath
    }

    override fun hashCode(): Int =
        if (hasVariableTarget) variableDivertName.hashCode()
        else targetPath.hashCode()
}
