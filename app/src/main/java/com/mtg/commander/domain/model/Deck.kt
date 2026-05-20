package com.mtg.commander.domain.model

data class Deck(
    val id: Long = 0,
    val playerId: Long,
    val name: String,
    val commanderName: String,
    val colors: String,
    val createdAt: Long = System.currentTimeMillis()
)
