package com.bladecoder.ink.runtime

/**
 * Holds all list definitions in a story. Provides lookup by name and
 * an unambiguous item cache for quick resolution.
 *
 * Identical across C#/Java/JS — straightforward port.
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
