package com.bladecoder.ink.runtime

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
