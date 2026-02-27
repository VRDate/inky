package ink.kt

/**
 * Type of push/pop operation on the call stack (Tunnel, Function, FunctionEval).
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.PushPopType` — enum
 * - Java: `PushPopType` — enum
 * - JS: `PushPopType` — numeric constants
 *
 * Kotlin: Same enum. Used by CallStack for tracking flow type.
 */
enum class PushPopType {
    Tunnel,
    Function,
    FunctionEvaluationFromGame
}
