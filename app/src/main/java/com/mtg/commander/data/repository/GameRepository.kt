package com.mtg.commander.data.repository

import com.mtg.commander.data.dao.*
import com.mtg.commander.data.entity.DiceRollEntity
import com.mtg.commander.data.entity.*
import com.mtg.commander.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(
    private val gameDao: GameDao,
    private val participantDao: GameParticipantDao,
    private val commanderDamageDao: CommanderDamageDao,
    private val killDao: KillDao,
    private val lifeChangeEventDao: LifeChangeEventDao,
    private val randomOpponentPickDao: RandomOpponentPickDao,
    private val diceRollDao: DiceRollDao
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

    // ─── Life Change Events ───────────────────────────────────────────────────

    suspend fun logLifeChange(
        gameId: Long, targetParticipantId: Long,
        activeTurnParticipantId: Long?, delta: Int, turnNumber: Int
    ) {
        lifeChangeEventDao.insert(
            LifeChangeEventEntity(
                gameId = gameId, targetParticipantId = targetParticipantId,
                activeTurnParticipantId = activeTurnParticipantId,
                delta = delta, turnNumber = turnNumber
            )
        )
    }

    suspend fun getLifeEventsForGame(gameId: Long): List<LifeChangeEventEntity> =
        lifeChangeEventDao.getEventsForGame(gameId)

    // ─── Random Opponent Picks ────────────────────────────────────────────────

    suspend fun logRandomOpponentPick(gameId: Long, chooserParticipantId: Long, chosenParticipantId: Long) {
        randomOpponentPickDao.insert(
            RandomOpponentPickEntity(
                gameId = gameId,
                chooserParticipantId = chooserParticipantId,
                chosenParticipantId = chosenParticipantId
            )
        )
    }

    suspend fun getRandomPickAggregates() = randomOpponentPickDao.getPickAggregates()

    // ─── Turn Tracking ────────────────────────────────────────────────────────

    suspend fun setStartingParticipant(gameId: Long, participantId: Long) {
        val game = gameDao.getGameById(gameId) ?: return
        gameDao.updateGame(game.copy(
            startingParticipantId = participantId,
            currentTurnParticipantId = participantId
        ))
    }

    suspend fun createGameWithDirection(playerDeckPairs: List<Pair<Long, Long?>>, clockwise: Boolean): Long {
        val gameId = gameDao.insertGame(GameEntity(turnClockwise = clockwise))
        playerDeckPairs.forEach { (playerId, deckId) ->
            participantDao.insertParticipant(
                GameParticipantEntity(gameId = gameId, playerId = playerId, deckId = deckId)
            )
        }
        return gameId
    }

    suspend fun setCurrentTurnParticipant(gameId: Long, participantId: Long) {
        val game = gameDao.getGameById(gameId) ?: return
        gameDao.updateGame(game.copy(currentTurnParticipantId = participantId))
    }

    suspend fun logDiceRoll(gameId: Long, participantId: Long, value: Int) {
        diceRollDao.insert(
            com.mtg.commander.data.entity.DiceRollEntity(
                gameId = gameId, rollerParticipantId = participantId, value = value
            )
        )
    }

    suspend fun deleteAllLifeChangeEvents() = lifeChangeEventDao.deleteAll()

    suspend fun deleteAllRandomOpponentPicks() = randomOpponentPickDao.deleteAll()

    suspend fun deleteAllDiceRolls() = diceRollDao.deleteAll()
}

private fun GameEntity.toDomain() = Game(
    id = id, startedAt = startedAt, endedAt = endedAt, status = status.name,
    startingParticipantId = startingParticipantId,
    currentTurnParticipantId = currentTurnParticipantId,
    turnClockwise = turnClockwise
)

private fun Game.toEntity() = GameEntity(
    id = id, startedAt = startedAt, endedAt = endedAt, status = GameStatus.valueOf(status),
    startingParticipantId = startingParticipantId,
    currentTurnParticipantId = currentTurnParticipantId,
    turnClockwise = turnClockwise
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
