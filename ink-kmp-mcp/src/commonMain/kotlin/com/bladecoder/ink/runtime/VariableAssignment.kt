package com.bladecoder.ink.runtime

class VariableAssignment(
    var variableName: String? = null,
    var isNewDeclaration: Boolean = false
) : InkObject() {
    var isGlobal: Boolean = false

    override fun toString(): String =
        "VarAssign to $variableName"
}
