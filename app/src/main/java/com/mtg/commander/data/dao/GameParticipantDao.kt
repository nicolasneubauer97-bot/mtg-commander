package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.GameParticipantEntity
import com.mtg.commander.data.entity.GameStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GameParticipantDao {
    @Query("SELECT * FROM game_participants WHERE gameId = :gameId")
    fun getParticipantsForGame(gameId: Long): Flow<List<GameParticipantEntity>>

    @Query("SELECT * FROM game_participants WHERE gameId = :gameId")
    suspend fun getParticipantsForGameSync(gameId: Long): List<GameParticipantEntity>

    @Query("SELECT * FROM game_participants WHERE id = :id")
    suspend fun getParticipantById(id: Long): GameParticipantEntity?

    @Query("""
        SELECT gp.* FROM game_participants gp
        INNER JOIN games g ON gp.gameId = g.id
        WHERE gp.playerId = :playerId AND g.status = :status
    """)
    suspend fun getParticipantsForPlayerWithStatus(
        playerId: Long,
        status: GameStatus
    ): List<GameParticipantEntity>

    @Query("""
        SELECT gp.* FROM game_participants gp
        INNER JOIN games g ON gp.gameId = g.id
        WHERE gp.deckId = :deckId AND g.status = :status
    """)
    suspend fun getParticipantsForDeckWithStatus(
        deckId: Long,
        status: GameStatus
    ): List<GameParticipantEntity>

    @Query("""
        SELECT COUNT(*) FROM game_participants gp
        INNER JOIN games g ON gp.gameId = g.id
        WHERE gp.deckId = :deckId AND g.status = 'FINISHED'
    """)
    suspend fun countFinishedGamesForDeck(deckId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM game_participants gp
        INNER JOIN games g ON gp.gameId = g.id
        WHERE gp.deckId = :deckId AND g.status = 'FINISHED' AND gp.placement = 1
    """)
    suspend fun countWinsForDeck(deckId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM game_participants gp
        INNER JOIN games g ON gp.gameId = g.id
        WHERE gp.playerId = :playerId AND g.status = 'FINISHED' AND gp.placement = 1
    """)
    suspend fun countWinsForPlayer(playerId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM game_participants gp
        INNER JOIN games g ON gp.gameId = g.id
        INNER JOIN decks d ON gp.deckId = d.id
        WHERE gp.playerId = :playerId AND d.commanderName = :commanderName
          AND g.status = 'FINISHED' AND gp.placement = 1
    """)
    suspend fun countWinsForPlayerWithCommander(playerId: Long, commanderName: String): Int

    // ─── Achievements: ueber ALLE Spiele (auch laufende), damit Fortschritt live sichtbar ist ──

    @Query("SELECT COUNT(*) FROM game_participants WHERE playerId = :playerId")
    suspend fun countAllGamesForPlayer(playerId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM games g
        INNER JOIN game_participants gp ON g.startingParticipantId = gp.id
        WHERE gp.playerId = :playerId
    """)
    suspend fun countStarterGamesForPlayer(playerId: Long): Int

    @Query("SELECT COUNT(*) FROM game_participants WHERE playerId = :playerId AND placement = 1 AND currentLife >= 40")
    suspend fun countImmortalWins(playerId: Long): Int

    @Insert
    suspend fun insertParticipant(participant: GameParticipantEntity): Long

    @Update
    suspend fun updateParticipant(participant: GameParticipantEntity)

    @Delete
    suspend fun deleteParticipant(participant: GameParticipantEntity)
}
