package com.wilhelmbeiche.roommemory.data

data class ItemEntity(
    val id: Long = 0L,
    val title: String,
    val room: String,
    val furniture: String,
    val compartment: String,
    val description: String = "",
    val imageUri: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
