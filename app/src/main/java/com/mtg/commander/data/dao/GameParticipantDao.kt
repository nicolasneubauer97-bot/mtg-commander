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

    @Insert
    suspend fun insertParticipant(participant: GameParticipantEntity): Long

    @Update
    suspend fun updateParticipant(participant: GameParticipantEntity)

    @Delete
    suspend fun deleteParticipant(participant: GameParticipantEntity)
}
