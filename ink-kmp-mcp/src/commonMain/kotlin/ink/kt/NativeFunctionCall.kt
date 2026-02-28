package ink.kt

import kotlin.math.*

/**
 * Built-in operations for the ink VM: arithmetic, comparison, logic, list ops.
 *
 * Compared all three implementations:
 * - C#: typed delegates `BinaryOp<T>` / `UnaryOp<T>` — clean but still erased at runtime
 * - Java: `BinaryOp` / `UnaryOp` interfaces with Object — verbose anonymous inner classes
 * - JS: plain function callbacks
 *
 * Kotlin: **fun interface (SAM) supplier pattern**
 * - `fun interface` enables SAM conversion (lambdas auto-convert)
 * - Supplier pattern: each operation is a functional interface that *supplies* a result
 * - Type-safe at declaration, composable, and Kotlin-idiomatic
 * - LinkedHashMap registry: insertion-order + O(1) lookup
 * - kotlin.math for cross-platform (pow, floor, ceil)
 */
class NativeFunctionCall : InkObject {

    // --- Supplier functional interfaces (SAM) ---
    // C# uses `delegate object BinaryOp<T>(T left, T right)`
    // Java uses `interface BinaryOp { Object invoke(Object, Object) }`
    // Kotlin: fun interface with SAM conversion — lambdas auto-convert
    fun interface BinaryOp {
        fun invoke(left: Any?, right: Any?): Any?
    }

    fun interface UnaryOp {
        fun invoke(value: Any?): Any?
    }

    private var _name: String? = null
    private var _numberOfParameters: Int = 0
    private var _isPrototype: Boolean = false
    private var _operationFuncs: LinkedHashMap<ValueType, Any>? = null
    private var _prototype: NativeFunctionCall? = null

    var name: String?
        get() = _name
        set(value) {
            _name = value
            if (!_isPrototype) _prototype = nativeFunctions[_name]
        }

    val numberOfParameters: Int
        get() = _prototype?.numberOfParameters ?: _numberOfParameters

    constructor() {
        generateNativeFunctionsIfNecessary()
    }

    constructor(name: String) : this() {
        this.name = name
    }

    internal constructor(name: String, numberOfParameters: Int) {
        _isPrototype = true
        _name = name
        _numberOfParameters = numberOfParameters
    }

    internal fun addOpFuncForType(valType: ValueType, op: Any) {
        if (_operationFuncs == null) _operationFuncs = LinkedHashMap()
        _operationFuncs!![valType] = op
    }

    fun call(parameters: List<InkObject>): InkObject? {
        if (_prototype != null) return _prototype!!.call(parameters)

        require(numberOfParameters == parameters.size) { "Unexpected number of parameters" }

        var hasList = false
        for (p in parameters) {
            if (p is InkVoid) throw StoryException(
                "Attempting to perform $name on a void value. Did you forget to 'return' a value from a function you called here?"
            )
            if (p is ListValue) hasList = true
        }

        if (parameters.size == 2 && hasList) return callBinaryListOperation(parameters)

        val coercedParams = coerceValuesToSingleType(parameters)
        return callType(coercedParams)
    }

    private fun callBinaryListOperation(parameters: List<InkObject>): Value<*> {
        if ((name == "+" || name == "-") && parameters[0] is ListValue && parameters[1] is IntValue) {
            return callListIncrementOperation(parameters)
        }

        val v1 = parameters[0] as Value<*>
        val v2 = parameters[1] as Value<*>

        // And/or with any other type requires coercion to bool (int)
        if ((name == "&&" || name == "||") && (v1.valueType != ValueType.List || v2.valueType != ValueType.List)) {
            val op = _operationFuncs!![ValueType.Int] as BinaryOp
            val result = op.invoke(if (v1.isTruthy) 1 else 0, if (v2.isTruthy) 1 else 0)
            return BoolValue(result as Boolean)
        }

        if (v1.valueType == ValueType.List && v2.valueType == ValueType.List) {
            return callType(listOf(v1, v2)) as Value<*>
        }

        throw StoryException("Cannot use '$name' operation on ${v1.valueType} and ${v2.valueType}")
    }

    private fun callListIncrementOperation(listIntParams: List<InkObject>): Value<*> {
        val listVal = listIntParams[0] as ListValue
        val intVal = listIntParams[1] as IntValue
        val resultRawList = InkList()

        for ((listItem, listItemValue) in listVal.value) {
            val intOp = _operationFuncs!![ValueType.Int] as BinaryOp
            val targetInt = intOp.invoke(listItemValue, intVal.value) as Int

            val itemOrigin = listVal.value.origins?.firstOrNull { it.name == listItem.originName }
            if (itemOrigin != null) {
                val incrementedItem = itemOrigin.getItemWithValue(targetInt)
                if (incrementedItem != null) resultRawList[incrementedItem] = targetInt
            }
        }

        return ListValue(resultRawList)
    }

    private fun callType(parametersOfSingleType: List<Value<*>>): InkObject? {
        val param1 = parametersOfSingleType[0]
        val valType = param1.valueType
        val paramCount = parametersOfSingleType.size

        require(paramCount in 1..2) { "Unexpected number of parameters: $paramCount" }

        val opForType = _operationFuncs?.get(valType)
            ?: throw StoryException("Cannot perform operation '$name' on $valType")

        return if (paramCount == 2) {
            val op = opForType as BinaryOp
            Value.create(op.invoke(param1.valueObject, parametersOfSingleType[1].valueObject))
        } else {
            val op = opForType as UnaryOp
            Value.create(op.invoke(param1.valueObject))
        }
    }

    private fun coerceValuesToSingleType(parametersIn: List<InkObject>): List<Value<*>> {
        var valType = ValueType.Int
        var specialCaseList: ListValue? = null

        for (obj in parametersIn) {
            val v = obj as Value<*>
            if (v.valueType.ordinal > valType.ordinal) valType = v.valueType
            if (v.valueType == ValueType.List) specialCaseList = v as ListValue
        }

        val parametersOut = ArrayList<Value<*>>()

        if (valType == ValueType.List) {
            for (p in parametersIn) {
                val v = p as Value<*>
                when (v.valueType) {
                    ValueType.List -> parametersOut.add(v)
                    ValueType.Int -> {
                        val intVal = v.valueObject as Int
                        val list = specialCaseList!!.value.originOfMaxItem
                            ?: throw StoryException("Could not find List origin for coercion")
                        val item = list.getItemWithValue(intVal)
                            ?: throw StoryException("Could not find List item with value $intVal in ${list.name}")
                        parametersOut.add(ListValue(item, intVal))
                    }
                    else -> throw StoryException("Cannot mix Lists and ${v.valueType} values in this operation")
                }
            }
        } else {
            for (p in parametersIn) {
                val v = p as Value<*>
                parametersOut.add(v.cast(valType) ?: throw StoryException("Failed to cast ${v.valueType} to $valType"))
            }
        }

        return parametersOut
    }

    override fun toString(): String = "Native '$name'"

    companion object {
        // --- Operation name constants ---
        const val Add = "+"
        const val Subtract = "-"
        const val Multiply = "*"
        const val Divide = "/"
        const val Mod = "%"
        const val Negate = "_"
        const val Equal = "=="
        const val NotEquals = "!="
        const val Greater = ">"
        const val Less = "<"
        const val GreaterThanOrEquals = ">="
        const val LessThanOrEquals = "<="
        const val Not = "!"
        const val And = "&&"
        const val Or = "||"
        const val Min = "MIN"
        const val Max = "MAX"
        const val Pow = "POW"
        const val Floor = "FLOOR"
        const val Ceiling = "CEILING"
        const val CastInt = "INT"
        const val CastFloat = "FLOAT"
        const val Has = "?"
        const val Hasnt = "!?"
        const val Intersect = "^"
        const val ListMin = "LIST_MIN"
        const val ListMax = "LIST_MAX"
        const val All = "LIST_ALL"
        const val Count = "LIST_COUNT"
        const val ValueOfList = "LIST_VALUE"
        const val Invert = "LIST_INVERT"

        // LinkedHashMap: insertion-order + O(1) lookup
        private var nativeFunctions: LinkedHashMap<String, NativeFunctionCall> = LinkedHashMap()

        fun callExistsWithName(functionName: String): Boolean {
            generateNativeFunctionsIfNecessary()
            return functionName in nativeFunctions
        }

        fun callWithName(functionName: String): NativeFunctionCall = NativeFunctionCall(functionName)

        // --- Supplier registration helpers ---
        // Each helper takes a `fun interface` (SAM) and registers it as a supplier
        private fun addOp(name: String, args: Int, valType: ValueType, op: Any) {
            val nativeFunc = nativeFunctions.getOrPut(name) { NativeFunctionCall(name, args) }
            nativeFunc.addOpFuncForType(valType, op)
        }

        private fun intBin(name: String, op: BinaryOp) = addOp(name, 2, ValueType.Int, op)
        private fun intUn(name: String, op: UnaryOp) = addOp(name, 1, ValueType.Int, op)
        private fun floatBin(name: String, op: BinaryOp) = addOp(name, 2, ValueType.Float, op)
        private fun floatUn(name: String, op: UnaryOp) = addOp(name, 1, ValueType.Float, op)
        private fun strBin(name: String, op: BinaryOp) = addOp(name, 2, ValueType.String, op)
        private fun listBin(name: String, op: BinaryOp) = addOp(name, 2, ValueType.List, op)
        private fun listUn(name: String, op: UnaryOp) = addOp(name, 1, ValueType.List, op)

        /** C#'s Identity<T> — supplier that returns the input unchanged. */
        private val identity = UnaryOp { v -> v }

        private fun generateNativeFunctionsIfNecessary() {
            if (nativeFunctions.isNotEmpty()) return

            // --- Int operations (SAM supplier lambdas) ---
            intBin(Add, BinaryOp { l, r -> (l as Int) + (r as Int) })
            intBin(Subtract, BinaryOp { l, r -> (l as Int) - (r as Int) })
            intBin(Multiply, BinaryOp { l, r -> (l as Int) * (r as Int) })
            intBin(Divide, BinaryOp { l, r -> (l as Int) / (r as Int) })
            intBin(Mod, BinaryOp { l, r -> (l as Int) % (r as Int) })
            intUn(Negate, UnaryOp { v -> -(v as Int) })

            intBin(Equal, BinaryOp { l, r -> (l as Int) == (r as Int) })
            intBin(Greater, BinaryOp { l, r -> (l as Int) > (r as Int) })
            intBin(Less, BinaryOp { l, r -> (l as Int) < (r as Int) })
            intBin(GreaterThanOrEquals, BinaryOp { l, r -> (l as Int) >= (r as Int) })
            intBin(LessThanOrEquals, BinaryOp { l, r -> (l as Int) <= (r as Int) })
            intBin(NotEquals, BinaryOp { l, r -> (l as Int) != (r as Int) })

            intUn(Not, UnaryOp { v -> (v as Int) == 0 })
            intBin(And, BinaryOp { l, r -> (l as Int) != 0 && (r as Int) != 0 })
            intBin(Or, BinaryOp { l, r -> (l as Int) != 0 || (r as Int) != 0 })

            intBin(Max, BinaryOp { l, r -> maxOf(l as Int, r as Int) })
            intBin(Min, BinaryOp { l, r -> minOf(l as Int, r as Int) })
            intBin(Pow, BinaryOp { l, r -> (l as Int).toFloat().pow(r as Int) })
            intUn(Floor, identity)
            intUn(Ceiling, identity)
            intUn(CastInt, identity)
            intUn(CastFloat, UnaryOp { v -> (v as Int).toFloat() })

            // --- Float operations ---
            floatBin(Add, BinaryOp { l, r -> (l as Float) + (r as Float) })
            floatBin(Subtract, BinaryOp { l, r -> (l as Float) - (r as Float) })
            floatBin(Multiply, BinaryOp { l, r -> (l as Float) * (r as Float) })
            floatBin(Divide, BinaryOp { l, r -> (l as Float) / (r as Float) })
            floatBin(Mod, BinaryOp { l, r -> (l as Float) % (r as Float) })
            floatUn(Negate, UnaryOp { v -> -(v as Float) })

            floatBin(Equal, BinaryOp { l, r -> (l as Float) == (r as Float) })
            floatBin(Greater, BinaryOp { l, r -> (l as Float) > (r as Float) })
            floatBin(Less, BinaryOp { l, r -> (l as Float) < (r as Float) })
            floatBin(GreaterThanOrEquals, BinaryOp { l, r -> (l as Float) >= (r as Float) })
            floatBin(LessThanOrEquals, BinaryOp { l, r -> (l as Float) <= (r as Float) })
            floatBin(NotEquals, BinaryOp { l, r -> (l as Float) != (r as Float) })

            floatUn(Not, UnaryOp { v -> (v as Float) == 0.0f })
            floatBin(And, BinaryOp { l, r -> (l as Float) != 0.0f && (r as Float) != 0.0f })
            floatBin(Or, BinaryOp { l, r -> (l as Float) != 0.0f || (r as Float) != 0.0f })

            floatBin(Max, BinaryOp { l, r -> maxOf(l as Float, r as Float) })
            floatBin(Min, BinaryOp { l, r -> minOf(l as Float, r as Float) })
            floatBin(Pow, BinaryOp { l, r -> (l as Float).pow(r as Float) })
            floatUn(Floor, UnaryOp { v -> floor(v as Float) })
            floatUn(Ceiling, UnaryOp { v -> ceil(v as Float) })
            floatUn(CastInt, UnaryOp { v -> (v as Float).toInt() })
            floatUn(CastFloat, identity)

            // --- String operations ---
            strBin(Add, BinaryOp { l, r -> (l as String) + (r as String) })
            strBin(Equal, BinaryOp { l, r -> l == r })
            strBin(NotEquals, BinaryOp { l, r -> l != r })
            strBin(Has, BinaryOp { l, r -> (l as String).contains(r.toString()) })
            strBin(Hasnt, BinaryOp { l, r -> !(l as String).contains(r.toString()) })

            // --- List operations ---
            listBin(Add, BinaryOp { l, r -> (l as InkList).union(r as InkList) })
            listBin(Subtract, BinaryOp { l, r -> (l as InkList).without(r as InkList) })
            listBin(Has, BinaryOp { l, r -> (l as InkList).containsList(r as InkList) })
            listBin(Hasnt, BinaryOp { l, r -> !(l as InkList).containsList(r as InkList) })
            listBin(Intersect, BinaryOp { l, r -> (l as InkList).intersect(r as InkList) })
            listBin(Equal, BinaryOp { l, r -> (l as InkList) == r })
            listBin(Greater, BinaryOp { l, r -> (l as InkList).let { it.isNotEmpty() && it.greaterThan(r as InkList) } })
            listBin(Less, BinaryOp { l, r -> (l as InkList).lessThan(r as InkList) })
            listBin(GreaterThanOrEquals, BinaryOp { l, r -> (l as InkList).let { it.isNotEmpty() && it.greaterThanOrEquals(r as InkList) } })
            listBin(LessThanOrEquals, BinaryOp { l, r -> (l as InkList).let { it.isNotEmpty() && it.lessThanOrEquals(r as InkList) } })
            listBin(NotEquals, BinaryOp { l, r -> (l as InkList) != r })
            listBin(And, BinaryOp { l, r -> (l as InkList).isNotEmpty() && (r as InkList).isNotEmpty() })
            listBin(Or, BinaryOp { l, r -> (l as InkList).isNotEmpty() || (r as InkList).isNotEmpty() })
            listUn(Not, UnaryOp { v -> (v as InkList).isEmpty() })
            listUn(Invert, UnaryOp { v -> (v as InkList).inverse })
            listUn(All, UnaryOp { v -> (v as InkList).all })
            listUn(ListMin, UnaryOp { v -> (v as InkList).minAsList() })
            listUn(ListMax, UnaryOp { v -> (v as InkList).maxAsList() })
            listUn(Count, UnaryOp { v -> (v as InkList).size })
            listUn(ValueOfList, UnaryOp { v -> (v as InkList).maxItem.value })

            // --- DivertTarget equality ---
            addOp(Equal, 2, ValueType.DivertTarget, BinaryOp { l, r -> l == r })
            addOp(NotEquals, 2, ValueType.DivertTarget, BinaryOp { l, r -> l != r })
        }
    }
}
