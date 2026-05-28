package com.mtg.commander.data.repository

import com.mtg.commander.data.dao.DiceRollDao
import com.mtg.commander.data.dao.GameDao
import com.mtg.commander.data.dao.GameParticipantDao
import com.mtg.commander.data.dao.KillDao
import com.mtg.commander.data.dao.LifeChangeEventDao
import com.mtg.commander.data.dao.RandomOpponentPickDao
import com.mtg.commander.data.entity.GameStatus
import com.mtg.commander.domain.model.DeckStats
import com.mtg.commander.domain.model.PlayerStats
import com.mtg.commander.domain.model.RandomOpponentStat

class StatsRepository(
    private val participantDao: GameParticipantDao,
    private val killDao: KillDao,
    private val gameDao: GameDao,
    private val lifeChangeEventDao: LifeChangeEventDao,
    private val randomOpponentPickDao: RandomOpponentPickDao,
    private val diceRollDao: DiceRollDao
) {
    suspend fun getPlayerStats(playerId: Long, playerName: String): PlayerStats {
        val participants = participantDao.getParticipantsForPlayerWithStatus(playerId, GameStatus.FINISHED)
        val gamesPlayed = participants.size
        val wins = participants.count { it.placement == 1 }
        val winRate = if (gamesPlayed > 0) wins.toDouble() / gamesPlayed else 0.0
        val avgPlacement = if (gamesPlayed > 0) participants.mapNotNull { it.placement }.average() else 0.0
        val kills = killDao.getKillCountForPlayerWithStatus(playerId, GameStatus.FINISHED)
        val deaths = killDao.getDeathCountForPlayerWithStatus(playerId, GameStatus.FINISHED)

        // Starter count: games where game.startingParticipantId matches one of this player's participantIds
        val participantIds = participants.map { it.id }.toSet()
        val allFinishedGames = gameDao.getFinishedGamesSync(GameStatus.FINISHED)
        val timesStarter = allFinishedGames.count { it.startingParticipantId in participantIds }

        // Life change stats: aggregate across all finished games this player participated in
        var totalDamageDealtToOthers = 0
        var totalLifeGained = 0
        var totalLifeLost = 0

        for (participant in participants) {
            val events = lifeChangeEventDao.getEventsForGame(participant.gameId)
            for (event in events) {
                // Life gained by this player (any source)
                if (event.targetParticipantId == participant.id && event.delta > 0) {
                    totalLifeGained += event.delta
                }
                // Life lost by this player (any source, including self)
                if (event.targetParticipantId == participant.id && event.delta < 0) {
                    totalLifeLost += (-event.delta)
                }
                // Damage dealt to OTHERS during THIS player's turn
                if (event.activeTurnParticipantId == participant.id
                    && event.targetParticipantId != participant.id
                    && event.delta < 0) {
                    totalDamageDealtToOthers += (-event.delta)
                }
            }
        }

        return PlayerStats(
            playerId = playerId, playerName = playerName,
            gamesPlayed = gamesPlayed, wins = wins, losses = gamesPlayed - wins,
            winRate = winRate, averagePlacement = avgPlacement,
            kills = kills, deaths = deaths,
            timesChosenAsStarter = timesStarter,
            totalDamageDealtToOthers = totalDamageDealtToOthers,
            totalLifeGained = totalLifeGained, totalLifeLost = totalLifeLost
        )
    }

    suspend fun getDeckStats(
        deckId: Long, deckName: String, commanderName: String, playerName: String
    ): DeckStats {
        val participants = participantDao.getParticipantsForDeckWithStatus(deckId, GameStatus.FINISHED)
        val gamesPlayed = participants.size
        val wins = participants.count { it.placement == 1 }
        val winRate = if (gamesPlayed > 0) wins.toDouble() / gamesPlayed else 0.0
        val avgPlacement = if (gamesPlayed > 0) participants.mapNotNull { it.placement }.average() else 0.0
        return DeckStats(
            deckId = deckId, deckName = deckName, commanderName = commanderName,
            playerName = playerName, gamesPlayed = gamesPlayed, wins = wins,
            winRate = winRate, averagePlacement = avgPlacement
        )
    }

    suspend fun getRandomOpponentStats(playerNameById: Map<Long, String>): List<RandomOpponentStat> {
        val aggregates = randomOpponentPickDao.getPickAggregates()
        return aggregates.mapNotNull { agg ->
            val chooserName = playerNameById[agg.chooserPlayerId] ?: return@mapNotNull null
            val chosenName = playerNameById[agg.chosenPlayerId] ?: return@mapNotNull null
            RandomOpponentStat(
                chooserPlayerId = agg.chooserPlayerId,
                chooserName = chooserName,
                chosenPlayerId = agg.chosenPlayerId,
                chosenName = chosenName,
                count = agg.pickCount
            )
        }.sortedByDescending { it.count }
    }

    // Per-game life change summary: Map<targetParticipantId, Pair<totalGained, totalLost>>
    suspend fun getLifeSummaryForGame(gameId: Long): Map<Long, Pair<Int, Int>> {
        val events = lifeChangeEventDao.getEventsForGame(gameId)
        val summary = mutableMapOf<Long, Pair<Int, Int>>()
        for (event in events) {
            val (gained, lost) = summary[event.targetParticipantId] ?: Pair(0, 0)
            summary[event.targetParticipantId] = if (event.delta > 0)
                Pair(gained + event.delta, lost)
            else
                Pair(gained, lost + (-event.delta))
        }
        return summary
    }

    // ─── Global damage: attacker → victim → total across all finished games ──
    suspend fun getGlobalDamageStats(playerNameById: Map<Long, String>): List<Triple<String, String, Int>> {
        val aggregates = lifeChangeEventDao.getGlobalDamageAggregates()
        return aggregates.mapNotNull { agg ->
            val attacker = playerNameById[agg.attackerPlayerId] ?: return@mapNotNull null
            val victim = playerNameById[agg.victimPlayerId] ?: return@mapNotNull null
            Triple(attacker, victim, agg.totalDamage)
        }.sortedByDescending { it.third }
    }

    // ─── Dice roll stats: playerId → value(1-6) → count ──────────────────────
    suspend fun getDiceRollStats(playerNameById: Map<Long, String>): Map<String, Map<Int, Int>> {
        val aggregates = diceRollDao.getAggregates()
        val result = mutableMapOf<String, MutableMap<Int, Int>>()
        for (agg in aggregates) {
            val name = playerNameById[agg.rollerPlayerId] ?: continue
            result.getOrPut(name) { mutableMapOf() }[agg.value] = agg.rollCount
        }
        return result
    }

    // Per-game damage dealt by each active-turn participant to others
    suspend fun getDamageByAttackerForGame(gameId: Long): Map<Long, Map<Long, Int>> {
        val events = lifeChangeEventDao.getEventsForGame(gameId)
        val result = mutableMapOf<Long, MutableMap<Long, Int>>()
        for (event in events) {
            val attacker = event.activeTurnParticipantId ?: continue
            if (event.targetParticipantId == attacker) continue   // skip self-damage
            if (event.delta >= 0) continue                        // skip healing
            val map = result.getOrPut(attacker) { mutableMapOf() }
            map[event.targetParticipantId] = (map[event.targetParticipantId] ?: 0) + (-event.delta)
        }
        return result
    }
}
