package ink.kt

/**
 * Represents a single flow in the ink runtime — a named execution context
 * with its own call stack, output stream, and current choices.
 *
 * Design decisions from comparing all three implementations:
 * - C#: Flow class with CallStack, List<Object>, List<Choice>, WriteJson lambda delegates
 * - Java: Flow class with CallStack, List<RTObject>, List<Choice>, InnerWriter anonymous classes
 * - JS: Flow class with CallStack, InkObject[], Choice[], arguments[] constructor (untyped)
 *
 * Kotlin improvements over all three:
 * - MutableList<InkObject> instead of raw Object/RTObject (type-safe output stream)
 * - LinkedHashSet-compatible Choice list (insertion-order, O(1) lookup)
 * - Clean constructor — no Java InnerWriter verbosity, no JS arguments[] hack
 * - JSON serialization deferred to JsonSerialisation.kt (separation of concerns)
 * - choiceThreads logic preserved for save/load thread bookkeeping
 */
class Flow(
    var name: String,
    rootContentContainer: Container
) {
    var callStack: CallStack = CallStack(rootContentContainer)
    val outputStream: MutableList<InkObject> = mutableListOf()
    val currentChoices: MutableList<Choice> = mutableListOf()

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
        jChoiceThreads: Map<String, CallStack.Thread>?,
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
