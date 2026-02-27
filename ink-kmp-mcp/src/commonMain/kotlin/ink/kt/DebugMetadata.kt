package ink.kt

import kotlin.math.max
import kotlin.math.min

/**
 * Source file location metadata attached to ink objects for error reporting.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.DebugMetadata` — same fields
 * - Java: `DebugMetadata` — same fields, separate file
 * - JS: `DebugMetadata` — same
 *
 * Kotlin: Simple data holder, no significant changes between ports.
 */
class DebugMetadata {
    var startLineNumber: Int = 0
    var endLineNumber: Int = 0
    var startCharacterNumber: Int = 0
    var endCharacterNumber: Int = 0
    var fileName: String? = null
    var sourceName: String? = null

    /**
     * Merge two debug metadata ranges into one that encompasses both.
     * Currently only used in VariableReference to merge Path.Of.Identifiers.
     */
    fun merge(dm: DebugMetadata): DebugMetadata {
        val result = DebugMetadata()
        result.fileName = fileName
        result.sourceName = sourceName

        when {
            startLineNumber < dm.startLineNumber -> {
                result.startLineNumber = startLineNumber
                result.startCharacterNumber = startCharacterNumber
            }
            startLineNumber > dm.startLineNumber -> {
                result.startLineNumber = dm.startLineNumber
                result.startCharacterNumber = dm.startCharacterNumber
            }
            else -> {
                result.startLineNumber = startLineNumber
                result.startCharacterNumber = min(startCharacterNumber, dm.startCharacterNumber)
            }
        }

        when {
            endLineNumber > dm.endLineNumber -> {
                result.endLineNumber = endLineNumber
                result.endCharacterNumber = endCharacterNumber
            }
            endLineNumber < dm.endLineNumber -> {
                result.endLineNumber = dm.endLineNumber
                result.endCharacterNumber = dm.endCharacterNumber
            }
            else -> {
                result.endLineNumber = endLineNumber
                result.endCharacterNumber = max(endCharacterNumber, dm.endCharacterNumber)
            }
        }

        return result
    }

    override fun toString(): String =
        if (fileName != null) "line $startLineNumber of $fileName"
        else "line $startLineNumber"
}
