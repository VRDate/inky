package ink.kt

/**
 * Control flow command in the ink runtime (eval start/end, string mode, etc.).
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.ControlCommand` — CommandType enum, same commands
 * - Java: `ControlCommand` — CommandType enum, separate file
 * - JS: `ControlCommand` — numeric constants
 *
 * Kotlin improvements:
 * - **`enum class CommandType`** — type-safe vs Java enum
 * - **`when` exhaustiveness** — compiler-checked command dispatch in Story.kt
 */
class ControlCommand(var commandType: CommandType = CommandType.NotSet) : InkObject() {

    enum class CommandType {
        NotSet,
        EvalStart,
        EvalOutput,
        EvalEnd,
        Duplicate,
        PopEvaluatedValue,
        PopFunction,
        PopTunnel,
        BeginString,
        EndString,
        NoOp,
        ChoiceCount,
        Turns,
        TurnsSince,
        ReadCount,
        Random,
        SeedRandom,
        VisitIndex,
        SequenceShuffleIndex,
        StartThread,
        Done,
        End,
        ListFromInt,
        ListRange,
        ListRandom,
        BeginTag,
        EndTag
    }

    override fun copy(): InkObject = ControlCommand(commandType)

    override fun toString(): String = commandType.toString()
}
