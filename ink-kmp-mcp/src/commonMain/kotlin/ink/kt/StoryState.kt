package ink.kt

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * All story state information — global variables, read counts, current pointer,
 * call stack, evaluation stack, output stream, flows, etc.
 *
 * Design decisions from comparing all three implementations:
 * - C#: Monolithic class with event delegates, Stream overload for toJson
 * - Java: Same monolithic pattern, HashMap everywhere, verbose InnerWriter for JSON
 * - JS: Same pattern, untyped arguments[], parseInt() casts
 *
 * Kotlin improvements over all three:
 * - **Functional enum OutputOp** — output stream operations as typed enum + fun interface
 * - **fun interface** for onDidLoadState callback (SAM conversion)
 * - **Kotlin properties** instead of get/set method pairs
 * - **LinkedHashMap** for visitCounts, turnIndices, namedFlows (insertion-order + O(1))
 * - **when expressions** instead of cascading if-instanceof chains
 * - **buildString** for output text/tag collection
 * - **kotlin.random.Random** for cross-platform deterministic seeding
 * - State operations are clean functional compositions
 */
class StoryState(
    /** Root content container — replaces Story reference for core state operations. */
    private val rootContentContainer: Container,
    /** List definitions for variable state initialization. */
    private val listDefsOrigin: ListDefinitionsOrigin,
    /** Error/warning handler — decouples from Story class. */
    private val errorHandler: ErrorHandler? = null
) {
    /**
     * Functional error handler — replaces Story.error() direct coupling.
     * C#: calls story.Error(), Java: calls story.error()
     * Kotlin: fun interface — injectable, testable, decoupled.
     */
    fun interface ErrorHandler {
        fun onError(message: String, isWarning: Boolean)
    }

    /**
     * Callback for when a state is loaded from JSON.
     * C#: event Action onDidLoadState, Java: not present
     * Kotlin: fun interface — SAM conversion.
     */
    fun interface OnDidLoadState {
        fun onLoaded()
    }

    // --- Output stream operation as functional enum ---
    // User directive: "StoryState should be functional enum"
    // Organizes output stream processing as typed operations

    /**
     * Output stream operations — functional enum pattern.
     * Each operation is a typed, named unit of work on the output stream.
     * Replaces the scattered methods in C#/Java/JS with an organized dispatch.
     */
    enum class OutputOp {
        /** Mark output stream as dirty (text + tags need recalculation). */
        DIRTY,
        /** Trim newlines from the end of the output stream. */
        TRIM_NEWLINES,
        /** Remove existing glue from the output stream. */
        REMOVE_GLUE,
        /** Trim whitespace from the end of a function call output. */
        TRIM_FUNCTION_END
    }

    /**
     * Execute an output stream operation.
     * Functional dispatch — cleaner than scattered methods.
     */
    fun executeOutputOp(op: OutputOp) {
        when (op) {
            OutputOp.DIRTY -> outputStreamDirty()
            OutputOp.TRIM_NEWLINES -> trimNewlinesFromOutputStream()
            OutputOp.REMOVE_GLUE -> removeExistingGlue()
            OutputOp.TRIM_FUNCTION_END -> trimWhitespaceFromFunctionEnd()
        }
    }

    // --- Constants (identical across all three) ---

    companion object {
        const val INK_SAVE_STATE_VERSION = 10
        const val MIN_COMPATIBLE_LOAD_VERSION = 8
        const val DEFAULT_FLOW_NAME = "DEFAULT_FLOW"
    }

    // --- State fields ---

    var currentErrors: MutableList<String>? = null
        private set
    var currentWarnings: MutableList<String>? = null
        private set

    var currentTurnIndex: Int = -1
    var didSafeExit: Boolean = false

    var divertedPointer: Pointer = Pointer.Null

    val evaluationStack: MutableList<InkObject> = mutableListOf()

    var storySeed: Int = 0
        internal set
    var previousRandom: Int = 0

    private var _visitCounts: LinkedHashMap<String, Int> = LinkedHashMap()
    private var _turnIndices: LinkedHashMap<String, Int> = LinkedHashMap()

    val visitCounts: LinkedHashMap<String, Int> get() = _visitCounts
    val turnIndices: LinkedHashMap<String, Int> get() = _turnIndices

    var variablesState: VariablesState
        private set

    var patch: StatePatch? = null

    // --- Multi-flow support ---

    var namedFlows: LinkedHashMap<String, InkFlow>? = null
        private set
    var currentFlow: InkFlow
        private set

    private var _aliveFlowNames: List<String>? = null
    private var _aliveFlowNamesDirty: Boolean = true

    // --- Output caching ---

    private var _outputStreamTextDirty: Boolean = true
    private var _outputStreamTagsDirty: Boolean = true
    private var _currentText: String = ""
    private var _currentTags: List<String> = emptyList()

    // --- Callbacks ---

    var onDidLoadState: OnDidLoadState? = null

    // --- Initialization ---

    init {
        currentFlow = InkFlow(DEFAULT_FLOW_NAME, rootContentContainer)
        outputStreamDirty()
        _aliveFlowNamesDirty = true

        variablesState = VariablesState(callStack, listDefsOrigin)

        // Seed the shuffle random numbers — kotlin.random.Random.Default is already time-seeded
        storySeed = abs(Random.Default.nextInt()) % 100
        previousRandom = 0

        goToStart()
    }

    // --- Derived properties (clean Kotlin properties vs Java getters) ---

    val callStack: CallStack get() = currentFlow.callStack

    val callStackDepth: Int get() = callStack.depth

    val outputStream: MutableList<InkObject> get() = currentFlow.outputStream

    val currentChoices: List<Choice>
        get() = if (canContinue) emptyList() else currentFlow.currentChoices

    val generatedChoices: MutableList<Choice> get() = currentFlow.currentChoices

    val canContinue: Boolean
        get() = !currentPointer.isNull && !hasError

    val hasError: Boolean get() = currentErrors?.isNotEmpty() == true

    val hasWarning: Boolean get() = currentWarnings?.isNotEmpty() == true

    var currentPointer: Pointer
        get() = callStack.currentElement.currentPointer
        set(value) { callStack.currentElement.currentPointer.assign(value) }

    var previousPointer: Pointer
        get() = callStack.currentThread.previousPointer
        set(value) { callStack.currentThread.previousPointer.assign(value) }

    var inExpressionEvaluation: Boolean
        get() = callStack.currentElement.inExpressionEvaluation
        set(value) { callStack.currentElement.inExpressionEvaluation = value }

    val currentPathString: String?
        get() = if (currentPointer.isNull) null else currentPointer.path?.toString()

    val previousPathString: String?
        get() = if (previousPointer.isNull) null else previousPointer.path?.toString()

    val currentFlowName: String get() = currentFlow.name

    val currentFlowIsDefaultFlow: Boolean get() = currentFlow.name == DEFAULT_FLOW_NAME

    val aliveFlowNames: List<String>
        get() {
            if (_aliveFlowNamesDirty) {
                _aliveFlowNames = namedFlows?.keys
                    ?.filter { it != DEFAULT_FLOW_NAME }
                    ?: emptyList()
                _aliveFlowNamesDirty = false
            }
            return _aliveFlowNames!!
        }

    // --- Output text (lazy, cached) ---

    val currentText: String
        get() {
            if (_outputStreamTextDirty) {
                _currentText = buildString {
                    var inTag = false
                    for (obj in outputStream) {
                        if (!inTag && obj is StringValue) {
                            append(obj.value)
                        } else if (obj is ControlCommand) {
                            when (obj.commandType) {
                                ControlCommand.CommandType.BeginTag -> inTag = true
                                ControlCommand.CommandType.EndTag -> inTag = false
                                else -> {}
                            }
                        }
                    }
                }.let { cleanOutputWhitespace(it) }
                _outputStreamTextDirty = false
            }
            return _currentText
        }

    val currentTags: List<String>
        get() {
            if (_outputStreamTagsDirty) {
                val tags = mutableListOf<String>()
                var inTag = false
                val sb = StringBuilder()

                for (obj in outputStream) {
                    when {
                        obj is ControlCommand -> {
                            when (obj.commandType) {
                                ControlCommand.CommandType.BeginTag -> {
                                    if (inTag && sb.isNotEmpty()) {
                                        tags.add(cleanOutputWhitespace(sb.toString()))
                                        sb.clear()
                                    }
                                    inTag = true
                                }
                                ControlCommand.CommandType.EndTag -> {
                                    if (sb.isNotEmpty()) {
                                        tags.add(cleanOutputWhitespace(sb.toString()))
                                        sb.clear()
                                    }
                                    inTag = false
                                }
                                else -> {}
                            }
                        }
                        inTag && obj is StringValue -> sb.append(obj.value)
                        obj is Tag -> obj.text?.takeIf { it.isNotEmpty() }?.let { tags.add(it) }
                    }
                }

                if (sb.isNotEmpty()) {
                    tags.add(cleanOutputWhitespace(sb.toString()))
                }

                _currentTags = tags
                _outputStreamTagsDirty = false
            }
            return _currentTags
        }

    // --- Error handling ---

    fun addError(message: String, isWarning: Boolean = false) {
        if (!isWarning) {
            if (currentErrors == null) currentErrors = mutableListOf()
            currentErrors!!.add(message)
        } else {
            if (currentWarnings == null) currentWarnings = mutableListOf()
            currentWarnings!!.add(message)
        }
    }

    fun resetErrors() { currentErrors = null }
    fun resetWarnings() { currentWarnings = null }

    // --- Navigation ---

    fun goToStart() {
        callStack.currentElement.currentPointer.assign(Pointer.startOf(rootContentContainer))
    }

    fun setChosenPath(path: Path, incrementingTurnIndex: Boolean) {
        currentFlow.currentChoices.clear()

        val newPointer = Pointer(resolvePointerAtPath(path))
        if (!newPointer.isNull && newPointer.index == -1) newPointer.index = 0

        currentPointer = newPointer

        if (incrementingTurnIndex) currentTurnIndex++
    }

    // --- Flow management ---

    fun switchFlowInternal(flowName: String) {
        requireNotNull(flowName) { "Must pass a non-null string to Story.SwitchFlow" }

        if (namedFlows == null) {
            namedFlows = LinkedHashMap()
            namedFlows!![DEFAULT_FLOW_NAME] = currentFlow
        }

        if (flowName == currentFlow.name) return

        var flow = namedFlows!![flowName]
        if (flow == null) {
            flow = InkFlow(flowName, rootContentContainer)
            namedFlows!![flowName] = flow
            _aliveFlowNamesDirty = true
        }

        currentFlow = flow
        variablesState.callStack = currentFlow.callStack
        outputStreamDirty()
    }

    fun switchToDefaultFlowInternal() {
        if (namedFlows == null) return
        switchFlowInternal(DEFAULT_FLOW_NAME)
    }

    fun removeFlowInternal(flowName: String) {
        require(flowName != DEFAULT_FLOW_NAME) { "Cannot destroy default flow" }

        if (currentFlow.name == flowName) {
            switchToDefaultFlowInternal()
        }

        namedFlows?.remove(flowName)
        _aliveFlowNamesDirty = true
    }

    // --- Evaluation stack ---

    fun pushEvaluationStack(obj: InkObject) {
        val listValue = obj as? ListValue
        if (listValue != null) {
            val rawList = listValue.value
            val originNames = rawList.originNames
            if (originNames != null) {
                if (rawList.origins == null) rawList.origins = mutableListOf()
                rawList.origins!!.clear()
                for (n in originNames) {
                    val def = listDefsOrigin.getListDefinition(n)
                    if (def != null && def !in rawList.origins!!) {
                        rawList.origins!!.add(def)
                    }
                }
            }
        }
        evaluationStack.add(obj)
    }

    fun popEvaluationStack(): InkObject = evaluationStack.removeAt(evaluationStack.size - 1)

    fun popEvaluationStack(numberOfObjects: Int): List<InkObject> {
        require(numberOfObjects <= evaluationStack.size) { "trying to pop too many objects" }
        val startIndex = evaluationStack.size - numberOfObjects
        val popped = ArrayList(evaluationStack.subList(startIndex, evaluationStack.size))
        evaluationStack.subList(startIndex, evaluationStack.size).clear()
        return popped
    }

    fun peekEvaluationStack(): InkObject = evaluationStack.last()

    // --- Output stream operations ---

    fun pushToOutputStream(obj: InkObject) {
        val text = obj as? StringValue
        if (text != null) {
            val listText = trySplittingHeadTailWhitespace(text)
            if (listText != null) {
                for (textObj in listText) pushToOutputStreamIndividual(textObj)
                outputStreamDirty()
                return
            }
        }
        pushToOutputStreamIndividual(obj)
    }

    private fun pushToOutputStreamIndividual(obj: InkObject) {
        val glue = obj as? Glue
        val text = obj as? StringValue
        var includeInOutput = true

        if (glue != null) {
            trimNewlinesFromOutputStream()
            includeInOutput = true
        } else if (text != null) {
            var functionTrimIndex = -1
            val currEl = callStack.currentElement
            if (currEl.type == PushPopType.Function) {
                functionTrimIndex = currEl.functionStartInOutputStream
            }

            var glueTrimIndex = -1
            for (i in outputStream.indices.reversed()) {
                val o = outputStream[i]
                if (o is Glue) { glueTrimIndex = i; break }
                if (o is ControlCommand && o.commandType == ControlCommand.CommandType.BeginString) {
                    if (i >= functionTrimIndex) functionTrimIndex = -1
                    break
                }
            }

            val trimIndex = when {
                glueTrimIndex != -1 && functionTrimIndex != -1 -> min(functionTrimIndex, glueTrimIndex)
                glueTrimIndex != -1 -> glueTrimIndex
                else -> functionTrimIndex
            }

            if (trimIndex != -1) {
                if (text.isNewline) {
                    includeInOutput = false
                } else if (text.isNonWhitespace) {
                    if (glueTrimIndex > -1) removeExistingGlue()
                    if (functionTrimIndex > -1) {
                        for (i in callStack.elements.indices.reversed()) {
                            val el = callStack.elements[i]
                            if (el.type == PushPopType.Function) {
                                el.functionStartInOutputStream = -1
                            } else break
                        }
                    }
                }
            } else if (text.isNewline) {
                if (outputStreamEndsInNewline || !outputStreamContainsContent) {
                    includeInOutput = false
                }
            }
        }

        if (includeInOutput) {
            outputStream.add(obj)
            outputStreamDirty()
        }
    }

    fun popFromOutputStream(count: Int) {
        outputStream.subList(outputStream.size - count, outputStream.size).clear()
        outputStreamDirty()
    }

    fun resetOutput(objs: List<InkObject>? = null) {
        outputStream.clear()
        if (objs != null) outputStream.addAll(objs)
        outputStreamDirty()
    }

    val outputStreamContainsContent: Boolean
        get() = outputStream.any { it is StringValue }

    val outputStreamEndsInNewline: Boolean
        get() {
            for (i in outputStream.indices.reversed()) {
                val obj = outputStream[i]
                if (obj is ControlCommand) break
                val text = obj as? StringValue
                if (text != null) {
                    return if (text.isNewline) true
                    else if (text.isNonWhitespace) false
                    else continue
                }
            }
            return false
        }

    val inStringEvaluation: Boolean
        get() = outputStream.asReversed().any {
            it is ControlCommand && it.commandType == ControlCommand.CommandType.BeginString
        }

    private fun outputStreamDirty() {
        _outputStreamTextDirty = true
        _outputStreamTagsDirty = true
    }

    private fun trimNewlinesFromOutputStream() {
        var removeWhitespaceFrom = -1
        var i = outputStream.size - 1
        while (i >= 0) {
            val obj = outputStream[i]
            if (obj is ControlCommand || (obj is StringValue && obj.isNonWhitespace)) break
            if (obj is StringValue && obj.isNewline) removeWhitespaceFrom = i
            i--
        }
        if (removeWhitespaceFrom >= 0) {
            i = removeWhitespaceFrom
            while (i < outputStream.size) {
                if (outputStream[i] is StringValue) outputStream.removeAt(i)
                else i++
            }
        }
        outputStreamDirty()
    }

    private fun removeExistingGlue() {
        for (i in outputStream.indices.reversed()) {
            val c = outputStream[i]
            if (c is Glue) outputStream.removeAt(i)
            else if (c is ControlCommand) break
        }
        outputStreamDirty()
    }

    private fun trimWhitespaceFromFunctionEnd() {
        var functionStartPoint = callStack.currentElement.functionStartInOutputStream
        if (functionStartPoint == -1) functionStartPoint = 0

        for (i in outputStream.indices.reversed()) {
            if (i < functionStartPoint) break
            val obj = outputStream[i]
            if (obj is ControlCommand) break
            val txt = obj as? StringValue ?: continue
            if (txt.isNewline || txt.isInlineWhitespace) {
                outputStream.removeAt(i)
                outputStreamDirty()
            } else break
        }
    }

    // --- Call stack operations ---

    fun popCallstack(popType: PushPopType? = null) {
        if (callStack.currentElement.type == PushPopType.Function) {
            trimWhitespaceFromFunctionEnd()
        }
        callStack.pop(popType)
    }

    fun forceEnd() {
        callStack.reset()
        currentFlow.currentChoices.clear()
        currentPointer = Pointer.Null
        previousPointer = Pointer.Null
        didSafeExit = true
    }

    // --- Function evaluation from game ---

    fun startFunctionEvaluationFromGame(funcContainer: Container, arguments: Array<Any?>?) {
        callStack.push(PushPopType.FunctionEvaluationFromGame, evaluationStack.size)
        callStack.currentElement.currentPointer.assign(Pointer.startOf(funcContainer))
        passArgumentsToEvaluationStack(arguments)
    }

    fun passArgumentsToEvaluationStack(arguments: Array<Any?>?) {
        arguments?.forEach { arg ->
            require(arg is Int || arg is Float || arg is String || arg is Boolean || arg is InkList) {
                "ink arguments must be int, float, string, bool or InkList. Was: ${arg?.let { it::class.simpleName } ?: "null"}"
            }
            pushEvaluationStack(Value.create(arg)!!)
        }
    }

    fun tryExitFunctionEvaluationFromGame(): Boolean {
        if (callStack.currentElement.type == PushPopType.FunctionEvaluationFromGame) {
            currentPointer = Pointer.Null
            didSafeExit = true
            return true
        }
        return false
    }

    fun completeFunctionEvaluationFromGame(): Any? {
        check(callStack.currentElement.type == PushPopType.FunctionEvaluationFromGame) {
            "Expected external function evaluation to be complete. Stack trace: ${callStack.callStackTrace}"
        }

        val originalEvalStackHeight = callStack.currentElement.evaluationStackHeightWhenPushed
        var returnedObj: InkObject? = null
        while (evaluationStack.size > originalEvalStackHeight) {
            val poppedObj = popEvaluationStack()
            if (returnedObj == null) returnedObj = poppedObj
        }

        callStack.pop(PushPopType.FunctionEvaluationFromGame)

        if (returnedObj != null) {
            if (returnedObj is InkVoid) return null
            val returnVal = returnedObj as? Value<*>
            if (returnVal?.valueType == ValueType.DivertTarget) {
                return returnVal.valueObject.toString()
            }
            return returnVal?.valueObject
        }
        return null
    }

    // --- Visit counts & turn indices ---

    fun visitCountForContainer(container: Container): Int {
        if (!container.visitsShouldBeCounted) {
            errorHandler?.onError(
                "Read count for target (${container.name} - on ${container.debugMetadata}) unknown.",
                false
            )
            return 0
        }

        patch?.getVisitCount(container)?.let { return it }

        val pathStr = container.path.toString()
        return _visitCounts[pathStr] ?: 0
    }

    fun incrementVisitCountForContainer(container: Container) {
        if (patch != null) {
            val currCount = visitCountForContainer(container)
            patch!!.setVisitCount(container, currCount + 1)
            return
        }

        val pathStr = container.path.toString()
        _visitCounts[pathStr] = (_visitCounts[pathStr] ?: 0) + 1
    }

    fun recordTurnIndexVisitToContainer(container: Container) {
        if (patch != null) {
            patch!!.setTurnIndex(container, currentTurnIndex)
            return
        }
        _turnIndices[container.path.toString()] = currentTurnIndex
    }

    fun turnsSinceForContainer(container: Container): Int {
        if (!container.turnIndexShouldBeCounted) {
            errorHandler?.onError(
                "TURNS_SINCE() for target (${container.name} - on ${container.debugMetadata}) unknown.",
                false
            )
        }

        patch?.getTurnIndex(container)?.let { return currentTurnIndex - it }

        val pathStr = container.path.toString()
        return _turnIndices[pathStr]?.let { currentTurnIndex - it } ?: -1
    }

    // --- Patch management ---

    fun copyAndStartPatching(forBackgroundSave: Boolean): StoryState {
        val copy = StoryState(rootContentContainer, listDefsOrigin, errorHandler)

        copy.patch = StatePatch(patch)

        // Hijack the new default flow to become a copy of our current one
        copy.currentFlow = InkFlow(currentFlow.name, rootContentContainer).apply {
            callStack = CallStack(this@StoryState.currentFlow.callStack)
            outputStream.addAll(this@StoryState.currentFlow.outputStream)
        }
        copy.outputStreamDirty()

        if (forBackgroundSave) {
            for (choice in currentFlow.currentChoices) {
                copy.currentFlow.currentChoices.add(choice.clone())
            }
        } else {
            copy.currentFlow.currentChoices.addAll(currentFlow.currentChoices)
        }

        if (namedFlows != null) {
            copy.namedFlows = LinkedHashMap()
            for ((name, flow) in namedFlows!!) {
                copy.namedFlows!![name] = flow
            }
            copy.namedFlows!![currentFlow.name] = copy.currentFlow
            copy._aliveFlowNamesDirty = true
        }

        if (hasError) {
            copy.currentErrors = ArrayList(currentErrors!!)
        }
        if (hasWarning) {
            copy.currentWarnings = ArrayList(currentWarnings!!)
        }

        // ref copy — exactly the same variables state!
        copy.variablesState = variablesState
        copy.variablesState.callStack = copy.callStack
        copy.variablesState.patch = copy.patch

        copy.evaluationStack.addAll(evaluationStack)

        if (!divertedPointer.isNull) copy.divertedPointer = Pointer(divertedPointer)

        copy.previousPointer = previousPointer

        copy._visitCounts = _visitCounts
        copy._turnIndices = _turnIndices

        copy.currentTurnIndex = currentTurnIndex
        copy.storySeed = storySeed
        copy.previousRandom = previousRandom
        copy.didSafeExit = didSafeExit

        return copy
    }

    fun restoreAfterPatch() {
        variablesState.callStack = callStack
        variablesState.patch = patch
    }

    fun applyAnyPatch() {
        val p = patch ?: return

        variablesState.applyPatch()

        for ((container, count) in p.visitCounts) {
            applyCountChanges(container, count, isVisit = true)
        }
        for ((container, index) in p.turnIndices) {
            applyCountChanges(container, index, isVisit = false)
        }

        patch = null
    }

    private fun applyCountChanges(container: Container, newCount: Int, isVisit: Boolean) {
        val counts = if (isVisit) _visitCounts else _turnIndices
        counts[container.path.toString()] = newCount
    }

    // --- Whitespace utilities (identical across C#/Java/JS) ---

    fun cleanOutputWhitespace(str: String): String = buildString(str.length) {
        var currentWhitespaceStart = -1
        var startOfLine = 0

        for (i in str.indices) {
            val c = str[i]
            val isInlineWhitespace = c == ' ' || c == '\t'

            if (isInlineWhitespace && currentWhitespaceStart == -1) currentWhitespaceStart = i
            if (!isInlineWhitespace) {
                if (c != '\n' && currentWhitespaceStart > 0 && currentWhitespaceStart != startOfLine) {
                    append(' ')
                }
                currentWhitespaceStart = -1
            }
            if (c == '\n') startOfLine = i + 1
            if (!isInlineWhitespace) append(c)
        }
    }

    fun trySplittingHeadTailWhitespace(single: StringValue): List<StringValue>? {
        val str = single.value

        var headFirstNewlineIdx = -1
        var headLastNewlineIdx = -1
        for (i in str.indices) {
            when (str[i]) {
                '\n' -> { if (headFirstNewlineIdx == -1) headFirstNewlineIdx = i; headLastNewlineIdx = i }
                ' ', '\t' -> continue
                else -> break
            }
        }

        var tailLastNewlineIdx = -1
        var tailFirstNewlineIdx = -1
        for (i in str.indices.reversed()) {
            when (str[i]) {
                '\n' -> { if (tailLastNewlineIdx == -1) tailLastNewlineIdx = i; tailFirstNewlineIdx = i }
                ' ', '\t' -> continue
                else -> break
            }
        }

        if (headFirstNewlineIdx == -1 && tailLastNewlineIdx == -1) return null

        val listTexts = mutableListOf<StringValue>()
        var innerStrStart = 0
        var innerStrEnd = str.length

        if (headFirstNewlineIdx != -1) {
            if (headFirstNewlineIdx > 0) {
                listTexts.add(StringValue(str.substring(0, headFirstNewlineIdx)))
            }
            listTexts.add(StringValue("\n"))
            innerStrStart = headLastNewlineIdx + 1
        }

        if (tailLastNewlineIdx != -1) {
            innerStrEnd = tailFirstNewlineIdx
        }

        if (innerStrEnd > innerStrStart) {
            listTexts.add(StringValue(str.substring(innerStrStart, innerStrEnd)))
        }

        if (tailLastNewlineIdx != -1 && tailFirstNewlineIdx > headLastNewlineIdx) {
            listTexts.add(StringValue("\n"))
            if (tailLastNewlineIdx < str.length - 1) {
                val numSpaces = (str.length - tailLastNewlineIdx) - 1
                listTexts.add(StringValue(str.substring(tailLastNewlineIdx + 1, tailLastNewlineIdx + 1 + numSpaces)))
            }
        }

        return listTexts
    }

    // --- Helper for path resolution (deferred to Story for full impl) ---

    private fun resolvePointerAtPath(path: Path): Pointer {
        val result = rootContentContainer.contentAtPath(path)
        val container = result.obj as? Container
        return if (container != null) Pointer(container, if (result.approximate) 0 else -1)
        else Pointer.Null
    }

    // --- JSON serialization ---

    /**
     * Exports the current state to JSON format, in order to save the game.
     * @return The save state in JSON format.
     */
    fun toJson(): String {
        val writer = SimpleJson.Writer()
        writeJson(writer)
        return writer.toString()
    }

    /**
     * Exports the current state to JSON, writing to the given Appendable (KMP stream equivalent).
     * C#: ToJson(Stream stream), Java: toJson(OutputStream stream)
     * Kotlin: Appendable is KMP-compatible (no java.io dependency).
     */
    fun toJson(appendable: Appendable) {
        appendable.append(toJson())
    }

    /**
     * Loads a previously saved state in JSON format.
     * @param json The JSON string to load.
     */
    fun loadJson(json: String) {
        val jObject = SimpleJson.textToDictionary(json)
        loadJsonObj(jObject)
    }

    /**
     * Gets the visit/read count of a particular Container at the given path.
     * For a knot or stitch, that path string will be in the form:
     * knot or knot.stitch
     *
     * @param pathString The dot-separated path string of the specific knot or stitch.
     * @return The number of times the specific knot or stitch has been encountered.
     */
    fun visitCountAtPathString(pathString: String): Int {
        if (patch != null) {
            val container = rootContentContainer.contentAtPath(Path(pathString)).container
                ?: throw StoryException("Content at path not found: $pathString")

            val patchCount = patch!!.getVisitCount(container)
            if (patchCount != null) return patchCount
        }

        return _visitCounts[pathString] ?: 0
    }

    internal fun writeJson(writer: SimpleJson.Writer) {
        writer.writeObjectStart()

        // Flows
        writer.writePropertyStart("flows")
        writer.writeObjectStart()

        if (namedFlows != null) {
            for ((name, flow) in namedFlows!!) {
                writer.writeProperty(name) { w -> flow.writeJson(w) }
            }
        } else {
            writer.writeProperty(currentFlow.name) { w -> currentFlow.writeJson(w) }
        }

        writer.writeObjectEnd()
        writer.writePropertyEnd()

        writer.writeProperty("currentFlowName", currentFlow.name)

        writer.writeProperty("variablesState") { w -> variablesState.writeJson(w) }

        writer.writeProperty("evalStack") { w ->
            JsonSerialisation.writeListRuntimeObjs(w, evaluationStack)
        }

        if (!divertedPointer.isNull) {
            writer.writeProperty("currentDivertTarget", divertedPointer.path!!.componentsString)
        }

        writer.writeProperty("visitCounts") { w ->
            JsonSerialisation.writeIntDictionary(w, _visitCounts)
        }

        writer.writeProperty("turnIndices") { w ->
            JsonSerialisation.writeIntDictionary(w, _turnIndices)
        }

        writer.writeProperty("turnIdx", currentTurnIndex)
        writer.writeProperty("storySeed", storySeed)
        writer.writeProperty("previousRandom", previousRandom)

        writer.writeProperty("inkSaveVersion", INK_SAVE_STATE_VERSION)

        // Not using this right now, but could do in future.
        writer.writeProperty("inkFormatVersion", Story.INK_VERSION_CURRENT)

        writer.writeObjectEnd()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun loadJsonObj(jObject: Map<String, Any?>) {
        val jSaveVersion = jObject["inkSaveVersion"]
            ?: throw StoryException("ink save format incorrect, can't load.")

        if ((jSaveVersion as Number).toInt() < MIN_COMPATIBLE_LOAD_VERSION) {
            throw StoryException(
                "Ink save format isn't compatible with the current version (saw '$jSaveVersion', " +
                "but minimum is $MIN_COMPATIBLE_LOAD_VERSION), so can't load."
            )
        }

        // Flows: Always exists in latest format (even if there's just one default)
        // but this dictionary doesn't exist in prev format
        val flowsObj = jObject["flows"]
        if (flowsObj != null) {
            val flowsObjDict = flowsObj as Map<String, Any?>

            // Single default flow
            if (flowsObjDict.size == 1) {
                namedFlows = null
            }
            // Multi-flow, need to create flows dict
            else if (namedFlows == null) {
                namedFlows = LinkedHashMap()
            }
            // Multi-flow, already have a flows dict
            else {
                namedFlows!!.clear()
            }

            // Load up each flow (there may only be one)
            for ((name, flowObj) in flowsObjDict) {
                val flowData = flowObj as Map<String, Any?>
                val flow = flowFromJson(name, flowData)

                if (flowsObjDict.size == 1) {
                    currentFlow = flowFromJson(name, flowData)
                } else {
                    namedFlows!![name] = flow
                }
            }

            if (namedFlows != null && namedFlows!!.size > 1) {
                val currFlowName = jObject["currentFlowName"] as String
                currentFlow = namedFlows!![currFlowName]!!
            }
        }
        // Old format: individually load up callstack, output stream, choices in current/default flow
        else {
            namedFlows = null
            currentFlow.name = DEFAULT_FLOW_NAME

            val jCallstackThreads = jObject["callstackThreads"] as Map<String, Any?>
            val (threads, threadCounter) = jArrayToThreads(jCallstackThreads)
            currentFlow.callStack.setJsonToken(threads, threadCounter, rootContentContainer)

            currentFlow.outputStream.clear()
            currentFlow.outputStream.addAll(
                JsonSerialisation.jArrayToRuntimeObjList(jObject["outputStream"] as List<Any?>)
            )

            currentFlow.currentChoices.clear()
            val jChoices = JsonSerialisation.jArrayToRuntimeObjList(jObject["currentChoices"] as List<Any?>)
            for (obj in jChoices) {
                currentFlow.currentChoices.add(obj as Choice)
            }

            val jChoiceThreadsObj = jObject["choiceThreads"]
            if (jChoiceThreadsObj != null) {
                val choiceThreads = parseChoiceThreads(jChoiceThreadsObj as Map<String, Any?>)
                currentFlow.loadFlowChoiceThreads(choiceThreads, currentFlow.callStack)
            }
        }

        outputStreamDirty()
        _aliveFlowNamesDirty = true

        variablesState.setJsonToken(jObject["variablesState"] as Map<String, Any?>)
        variablesState.callStack = currentFlow.callStack

        evaluationStack.clear()
        evaluationStack.addAll(
            JsonSerialisation.jArrayToRuntimeObjList(jObject["evalStack"] as List<Any?>)
        )

        val currentDivertTargetPath = jObject["currentDivertTarget"]
        if (currentDivertTargetPath != null) {
            val divertPath = Path(currentDivertTargetPath.toString())
            divertedPointer = pointerAtPath(divertPath)
        }

        _visitCounts = JsonSerialisation.jObjectToIntDictionary(jObject["visitCounts"] as Map<String, Any?>)
        _turnIndices = JsonSerialisation.jObjectToIntDictionary(jObject["turnIndices"] as Map<String, Any?>)

        currentTurnIndex = (jObject["turnIdx"] as Number).toInt()
        storySeed = (jObject["storySeed"] as Number).toInt()

        // Not optional, but bug in inkjs means it's actually missing in inkjs saves
        val previousRandomObj = jObject["previousRandom"]
        previousRandom = if (previousRandomObj != null) (previousRandomObj as Number).toInt() else 0

        onDidLoadState?.onLoaded()
    }

    // --- JSON deserialization helpers ---

    private fun pointerAtPath(path: Path): Pointer {
        if (path.length == 0) return Pointer.Null

        val p = Pointer()

        if (path.lastComponent!!.isIndex) {
            val pathLengthToUse = path.length - 1
            val result = SearchResult(rootContentContainer.contentAtPath(path, 0, pathLengthToUse))
            p.container = result.container
            p.index = path.lastComponent!!.index
        } else {
            val result = SearchResult(rootContentContainer.contentAtPath(path))
            p.container = result.container
            p.index = -1
        }

        return p
    }

    @Suppress("UNCHECKED_CAST")
    private fun threadFromJson(jThreadObj: Map<String, Any?>): CallStack.InkThread {
        val thread = CallStack.InkThread()
        thread.threadIndex = (jThreadObj["threadIndex"] as Number).toInt()

        val jThreadCallstack = jThreadObj["callstack"] as List<Any?>

        for (jElTok in jThreadCallstack) {
            val jElementObj = jElTok as Map<String, Any?>

            val pushPopType = PushPopType.entries[(jElementObj["type"] as Number).toInt()]

            val pointer = Pointer(Pointer.Null)

            val currentContainerPathStrToken = jElementObj["cPath"]
            if (currentContainerPathStrToken != null) {
                val currentContainerPathStr = currentContainerPathStrToken.toString()
                val threadPointerResult = rootContentContainer.contentAtPath(Path(currentContainerPathStr))
                pointer.container = threadPointerResult.container
                pointer.index = (jElementObj["idx"] as Number).toInt()

                if (threadPointerResult.obj == null) {
                    throw StoryException(
                        "When loading state, internal story location couldn't be found: " +
                        "$currentContainerPathStr. Has the story changed since this save data was created?"
                    )
                } else if (threadPointerResult.approximate) {
                    errorHandler?.onError(
                        "When loading state, exact internal story location couldn't be found: " +
                        "'$currentContainerPathStr', so it was approximated to " +
                        "'${pointer.container?.path}'. Has the story changed since this save data was created?",
                        true
                    )
                }
            }

            val inExpressionEvaluation = jElementObj["exp"] as Boolean
            val el = CallStack.Element(pushPopType, pointer, inExpressionEvaluation)

            val temps = jElementObj["temp"]
            if (temps != null) {
                el.temporaryVariables = JsonSerialisation.jObjectToDictionaryRuntimeObjs(
                    temps as Map<String, Any?>
                )
            } else {
                el.temporaryVariables = LinkedHashMap()
            }

            thread.callstack.add(el)
        }

        val prevContentObjPath = jThreadObj["previousContentObject"]
        if (prevContentObjPath != null) {
            val prevPath = Path(prevContentObjPath.toString())
            thread.previousPointer = pointerAtPath(prevPath)
        }

        return thread
    }

    @Suppress("UNCHECKED_CAST")
    private fun jArrayToThreads(jObj: Map<String, Any?>): Pair<List<CallStack.InkThread>, Int> {
        val jThreads = jObj["threads"] as List<Any?>
        val threads = mutableListOf<CallStack.InkThread>()

        for (jThreadTok in jThreads) {
            val jThreadObj = jThreadTok as Map<String, Any?>
            threads.add(threadFromJson(jThreadObj))
        }

        val threadCounter = (jObj["threadCounter"] as Number).toInt()
        return Pair(threads, threadCounter)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseChoiceThreads(jChoiceThreads: Map<String, Any?>): Map<String, CallStack.InkThread> {
        val result = LinkedHashMap<String, CallStack.InkThread>()
        for ((key, value) in jChoiceThreads) {
            val jThreadObj = value as Map<String, Any?>
            result[key] = threadFromJson(jThreadObj)
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun flowFromJson(name: String, jObject: Map<String, Any?>): InkFlow {
        val flow = InkFlow(name, rootContentContainer)

        val jCallstackObj = jObject["callstack"] as Map<String, Any?>
        val (threads, threadCounter) = jArrayToThreads(jCallstackObj)
        flow.callStack.setJsonToken(threads, threadCounter, rootContentContainer)

        flow.outputStream.clear()
        flow.outputStream.addAll(
            JsonSerialisation.jArrayToRuntimeObjList(jObject["outputStream"] as List<Any?>)
        )

        flow.currentChoices.clear()
        val jChoices = JsonSerialisation.jArrayToRuntimeObjList(jObject["currentChoices"] as List<Any?>)
        for (obj in jChoices) {
            flow.currentChoices.add(obj as Choice)
        }

        // choiceThreads is optional
        val jChoiceThreadsObj = jObject["choiceThreads"]
        if (jChoiceThreadsObj != null) {
            val choiceThreads = parseChoiceThreads(jChoiceThreadsObj as Map<String, Any?>)
            flow.loadFlowChoiceThreads(choiceThreads, flow.callStack)
        }

        return flow
    }
}
