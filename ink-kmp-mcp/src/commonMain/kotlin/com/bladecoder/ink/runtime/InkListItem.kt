package com.bladecoder.ink.runtime

/**
 * The underlying type for a list item in ink. Stores the original list
 * definition name and item name, without the value.
 *
 * Compared all three:
 * - C#: struct (value type) with readonly fields — Kotlin data class is the perfect equivalent
 * - Java: regular class with getters — verbose
 * - JS: class with properties
 *
 * Kotlin data class gives us: equals, hashCode, copy, destructuring — all for free.
 * C# struct semantics map perfectly to Kotlin data class (value equality, immutable-by-convention).
 */
data class InkListItem(
    val originName: String? = null,
    val itemName: String? = null
) {
    /**
     * Create from dot-separated full name "listDefinitionName.listItemName".
     * All three implementations share this constructor pattern.
     */
    constructor(fullName: String) : this(
        originName = fullName.substringBefore('.'),
        itemName = fullName.substringAfter('.')
    )

    val isNull: Boolean get() = originName == null && itemName == null

    val fullName: String get() = "${originName ?: "?"}.$itemName"

    override fun toString(): String = fullName

    // Custom hashCode matching C#/Java: originCode + itemCode
    // (data class default would be different order)
    override fun hashCode(): Int {
        val originCode = originName?.hashCode() ?: 0
        val itemCode = itemName?.hashCode() ?: 0
        return originCode + itemCode
    }

    companion object {
        val Null = InkListItem(null, null)
    }
}
