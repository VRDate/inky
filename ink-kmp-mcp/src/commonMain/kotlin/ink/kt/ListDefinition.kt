package ink.kt

/**
 * Defines a list type in ink — the set of possible items and their integer values.
 *
 * All three implementations (C#, Java, JS) are identical in structure:
 * - Stores name→value mapping for quick lookup by item name
 * - Lazily builds full InkListItem→value mapping when needed
 *
 * Kotlin improvements:
 * - Lazy property for items (instead of null-check pattern in Java/C#)
 * - Direct map operations instead of Entry iteration
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
