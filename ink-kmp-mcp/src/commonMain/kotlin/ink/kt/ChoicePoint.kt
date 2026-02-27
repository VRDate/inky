package ink.kt

/**
 * Compile-time choice structure. Resolved at runtime into a Choice.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.ChoicePoint` — same fields
 * - Java: `ChoicePoint` — same, flag bitmask for conditions
 * - JS: `ChoicePoint` — same
 *
 * Kotlin: Same flag-based approach as Java/C#. Properties for hasCondition,
 * hasStartContent, etc. each backed by bit flags.
 */
class ChoicePoint(var onceOnly: Boolean = true) : InkObject() {
    private var _pathOnChoice: Path? = null

    var hasCondition: Boolean = false
    var hasStartContent: Boolean = false
    var hasChoiceOnlyContent: Boolean = false
    var isInvisibleDefault: Boolean = false

    val choiceTarget: Container
        get() = resolvePath(_pathOnChoice!!).container!!

    var pathOnChoice: Path?
        get() {
            if (_pathOnChoice != null && _pathOnChoice!!.isRelative) {
                val choiceTargetObj = choiceTarget
                if (choiceTargetObj != null) {
                    _pathOnChoice = choiceTargetObj.path
                }
            }
            return _pathOnChoice
        }
        set(value) { _pathOnChoice = value }

    var pathStringOnChoice: String?
        get() = _pathOnChoice?.let { compactPathString(it) }
        set(value) { _pathOnChoice = if (value != null) Path(value) else null }

    var flags: Int
        get() {
            var f = 0
            if (hasCondition) f = f or 1
            if (hasStartContent) f = f or 2
            if (hasChoiceOnlyContent) f = f or 4
            if (isInvisibleDefault) f = f or 8
            if (onceOnly) f = f or 16
            return f
        }
        set(value) {
            hasCondition = (value and 1) > 0
            hasStartContent = (value and 2) > 0
            hasChoiceOnlyContent = (value and 4) > 0
            isInvisibleDefault = (value and 8) > 0
            onceOnly = (value and 16) > 0
        }

    override fun toString(): String =
        "Choice: -> ${pathOnChoice?.componentsString ?: "?"}"
}
