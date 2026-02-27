package com.bladecoder.ink.runtime

/**
 * Ink runtime value hierarchy — best of C# (inkle/ink), Java (blade-ink), JS (inkjs).
 *
 * Design decisions from comparing all three:
 * - Sealed class hierarchy (Kotlin-unique): exhaustive `when`, no runtime type-check bugs
 * - `when` expressions for cast() instead of if-chains (C#/Java/JS all use if-chains)
 * - C#-style "true"/"false" for BoolValue.toString() (not Java's "True"/"False")
 * - Platform-safe float formatting (C# uses InvariantCulture, Java/JS use defaults)
 * - Factory companion on Value (all three have static Create)
 * - Value<T> generic pattern (shared across all three impls)
 *
 * Hierarchy:
 *   InkObject
 *     └─ Value<T> (sealed)
 *          ├─ BoolValue      : Value<Boolean>
 *          ├─ IntValue        : Value<Int>
 *          ├─ FloatValue      : Value<Float>
 *          ├─ StringValue     : Value<String>
 *          ├─ DivertTargetValue : Value<Path>
 *          ├─ VariablePointerValue : Value<String>
 *          └─ ListValue       : Value<InkList>  (deferred until InkList is ported)
 */
sealed class Value<T>(var value: T) : InkObject() {

    abstract val valueType: ValueType
    abstract val isTruthy: Boolean
    abstract fun cast(newType: ValueType): Value<*>?

    val valueObject: Any? get() = value

    override fun copy(): InkObject = create(valueObject) ?: throw StoryException("Failed to copy value: $valueObject")

    override fun toString(): String = value.toString()

    protected fun badCastException(targetType: ValueType): StoryException =
        StoryException("Can't cast $valueObject from $valueType to $targetType")

    companion object {
        /**
         * Factory method shared across all three official implementations.
         * C#: Value.Create(object), Java: AbstractValue.create(Object), JS: Value.Create(val)
         */
        fun create(obj: Any?): Value<*>? = when (obj) {
            is Boolean -> BoolValue(obj)
            is Int -> IntValue(obj)
            is Long -> IntValue(obj.toInt())        // C#/Java both handle long→int
            is Float -> FloatValue(obj)
            is Double -> FloatValue(obj.toFloat())   // All three: double loses precision to float
            is String -> StringValue(obj)
            is Path -> DivertTargetValue(obj)
            is InkList -> ListValue(obj)
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------
// BoolValue — C# has Bool=-1 for coercion ordering, all three: truthy = value itself
// ---------------------------------------------------------------------------
class BoolValue(value: Boolean = false) : Value<Boolean>(value) {

    override val valueType: ValueType get() = ValueType.Bool
    override val isTruthy: Boolean get() = value

    override fun cast(newType: ValueType): Value<*>? = when (newType) {
        valueType -> this
        ValueType.Int -> IntValue(if (value) 1 else 0)
        ValueType.Float -> FloatValue(if (value) 1f else 0f)
        ValueType.String -> StringValue(if (value) "true" else "false") // C#-style, not Java's "True"
        else -> throw badCastException(newType)
    }

    // C# overrides ToString to produce "true"/"false" instead of "True"/"False"
    override fun toString(): String = if (value) "true" else "false"
}

// ---------------------------------------------------------------------------
// IntValue — identical across all three implementations
// ---------------------------------------------------------------------------
class IntValue(value: Int = 0) : Value<Int>(value) {

    override val valueType: ValueType get() = ValueType.Int
    override val isTruthy: Boolean get() = value != 0

    override fun cast(newType: ValueType): Value<*>? = when (newType) {
        valueType -> this
        ValueType.Bool -> BoolValue(value != 0)
        ValueType.Float -> FloatValue(value.toFloat())
        ValueType.String -> StringValue(value.toString())
        else -> throw badCastException(newType)
    }
}

// ---------------------------------------------------------------------------
// FloatValue — C# uses InvariantCulture for string, Kotlin's toString() is locale-independent
// ---------------------------------------------------------------------------
class FloatValue(value: Float = 0.0f) : Value<Float>(value) {

    override val valueType: ValueType get() = ValueType.Float
    override val isTruthy: Boolean get() = value != 0.0f

    override fun cast(newType: ValueType): Value<*>? = when (newType) {
        valueType -> this
        ValueType.Bool -> BoolValue(value != 0.0f)
        ValueType.Int -> IntValue(value.toInt())
        ValueType.String -> StringValue(value.toString()) // Kotlin Float.toString() is locale-independent
        else -> throw badCastException(newType)
    }
}

// ---------------------------------------------------------------------------
// StringValue — all three have whitespace classification; C# uses TryParse for numeric casts
// ---------------------------------------------------------------------------
class StringValue(value: String = "") : Value<String>(value) {

    val isNewline: Boolean = value == "\n"
    val isInlineWhitespace: Boolean = value.isNotEmpty() && value.all { it == ' ' || it == '\t' }
    val isNonWhitespace: Boolean get() = !isNewline && !isInlineWhitespace

    override val valueType: ValueType get() = ValueType.String
    override val isTruthy: Boolean get() = value.isNotEmpty()

    override fun cast(newType: ValueType): Value<*>? = when (newType) {
        valueType -> this
        ValueType.Int -> value.toIntOrNull()?.let { IntValue(it) } // C#: TryParse, Java: try/catch, Kotlin: toIntOrNull
        ValueType.Float -> value.toFloatOrNull()?.let { FloatValue(it) }
        else -> throw badCastException(newType)
    }
}

// ---------------------------------------------------------------------------
// DivertTargetValue — identical across all three; truthiness throws exception
// ---------------------------------------------------------------------------
class DivertTargetValue(targetPath: Path? = null) : Value<Path?>(targetPath) {

    var targetPath: Path?
        get() = value
        set(v) { value = v }

    override val valueType: ValueType get() = ValueType.DivertTarget
    override val isTruthy: Boolean get() = throw StoryException("Shouldn't be checking the truthiness of a divert target")

    override fun cast(newType: ValueType): Value<*>? = when (newType) {
        valueType -> this
        else -> throw badCastException(newType)
    }

    override fun toString(): String = "DivertTargetValue($targetPath)"
}

// ---------------------------------------------------------------------------
// VariablePointerValue — all three have contextIndex; overrides copy() to preserve it
// ---------------------------------------------------------------------------
class VariablePointerValue(
    variableName: String? = null,
    var contextIndex: Int = -1  // -1=unknown, 0=global, 1+=callstack index+1
) : Value<String?>(variableName) {

    var variableName: String?
        get() = value
        set(v) { value = v }

    override val valueType: ValueType get() = ValueType.VariablePointer
    override val isTruthy: Boolean get() = throw StoryException("Shouldn't be checking the truthiness of a variable pointer")

    override fun cast(newType: ValueType): Value<*>? = when (newType) {
        valueType -> this
        else -> throw badCastException(newType)
    }

    override fun copy(): InkObject = VariablePointerValue(variableName, contextIndex)

    override fun toString(): String = "VariablePointerValue($variableName)"
}

// ---------------------------------------------------------------------------
// ListValue — wraps InkList; casts use max item (identical in C#/Java/JS)
// Will be fully functional once InkList is ported.
// ---------------------------------------------------------------------------
class ListValue : Value<InkList> {

    constructor(list: InkList) : super(list)
    constructor() : super(InkList())
    constructor(singleItem: InkListItem, singleValue: Int) : super(InkList().also { it[singleItem] = singleValue })

    override val valueType: ValueType get() = ValueType.List
    override val isTruthy: Boolean get() = value.size > 0

    override fun cast(newType: ValueType): Value<*>? = when (newType) {
        ValueType.Int -> {
            val max = value.maxItem
            if (max.key.isNull) IntValue(0) else IntValue(max.value)
        }
        ValueType.Float -> {
            val max = value.maxItem
            if (max.key.isNull) FloatValue(0.0f) else FloatValue(max.value.toFloat())
        }
        ValueType.String -> {
            val max = value.maxItem
            if (max.key.isNull) StringValue("") else StringValue(max.key.toString())
        }
        valueType -> this
        else -> throw badCastException(newType)
    }

    companion object {
        fun retainListOriginsForAssignment(oldValue: InkObject, newValue: InkObject) {
            val oldList = oldValue as? ListValue
            val newList = newValue as? ListValue
            if (oldList != null && newList != null && newList.value.size == 0) {
                newList.value.setInitialOriginNames(oldList.value.originNames)
            }
        }
    }
}
