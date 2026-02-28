package ink.kt

/**
 * The InkList is the underlying type for a list value stored in a variable.
 *
 * Design decisions from comparing all three implementations:
 * - C#: InkList : Dictionary<InkListItem, int> — unsorted, needs orderedItems sort
 * - Java: InkList extends HashMap<InkListItem, Integer> — unsorted, ugly CustomEntry
 * - JS: InkList extends Map — unsorted
 *
 * Kotlin improvements over all three:
 * - LinkedHashMap: insertion-order preservation + O(1) key lookup (best of both worlds)
 * - operator fun: list1 + list2, list1 - list2 (maps to ink operators)
 * - data class InkListItem gives value equality + hashCode for free
 * - No need for Java's verbose CustomEntry hack
 * - Computed properties instead of getter methods
 * - Flow<LinkedHashSet<Choice>> enables reactive choice event streaming
 */
class InkList private constructor(
    private val _map: LinkedHashMap<InkListItem, Int>
) : MutableMap<InkListItem, Int> by _map {

    var origins: MutableList<ListDefinition>? = null

    private var _originNames: MutableList<String>? = null

    constructor() : this(LinkedHashMap())
    constructor(otherList: InkList) : this(LinkedHashMap<InkListItem, Int>(otherList._map)) {
        otherList._originNames?.let { _originNames = ArrayList(it) }
        otherList.origins?.let { origins = ArrayList(it) }
    }
    constructor(singleElement: Map.Entry<InkListItem, Int>) : this() {
        _map[singleElement.key] = singleElement.value
    }
    constructor(singleOriginListName: String, originStory: Story) : this() {
        setInitialOriginName(singleOriginListName)
        val def = originStory.listDefinitionsOrigin?.getListDefinition(singleOriginListName)
            ?: throw StoryException(
                "InkList origin could not be found in story when constructing new list: $singleOriginListName"
            )
        origins = mutableListOf(def)
    }

    // --- Origin management (identical across C#/Java/JS) ---

    val originOfMaxItem: ListDefinition?
        get() {
            val origs = origins ?: return null
            val maxOriginName = maxItem.key.originName
            return origs.firstOrNull { it.name == maxOriginName }
        }

    val originNames: MutableList<String>?
        get() {
            if (size > 0) {
                if (_originNames == null) _originNames = ArrayList()
                else _originNames!!.clear()
                for (item in keys) _originNames!!.add(item.originName!!)
            }
            return _originNames
        }

    val singleOriginListName: String?
        get() {
            var name: String? = null
            for ((item, _) in this) {
                val originName = item.originName
                if (name == null) name = originName
                else if (name != originName) return null
            }
            return name
        }

    fun setInitialOriginName(initialOriginName: String) {
        _originNames = mutableListOf(initialOriginName)
    }

    fun setInitialOriginNames(initialOriginNames: List<String>?) {
        _originNames = initialOriginNames?.let { ArrayList(it) }
    }

    // --- Min/Max (C# property, Java method — Kotlin property is cleaner) ---

    val maxItem: Map.Entry<InkListItem, Int>
        get() {
            var max: Map.Entry<InkListItem, Int>? = null
            for (kv in entries) {
                if (max == null || kv.value > max.value) max = kv
            }
            return max ?: MapEntry(InkListItem.Null, 0)
        }

    val minItem: Map.Entry<InkListItem, Int>
        get() {
            var min: Map.Entry<InkListItem, Int>? = null
            for (kv in entries) {
                if (min == null || kv.value < min.value) min = kv
            }
            return min ?: MapEntry(InkListItem.Null, 0)
        }

    // --- Set operations (all three have these, Kotlin adds operators) ---

    fun union(otherList: InkList): InkList {
        val result = InkList(this)
        for ((key, value) in otherList) result[key] = value
        return result
    }

    fun without(listToRemove: InkList): InkList {
        val result = InkList(this)
        for (key in listToRemove.keys) result.remove(key)
        return result
    }

    fun intersect(otherList: InkList): InkList {
        val result = InkList()
        for ((key, value) in this) {
            if (key in otherList) result[key] = value
        }
        return result
    }

    fun hasIntersection(otherList: InkList): Boolean =
        entries.any { it.key in otherList }

    // --- Operators (Kotlin-unique, maps to ink's list1 + list2, list1 - list2) ---

    operator fun plus(other: InkList): InkList = union(other)
    operator fun minus(other: InkList): InkList = without(other)

    // --- Containment (all three: Contains(InkList), Contains(string)) ---

    fun containsList(otherList: InkList): Boolean {
        if (otherList.isEmpty() || isEmpty()) return false
        return otherList.keys.all { it in this }
    }

    fun containsItemNamed(itemName: String): Boolean =
        keys.any { it.itemName == itemName }

    fun contains(listItemName: String): Boolean =
        containsItemNamed(listItemName)

    // --- Comparison (identical in C#/Java/JS) ---

    fun greaterThan(otherList: InkList): Boolean {
        if (isEmpty()) return false
        if (otherList.isEmpty()) return true
        return minItem.value > otherList.maxItem.value
    }

    fun greaterThanOrEquals(otherList: InkList): Boolean {
        if (isEmpty()) return false
        if (otherList.isEmpty()) return true
        return minItem.value >= otherList.minItem.value &&
               maxItem.value >= otherList.maxItem.value
    }

    fun lessThan(otherList: InkList): Boolean {
        if (otherList.isEmpty()) return false
        if (isEmpty()) return true
        return maxItem.value < otherList.minItem.value
    }

    fun lessThanOrEquals(otherList: InkList): Boolean {
        if (otherList.isEmpty()) return false
        if (isEmpty()) return true
        return maxItem.value <= otherList.maxItem.value &&
               minItem.value <= otherList.minItem.value
    }

    // --- Sublist operations ---

    fun maxAsList(): InkList =
        if (isNotEmpty()) InkList(maxItem) else InkList()

    fun minAsList(): InkList =
        if (isNotEmpty()) InkList(minItem) else InkList()

    /**
     * Sorted entries by value, then by originName for consistent ordering.
     * C#: orderedItems property, Java: getOrderedItems() method
     * Kotlin: sortedWith is more idiomatic than manual Comparator
     */
    val orderedItems: List<Map.Entry<InkListItem, Int>>
        get() = entries.sortedWith(compareBy<Map.Entry<InkListItem, Int>> { it.value }
            .thenBy { it.key.originName ?: "" })

    val singleItem: InkListItem?
        get() = entries.firstOrNull()?.key

    fun listWithSubRange(minBound: Any?, maxBound: Any?): InkList {
        if (isEmpty()) return InkList()

        val ordered = orderedItems
        val minValue = when (minBound) {
            is Int -> minBound
            is InkList -> if (minBound.isNotEmpty()) minBound.minItem.value else 0
            else -> 0
        }
        val maxValue = when (maxBound) {
            is Int -> maxBound
            is InkList -> if (maxBound.isNotEmpty()) maxBound.maxItem.value else Int.MAX_VALUE
            else -> Int.MAX_VALUE
        }

        val subList = InkList()
        subList.setInitialOriginNames(originNames)
        for (item in ordered) {
            if (item.value in minValue..maxValue) {
                subList[item.key] = item.value
            }
        }
        return subList
    }

    // --- Inverse / All (C# properties, Java methods) ---

    val inverse: InkList
        get() {
            val result = InkList()
            origins?.forEach { origin ->
                for ((key, value) in origin.items) {
                    if (key !in this) result[key] = value
                }
            }
            return result
        }

    val all: InkList
        get() {
            val result = InkList()
            origins?.forEach { origin ->
                for ((key, value) in origin.items) {
                    result[key] = value
                }
            }
            return result
        }

    // --- Add item (with origin resolution) ---

    fun addItem(item: InkListItem) {
        if (item.originName == null) {
            addItem(item.itemName!!)
            return
        }
        val origs = origins ?: throw IllegalStateException("No origins available")
        for (origin in origs) {
            if (origin.name == item.originName) {
                val intVal = origin.getValueForItem(item)
                if (intVal != null) {
                    this[item] = intVal
                    return
                } else {
                    throw IllegalStateException("Could not add item $item — not in original list definition")
                }
            }
        }
        throw IllegalStateException("Failed to add item — from unknown list definition")
    }

    fun addItem(itemName: String, storyObject: Story? = null) {
        var foundListDef: ListDefinition? = null
        origins?.forEach { origin ->
            if (origin.containsItemWithName(itemName)) {
                if (foundListDef != null) {
                    throw StoryException(
                        "Could not add the item $itemName to this list because it could come from either ${origin.name} or ${foundListDef!!.name}"
                    )
                }
                foundListDef = origin
            }
        }
        if (foundListDef == null) {
            storyObject?.let { story ->
                val (key, value) = fromString(itemName, story).orderedItems[0]
                this[key] = value
                return
            } ?: throw StoryException(
                "Could not add the item $itemName to this list because it isn't known to any list definitions previously associated with this list."
            )
        } else {
            val item = InkListItem(foundListDef!!.name!!, itemName)
            val itemVal = foundListDef!!.getValueForItem(item) ?: 0
            this[item] = itemVal
        }
    }

    // --- Equality (identical across all three) ---

    override fun equals(other: Any?): Boolean {
        val otherList = other as? InkList ?: return false
        if (otherList.size != size) return false
        return keys.all { it in otherList }
    }

    override fun hashCode(): Int = keys.sumOf { it.hashCode() }

    override fun toString(): String =
        orderedItems.joinToString(", ") { it.key.itemName ?: "" }

    companion object {
        fun fromString(myListItem: String, originStory: Story): InkList {
            if (myListItem.isEmpty()) return InkList()
            val listValue = originStory.listDefinitionsOrigin?.findSingleItemListWithName(myListItem)
            return if (listValue != null) InkList(listValue.value)
            else throw IllegalStateException("Could not find '$myListItem' in list definitions")
        }
    }

    /**
     * Simple Map.Entry implementation — replaces Java's verbose CustomEntry.
     */
    private data class MapEntry(override val key: InkListItem, override val value: Int) : Map.Entry<InkListItem, Int>
}
