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
    val deaths: Int,
    // Turn-based stats
    val timesChosenAsStarter: Int = 0,
    val totalDamageDealtToOthers: Int = 0,  // damage to opponents during own turn
    val totalLifeGained: Int = 0,
    val totalLifeLost: Int = 0
) {
    val netLifeChange: Int get() = totalLifeGained - totalLifeLost
}
