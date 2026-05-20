package com.mtg.commander.domain.model

data class DeckStats(
    val deckId: Long,
    val deckName: String,
    val commanderName: String,
    val playerName: String,
    val gamesPlayed: Int,
    val wins: Int,
    val winRate: Double,
    val averagePlacement: Double
)
