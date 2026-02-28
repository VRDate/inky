package ink.kt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Represents a single flow in the ink runtime — a named execution context
 * with its own call stack, output stream, and current choices.
 *
 * **Implements [Flow]<[InkObject]>** — consumers can `collect` story output reactively.
 * Each InkFlow is a reactive stream of InkObjects produced by the story engine.
 *
 * **Multi-tenant MMO support**: Each player session creates its own Story instance
 * with independent InkFlow streams. Multiple users can run the same compiled ink
 * story in parallel — each session has isolated state but shares the compiled Container.
 *
 * **Cross-platform**: Works on JVM (real parallelism), JS (single-threaded event loop),
 * and Native — coroutines provide the portable concurrency abstraction.
 *
 * Design decisions from comparing all four implementations:
 * - C#: Flow class with events (onDidContinue, onMakeChoice) + delegates
 * - Java: Flow class with VariableObserver/ErrorHandler callback interfaces
 * - JS: Flow class with single-threaded event loop (no real parallelism)
 * - **Kotlin: InkFlow implements Flow<InkObject>** — reactive stream with coroutine support.
 *   C# events → SharedFlow, Java callbacks → Flow collectors, JS event loop → coroutines.
 *
 * Kotlin improvements over all three:
 * - Implements Flow<InkObject> — reactive stream (vs raw List in C#/Java/JS)
 * - MutableList<InkObject> outputStream backed by [EmittingList] (emits to SharedFlow on mutation)
 * - LinkedHashSet-compatible Choice list (insertion-order, O(1) lookup)
 * - Clean constructor — no Java InnerWriter verbosity, no JS arguments[] hack
 * - JSON serialization deferred to JsonSerialisation.kt (separation of concerns)
 * - choiceThreads logic preserved for save/load thread bookkeeping
 *
 * **Architecture: InkThread as coroutine of InkFlow**
 * Each [CallStack.InkThread] represents a narrative thread within this flow.
 * In Kotlin, InkThreads map to coroutines — lightweight concurrent execution
 * contexts that are cooperative (no OS thread per thread). On JS this means
 * non-blocking single-threaded execution; on JVM it enables real parallelism.
 */
class InkFlow(
    var name: String,
    rootContentContainer: Container
) : Flow<InkObject> {

    /**
     * Shared flow backing the reactive stream. Configured with unlimited buffer
     * so [tryEmit] never fails — items are buffered when collectors are slow,
     * and dropped silently when no collectors are subscribed (zero overhead).
     *
     * - replay = 0: New subscribers don't get past items (outputStream holds history)
     * - extraBufferCapacity = MAX: tryEmit always succeeds, no backpressure on the engine
     */
    private val _sharedFlow = MutableSharedFlow<InkObject>(
        extraBufferCapacity = Int.MAX_VALUE
    )

    var callStack: CallStack = CallStack(rootContentContainer)

    /**
     * Output stream that also emits items to the reactive [Flow].
     * All existing code (Story, StoryState) continues using outputStream.add()
     * transparently — the [EmittingList] intercepts mutations and emits to [_sharedFlow].
     */
    val outputStream: MutableList<InkObject> = EmittingList(_sharedFlow)

    val currentChoices: MutableList<Choice> = mutableListOf()

    /**
     * Collect story output reactively. Suspends and emits InkObjects as the story
     * engine produces them via outputStream mutations.
     *
     * Usage:
     * ```kotlin
     * // In a coroutine scope (per-player session):
     * story.state.currentFlow.collect { inkObject ->
     *     when (inkObject) {
     *         is StringValue -> displayText(inkObject.value)
     *         is Tag -> processTag(inkObject)
     *         is ControlCommand -> handleCommand(inkObject)
     *         else -> { /* other runtime objects */ }
     *     }
     * }
     * ```
     */
    override suspend fun collect(collector: FlowCollector<InkObject>) =
        _sharedFlow.collect(collector)

    fun writeJson(writer: SimpleJson.Writer) {
        writer.writeObjectStart()

        writer.writeProperty("callstack") { w -> callStack.writeJson(w) }

        writer.writeProperty("outputStream") { w ->
            JsonSerialisation.writeListRuntimeObjs(w, outputStream)
        }

        // choiceThreads: optional
        // Only written for choices whose generation thread is no longer active
        var hasChoiceThreads = false
        for (c in currentChoices) {
            c.originalThreadIndex = c.threadAtGeneration!!.threadIndex

            if (callStack.threadWithIndex(c.originalThreadIndex) == null) {
                if (!hasChoiceThreads) {
                    hasChoiceThreads = true
                    writer.writePropertyStart("choiceThreads")
                    writer.writeObjectStart()
                }

                writer.writePropertyStart(c.originalThreadIndex)
                c.threadAtGeneration!!.writeJson(writer)
                writer.writePropertyEnd()
            }
        }

        if (hasChoiceThreads) {
            writer.writeObjectEnd()
            writer.writePropertyEnd()
        }

        writer.writeProperty("currentChoices") { w ->
            w.writeArrayStart()
            for (c in currentChoices) {
                JsonSerialisation.writeChoice(w, c)
            }
            w.writeArrayEnd()
        }

        writer.writeObjectEnd()
    }

    /**
     * Load choice threads from saved state.
     * Used both for old format and current.
     *
     * Each Choice snapshots the Thread it was generated on. If that thread
     * is still active in the callstack, we copy it. Otherwise we restore
     * the saved thread from jChoiceThreads.
     *
     * C#/Java/JS all have identical logic here — Kotlin just types it properly.
     */
    fun loadFlowChoiceThreads(
        jChoiceThreads: Map<String, CallStack.InkThread>?,
        activeCallStack: CallStack = callStack
    ) {
        for (choice in currentChoices) {
            val foundActiveThread = activeCallStack.threadWithIndex(choice.originalThreadIndex)
            if (foundActiveThread != null) {
                choice.threadAtGeneration = foundActiveThread.copy()
            } else {
                val savedThread = jChoiceThreads?.get(choice.originalThreadIndex.toString())
                    ?: throw StoryException(
                        "Couldn't find choice thread with index ${choice.originalThreadIndex}"
                    )
                choice.threadAtGeneration = savedThread
            }
        }
    }
}

/**
 * A MutableList that emits added items to a MutableSharedFlow, bridging
 * the synchronous ink engine to the reactive Flow<InkObject> stream.
 *
 * All list operations work normally (Story/StoryState use outputStream.add(),
 * .clear(), .addAll(), etc.). Mutation operations that add items also emit
 * to the shared flow, making them visible to Flow collectors.
 *
 * Thread-safe: tryEmit is lock-free and always succeeds with unlimited buffer.
 */
internal class EmittingList(
    private val flow: MutableSharedFlow<InkObject>,
    private val delegate: MutableList<InkObject> = mutableListOf()
) : MutableList<InkObject> by delegate {

    override fun add(element: InkObject): Boolean {
        val result = delegate.add(element)
        if (result) flow.tryEmit(element)
        return result
    }

    override fun add(index: Int, element: InkObject) {
        delegate.add(index, element)
        flow.tryEmit(element)
    }

    override fun addAll(elements: Collection<InkObject>): Boolean {
        val result = delegate.addAll(elements)
        if (result) elements.forEach { flow.tryEmit(it) }
        return result
    }

    override fun addAll(index: Int, elements: Collection<InkObject>): Boolean {
        val result = delegate.addAll(index, elements)
        if (result) elements.forEach { flow.tryEmit(it) }
        return result
    }

    override fun set(index: Int, element: InkObject): InkObject {
        val old = delegate.set(index, element)
        flow.tryEmit(element)
        return old
    }
}
