package com.bladecoder.ink.runtime

/**
 * Encompasses all the global variables in an ink Story, and allows binding
 * of a VariableChanged event so that game code can be notified whenever
 * global variables change.
 *
 * Design decisions from comparing all three implementations:
 * - C#: VariablesState : IEnumerable<string>, delegate VariableChanged, indexer this[], event pattern
 * - Java: VariablesState implements Iterable<String>, VariableChanged interface, get/set methods
 * - JS: VariablesState class, callback function, $ / $$ methods
 *
 * Kotlin improvements over all three:
 * - fun interface VariableChanged — SAM conversion, type-safe (vs Java's verbose interface, C# delegate)
 * - operator get/set — val x = state["var"] / state["var"] = value (like C# indexer, cleaner than Java)
 * - Iterable<String> via delegation — no manual iterator() boilerplate
 * - LinkedHashMap for globalVariables (insertion-order + O(1), per project directive)
 * - Nullable returns instead of C#'s TryGet out-param pattern
 * - when expression for type-safe RuntimeObjectsEqual (vs Java/C# cascading if-instanceof)
 * - StatePatch integration — transactional overlay for background saves
 */
class VariablesState(
    private var _callStack: CallStack,
    private val _listDefsOrigin: ListDefinitionsOrigin
) : Iterable<String> {

    /**
     * Functional supplier for variable change observation.
     * C#: delegate, Java: interface, JS: callback function.
     * Kotlin: fun interface — enables lambda SAM conversion.
     */
    fun interface VariableChanged {
        fun onVariableChanged(variableName: String, newValue: InkObject)
    }

    private var _globalVariables: LinkedHashMap<String, InkObject> = LinkedHashMap()
    private var _defaultGlobalVariables: LinkedHashMap<String, InkObject>? = null
    private var _batchObservingVariableChanges: Boolean = false
    private var _changedVariablesForBatchObs: LinkedHashSet<String>? = null

    var variableChangedEvent: VariableChanged? = null

    var patch: StatePatch? = null

    var callStack: CallStack
        get() = _callStack
        set(value) { _callStack = value }

    // --- Global variable access (read-only map exposure) ---

    val globalVariables: LinkedHashMap<String, InkObject> get() = _globalVariables

    // --- Operator access (Kotlin-unique — like C# indexer) ---

    /**
     * Get the value of a named global ink variable.
     * C#: this[variableName] { get }, Java: get(variableName), JS: $(variableName)
     */
    operator fun get(variableName: String): Any? {
        val varContents = patch?.getGlobal(variableName)
        if (varContents != null) return (varContents as? Value<*>)?.valueObject

        val global = _globalVariables[variableName]
        if (global != null) return (global as? Value<*>)?.value

        val defaultGlobal = _defaultGlobalVariables?.get(variableName)
        if (defaultGlobal != null) return (defaultGlobal as? Value<*>)?.value

        return null
    }

    /**
     * Set the value of a named global ink variable.
     * C#: this[variableName] { set }, Java: set(variableName, value)
     */
    operator fun set(variableName: String, value: Any?) {
        if (_defaultGlobalVariables?.containsKey(variableName) != true) {
            throw StoryException("Cannot assign to a variable ($variableName) that hasn't been declared in the story")
        }

        val inkValue = Value.create(value)
            ?: if (value == null) throw StoryException("Cannot pass null to VariableState")
               else throw StoryException("Invalid value passed to VariableState: $value")

        setGlobal(variableName, inkValue)
    }

    /** Enumerator over all global variable names. */
    override fun iterator(): Iterator<String> = _globalVariables.keys.iterator()

    // --- Variable observation (batch mode for background saves) ---

    fun startVariableObservation() {
        _batchObservingVariableChanges = true
        _changedVariablesForBatchObs = LinkedHashSet()
    }

    fun completeVariableObservation(): LinkedHashMap<String, InkObject> {
        _batchObservingVariableChanges = false

        val changedVars = LinkedHashMap<String, InkObject>()
        _changedVariablesForBatchObs?.forEach { variableName ->
            val currentValue = _globalVariables[variableName]
            if (currentValue != null) changedVars[variableName] = currentValue
        }

        // Patch may still be active — e.g. if we were in the middle of a background save
        patch?.let { p ->
            for (variableName in p.changedVariables) {
                val patchedVal = p.getGlobal(variableName)
                if (patchedVal != null) changedVars[variableName] = patchedVal
            }
        }

        _changedVariablesForBatchObs = null
        return changedVars
    }

    fun notifyObservers(changedVars: Map<String, InkObject>) {
        val event = variableChangedEvent ?: return
        for ((name, value) in changedVars) {
            event.onVariableChanged(name, value)
        }
    }

    // --- Variable assignment (core ink operation) ---

    fun assign(varAss: VariableAssignment, value: InkObject) {
        var name = varAss.variableName!!
        var contextIndex = -1
        var assignedValue = value

        // Are we assigning to a global variable?
        var setGlobal = if (varAss.isNewDeclaration) {
            varAss.isGlobal
        } else {
            globalVariableExistsWithName(name)
        }

        // Constructing new variable pointer reference
        if (varAss.isNewDeclaration) {
            val varPointer = assignedValue as? VariablePointerValue
            if (varPointer != null) {
                assignedValue = resolveVariablePointer(varPointer)
            }
        } else {
            // Assign to existing variable pointer?
            // Then assign to the variable that the pointer is pointing to by name.
            // De-reference variable reference to point to
            var existingPointer: VariablePointerValue?
            do {
                existingPointer = getRawVariableWithName(name, contextIndex) as? VariablePointerValue
                if (existingPointer != null) {
                    name = existingPointer.variableName!!
                    contextIndex = existingPointer.contextIndex
                    setGlobal = (contextIndex == 0)
                }
            } while (existingPointer != null)
        }

        if (setGlobal) {
            setGlobal(name, assignedValue)
        } else {
            _callStack.setTemporaryVariable(name, assignedValue, varAss.isNewDeclaration, contextIndex)
        }
    }

    // --- Patch management ---

    fun applyPatch() {
        val p = patch ?: return
        for ((name, value) in p.globals) {
            _globalVariables[name] = value
        }
        _changedVariablesForBatchObs?.let { obs ->
            for (name in p.changedVariables) obs.add(name)
        }
        patch = null
    }

    // --- JSON serialization support ---

    fun setJsonToken(jToken: Map<String, Any?>) {
        _globalVariables.clear()
        val defaults = _defaultGlobalVariables ?: return
        for ((name, defaultValue) in defaults) {
            val loadedToken = jToken[name]
            if (loadedToken != null) {
                _globalVariables[name] = Json.jTokenToRuntimeObject(loadedToken)
            } else {
                _globalVariables[name] = defaultValue
            }
        }
    }

    fun snapshotDefaultGlobals() {
        _defaultGlobalVariables = LinkedHashMap(_globalVariables)
    }

    /**
     * When saving out JSON state, we can skip saving global values that
     * remain equal to the initial values that were declared in ink.
     * This makes the save file (potentially) much smaller assuming that
     * at least a portion of the globals haven't changed.
     */
    var dontSaveDefaultValues: Boolean = true

    // --- Variable resolution ---

    fun getVariableWithName(name: String, contextIndex: Int = -1): InkObject? {
        val varValue = getRawVariableWithName(name, contextIndex)
        val varPointer = varValue as? VariablePointerValue
        return if (varPointer != null) valueAtVariablePointer(varPointer) else varValue
    }

    fun getRawVariableWithName(name: String, contextIndex: Int): InkObject? {
        // 0 context = global
        if (contextIndex == 0 || contextIndex == -1) {
            patch?.getGlobal(name)?.let { return it }
            _globalVariables[name]?.let { return it }

            // Getting variables can actually happen during globals set up since you can do
            // VAR x = A_LIST_ITEM
            // So _defaultGlobalVariables may be null.
            _defaultGlobalVariables?.get(name)?.let { return it }

            _listDefsOrigin.findSingleItemListWithName(name)?.let { return it }
        }

        // Temporary
        return _callStack.getTemporaryVariableWithName(name, contextIndex)
    }

    fun valueAtVariablePointer(pointer: VariablePointerValue): InkObject? =
        getVariableWithName(pointer.variableName!!, pointer.contextIndex)

    fun tryGetDefaultVariableValue(name: String): InkObject? = _defaultGlobalVariables?.get(name)

    fun globalVariableExistsWithName(name: String): Boolean =
        _globalVariables.containsKey(name) ||
        (_defaultGlobalVariables?.containsKey(name) == true)

    // --- Internal ---

    private fun getContextIndexOfVariableNamed(varName: String): Int =
        if (globalVariableExistsWithName(varName)) 0 else _callStack.currentElementIndex

    /**
     * Given a variable pointer with just the name of the target known, resolve to a variable
     * pointer that more specifically points to the exact instance: whether it's global,
     * or the exact position of a temporary on the callstack.
     */
    private fun resolveVariablePointer(varPointer: VariablePointerValue): VariablePointerValue {
        var contextIndex = varPointer.contextIndex
        if (contextIndex == -1) {
            contextIndex = getContextIndexOfVariableNamed(varPointer.variableName!!)
        }

        val valueOfVariablePointedTo = getRawVariableWithName(varPointer.variableName!!, contextIndex)

        // Extra layer of indirection:
        // When accessing a pointer to a pointer (e.g. when calling nested or
        // recursive functions that take a variable references, ensure we don't create
        // a chain of indirection by just returning the final target.
        val doubleRedirectionPointer = valueOfVariablePointedTo as? VariablePointerValue
        return doubleRedirectionPointer ?: VariablePointerValue(varPointer.variableName, contextIndex)
    }

    fun setGlobal(variableName: String, value: InkObject) {
        var oldValue: InkObject? = patch?.getGlobal(variableName)
        if (oldValue == null) oldValue = _globalVariables[variableName]

        ListValue.retainListOriginsForAssignment(oldValue ?: return, value)

        if (patch != null) {
            patch!!.setGlobal(variableName, value)
        } else {
            _globalVariables[variableName] = value
        }

        if (variableChangedEvent != null && value != oldValue) {
            if (_batchObservingVariableChanges) {
                if (patch != null) {
                    patch!!.addChangedVariable(variableName)
                } else {
                    _changedVariablesForBatchObs?.add(variableName)
                }
            } else {
                variableChangedEvent!!.onVariableChanged(variableName, value)
            }
        }
    }

    /**
     * Fast equality check for runtime objects — avoids full equals() for primitives.
     * C# and Java both have this same optimization. Kotlin's when is cleaner than cascading if-instanceof.
     */
    fun runtimeObjectsEqual(obj1: InkObject, obj2: InkObject): Boolean {
        if (obj1::class != obj2::class) return false
        return when (obj1) {
            is BoolValue -> obj1.value == (obj2 as BoolValue).value
            is IntValue -> obj1.value == (obj2 as IntValue).value
            is FloatValue -> obj1.value == (obj2 as FloatValue).value
            is Value<*> -> obj1.valueObject == (obj2 as Value<*>).valueObject
            else -> throw StoryException("FastRoughDefinitelyEquals: Unsupported runtime object type: ${obj1::class}")
        }
    }

    companion object {
        /** Static flag — matches C#/Java static field. */
        var dontSaveDefaultValuesGlobal: Boolean = true
    }
}

/**
 * Placeholder for Json deserialization — will be fully implemented when JsonSerialisation is ported.
 * C#: Json.JTokenToRuntimeObject, Java: Json.jTokenToRuntimeObject
 */
internal object Json {
    fun jTokenToRuntimeObject(token: Any?): InkObject {
        // Stub — will be replaced by full JsonSerialisation port
        return when (token) {
            is Int -> IntValue(token)
            is Float -> FloatValue(token)
            is Double -> FloatValue(token.toFloat())
            is Boolean -> BoolValue(token)
            is String -> StringValue(token)
            else -> throw StoryException("Unsupported JSON token type: ${token?.let { it::class }}")
        }
    }
}
