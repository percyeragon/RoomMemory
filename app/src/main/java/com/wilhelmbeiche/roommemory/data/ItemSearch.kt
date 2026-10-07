package com.wilhelmbeiche.roommemory.data

/**
 * Search in Kotlin rather than SQL: SQLite's LIKE ignores case only for ASCII,
 * so "äpfel" would not find "Äpfel".
 */
object ItemSearch {
    /** The words of [query]; each one has to match for an item to be found. */
    fun terms(query: String): List<String> =
        query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** True if every term appears in one of the item's text fields, in any order. */
    fun matches(item: ItemEntity, terms: List<String>): Boolean {
        val fields = listOf(item.title, item.room, item.furniture, item.compartment, item.description)
        return terms.all { term -> fields.any { it.contains(term, ignoreCase = true) } }
    }

    fun filter(items: List<ItemEntity>, query: String): List<ItemEntity> {
        val terms = terms(query)
        if (terms.isEmpty()) return items
        return items.filter { matches(it, terms) }
    }
}
