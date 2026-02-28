package ink.kt

/**
 * Simple ink profiler that logs every instruction and counts frequency + timing.
 *
 * Three-way comparison:
 * - C#: Profiler class, ProfileNode tree, System.Diagnostics.Stopwatch
 * - Java: Profiler + ProfileNode (separate files), Stopwatch.getElapsedMilliseconds(), 300+ lines
 * - JS: not implemented (profiling is C#/Java only)
 *
 * Kotlin improvements:
 * - **ProfileNode as nested class** — single file, clear ownership
 * - **kotlin.time Stopwatch** — TimeSource.Monotonic, nano precision
 * - **sortedByDescending** — replaces Collections.sort + Comparator
 * - **buildString** — replaces StringBuilder pattern
 * - **data class StepDetails** — replaces inner class with manual fields
 *
 * Usage:
 * ```kotlin
 * val profiler = story.startProfiling()
 * // play story...
 * println(profiler.report())
 * story.endProfiling()
 * ```
 */
class Profiler {

    private val continueWatch = Stopwatch()
    private val stepWatch = Stopwatch()
    private val snapWatch = Stopwatch()

    private var continueTotal: Double = 0.0
    private var snapTotal: Double = 0.0
    private var stepTotal: Double = 0.0

    private var currStepStack: Array<String>? = null
    private var currStepDetails: StepDetails? = null
    val rootNode: ProfileNode = ProfileNode()
    private var numContinues: Int = 0

    private data class StepDetails(
        val type: String,
        val obj: InkObject,
        var time: Double
    )

    private val stepDetails: MutableList<StepDetails> = mutableListOf()

    /** Generate a printable report based on the data recorded during profiling. */
    fun report(): String = buildString {
        append("$numContinues CONTINUES / LINES:\n")
        append("TOTAL TIME: ${formatMillisecs(continueTotal)}\n")
        append("SNAPSHOTTING: ${formatMillisecs(snapTotal)}\n")
        append("OTHER: ${formatMillisecs(continueTotal - (stepTotal + snapTotal))}\n")
        append(rootNode.toString())
    }

    internal fun preContinue() {
        continueWatch.reset()
        continueWatch.start()
    }

    internal fun postContinue() {
        continueWatch.stop()
        continueTotal += continueWatch.elapsedMilliseconds.toDouble()
        numContinues++
    }

    internal fun preStep() {
        currStepStack = null
        stepWatch.reset()
        stepWatch.start()
    }

    internal fun step(callstack: CallStack) {
        stepWatch.stop()

        val stack = Array(callstack.elements.size) { i ->
            var stackElementName = ""
            val ptr = callstack.elements[i].currentPointer
            if (!ptr.isNull) {
                val objPath = ptr.path!!
                for (c in 0 until objPath.length) {
                    val comp = objPath.getComponent(c)
                    if (!comp.isIndex) {
                        stackElementName = comp.name ?: ""
                        break
                    }
                }
            }
            stackElementName
        }

        currStepStack = stack

        val currObj = callstack.currentElement.currentPointer.resolve()!!

        val stepType = when (currObj) {
            is ControlCommand -> "${currObj.commandType} CC"
            else -> currObj::class.simpleName ?: "Unknown"
        }

        currStepDetails = StepDetails(stepType, currObj, 0.0)
        stepWatch.start()
    }

    internal fun postStep() {
        stepWatch.stop()

        val duration = stepWatch.elapsedMilliseconds.toDouble()
        stepTotal += duration

        rootNode.addSample(currStepStack!!, duration)

        currStepDetails!!.time = duration
        stepDetails.add(currStepDetails!!)
    }

    /** Report specifying average and maximum times spent on different instruction types. */
    fun stepLengthReport(): String = buildString {
        append("TOTAL: ${rootNode.totalMillisecs}ms\n")

        // AVERAGE STEP TIMES
        val typeToAvg = linkedMapOf<String, Double>()
        for (sd in stepDetails) {
            if (typeToAvg.containsKey(sd.type)) continue
            val matching = stepDetails.filter { it.type == sd.type }
            typeToAvg[sd.type] = matching.sumOf { it.time } / matching.size
        }

        val averageStepTimes = typeToAvg.entries.sortedBy { it.value }

        append("AVERAGE STEP TIMES: ")
        append(averageStepTimes.joinToString(",") { "${it.key}: ${it.value}ms" })
        append('\n')

        // ACCUMULATED STEP TIMES
        val typeToSum = linkedMapOf<String, Double>()
        for (sd in stepDetails) {
            if (typeToSum.containsKey(sd.type)) continue
            val matching = stepDetails.filter { it.type == sd.type }
            typeToSum["${sd.type} (x${matching.size})"] = matching.sumOf { it.time }
        }

        val accumStepTimes = typeToSum.entries.sortedBy { it.value }

        append("ACCUMULATED STEP TIMES: ")
        append(accumStepTimes.joinToString(",") { "${it.key}: ${it.value}" })
        append('\n')
    }

    /** Large log of all instructions evaluated while profiling. Tab-separated for spreadsheets. */
    fun megalog(): String = buildString {
        append("Step type\tDescription\tPath\tTime\n")
        for (step in stepDetails) {
            append("${step.type}\t${step.obj}\t${step.obj.path}\t${step.time}\n")
        }
    }

    internal fun preSnapshot() {
        snapWatch.reset()
        snapWatch.start()
    }

    internal fun postSnapshot() {
        snapWatch.stop()
        snapTotal += snapWatch.elapsedMilliseconds.toDouble()
    }

    companion object {
        fun formatMillisecs(num: Double): String = when {
            num > 5000 -> "${roundTo(num / 1000.0, 1)} secs"
            num > 1000 -> "${roundTo(num / 1000.0, 2)} secs"
            num > 100 -> "${roundTo(num, 0)} ms"
            num > 1 -> "${roundTo(num, 1)} ms"
            num > 0.01 -> "${roundTo(num, 3)} ms"
            else -> "${roundTo(num, 0)} ms"
        }

        private fun roundTo(value: Double, decimals: Int): String {
            if (decimals == 0) return value.toLong().toString()
            var factor = 1.0
            repeat(decimals) { factor *= 10 }
            val rounded = kotlin.math.round(value * factor) / factor
            val str = rounded.toString()
            val dot = str.indexOf('.')
            return if (dot < 0) "$str.${"0".repeat(decimals)}"
            else str.padEnd(dot + 1 + decimals, '0').substring(0, dot + 1 + decimals)
        }
    }
}

// ── ProfileNode ─────────────────────────────────────────────────────

/**
 * Node in the hierarchical tree of timings used by the Profiler.
 * Each node corresponds to a single line in a UI-based representation.
 *
 * Three-way comparison:
 * - C#: ProfileNode — separate file, Dictionary<string, ProfileNode>
 * - Java: ProfileNode — separate file, HashMap, Collections.sort + Comparator
 * - JS: not implemented
 *
 * Kotlin improvements:
 * - **sortedByDescending** — one-liner sorting (vs 10 lines Java Comparator)
 * - **buildString** — clean string building
 * - Kept as top-level class for compatibility with existing usage
 */
class ProfileNode(val key: String? = null) {

    private var nodes: HashMap<String, ProfileNode>? = null
    private var selfMillisecs: Double = 0.0
    var totalMillisecs: Double = 0.0
        private set
    private var selfSampleCount: Int = 0
    private var totalSampleCount: Int = 0

    /** Hacky field for UI state tracking (same as C#/Java). */
    var openInUI: Boolean = false

    /** Whether this node has sub-nodes. */
    val hasChildren: Boolean get() = nodes != null && nodes!!.isNotEmpty()

    internal fun addSample(stack: Array<String>, duration: Double) =
        addSample(stack, -1, duration)

    internal fun addSample(stack: Array<String>, stackIdx: Int, duration: Double) {
        totalSampleCount++
        totalMillisecs += duration

        if (stackIdx == stack.size - 1) {
            selfSampleCount++
            selfMillisecs += duration
        }

        if (stackIdx + 1 < stack.size) addSampleToNode(stack, stackIdx + 1, duration)
    }

    private fun addSampleToNode(stack: Array<String>, stackIdx: Int, duration: Double) {
        val nodeKey = stack[stackIdx]
        if (nodes == null) nodes = HashMap()

        val node = nodes!!.getOrPut(nodeKey) { ProfileNode(nodeKey) }
        node.addSample(stack, stackIdx, duration)
    }

    /** Nodes sorted in descending order of total time. */
    val descendingOrderedNodes: List<Map.Entry<String, ProfileNode>>?
        get() = nodes?.entries?.sortedByDescending { it.value.totalMillisecs }

    private fun printHierarchy(sb: StringBuilder, indent: Int) {
        repeat(indent) { sb.append("   ") }
        sb.append(key)
        sb.append(": ")
        sb.append(ownReport)
        sb.append('\n')

        descendingOrderedNodes?.forEach { (_, node) ->
            node.printHierarchy(sb, indent + 1)
        }
    }

    /** Timing information for this single node. */
    val ownReport: String get() = buildString {
        append("total ${Profiler.formatMillisecs(totalMillisecs)}")
        append(", self ${Profiler.formatMillisecs(selfMillisecs)}")
        append(" ($selfSampleCount self samples, $totalSampleCount total)")
    }

    override fun toString(): String = buildString { printHierarchy(this, 0) }
}
