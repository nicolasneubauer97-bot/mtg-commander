package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.GameParticipantEntity
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
        SELECT * FROM game_participants
        WHERE playerId = :playerId
        AND gameId IN (SELECT id FROM games WHERE status = 'FINISHED')
    """)
    suspend fun getFinishedParticipantsForPlayer(playerId: Long): List<GameParticipantEntity>

    @Query("""
        SELECT * FROM game_participants
        WHERE deckId = :deckId
        AND gameId IN (SELECT id FROM games WHERE status = 'FINISHED')
    """)
    suspend fun getFinishedParticipantsForDeck(deckId: Long): List<GameParticipantEntity>

    @Insert
    suspend fun insertParticipant(participant: GameParticipantEntity): Long

    @Update
    suspend fun updateParticipant(participant: GameParticipantEntity)

    @Delete
    suspend fun deleteParticipant(participant: GameParticipantEntity)
}
