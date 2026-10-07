package com.wilhelmbeiche.roommemory

import com.wilhelmbeiche.roommemory.data.ItemEntity
import com.wilhelmbeiche.roommemory.data.ItemSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemSearchTest {
    private val cable = ItemEntity(
        title = "Ladekabel",
        room = "Arbeitszimmer",
        furniture = "Schreibtisch",
        compartment = "Obere Schublade",
        description = "rot, USB-C"
    )
    private val apples = ItemEntity(title = "Äpfel", room = "Küche", furniture = "Regal", compartment = "")
    private val percent = ItemEntity(title = "Rabatt 50%", room = "Flur", furniture = "", compartment = "")
    private val all = listOf(cable, apples, percent)

    private fun search(query: String) = ItemSearch.filter(all, query)

    @Test
    fun blankQueryReturnsEverything() {
        assertEquals(all, search("   "))
    }

    @Test
    fun ignoresCaseOfUmlauts() {
        assertEquals(listOf(apples), search("äpfel"))
        assertEquals(listOf(apples), search("KÜCHE"))
    }

    @Test
    fun everyWordMustMatchInAnyField() {
        assertEquals(listOf(cable), search("kabel rot"))
        assertEquals(listOf(cable), search("  schublade   kabel "))
        assertEquals(emptyList<ItemEntity>(), search("kabel küche"))
    }

    @Test
    fun wildcardCharactersAreLiteral() {
        assertEquals(listOf(percent), search("%"))
        assertEquals(emptyList<ItemEntity>(), search("_"))
    }
}
