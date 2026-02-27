package ink.kt

/**
 * Container for all LIST definitions in a story. Resolves list items by name.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.ListDefinitionsOrigin` — same
 * - Java: `ListDefinitionsOrigin` — same
 * - JS: `ListDefinitionsOrigin` — same
 *
 * Kotlin: Same structure. `tryListGetDefinition()` API for name-based lookup.
 */
class ListDefinitionsOrigin(listDefs: List<ListDefinition>) {

    private val _lists: Map<String, ListDefinition>
    private val _allUnambiguousListValueCache: Map<String, ListValue>

    val lists: List<ListDefinition> get() = _lists.values.toList()

    init {
        val listsMap = HashMap<String, ListDefinition>()
        val cacheMap = HashMap<String, ListValue>()

        for (list in listDefs) {
            listsMap[list.name!!] = list
            for ((item, value) in list.items) {
                val listValue = ListValue(item, value)
                // Both short name and full name for lookup
                cacheMap[item.itemName!!] = listValue
                cacheMap[item.fullName] = listValue
            }
        }

        _lists = listsMap
        _allUnambiguousListValueCache = cacheMap
    }

    fun getListDefinition(name: String): ListDefinition? = _lists[name]

    fun findSingleItemListWithName(name: String): ListValue? {
        if (name.isBlank()) return null
        return _allUnambiguousListValueCache[name]
    }
}
