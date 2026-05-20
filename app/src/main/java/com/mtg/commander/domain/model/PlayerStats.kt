package com.mtg.commander.domain.model

data class PlayerStats(
    val playerId: Long,
    val playerName: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val averagePlacement: Double,
    val kills: Int,
    val deaths: Int
)
