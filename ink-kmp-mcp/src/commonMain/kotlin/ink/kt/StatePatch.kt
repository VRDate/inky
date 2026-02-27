package ink.kt

/**
 * A transactional overlay on global variables, visit counts, and turn indices.
 * Allows changes to be accumulated and either applied or discarded (e.g. during
 * background saves or speculative evaluation).
 *
 * Design decisions from comparing all three implementations:
 * - C#: StatePatch with Dictionary<string, Object>, TryGetGlobal out-param pattern
 * - Java: StatePatch with HashMap<String, RTObject>, null-check getters
 * - JS: StatePatch with Map<string, InkObject>, has() checks
 *
 * Kotlin improvements over all three:
 * - LinkedHashMap for globals (insertion-order + O(1), per project directive)
 * - LinkedHashSet for changedVariables (insertion-order)
 * - Nullable return instead of C#'s TryGet out-param or Java null-check
 * - Clean copy constructor with Kotlin's ?. let pattern
 */
class StatePatch(toCopy: StatePatch? = null) {

    val globals: LinkedHashMap<String, InkObject> =
        toCopy?.globals?.let { LinkedHashMap(it) } ?: LinkedHashMap()

    val changedVariables: LinkedHashSet<String> =
        toCopy?.changedVariables?.let { LinkedHashSet(it) } ?: LinkedHashSet()

    val visitCounts: LinkedHashMap<Container, Int> =
        toCopy?.visitCounts?.let { LinkedHashMap(it) } ?: LinkedHashMap()

    val turnIndices: LinkedHashMap<Container, Int> =
        toCopy?.turnIndices?.let { LinkedHashMap(it) } ?: LinkedHashMap()

    fun getGlobal(name: String): InkObject? = globals[name]

    fun setGlobal(name: String, value: InkObject) {
        globals[name] = value
    }

    fun addChangedVariable(name: String) {
        changedVariables.add(name)
    }

    fun getVisitCount(container: Container): Int? = visitCounts[container]

    fun setVisitCount(container: Container, count: Int) {
        visitCounts[container] = count
    }

    fun getTurnIndex(container: Container): Int? = turnIndices[container]

    fun setTurnIndex(container: Container, index: Int) {
        turnIndices[container] = index
    }
}
