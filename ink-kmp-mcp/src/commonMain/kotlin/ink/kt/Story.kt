package ink.kt

import kotlin.random.Random

/**
 * A Story is the core class that represents a complete Ink narrative,
 * and manages the evaluation and state of it.
 *
 * Three-way comparison:
 * - C#: Story : Object — 3000 lines, delegates, Stream overloads
 * - Java: Story implements VariablesState.VariableChanged — 2928 lines
 * - JS: Story — 2000 lines, untyped callbacks
 *
 * Kotlin improvements over all three:
 * - **fun interface** ExternalFunction — vararg SAM, replaces 4 Java abstract classes
 * - **fun interface** VariableObserver — SAM lambda binding
 * - **when expressions** for ControlCommand dispatch (vs switch/case)
 * - **Kotlin properties** instead of getter/setter pairs
 * - **data class Pointer** — value semantics, copy()
 * - Implements VariablesState.VariableChanged via fun interface
 * - StoryState takes rootContentContainer + listDefsOrigin (not Story reference)
 * - Stopwatch from kotlin.time.TimeSource.Monotonic (not System.nanoTime)
 */
class Story : VariablesState.VariableChanged {

    // ── External function binding ──────────────────────────────────────

    /**
     * General purpose delegate for bound EXTERNAL function definitions.
     * Replaces Java's ExternalFunction<R>, ExternalFunction0/1/2/3 hierarchy.
     * Kotlin fun interface enables SAM conversion: `bindExternalFunction("f") { args -> ... }`
     */
    fun interface ExternalFunction {
        @Throws(Exception::class)
        fun call(args: Array<out Any?>): Any?
    }

    /**
     * Delegate for variable observation — see observeVariable.
     */
    fun interface VariableObserver {
        fun call(variableName: String, newValue: Any?)
    }

    private class ExternalFunctionDef(
        val function: ExternalFunction,
        val lookaheadSafe: Boolean
    )

    // ── Version constants ──────────────────────────────────────────────

    companion object {
        /** The current version of the ink story file format. */
        const val INK_VERSION_CURRENT = 21

        /** The minimum legacy version of ink that can be loaded. */
        const val INK_VERSION_MINIMUM_COMPATIBLE = 18
    }

    // ── Fields ─────────────────────────────────────────────────────────

    private var mainContentContainer: Container?
    private var listDefinitions: ListDefinitionsOrigin? = null
    private var allowExternalFunctionFallbacks: Boolean = false
    private val externals: HashMap<String, ExternalFunctionDef> = HashMap()
    private var hasValidatedExternals: Boolean = false

    var state: StoryState
        private set

    private var temporaryEvaluationContainer: Container? = null
    private var variableObservers: HashMap<String, MutableList<VariableObserver>>? = null
    private val prevContainers: MutableList<Container> = mutableListOf()
    private var profiler: Profiler? = null
    private var asyncContinueActive: Boolean = false
    private var stateSnapshotAtLastNewline: StoryState? = null
    private var recursiveContinueCount: Int = 0
    private var asyncSaving: Boolean = false
    private var sawLookaheadUnsafeFunctionAfterNewline: Boolean = false

    /** Error handler callback. Assign to handle errors/warnings during evaluation. */
    var onError: ErrorHandler? = null

    // ── Constructors ───────────────────────────────────────────────────

    /**
     * Internal constructor for compiler use. Call resetState() before use.
     */
    constructor(contentContainer: Container, lists: List<ListDefinition>? = null) {
        mainContentContainer = contentContainer
        if (lists != null) {
            listDefinitions = ListDefinitionsOrigin(lists)
        }
        // Initialize state with a temporary value; resetState() will replace it
        state = StoryState(contentContainer, listDefinitions ?: ListDefinitionsOrigin(emptyList()))
    }

    /**
     * Construct a Story using a JSON String compiled through inklecate.
     */
    constructor(jsonString: String) : this(null as Container?) {
        val rootObject = SimpleJson.textToDictionary(jsonString)

        val versionObj = rootObject["inkVersion"]
            ?: throw Exception("ink version number not found. Are you sure it's a valid .ink.json file?")

        val formatFromFile = when (versionObj) {
            is String -> versionObj.toInt()
            is Number -> versionObj.toInt()
            else -> throw Exception("Unexpected ink version type")
        }

        when {
            formatFromFile > INK_VERSION_CURRENT ->
                throw Exception("Version of ink used to build story was newer than the current version of the engine")
            formatFromFile < INK_VERSION_MINIMUM_COMPATIBLE ->
                throw Exception("Version of ink used to build story is too old to be loaded by this version of the engine")
            formatFromFile != INK_VERSION_CURRENT ->
                println("WARNING: Version of ink used to build story doesn't match current version of engine. Non-critical, but recommend synchronising.")
        }

        val rootToken = rootObject["root"]
            ?: throw Exception("Root node for ink not found. Are you sure it's a valid .ink.json file?")

        val listDefsObj = rootObject["listDefs"]
        if (listDefsObj != null) {
            listDefinitions = JsonSerialisation.jTokenToListDefinitions(listDefsObj)
        }

        val runtimeObject = JsonSerialisation.jTokenToRuntimeObject(rootToken)
        mainContentContainer = runtimeObject as? Container

        resetState()
    }

    private constructor(container: Container?) {
        mainContentContainer = container
        state = StoryState(
            container ?: Container(),
            listDefinitions ?: ListDefinitionsOrigin(emptyList())
        )
    }

    // ── Error handling ─────────────────────────────────────────────────

    internal fun addError(message: String, isWarning: Boolean = false, useEndLineNumber: Boolean = false) {
        val dm = currentDebugMetadata()
        val errorTypeStr = if (isWarning) "WARNING" else "ERROR"

        val formattedMessage = when {
            dm != null -> {
                val lineNum = if (useEndLineNumber) dm.endLineNumber else dm.startLineNumber
                "RUNTIME $errorTypeStr: '${dm.fileName}' line $lineNum: $message"
            }
            !state.currentPointer.isNull ->
                "RUNTIME $errorTypeStr: (${state.currentPointer.path}): $message"
            else ->
                "RUNTIME $errorTypeStr: $message"
        }

        state.addError(formattedMessage, isWarning)

        // In a broken state don't need to know about any other errors.
        if (!isWarning) state.forceEnd()
    }

    internal fun warning(message: String) = addError(message, isWarning = true)

    // ── Profiling ──────────────────────────────────────────────────────

    fun startProfiling(): Profiler {
        ifAsyncWeCant("start profiling")
        profiler = Profiler()
        return profiler!!
    }

    fun endProfiling() {
        profiler = null
    }

    // ── Assert ─────────────────────────────────────────────────────────

    internal fun inkAssert(condition: Boolean, message: String? = null, vararg formatParams: Any?) {
        if (!condition) {
            var msg = message ?: "Story assert"
            if (formatParams.isNotEmpty()) {
                msg = msg.format(*formatParams)
            }
            throw Exception("$msg ${currentDebugMetadata()}")
        }
    }

    // ── External function binding ──────────────────────────────────────

    fun bindExternalFunction(funcName: String, func: ExternalFunction, lookaheadSafe: Boolean = true) {
        ifAsyncWeCant("bind an external function")
        inkAssert(!externals.containsKey(funcName), "Function '$funcName' has already been bound.")
        externals[funcName] = ExternalFunctionDef(func, lookaheadSafe)
    }

    fun unbindExternalFunction(funcName: String) {
        ifAsyncWeCant("unbind an external a function")
        inkAssert(externals.containsKey(funcName), "Function '$funcName' has not been bound.")
        externals.remove(funcName)
    }

    fun getExternalFunction(functionName: String): ExternalFunction? =
        externals[functionName]?.function

    internal fun callExternalFunction(funcName: String, numberOfArguments: Int) {
        val funcDef = externals[funcName]
        var fallbackFunctionContainer: Container? = null

        if (funcDef != null && funcDef.lookaheadSafe && state.inStringEvaluation) {
            error("External function $funcName could not be called because 1) it wasn't marked as lookaheadSafe when BindExternalFunction was called and 2) the story is in the middle of string generation, either because choice text is being generated, or because you have ink like \"hello {func()}\". You can work around this by generating the result of your function into a temporary variable before the string or choice gets generated: ~ temp x = $funcName()")
            return
        }

        // Should this function break glue? Abort run if we've already seen a newline.
        if (funcDef != null && !funcDef.lookaheadSafe && stateSnapshotAtLastNewline != null) {
            sawLookaheadUnsafeFunctionAfterNewline = true
            return
        }

        // Try to use fallback function?
        if (funcDef == null) {
            if (allowExternalFunctionFallbacks) {
                fallbackFunctionContainer = knotContainerWithName(funcName)
                inkAssert(fallbackFunctionContainer != null,
                    "Trying to call EXTERNAL function '$funcName' which has not been bound, and fallback ink function could not be found.")

                // Divert direct into fallback function and we're done
                state.callStack.push(PushPopType.Function, 0, state.outputStream.size)
                state.divertedPointer = Pointer.startOf(fallbackFunctionContainer!!)
                return
            } else {
                inkAssert(false,
                    "Trying to call EXTERNAL function '$funcName' which has not been bound (and ink fallbacks disabled).")
            }
        }

        // Pop arguments
        val arguments = mutableListOf<Any?>()
        for (i in 0 until numberOfArguments) {
            val poppedObj = state.popEvaluationStack() as Value<*>
            arguments.add(poppedObj.valueObject)
        }
        arguments.reverse()

        // Run the function!
        val funcResult = funcDef!!.function.call(arguments.toTypedArray())

        // Convert return value to ink runtime type
        val returnObj: InkObject = if (funcResult != null) {
            val created = Value.create(funcResult)
            inkAssert(created != null,
                "Could not create ink value from returned Object of type ${funcResult::class.simpleName}")
            created!!
        } else {
            Void()
        }

        state.pushEvaluationStack(returnObj)
    }

    // ── Tags ───────────────────────────────────────────────────────────

    /** Get global tags defined at the very top of the story. */
    fun getGlobalTags(): List<String>? = tagsAtStartOfFlowContainerWithPathString("")

    /** Get tags associated with a knot or knot.stitch. */
    fun tagsForContentAtPath(path: String): List<String>? =
        tagsAtStartOfFlowContainerWithPathString(path)

    internal fun tagsAtStartOfFlowContainerWithPathString(pathString: String): List<String>? {
        val path = Path(pathString)
        var flowContainer = contentAtPath(path).container!!

        while (true) {
            val firstContent = flowContainer.content[0]
            if (firstContent is Container) flowContainer = firstContent
            else break
        }

        var inTag = false
        var tags: MutableList<String>? = null
        for (c in flowContainer.content) {
            if (c is ControlCommand) {
                if (c.commandType == ControlCommand.CommandType.BeginTag) {
                    inTag = true
                } else if (c.commandType == ControlCommand.CommandType.EndTag) {
                    inTag = false
                }
            } else if (inTag) {
                if (c is StringValue) {
                    if (tags == null) tags = mutableListOf()
                    tags.add(c.value)
                } else {
                    error("Tag contained non-text content. Only plain text is allowed when using globalTags or TagsAtContentPath. If you want to evaluate dynamic content, you need to use story.Continue().")
                }
            } else {
                break
            }
        }

        return tags
    }

    // ── Continue / async continue ──────────────────────────────────────

    /** Whether more content is available (are we mid story rather than at a choice point or end). */
    fun canContinue(): Boolean = state.canContinue

    /**
     * Continue the story for one line of content.
     * @return The line of text content.
     */
    fun continueStory(): String {
        continueAsync(0f)
        return currentText
    }

    /** If ContinueAsync was called, returns false if ink evaluation isn't yet finished. */
    fun asyncContinueComplete(): Boolean = !asyncContinueActive

    /**
     * Asynchronous version of Continue that only partially evaluates the ink,
     * with a budget of a certain time limit.
     */
    fun continueAsync(millisecsLimitAsync: Float) {
        if (!hasValidatedExternals) validateExternalBindings()
        continueInternal(millisecsLimitAsync)
    }

    /**
     * Continue the story until the next choice point or until it runs out of content.
     */
    fun continueMaximally(): String {
        ifAsyncWeCant("ContinueMaximally")
        return buildString {
            while (canContinue()) {
                append(continueStory())
            }
        }
    }

    private fun continueInternal(millisecsLimitAsync: Float = 0f) {
        profiler?.preContinue()

        val isAsyncTimeLimited = millisecsLimitAsync > 0

        recursiveContinueCount++

        // Doing either:
        // - full run through non-async (so not active and don't want to be)
        // - Starting async run-through
        if (!asyncContinueActive) {
            asyncContinueActive = isAsyncTimeLimited
            if (!canContinue()) {
                throw Exception("Can't continue - should check canContinue before calling Continue")
            }

            state.didSafeExit = false
            state.resetOutput()

            if (recursiveContinueCount == 1) state.variablesState.startVariableObservation()
        } else if (asyncContinueActive && !isAsyncTimeLimited) {
            asyncContinueActive = false
        }

        // Start timing
        val durationStopwatch = Stopwatch()
        durationStopwatch.start()

        var outputStreamEndsInNewline = false
        sawLookaheadUnsafeFunctionAfterNewline = false
        do {
            try {
                outputStreamEndsInNewline = continueSingleStep()
            } catch (e: StoryException) {
                addError(e.message ?: "", false, e.useEndLineNumber)
                break
            }

            if (outputStreamEndsInNewline) break

            // Run out of async time?
            if (asyncContinueActive && durationStopwatch.elapsedMilliseconds > millisecsLimitAsync) {
                break
            }
        } while (canContinue())

        durationStopwatch.stop()

        var changedVariablesToObserve: HashMap<String, InkObject>? = null

        // Successfully finished evaluation in time (or in error)
        if (outputStreamEndsInNewline || !canContinue()) {
            // Need to rewind, due to evaluating further than we should?
            if (stateSnapshotAtLastNewline != null) {
                restoreStateSnapshot()
            }

            // Finished a section of content / reached a choice point?
            if (!canContinue()) {
                if (state.callStack.canPopThread)
                    addError("Thread available to pop, threads should always be flat by the end of evaluation?")

                if (state.generatedChoices.isEmpty() && !state.didSafeExit && temporaryEvaluationContainer == null) {
                    if (state.callStack.canPop(PushPopType.Tunnel))
                        addError("unexpectedly reached end of content. Do you need a '->->' to return from a tunnel?")
                    else if (state.callStack.canPop(PushPopType.Function))
                        addError("unexpectedly reached end of content. Do you need a '~ return'?")
                    else if (!state.callStack.canPop)
                        addError("ran out of content. Do you need a '-> DONE' or '-> END'?")
                    else addError("unexpectedly reached end of content for unknown reason. Please debug compiler!")
                }
            }
            state.didSafeExit = false
            sawLookaheadUnsafeFunctionAfterNewline = false

            if (recursiveContinueCount == 1)
                changedVariablesToObserve = state.variablesState.completeVariableObservation()
            asyncContinueActive = false
        }

        recursiveContinueCount--

        profiler?.postContinue()

        // Report errors/warnings
        if (state.hasError || state.hasWarning) {
            val handler = onError
            if (handler != null) {
                state.currentErrors?.forEach { handler.error(it, ErrorType.Error) }
                state.currentWarnings?.forEach { handler.error(it, ErrorType.Warning) }
                resetErrors()
            } else {
                val sb = StringBuilder("Ink had ")
                if (state.hasError) {
                    val errCount = state.currentErrors!!.size
                    sb.append(errCount)
                    sb.append(if (errCount == 1) " error" else " errors")
                    if (state.hasWarning) sb.append(" and ")
                }
                if (state.hasWarning) {
                    val warnCount = state.currentWarnings!!.size
                    sb.append(warnCount)
                    sb.append(if (warnCount == 1) " warning" else " warnings")
                }
                sb.append(". It is strongly suggested that you assign an error handler to story.onError. The first issue was: ")
                sb.append(if (state.hasError) state.currentErrors!![0] else state.currentWarnings!![0])
                throw StoryException(sb.toString())
            }
        }

        // Send out variable observation events at the last second
        if (changedVariablesToObserve != null && changedVariablesToObserve.isNotEmpty()) {
            state.variablesState.notifyObservers(changedVariablesToObserve)
        }
    }

    private fun continueSingleStep(): Boolean {
        profiler?.preStep()

        step()

        profiler?.postStep()

        // Run out of content and we have a default invisible choice that we can follow?
        if (!canContinue() && !state.callStack.elementIsEvaluateFromGame) {
            tryFollowDefaultInvisibleChoice()
        }

        profiler?.preSnapshot()

        // Don't save/rewind during string evaluation
        if (!state.inStringEvaluation) {
            if (stateSnapshotAtLastNewline != null) {
                val change = calculateNewlineOutputStateChange(
                    stateSnapshotAtLastNewline!!.currentText, state.currentText,
                    stateSnapshotAtLastNewline!!.currentTags.size, state.currentTags.size
                )

                if (change == OutputStateChange.ExtendedBeyondNewline || sawLookaheadUnsafeFunctionAfterNewline) {
                    restoreStateSnapshot()
                    return true
                } else if (change == OutputStateChange.NewlineRemoved) {
                    stateSnapshotAtLastNewline = null
                    discardSnapshot()
                }
            }

            if (state.outputStreamEndsInNewline) {
                if (canContinue()) {
                    if (stateSnapshotAtLastNewline == null) stateSnapshot()
                } else {
                    discardSnapshot()
                }
            }
        }

        profiler?.postSnapshot()

        return false
    }

    // ── Debug metadata ─────────────────────────────────────────────────

    internal fun currentDebugMetadata(): DebugMetadata? {
        val pointer = Pointer(state.currentPointer)
        if (!pointer.isNull) {
            pointer.resolve()?.debugMetadata?.let { return it }
        }

        // Move up callstack
        for (i in state.callStack.elements.indices.reversed()) {
            val elemPointer = state.callStack.elements[i].currentPointer
            if (!elemPointer.isNull) {
                elemPointer.resolve()?.debugMetadata?.let { return it }
            }
        }

        // Last resort: output stream
        for (i in state.outputStream.indices.reversed()) {
            state.outputStream[i].debugMetadata?.let { return it }
        }

        return null
    }

    internal fun currentLineNumber(): Int = currentDebugMetadata()?.startLineNumber ?: 0

    // Throw an exception that gets caught and causes addError to be called
    internal fun error(message: String, useEndLineNumber: Boolean = false) {
        val e = StoryException(message)
        e.useEndLineNumber = useEndLineNumber
        throw e
    }

    // ── Expression evaluation ──────────────────────────────────────────

    fun evaluateExpression(exprContainer: Container): InkObject? {
        val startCallStackHeight = state.callStack.elements.size

        state.callStack.push(PushPopType.Tunnel)

        temporaryEvaluationContainer = exprContainer

        state.goToStart()

        val evalStackHeight = state.evaluationStack.size

        continueStory()

        temporaryEvaluationContainer = null

        if (state.callStack.elements.size > startCallStackHeight) {
            state.popCallstack()
        }

        return if (state.evaluationStack.size > evalStackHeight) {
            state.popEvaluationStack()
        } else null
    }

    // ── Current state accessors ────────────────────────────────────────

    /** The list of Choice objects available at the current point. */
    val currentChoices: List<Choice>
        get() {
            val choices = mutableListOf<Choice>()
            for (c in state.currentChoices) {
                if (!c.isInvisibleDefault) {
                    c.index = choices.size
                    choices.add(c)
                }
            }
            return choices
        }

    /** Gets tags seen during the latest Continue() call. */
    val currentTags: List<String> get() {
        ifAsyncWeCant("call currentTags since it's a work in progress")
        return state.currentTags
    }

    /** Any warnings generated during evaluation. */
    val currentWarnings: List<String>? get() = state.currentWarnings

    /** Any errors generated during evaluation. */
    val currentErrors: List<String>? get() = state.currentErrors

    /** The latest line of text generated from a Continue() call. */
    val currentText: String get() {
        ifAsyncWeCant("call currentText since it's a work in progress")
        return state.currentText
    }

    /** The VariablesState containing all global variables. */
    val variablesState: VariablesState get() = state.variablesState

    val listDefinitionsOrigin: ListDefinitionsOrigin? get() = listDefinitions

    val hasError: Boolean get() = state.hasError
    val hasWarning: Boolean get() = state.hasWarning

    /** The current flow name if using multi-flow functionality. */
    val currentFlowName: String get() = state.currentFlowName

    /** Is the default flow currently active? */
    fun currentFlowIsDefaultFlow(): Boolean = state.currentFlowIsDefaultFlow

    /** Names of currently alive flows (not including the default flow). */
    val aliveFlowNames: List<String> get() = state.aliveFlowNames

    // ── Choice / path navigation ───────────────────────────────────────

    /** Chooses the Choice from currentChoices with the given index. */
    fun chooseChoiceIndex(choiceIdx: Int) {
        val choices = currentChoices
        inkAssert(choiceIdx in choices.indices, "choice out of range")

        val choiceToChoose = choices[choiceIdx]
        state.callStack.currentThread = choiceToChoose.threadAtGeneration!!

        choosePath(choiceToChoose.targetPath!!)
    }

    internal fun choosePath(p: Path, incrementingTurnIndex: Boolean = true) {
        state.setChosenPath(p, incrementingTurnIndex)
        visitChangedContainersDueToDivert()
    }

    fun choosePathString(path: String, resetCallstack: Boolean = true, arguments: Array<Any?>? = null) {
        ifAsyncWeCant("call ChoosePathString right now")

        if (resetCallstack) {
            resetCallstack()
        } else {
            if (state.callStack.currentElement.type == PushPopType.Function) {
                var funcDetail = ""
                val container = state.callStack.currentElement.currentPointer.container
                if (container != null) {
                    funcDetail = "(${container.path}) "
                }
                throw Exception("Story was running a function ${funcDetail}when you called ChoosePathString($path) - this is almost certainly not not what you want! Full stack trace: \n${state.callStack.callStackTrace}")
            }
        }

        state.passArgumentsToEvaluationStack(arguments)
        choosePath(Path(path))
    }

    // ── Flow management ────────────────────────────────────────────────

    fun switchFlow(flowName: String) {
        ifAsyncWeCant("switch flow")
        if (asyncSaving)
            throw Exception("Story is already in background saving mode, can't switch flow to $flowName")
        state.switchFlowInternal(flowName)
    }

    fun removeFlow(flowName: String) = state.removeFlowInternal(flowName)

    fun switchToDefaultFlow() = state.switchToDefaultFlowInternal()

    // ── Content access ─────────────────────────────────────────────────

    fun contentAtPath(path: Path): SearchResult =
        mainContentContainer!!.contentAtPath(path)

    internal fun knotContainerWithName(name: String): Container? {
        val namedContainer = mainContentContainer?.namedContent?.get(name)
        return namedContainer as? Container
    }

    val mainContent: Container
        get() = temporaryEvaluationContainer ?: mainContentContainer!!

    fun buildStringOfHierarchy(): String {
        val sb = StringBuilder()
        mainContent.buildStringOfHierarchy(sb, 0, state.currentPointer.resolve())
        return sb.toString()
    }

    // ── Increment content pointer ──────────────────────────────────────

    private fun incrementContentPointer(): Boolean {
        var successfulIncrement = true

        var pointer = Pointer(state.callStack.currentElement.currentPointer)
        pointer.index++

        while (pointer.index >= pointer.container!!.content.size) {
            successfulIncrement = false

            val nextAncestor = pointer.container?.parent as? Container ?: break

            val indexInAncestor = nextAncestor.content.indexOf(pointer.container!!)
            if (indexInAncestor == -1) break

            pointer = Pointer(nextAncestor, indexInAncestor)
            pointer.index++

            successfulIncrement = true
        }

        if (!successfulIncrement) pointer.assign(Pointer.Null)

        state.callStack.currentElement.currentPointer.assign(pointer)

        return successfulIncrement
    }

    // ── Truthiness ─────────────────────────────────────────────────────

    internal fun isTruthy(obj: InkObject): Boolean {
        if (obj is Value<*>) {
            if (obj is DivertTargetValue) {
                error("Shouldn't use a divert target (to ${obj.targetPath}) as a conditional value. Did you intend a function call 'likeThis()' or a read count check 'likeThis'? (no arrows)")
                return false
            }
            return obj.isTruthy
        }
        return false
    }

    // ── Variable observation ───────────────────────────────────────────

    fun observeVariable(variableName: String, observer: VariableObserver) {
        ifAsyncWeCant("observe a new variable")

        if (variableObservers == null) variableObservers = HashMap()

        if (!state.variablesState.globalVariableExistsWithName(variableName))
            throw Exception("Cannot observe variable '$variableName' because it wasn't declared in the ink story.")

        val observers = variableObservers!!
        if (observers.containsKey(variableName)) {
            observers[variableName]!!.add(observer)
        } else {
            observers[variableName] = mutableListOf(observer)
        }
    }

    fun observeVariables(variableNames: List<String>, observer: VariableObserver) {
        for (varName in variableNames) {
            observeVariable(varName, observer)
        }
    }

    fun removeVariableObserver(observer: VariableObserver, specificVariableName: String? = null) {
        ifAsyncWeCant("remove a variable observer")

        val observers = variableObservers ?: return

        if (specificVariableName != null) {
            observers[specificVariableName]?.let { list ->
                list.remove(observer)
                if (list.isEmpty()) observers.remove(specificVariableName)
            }
        } else {
            val keysToRemove = mutableListOf<String>()
            for ((key, list) in observers) {
                list.remove(observer)
                if (list.isEmpty()) keysToRemove.add(key)
            }
            keysToRemove.forEach { observers.remove(it) }
        }
    }

    override fun onVariableChanged(variableName: String, newValue: InkObject) {
        val observers = variableObservers ?: return
        val list = observers[variableName] ?: return

        if (newValue !is Value<*>) {
            throw Exception("Tried to get the value of a variable that isn't a standard type")
        }

        for (o in list) {
            o.call(variableName, newValue.valueObject)
        }
    }

    // ── ifAsyncWeCant ──────────────────────────────────────────────────

    private fun ifAsyncWeCant(activityStr: String) {
        if (asyncContinueActive)
            throw Exception("Can't $activityStr. Story is in the middle of a ContinueAsync(). Make more ContinueAsync() calls or a single Continue() call beforehand.")
    }

    // ── nextContent ────────────────────────────────────────────────────

    private fun nextContent() {
        state.previousPointer = state.currentPointer

        // Divert step?
        if (!state.divertedPointer.isNull) {
            state.currentPointer = state.divertedPointer
            state.divertedPointer = Pointer.Null

            visitChangedContainersDueToDivert()

            if (!state.currentPointer.isNull) return
        }

        val successfulPointerIncrement = incrementContentPointer()

        if (!successfulPointerIncrement) {
            var didPop = false

            if (state.callStack.canPop(PushPopType.Function)) {
                state.popCallstack(PushPopType.Function)

                if (state.inExpressionEvaluation) {
                    state.pushEvaluationStack(Void())
                }

                didPop = true
            } else if (state.callStack.canPopThread) {
                state.callStack.popThread()
                didPop = true
            } else {
                state.tryExitFunctionEvaluationFromGame()
            }

            if (didPop && !state.currentPointer.isNull) {
                nextContent()
            }
        }
    }

    // ── Shuffle index ──────────────────────────────────────────────────

    private fun nextSequenceShuffleIndex(): Int {
        val popEvalStack = state.popEvaluationStack()
        val numElementsIntVal = popEvalStack as? IntValue

        if (numElementsIntVal == null) {
            error("expected number of elements in sequence for shuffle index")
            return 0
        }

        val seqContainer = state.currentPointer.container!!

        val numElements = numElementsIntVal.value
        val seqCountVal = state.popEvaluationStack() as IntValue
        val seqCount = seqCountVal.value
        val loopIndex = seqCount / numElements
        val iterationIndex = seqCount % numElements

        val seqPathStr = seqContainer.path.toString()
        var sequenceHash = 0
        for (c in seqPathStr) {
            sequenceHash += c.code
        }

        val randomSeed = sequenceHash + loopIndex + state.storySeed

        val random = Random(randomSeed)

        val unpickedIndices = MutableList(numElements) { it }

        for (i in 0..iterationIndex) {
            val chosen = (random.nextInt(Int.MAX_VALUE)) % unpickedIndices.size
            val chosenIndex = unpickedIndices[chosen]
            unpickedIndices.removeAt(chosen)

            if (i == iterationIndex) {
                return chosenIndex
            }
        }

        throw Exception("Should never reach here")
    }

    // ── performLogicAndFlowControl ─────────────────────────────────────

    private fun performLogicAndFlowControl(contentObj: InkObject?): Boolean {
        if (contentObj == null) return false

        // Divert
        if (contentObj is Divert) {
            val currentDivert = contentObj

            if (currentDivert.isConditional) {
                val conditionValue = state.popEvaluationStack()
                if (!isTruthy(conditionValue)) return true
            }

            if (currentDivert.hasVariableTarget) {
                val varName = currentDivert.variableDivertName!!
                val varContents = state.variablesState.getVariableWithName(varName)

                if (varContents == null) {
                    error("Tried to divert using a target from a variable that could not be found ($varName)")
                } else if (varContents !is DivertTargetValue) {
                    val intContent = varContents as? IntValue
                    val errorMessage = "Tried to divert to a target from a variable, but the variable ($varName) didn't contain a divert target, it " +
                        if (intContent != null && intContent.value == 0) "was empty/null (the value 0)."
                        else "contained '$varContents'."
                    error(errorMessage)
                }

                val target = varContents as DivertTargetValue
                state.divertedPointer = pointerAtPath(target.targetPath!!)
            } else if (currentDivert.isExternal) {
                callExternalFunction(currentDivert.targetPathString!!, currentDivert.externalArgs)
                return true
            } else {
                state.divertedPointer = currentDivert.targetPointer
            }

            if (currentDivert.pushesToStack) {
                state.callStack.push(currentDivert.stackPushType, 0, state.outputStream.size)
            }

            if (state.divertedPointer.isNull && !currentDivert.isExternal) {
                if (currentDivert.debugMetadata?.sourceName != null) {
                    error("Divert target doesn't exist: ${currentDivert.debugMetadata!!.sourceName}")
                } else {
                    error("Divert resolution failed: $currentDivert")
                }
            }

            return true
        }

        // ControlCommand
        else if (contentObj is ControlCommand) {
            val evalCommand = contentObj

            when (evalCommand.commandType) {
                ControlCommand.CommandType.EvalStart -> {
                    inkAssert(!state.inExpressionEvaluation, "Already in expression evaluation?")
                    state.inExpressionEvaluation = true
                }

                ControlCommand.CommandType.EvalEnd -> {
                    inkAssert(state.inExpressionEvaluation, "Not in expression evaluation mode")
                    state.inExpressionEvaluation = false
                }

                ControlCommand.CommandType.EvalOutput -> {
                    if (state.evaluationStack.isNotEmpty()) {
                        val output = state.popEvaluationStack()
                        if (output !is Void) {
                            state.pushToOutputStream(StringValue(output.toString()))
                        }
                    }
                }

                ControlCommand.CommandType.NoOp -> {}

                ControlCommand.CommandType.Duplicate ->
                    state.pushEvaluationStack(state.peekEvaluationStack())

                ControlCommand.CommandType.PopEvaluatedValue ->
                    state.popEvaluationStack()

                ControlCommand.CommandType.PopFunction, ControlCommand.CommandType.PopTunnel -> {
                    val popType = if (evalCommand.commandType == ControlCommand.CommandType.PopFunction)
                        PushPopType.Function else PushPopType.Tunnel

                    var overrideTunnelReturnTarget: DivertTargetValue? = null
                    if (popType == PushPopType.Tunnel) {
                        val popped = state.popEvaluationStack()
                        overrideTunnelReturnTarget = popped as? DivertTargetValue
                        if (overrideTunnelReturnTarget == null) {
                            inkAssert(popped is Void, "Expected void if ->-> doesn't override target")
                        }
                    }

                    if (state.tryExitFunctionEvaluationFromGame()) {
                        // break
                    } else if (state.callStack.currentElement.type != popType || !state.callStack.canPop) {
                        val names = mapOf(
                            PushPopType.Function to "function return statement (~ return)",
                            PushPopType.Tunnel to "tunnel onwards statement (->->)"
                        )
                        val expected = if (!state.callStack.canPop) "end of flow (-> END or choice)"
                                       else names[state.callStack.currentElement.type] ?: ""
                        error("Found ${names[popType]}, when expected $expected")
                    } else {
                        state.popCallstack()
                        if (overrideTunnelReturnTarget != null)
                            state.divertedPointer = pointerAtPath(overrideTunnelReturnTarget.targetPath!!)
                    }
                }

                ControlCommand.CommandType.BeginString -> {
                    state.pushToOutputStream(evalCommand)
                    inkAssert(state.inExpressionEvaluation, "Expected to be in an expression when evaluating a string")
                    state.inExpressionEvaluation = false
                }

                ControlCommand.CommandType.BeginTag ->
                    state.pushToOutputStream(evalCommand)

                ControlCommand.CommandType.EndTag -> {
                    if (state.inStringEvaluation) {
                        val contentStackForTag = ArrayDeque<StringValue>()
                        var outputCountConsumed = 0

                        for (i in state.outputStream.indices.reversed()) {
                            val obj = state.outputStream[i]
                            outputCountConsumed++

                            if (obj is ControlCommand) {
                                if (obj.commandType == ControlCommand.CommandType.BeginTag) break
                                else {
                                    error("Unexpected ControlCommand while extracting tag from choice")
                                    break
                                }
                            }

                            if (obj is StringValue) contentStackForTag.addFirst(obj)
                        }

                        state.popFromOutputStream(outputCountConsumed)

                        val sb = StringBuilder()
                        for (strVal in contentStackForTag) {
                            sb.append(strVal.value)
                        }

                        val choiceTag = Tag(state.cleanOutputWhitespace(sb.toString()))
                        state.pushEvaluationStack(choiceTag)
                    } else {
                        state.pushToOutputStream(evalCommand)
                    }
                }

                ControlCommand.CommandType.EndString -> {
                    val contentStackForString = ArrayDeque<InkObject>()
                    val contentToRetain = ArrayDeque<InkObject>()

                    var outputCountConsumed = 0
                    for (i in state.outputStream.indices.reversed()) {
                        val obj = state.outputStream[i]
                        outputCountConsumed++

                        val command = obj as? ControlCommand
                        if (command != null && command.commandType == ControlCommand.CommandType.BeginString) break

                        if (obj is Tag) contentToRetain.addFirst(obj)
                        if (obj is StringValue) contentStackForString.addFirst(obj)
                    }

                    state.popFromOutputStream(outputCountConsumed)

                    // Rescue tags
                    for (c in contentToRetain) {
                        state.pushToOutputStream(c)
                    }

                    val sb = StringBuilder()
                    for (c in contentStackForString) {
                        sb.append(c.toString())
                    }

                    state.inExpressionEvaluation = true
                    state.pushEvaluationStack(StringValue(sb.toString()))
                }

                ControlCommand.CommandType.ChoiceCount ->
                    state.pushEvaluationStack(IntValue(state.generatedChoices.size))

                ControlCommand.CommandType.Turns ->
                    state.pushEvaluationStack(IntValue(state.currentTurnIndex + 1))

                ControlCommand.CommandType.TurnsSince, ControlCommand.CommandType.ReadCount -> {
                    val target = state.popEvaluationStack()
                    if (target !is DivertTargetValue) {
                        val extraNote = if (target is IntValue)
                            ". Did you accidentally pass a read count ('knot_name') instead of a target ('-> knot_name')?"
                        else ""
                        error("TURNS_SINCE expected a divert target (knot, stitch, label name), but saw $target$extraNote")
                    } else {
                        val otmp = contentAtPath(target.targetPath!!).correctObj
                        val container = otmp as? Container

                        val eitherCount = if (container != null) {
                            if (evalCommand.commandType == ControlCommand.CommandType.TurnsSince)
                                state.turnsSinceForContainer(container)
                            else state.visitCountForContainer(container)
                        } else {
                            if (evalCommand.commandType == ControlCommand.CommandType.TurnsSince) -1
                            else 0.also {
                                warning("Failed to find container for $evalCommand lookup at ${target.targetPath}")
                            }
                        }

                        state.pushEvaluationStack(IntValue(eitherCount))
                    }
                }

                ControlCommand.CommandType.Random -> {
                    val o1 = state.popEvaluationStack()
                    val maxInt = o1 as? IntValue
                    val o2 = state.popEvaluationStack()
                    val minInt = o2 as? IntValue

                    if (minInt == null) error("Invalid value for minimum parameter of RANDOM(min, max)")
                    if (maxInt == null) error("Invalid value for maximum parameter of RANDOM(min, max)")

                    val randomRange = maxInt!!.value - minInt!!.value + 1
                    if (randomRange <= 0)
                        error("RANDOM was called with minimum as ${minInt.value} and maximum as ${maxInt.value}. The maximum must be larger")

                    val resultSeed = state.storySeed + state.previousRandom
                    val random = Random(resultSeed)

                    val nextRandom = random.nextInt(Int.MAX_VALUE)
                    val chosenValue = (nextRandom % randomRange) + minInt.value
                    state.pushEvaluationStack(IntValue(chosenValue))

                    state.previousRandom = state.previousRandom + 1
                }

                ControlCommand.CommandType.SeedRandom -> {
                    val o = state.popEvaluationStack()
                    val seed = o as? IntValue
                    if (seed == null) error("Invalid value passed to SEED_RANDOM")

                    state.storySeed = seed!!.value
                    state.previousRandom = 0
                    state.pushEvaluationStack(Void())
                }

                ControlCommand.CommandType.VisitIndex -> {
                    val count = state.visitCountForContainer(state.currentPointer.container!!) - 1
                    state.pushEvaluationStack(IntValue(count))
                }

                ControlCommand.CommandType.SequenceShuffleIndex -> {
                    val shuffleIndex = nextSequenceShuffleIndex()
                    state.pushEvaluationStack(IntValue(shuffleIndex))
                }

                ControlCommand.CommandType.StartThread -> {
                    // Handled in main step function
                }

                ControlCommand.CommandType.Done -> {
                    if (state.callStack.canPopThread) {
                        state.callStack.popThread()
                    } else {
                        state.didSafeExit = true
                        state.currentPointer = Pointer.Null
                    }
                }

                ControlCommand.CommandType.End -> state.forceEnd()

                ControlCommand.CommandType.ListFromInt -> {
                    val o1 = state.popEvaluationStack()
                    val intVal = o1 as? IntValue
                    val o2 = state.popEvaluationStack()
                    val listNameVal = o2 as? StringValue

                    if (intVal == null)
                        throw StoryException("Passed non-integer when creating a list element from a numerical value.")

                    var generatedListValue: ListValue? = null
                    val foundListDef = listDefinitions!!.getListDefinition(listNameVal!!.value)

                    if (foundListDef != null) {
                        val foundItem = foundListDef.getItemWithValue(intVal.value)
                        if (foundItem != null) {
                            generatedListValue = ListValue(foundItem, intVal.value)
                        }
                    } else {
                        throw StoryException("Failed to find List called ${listNameVal.value}")
                    }

                    if (generatedListValue == null) generatedListValue = ListValue()
                    state.pushEvaluationStack(generatedListValue)
                }

                ControlCommand.CommandType.ListRange -> {
                    val p1 = state.popEvaluationStack()
                    val max = p1 as? Value<*>
                    val p2 = state.popEvaluationStack()
                    val min = p2 as? Value<*>
                    val p3 = state.popEvaluationStack()
                    val targetList = p3 as? ListValue

                    if (targetList == null || min == null || max == null)
                        throw StoryException("Expected List, minimum and maximum for LIST_RANGE")

                    val result = targetList.value.listWithSubRange(min.valueObject, max.valueObject)
                    state.pushEvaluationStack(ListValue(result))
                }

                ControlCommand.CommandType.ListRandom -> {
                    val o = state.popEvaluationStack()
                    val listVal = o as? ListValue
                        ?: throw StoryException("Expected list for LIST_RANDOM")

                    val list = listVal.value
                    val newList: InkList

                    if (list.isEmpty()) {
                        newList = InkList()
                    } else {
                        val resultSeed = state.storySeed + state.previousRandom
                        val random = Random(resultSeed)
                        val nextRandom = random.nextInt(Int.MAX_VALUE)
                        val listItemIndex = nextRandom % list.size

                        val entries = list.entries.toList()
                        val randomItem = entries[listItemIndex]

                        newList = InkList.fromString(randomItem.key.itemName!!, this)
                        newList[randomItem.key] = randomItem.value

                        state.previousRandom = nextRandom
                    }

                    state.pushEvaluationStack(ListValue(newList))
                }

                else -> error("unhandled ControlCommand: $evalCommand")
            }

            return true
        }

        // Variable assignment
        else if (contentObj is VariableAssignment) {
            val assignedVal = state.popEvaluationStack()
            state.variablesState.assign(contentObj, assignedVal)
            return true
        }

        // Variable reference
        else if (contentObj is VariableReference) {
            var foundValue: InkObject?

            if (contentObj.pathForCount != null) {
                val container = contentObj.containerForCount
                val count = state.visitCountForContainer(container!!)
                foundValue = IntValue(count)
            } else {
                foundValue = state.variablesState.getVariableWithName(contentObj.name!!)
                if (foundValue == null) {
                    warning("Variable not found: '${contentObj.name}'. Using default value of 0 (false). This can happen with temporary variables if the declaration hasn't yet been hit. Globals are always given a default value on load if a value doesn't exist in the save state.")
                    foundValue = IntValue(0)
                }
            }

            state.pushEvaluationStack(foundValue)
            return true
        }

        // Native function call
        else if (contentObj is NativeFunctionCall) {
            val funcParams = state.popEvaluationStack(contentObj.numberOfParameters)
            val result = contentObj.call(funcParams)
            state.pushEvaluationStack(result!!)
            return true
        }

        return false
    }

    // ── Output state change detection ──────────────────────────────────

    private enum class OutputStateChange {
        NoChange,
        ExtendedBeyondNewline,
        NewlineRemoved
    }

    private fun calculateNewlineOutputStateChange(
        prevText: String, currText: String, prevTagCount: Int, currTagCount: Int
    ): OutputStateChange {
        val newlineStillExists = currText.length >= prevText.length
                && prevText.isNotEmpty()
                && currText[prevText.length - 1] == '\n'
        if (prevTagCount == currTagCount && prevText.length == currText.length && newlineStillExists)
            return OutputStateChange.NoChange

        if (!newlineStillExists)
            return OutputStateChange.NewlineRemoved

        if (currTagCount > prevTagCount)
            return OutputStateChange.ExtendedBeyondNewline

        for (i in prevText.length until currText.length) {
            val c = currText[i]
            if (c != ' ' && c != '\t') return OutputStateChange.ExtendedBeyondNewline
        }

        return OutputStateChange.NoChange
    }

    // ── Choice processing ──────────────────────────────────────────────

    private fun popChoiceStringAndTags(tags: MutableList<String>): String {
        val choiceOnlyStrVal = state.popEvaluationStack() as StringValue

        while (state.evaluationStack.isNotEmpty() && state.peekEvaluationStack() is Tag) {
            val tag = state.popEvaluationStack() as Tag
            tags.add(0, tag.text) // popped in reverse order
        }

        return choiceOnlyStrVal.value
    }

    private fun processChoice(choicePoint: ChoicePoint): Choice? {
        var showChoice = true

        if (choicePoint.hasCondition) {
            val conditionValue = state.popEvaluationStack()
            if (!isTruthy(conditionValue)) showChoice = false
        }

        var startText = ""
        var choiceOnlyText = ""
        val tags = mutableListOf<String>()

        if (choicePoint.hasChoiceOnlyContent) {
            choiceOnlyText = popChoiceStringAndTags(tags)
        }

        if (choicePoint.hasStartContent) {
            startText = popChoiceStringAndTags(tags)
        }

        if (choicePoint.onceOnly) {
            val visitCount = state.visitCountForContainer(choicePoint.choiceTarget!!)
            if (visitCount > 0) showChoice = false
        }

        if (!showChoice) return null

        return Choice().also {
            it.targetPath = choicePoint.pathOnChoice
            it.sourcePath = choicePoint.path.toString()
            it.isInvisibleDefault = choicePoint.isInvisibleDefault
            it.tags = tags
            it.threadAtGeneration = state.callStack.forkThread()
            it.text = (startText + choiceOnlyText).trim()
        }
    }

    // ── Reset / state management ───────────────────────────────────────

    fun resetCallstack() {
        ifAsyncWeCant("ResetCallstack")
        state.forceEnd()
    }

    fun resetErrors() = state.resetErrors()

    private fun resetGlobals() {
        if (mainContentContainer!!.namedContent.containsKey("global decl")) {
            val originalPointer = Pointer(state.currentPointer)
            choosePath(Path("global decl"), false)
            continueInternal()
            state.currentPointer = originalPointer
        }
        state.variablesState.snapshotDefaultGlobals()
    }

    fun resetState() {
        ifAsyncWeCant("ResetState")
        state = StoryState(
            mainContentContainer ?: Container(),
            listDefinitions ?: ListDefinitionsOrigin(emptyList())
        )
        state.variablesState.variableChangedEvent = this
        resetGlobals()
    }

    // ── Pointer at path ────────────────────────────────────────────────

    internal fun pointerAtPath(path: Path): Pointer {
        if (path.length == 0) return Pointer.Null

        val p = Pointer()
        var pathLengthToUse: Int = 0
        val result: SearchResult

        if (path.lastComponent!!.isIndex) {
            pathLengthToUse = path.length - 1
            result = SearchResult(mainContentContainer!!.contentAtPath(path, 0, pathLengthToUse))
            p.container = result.container
            p.index = path.lastComponent!!.index
        } else {
            result = SearchResult(mainContentContainer!!.contentAtPath(path))
            p.container = result.container
            p.index = -1
        }

        if (result.obj == null || (result.obj == mainContentContainer && pathLengthToUse > 0))
            error("Failed to find content at path '$path', and no approximation of it was possible.")
        else if (result.approximate)
            warning("Failed to find content at path '$path', so it was approximated to: '${result.obj!!.path}'.")

        return p
    }

    // ── Step ───────────────────────────────────────────────────────────

    private fun step() {
        var shouldAddToStream = true

        val pointer = Pointer()
        pointer.assign(state.currentPointer)

        if (pointer.isNull) return

        // Step directly to the first element of content in a container
        var r = pointer.resolve()
        var containerToEnter = r as? Container

        while (containerToEnter != null) {
            visitContainer(containerToEnter, true)

            if (containerToEnter.content.isEmpty()) break

            pointer.assign(Pointer.startOf(containerToEnter))
            r = pointer.resolve()
            containerToEnter = r as? Container
        }

        state.currentPointer = pointer

        profiler?.step(state.callStack)

        var currentContentObj = pointer.resolve()
        val isLogicOrFlowControl = performLogicAndFlowControl(currentContentObj)

        if (state.currentPointer.isNull) return

        if (isLogicOrFlowControl) shouldAddToStream = false

        // Choice with condition?
        val choicePoint = currentContentObj as? ChoicePoint
        if (choicePoint != null) {
            val choice = processChoice(choicePoint)
            if (choice != null) {
                state.generatedChoices.add(choice)
            }
            currentContentObj = null
            shouldAddToStream = false
        }

        // Skip containers
        if (currentContentObj is Container) shouldAddToStream = false

        // Content to add
        if (shouldAddToStream) {
            val varPointer = currentContentObj as? VariablePointerValue
            if (varPointer != null && varPointer.contextIndex == -1) {
                val contextIdx = state.callStack.contextForVariableNamed(varPointer.variableName!!)
                currentContentObj = VariablePointerValue(varPointer.variableName!!, contextIdx)
            }

            if (state.inExpressionEvaluation) {
                state.pushEvaluationStack(currentContentObj!!)
            } else {
                state.pushToOutputStream(currentContentObj!!)
            }
        }

        nextContent()

        // StartThread handling
        val controlCmd = currentContentObj as? ControlCommand
        if (controlCmd != null && controlCmd.commandType == ControlCommand.CommandType.StartThread) {
            state.callStack.pushThread()
        }
    }

    // ── JSON serialization ─────────────────────────────────────────────

    fun toJson(): String {
        val writer = SimpleJson.Writer()
        toJson(writer)
        return writer.toString()
    }

    internal fun toJson(writer: SimpleJson.Writer) {
        writer.writeObjectStart()
        writer.writeProperty("inkVersion", INK_VERSION_CURRENT)

        writer.writeProperty("root") { w ->
            JsonSerialisation.writeRuntimeContainer(w, mainContentContainer!!)
        }

        // List definitions
        if (listDefinitions != null) {
            writer.writePropertyStart("listDefs")
            writer.writeObjectStart()

            for (def in listDefinitions!!.lists) {
                writer.writePropertyStart(def.name!!)
                writer.writeObjectStart()

                for ((item, value) in def.items) {
                    writer.writeProperty(item.itemName!!, value)
                }

                writer.writeObjectEnd()
                writer.writePropertyEnd()
            }

            writer.writeObjectEnd()
            writer.writePropertyEnd()
        }

        writer.writeObjectEnd()
    }

    // ── Try follow default invisible choice ────────────────────────────

    private fun tryFollowDefaultInvisibleChoice(): Boolean {
        val allChoices = state.currentChoices
        val invisibleChoices = allChoices.filter { it.isInvisibleDefault }

        if (invisibleChoices.isEmpty() || allChoices.size > invisibleChoices.size) return false

        val choice = invisibleChoices[0]

        state.callStack.currentThread = choice.threadAtGeneration!!

        if (stateSnapshotAtLastNewline != null)
            state.callStack.currentThread = state.callStack.forkThread()

        choosePath(choice.targetPath!!, false)

        return true
    }

    // ── Validate external bindings ─────────────────────────────────────

    fun validateExternalBindings() {
        val missingExternals = mutableSetOf<String>()
        validateExternalBindings(mainContentContainer!!, missingExternals)
        hasValidatedExternals = true

        if (missingExternals.isNotEmpty()) {
            val message = "ERROR: Missing function binding for external${if (missingExternals.size > 1) "s" else ""}: " +
                "'${missingExternals.joinToString("', '")}' " +
                if (allowExternalFunctionFallbacks) ", and no fallback ink function found."
                else " (ink fallbacks disabled)"
            error(message)
        }
    }

    private fun validateExternalBindings(c: Container, missingExternals: MutableSet<String>) {
        for (innerContent in c.content) {
            val container = innerContent as? Container
            if (container == null || !container.hasValidName)
                validateExternalBindings(innerContent, missingExternals)
        }
        for (innerKeyValue in c.namedContent.values) {
            validateExternalBindings(innerKeyValue as? InkObject ?: continue, missingExternals)
        }
    }

    private fun validateExternalBindings(o: InkObject, missingExternals: MutableSet<String>) {
        val container = o as? Container
        if (container != null) {
            validateExternalBindings(container, missingExternals)
            return
        }

        val divert = o as? Divert
        if (divert != null && divert.isExternal) {
            val name = divert.targetPathString!!
            if (!externals.containsKey(name)) {
                if (allowExternalFunctionFallbacks) {
                    if (!mainContentContainer!!.namedContent.containsKey(name)) {
                        missingExternals.add(name)
                    }
                } else {
                    missingExternals.add(name)
                }
            }
        }
    }

    // ── Visit changed containers ───────────────────────────────────────

    private fun visitChangedContainersDueToDivert() {
        val previousPointer = Pointer(state.previousPointer)
        val pointer = Pointer(state.currentPointer)

        if (pointer.isNull || pointer.index == -1) return

        prevContainers.clear()

        if (!previousPointer.isNull) {
            var prevAncestor: Container? = when {
                previousPointer.resolve() is Container -> previousPointer.resolve() as Container
                previousPointer.container is Container -> previousPointer.container
                else -> null
            }

            while (prevAncestor != null) {
                prevContainers.add(prevAncestor)
                prevAncestor = prevAncestor.parent as? Container
            }
        }

        var currentChildOfContainer = pointer.resolve() ?: return

        var currentContainerAncestor = currentChildOfContainer.parent as? Container

        var allChildrenEnteredAtStart = true
        while (currentContainerAncestor != null
            && (!prevContainers.contains(currentContainerAncestor) || currentContainerAncestor.countingAtStartOnly)
        ) {
            val enteringAtStart = currentContainerAncestor.content.isNotEmpty()
                    && currentChildOfContainer == currentContainerAncestor.content[0]
                    && allChildrenEnteredAtStart

            if (!enteringAtStart) allChildrenEnteredAtStart = false

            visitContainer(currentContainerAncestor, enteringAtStart)

            currentChildOfContainer = currentContainerAncestor
            currentContainerAncestor = currentContainerAncestor.parent as? Container
        }
    }

    private fun visitContainer(container: Container, atStart: Boolean) {
        if (!container.countingAtStartOnly || atStart) {
            if (container.visitsShouldBeCounted) state.incrementVisitCountForContainer(container)
            if (container.turnIndexShouldBeCounted) state.recordTurnIndexVisitToContainer(container)
        }
    }

    // ── Allow external function fallbacks ──────────────────────────────

    var allowExternalFallbacks: Boolean
        get() = allowExternalFunctionFallbacks
        set(value) { allowExternalFunctionFallbacks = value }

    // ── Evaluate function ──────────────────────────────────────────────

    fun evaluateFunction(functionName: String, arguments: Array<Any?>? = null): Any? =
        evaluateFunction(functionName, null, arguments)

    fun hasFunction(functionName: String): Boolean =
        try { knotContainerWithName(functionName) != null } catch (_: Exception) { false }

    fun evaluateFunction(functionName: String, textOutput: StringBuilder?, arguments: Array<Any?>?): Any? {
        ifAsyncWeCant("evaluate a function")

        if (functionName.isNullOrBlank())
            throw Exception("Function is null or empty or white space.")

        val funcContainer = knotContainerWithName(functionName)
            ?: throw Exception("Function doesn't exist: '$functionName'")

        val outputStreamBefore = ArrayList(state.outputStream)
        state.resetOutput()

        state.startFunctionEvaluationFromGame(funcContainer, arguments)

        while (canContinue()) {
            val text = continueStory()
            textOutput?.append(text)
        }

        state.resetOutput(outputStreamBefore)

        return state.completeFunctionEvaluationFromGame()
    }

    // ── State snapshot / restore ────────────────────────────────────────

    private fun stateSnapshot() {
        stateSnapshotAtLastNewline = state
        state = state.copyAndStartPatching(false)
    }

    private fun restoreStateSnapshot() {
        stateSnapshotAtLastNewline!!.restoreAfterPatch()
        state = stateSnapshotAtLastNewline!!
        stateSnapshotAtLastNewline = null

        if (!asyncSaving) {
            state.applyAnyPatch()
        }
    }

    private fun discardSnapshot() {
        if (!asyncSaving) state.applyAnyPatch()
        stateSnapshotAtLastNewline = null
    }

    fun copyStateForBackgroundThreadSave(): StoryState {
        ifAsyncWeCant("start saving on a background thread")
        if (asyncSaving)
            throw Exception("Story is already in background saving mode, can't call CopyStateForBackgroundThreadSave again!")
        val stateToSave = state
        state = state.copyAndStartPatching(true)
        asyncSaving = true
        return stateToSave
    }

    fun backgroundSaveComplete() {
        if (stateSnapshotAtLastNewline == null) {
            state.applyAnyPatch()
        }
        asyncSaving = false
    }
}
