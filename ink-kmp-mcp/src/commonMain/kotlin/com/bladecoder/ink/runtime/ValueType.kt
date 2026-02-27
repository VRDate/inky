package com.bladecoder.ink.runtime

/**
 * Order is significant for type coercion.
 * If types aren't directly compatible for an operation,
 * they're coerced to the same type, downward.
 * Higher value types "infect" an operation.
 */
enum class ValueType {
    // Used in coercion
    Bool,
    Int,
    Float,
    List,
    String,
    // Not used for coercion described above
    DivertTarget,
    VariablePointer
}
