package com.mtg.commander.data.repository

import com.mtg.commander.data.dao.GameParticipantDao
import com.mtg.commander.data.dao.KillDao
import com.mtg.commander.data.entity.GameStatus
import com.mtg.commander.domain.model.DeckStats
import com.mtg.commander.domain.model.PlayerStats

class StatsRepository(
    private val participantDao: GameParticipantDao,
    private val killDao: KillDao
) {
    suspend fun getPlayerStats(playerId: Long, playerName: String): PlayerStats {
        val participants = participantDao.getParticipantsForPlayerWithStatus(playerId, GameStatus.FINISHED)
        val gamesPlayed = participants.size
        val wins = participants.count { it.placement == 1 }
        val winRate = if (gamesPlayed > 0) wins.toDouble() / gamesPlayed else 0.0
        val avgPlacement = if (gamesPlayed > 0) participants.mapNotNull { it.placement }.average() else 0.0
        val kills = killDao.getKillCountForPlayerWithStatus(playerId, GameStatus.FINISHED)
        val deaths = killDao.getDeathCountForPlayerWithStatus(playerId, GameStatus.FINISHED)
        return PlayerStats(
            playerId = playerId,
            playerName = playerName,
            gamesPlayed = gamesPlayed,
            wins = wins,
            losses = gamesPlayed - wins,
            winRate = winRate,
            averagePlacement = avgPlacement,
            kills = kills,
            deaths = deaths
        )
    }

    suspend fun getDeckStats(
        deckId: Long,
        deckName: String,
        commanderName: String,
        playerName: String
    ): DeckStats {
        val participants = participantDao.getParticipantsForDeckWithStatus(deckId, GameStatus.FINISHED)
        val gamesPlayed = participants.size
        val wins = participants.count { it.placement == 1 }
        val winRate = if (gamesPlayed > 0) wins.toDouble() / gamesPlayed else 0.0
        val avgPlacement = if (gamesPlayed > 0) participants.mapNotNull { it.placement }.average() else 0.0
        return DeckStats(
            deckId = deckId,
            deckName = deckName,
            commanderName = commanderName,
            playerName = playerName,
            gamesPlayed = gamesPlayed,
            wins = wins,
            winRate = winRate,
            averagePlacement = avgPlacement
        )
    }
}
