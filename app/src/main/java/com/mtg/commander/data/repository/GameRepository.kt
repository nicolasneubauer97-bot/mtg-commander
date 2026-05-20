package com.mtg.commander.data.repository

import com.mtg.commander.data.dao.CommanderDamageDao
import com.mtg.commander.data.dao.GameDao
import com.mtg.commander.data.dao.GameParticipantDao
import com.mtg.commander.data.dao.KillDao
import com.mtg.commander.data.entity.*
import com.mtg.commander.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(
    private val gameDao: GameDao,
    private val participantDao: GameParticipantDao,
    private val commanderDamageDao: CommanderDamageDao,
    private val killDao: KillDao
) {
    fun getAllGames(): Flow<List<Game>> =
        gameDao.getAllGames().map { it.map(GameEntity::toDomain) }

    fun getFinishedGames(): Flow<List<Game>> =
        gameDao.getGamesByStatus(GameStatus.FINISHED).map { it.map(GameEntity::toDomain) }

    fun getActiveGames(): Flow<List<Game>> =
        gameDao.getGamesByStatus(GameStatus.IN_PROGRESS).map { it.map(GameEntity::toDomain) }

    suspend fun getGameById(id: Long): Game? = gameDao.getGameById(id)?.toDomain()

    suspend fun getLatestActiveGame(): Game? =
        gameDao.getLatestActiveGame(GameStatus.IN_PROGRESS)?.toDomain()

    suspend fun createGame(playerDeckPairs: List<Pair<Long, Long?>>): Long {
        val gameId = gameDao.insertGame(GameEntity())
        playerDeckPairs.forEach { (playerId, deckId) ->
            participantDao.insertParticipant(
                GameParticipantEntity(gameId = gameId, playerId = playerId, deckId = deckId)
            )
        }
        return gameId
    }

    fun getParticipantsForGame(gameId: Long): Flow<List<GameParticipant>> =
        participantDao.getParticipantsForGame(gameId).map { it.map(GameParticipantEntity::toDomain) }

    suspend fun getParticipantsForGameSync(gameId: Long): List<GameParticipant> =
        participantDao.getParticipantsForGameSync(gameId).map(GameParticipantEntity::toDomain)

    suspend fun updateParticipant(participant: GameParticipant) =
        participantDao.updateParticipant(participant.toEntity())

    suspend fun updateGame(game: Game) = gameDao.updateGame(game.toEntity())

    suspend fun deleteGame(game: Game) = gameDao.deleteGame(game.toEntity())

    suspend fun deleteAllFinishedGames() =
        gameDao.deleteGamesByStatus(GameStatus.FINISHED)

    suspend fun deleteAllGames() = gameDao.deleteAllGames()

    suspend fun deleteAllKills() = killDao.deleteAllKills()

    suspend fun deleteAllCommanderDamage() = commanderDamageDao.deleteAllCommanderDamage()

    fun getCommanderDamageForGame(gameId: Long): Flow<List<CommanderDamage>> =
        commanderDamageDao.getCommanderDamageForGame(gameId).map { it.map(CommanderDamageEntity::toDomain) }

    suspend fun getCommanderDamageForGameSync(gameId: Long): List<CommanderDamage> =
        commanderDamageDao.getCommanderDamageForGameSync(gameId).map(CommanderDamageEntity::toDomain)

    suspend fun updateCommanderDamage(gameId: Long, attackerId: Long, targetId: Long, delta: Int) {
        val existing = commanderDamageDao.getCommanderDamage(gameId, attackerId, targetId)
        if (existing == null) {
            if (delta > 0) {
                commanderDamageDao.insertCommanderDamage(
                    CommanderDamageEntity(
                        gameId = gameId,
                        attackerParticipantId = attackerId,
                        targetParticipantId = targetId,
                        damage = delta
                    )
                )
            }
        } else {
            commanderDamageDao.updateCommanderDamage(
                existing.copy(damage = maxOf(0, existing.damage + delta))
            )
        }
    }

    suspend fun insertKill(kill: Kill): Long = killDao.insertKill(kill.toEntity())

    fun getKillsForGame(gameId: Long): Flow<List<Kill>> =
        killDao.getKillsForGame(gameId).map { it.map(KillEntity::toDomain) }

    suspend fun getKillsForGameSync(gameId: Long): List<Kill> =
        killDao.getKillsForGameSync(gameId).map(KillEntity::toDomain)
}

private fun GameEntity.toDomain() = Game(
    id = id, startedAt = startedAt, endedAt = endedAt, status = status.name
)

private fun Game.toEntity() = GameEntity(
    id = id, startedAt = startedAt, endedAt = endedAt, status = GameStatus.valueOf(status)
)

private fun GameParticipantEntity.toDomain() = GameParticipant(
    id = id, gameId = gameId, playerId = playerId, deckId = deckId,
    startingLife = startingLife, currentLife = currentLife,
    placement = placement, isEliminated = isEliminated, eliminatedAt = eliminatedAt
)

private fun GameParticipant.toEntity() = GameParticipantEntity(
    id = id, gameId = gameId, playerId = playerId, deckId = deckId,
    startingLife = startingLife, currentLife = currentLife,
    placement = placement, isEliminated = isEliminated, eliminatedAt = eliminatedAt
)


private fun CommanderDamageEntity.toDomain() = CommanderDamage(
    id = id, gameId = gameId,
    attackerParticipantId = attackerParticipantId,
    targetParticipantId = targetParticipantId, damage = damage
)

private fun Kill.toEntity() = KillEntity(
    id = id, gameId = gameId,
    killerParticipantId = killerParticipantId,
    victimParticipantId = victimParticipantId, createdAt = createdAt
)

private fun KillEntity.toDomain() = Kill(
    id = id, gameId = gameId,
    killerParticipantId = killerParticipantId,
    victimParticipantId = victimParticipantId, createdAt = createdAt
)
