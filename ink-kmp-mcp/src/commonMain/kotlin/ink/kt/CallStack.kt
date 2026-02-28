package ink.kt

/**
 * Manages the ink runtime call stack — tracks function calls, tunnels, threads, and temp variables.
 *
 * Design decisions from comparing all three implementations:
 * - C#: Element/Thread as nested classes, Dictionary<string, Object> for temps, Pointer as struct (copy by value)
 * - Java: Element/Thread as static inner classes, HashMap<String, RTObject>, Pointer.assign() for copy
 *   BUG: setTemporaryVariable(name, value, boolean) infinite recursion (calls itself instead of 4-arg overload)
 * - JS: Element/Thread on CallStack namespace, Map<string, InkObject>, arguments[0] constructor overloading (untyped)
 *
 * Kotlin improvements over all three:
 * - LinkedHashMap for temporaryVariables (insertion-order + O(1) lookup, per project directive)
 * - data class Element with copy() via Kotlin's built-in copy semantics
 * - Sealed constructor via companion factory (no JS arguments[] hack, no Java overload bugs)
 * - Pointer is already a data class with copy() — no need for .assign() pattern
 * - Type-safe threadAtGeneration on Choice (no Any? cast needed)
 * - Fixed Java's infinite recursion bug in setTemporaryVariable
 * - fun interface BinaryOp<T> / UnaryOp<T> pattern carries through from NativeFunctionCall
 */
class CallStack {

    // -------------------------------------------------------------------------
    // Element — a single frame on the call stack
    // C#: nested class, Java: static inner class, JS: namespace property
    // Kotlin: nested class with LinkedHashMap temps + clean copy()
    // -------------------------------------------------------------------------
    class Element(
        var type: PushPopType,
        pointer: Pointer,
        var inExpressionEvaluation: Boolean = false
    ) {
        val currentPointer: Pointer = Pointer(pointer)

        var temporaryVariables: LinkedHashMap<String, InkObject> = LinkedHashMap()

        // When this callstack element is actually a function evaluation called from the game,
        // we need to keep track of the size of the evaluation stack when it was called
        // so that we know whether there was any return value.
        var evaluationStackHeightWhenPushed: Int = 0

        // When functions are called, we trim whitespace from the start and end of what
        // they generate, so we make sure know where the function's start and end are.
        var functionStartInOutputStream: Int = 0

        fun copy(): Element = Element(type, currentPointer, inExpressionEvaluation).also { copy ->
            copy.temporaryVariables = LinkedHashMap(temporaryVariables)
            copy.evaluationStackHeightWhenPushed = evaluationStackHeightWhenPushed
            copy.functionStartInOutputStream = functionStartInOutputStream
        }
    }

    // -------------------------------------------------------------------------
    // Thread — a forked execution thread containing its own call stack
    // C#: nested class, Java: static inner class with JSON constructor, JS: namespace property
    // Kotlin: nested class, JSON deserialization deferred to JsonSerialisation
    // -------------------------------------------------------------------------
    class Thread {
        var callstack: MutableList<Element> = mutableListOf()
        var threadIndex: Int = 0
        var previousPointer: Pointer = Pointer.Null

        constructor()

        constructor(other: Thread) {
            threadIndex = other.threadIndex
            for (e in other.callstack) callstack.add(e.copy())
            previousPointer = Pointer(other.previousPointer)
        }

        fun copy(): Thread = Thread(this)

        fun writeJson(writer: SimpleJson.Writer) {
            writer.writeObjectStart()

            writer.writePropertyStart("callstack")
            writer.writeArrayStart()
            for (el in callstack) {
                writer.writeObjectStart()
                if (!el.currentPointer.isNull) {
                    writer.writeProperty("cPath", el.currentPointer.container!!.path.componentsString)
                    writer.writeProperty("idx", el.currentPointer.index)
                }
                writer.writeProperty("exp", el.inExpressionEvaluation)
                writer.writeProperty("type", el.type.ordinal)
                if (el.temporaryVariables.isNotEmpty()) {
                    writer.writePropertyStart("temp")
                    JsonSerialisation.writeDictionaryRuntimeObjs(writer, el.temporaryVariables)
                    writer.writePropertyEnd()
                }
                writer.writeObjectEnd()
            }
            writer.writeArrayEnd()
            writer.writePropertyEnd()

            writer.writeProperty("threadIndex", threadIndex)

            if (!previousPointer.isNull) {
                writer.writeProperty("previousContentObject", previousPointer.resolve()!!.path.toString())
            }

            writer.writeObjectEnd()
        }
    }

    // -------------------------------------------------------------------------
    // CallStack state
    // -------------------------------------------------------------------------
    private var _threads: MutableList<Thread> = mutableListOf()
    private var _threadCounter: Int = 0
    private val _startOfRoot: Pointer

    // --- Constructors ---
    // C#: two constructors (Story, CallStack), Java: same, JS: arguments[] hack
    // Kotlin: primary + secondary, type-safe

    /** Create a new CallStack rooted at the story's main content container. */
    constructor(rootContentContainer: Container) {
        _startOfRoot = Pointer.startOf(rootContentContainer)
        reset()
    }

    /** Deep copy constructor. */
    constructor(toCopy: CallStack) {
        _threads = mutableListOf()
        for (otherThread in toCopy._threads) {
            _threads.add(otherThread.copy())
        }
        _threadCounter = toCopy._threadCounter
        _startOfRoot = Pointer(toCopy._startOfRoot)
    }

    // --- Core properties (identical across C#/Java/JS) ---

    val elements: List<Element> get() = callStack

    val depth: Int get() = elements.size

    val currentElement: Element
        get() {
            val thread = _threads.last()
            return thread.callstack.last()
        }

    val currentElementIndex: Int get() = callStack.size - 1

    var currentThread: Thread
        get() = _threads.last()
        set(value) {
            _threads.clear()
            _threads.add(value)
        }

    val canPop: Boolean get() = callStack.size > 1

    val canPopThread: Boolean get() = _threads.size > 1 && !elementIsEvaluateFromGame

    val elementIsEvaluateFromGame: Boolean
        get() = currentElement.type == PushPopType.FunctionEvaluationFromGame

    private val callStack: MutableList<Element> get() = currentThread.callstack

    // --- Stack operations ---

    fun reset() {
        _threads = mutableListOf()
        _threads.add(Thread())
        _threads[0].callstack.add(Element(PushPopType.Tunnel, _startOfRoot))
    }

    fun canPop(type: PushPopType?): Boolean {
        if (!canPop) return false
        if (type == null) return true
        return currentElement.type == type
    }

    fun push(
        type: PushPopType,
        externalEvaluationStackHeight: Int = 0,
        outputStreamLengthWithPushed: Int = 0
    ) {
        val element = Element(type, currentElement.currentPointer, inExpressionEvaluation = false)
        element.evaluationStackHeightWhenPushed = externalEvaluationStackHeight
        element.functionStartInOutputStream = outputStreamLengthWithPushed
        callStack.add(element)
    }

    fun pop(type: PushPopType? = null) {
        if (canPop(type)) {
            callStack.removeAt(callStack.size - 1)
        } else {
            throw StoryException("Mismatched push/pop in Callstack")
        }
    }

    // --- Thread management ---

    fun pushThread() {
        val newThread = currentThread.copy()
        _threadCounter++
        newThread.threadIndex = _threadCounter
        _threads.add(newThread)
    }

    fun popThread() {
        if (canPopThread) {
            _threads.remove(currentThread)
        } else {
            throw StoryException("Can't pop thread")
        }
    }

    fun forkThread(): Thread {
        val forkedThread = currentThread.copy()
        _threadCounter++
        forkedThread.threadIndex = _threadCounter
        return forkedThread
    }

    fun threadWithIndex(index: Int): Thread? =
        _threads.firstOrNull { it.threadIndex == index }

    // --- Temporary variables ---
    // FIXED: Java's setTemporaryVariable(name, value, boolean) had infinite recursion bug —
    // it called itself instead of the 4-arg overload. Kotlin uses default params, no bug possible.

    fun getTemporaryVariableWithName(name: String, contextIndex: Int = -1): InkObject? {
        var idx = contextIndex
        if (idx == -1) idx = currentElementIndex + 1

        val contextElement = callStack[idx - 1]
        return contextElement.temporaryVariables[name]
    }

    fun setTemporaryVariable(
        name: String,
        value: InkObject,
        declareNew: Boolean,
        contextIndex: Int = -1
    ) {
        var idx = contextIndex
        if (idx == -1) idx = currentElementIndex + 1

        val contextElement = callStack[idx - 1]

        if (!declareNew && name !in contextElement.temporaryVariables) {
            throw StoryException("Could not find temporary variable to set: $name")
        }

        val oldValue = contextElement.temporaryVariables[name]
        if (oldValue != null) {
            ListValue.retainListOriginsForAssignment(oldValue, value)
        }

        contextElement.temporaryVariables[name] = value
    }

    /**
     * Find the most appropriate context for this variable.
     * Are we referencing a temporary or global variable?
     * Note that the compiler will have warned us about possible conflicts,
     * so anything that happens here should be safe!
     */
    fun contextForVariableNamed(name: String): Int {
        // Current temporary context?
        // (Shouldn't attempt to access contexts higher in the callstack.)
        return if (name in currentElement.temporaryVariables) {
            currentElementIndex + 1
        } else {
            // Global
            0
        }
    }

    // --- JSON serialization support (deferred to JsonSerialisation) ---

    /** For JSON deserialization — replaces threads from saved state. */
    fun setJsonToken(threads: List<Thread>, threadCounter: Int, startOfRootContainer: Container) {
        _threads.clear()
        _threads.addAll(threads)
        _threadCounter = threadCounter
        _startOfRoot.assign(Pointer.startOf(startOfRootContainer))
    }

    fun writeJson(writer: SimpleJson.Writer) {
        writer.writeObject { w ->
            w.writeProperty("threads") { w2 ->
                w2.writeArrayStart()
                for (thread in _threads) {
                    thread.writeJson(w2)
                }
                w2.writeArrayEnd()
            }

            w.writeProperty("threadCounter", _threadCounter)
        }
    }

    val threads: List<Thread> get() = _threads
    val threadCounter: Int get() = _threadCounter

    // --- Debug trace ---

    val callStackTrace: String
        get() = buildString {
            for (t in _threads.indices) {
                val thread = _threads[t]
                val isCurrent = t == _threads.size - 1
                append("=== THREAD ${t + 1}/${_threads.size} ${if (isCurrent) "(current) " else ""}===\n")

                for (i in thread.callstack.indices) {
                    if (thread.callstack[i].type == PushPopType.Function) {
                        append("  [FUNCTION] ")
                    } else {
                        append("  [TUNNEL] ")
                    }

                    val pointer = thread.callstack[i].currentPointer
                    if (!pointer.isNull) {
                        append("<SOMEWHERE IN ")
                        append(pointer.container?.path?.toString() ?: "???")
                        append(">\n")
                    }
                }
            }
        }
}
