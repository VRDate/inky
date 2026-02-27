package ink.kt

/**
 * Definition of a single ink LIST type with its named items and integer values.
 *
 * Three-way comparison:
 * - C#: `Ink.Runtime.ListDefinition` — Dictionary<InkListItem, int>
 * - Java: `ListDefinition` — HashMap<InkListItem, Integer>
 * - JS: `ListDefinition` — same
 *
 * Kotlin improvements:
 * - **`LinkedHashMap`** preserves insertion order
 * - **`itemForValue()` / `valueForItem()`** — same API as Java/C#
 */
class ListDefinition(
    val name: String?,
    private val itemNameToValues: Map<String, Int>
) {
    val items: Map<InkListItem, Int> by lazy {
        itemNameToValues.map { (itemName, value) ->
            InkListItem(name, itemName) to value
        }.toMap()
    }

    fun getValueForItem(item: InkListItem): Int? =
        itemNameToValues[item.itemName]

    fun containsItem(item: InkListItem): Boolean =
        item.originName == name && item.itemName in itemNameToValues

    fun containsItemWithName(itemName: String): Boolean =
        itemName in itemNameToValues

    fun getItemWithValue(value: Int): InkListItem? {
        val entry = itemNameToValues.entries.firstOrNull { it.value == value }
        return entry?.let { InkListItem(name, it.key) }
    }
}
