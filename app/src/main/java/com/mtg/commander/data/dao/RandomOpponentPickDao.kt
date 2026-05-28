package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.RandomOpponentPickEntity

data class RandomPickAggregate(
    val chooserPlayerId: Long,
    val chosenPlayerId: Long,
    val pickCount: Int
)

@Dao
interface RandomOpponentPickDao {
    @Insert
    suspend fun insert(pick: RandomOpponentPickEntity)

    @Query("SELECT * FROM random_opponent_picks WHERE gameId = :gameId")
    suspend fun getPicksForGame(gameId: Long): List<RandomOpponentPickEntity>

    @Query("""
        SELECT gp1.playerId AS chooserPlayerId, gp2.playerId AS chosenPlayerId, COUNT(*) AS pickCount
        FROM random_opponent_picks rop
        JOIN game_participants gp1 ON rop.chooserParticipantId = gp1.id
        JOIN game_participants gp2 ON rop.chosenParticipantId = gp2.id
        GROUP BY gp1.playerId, gp2.playerId
        ORDER BY COUNT(*) DESC
    """)
    suspend fun getPickAggregates(): List<RandomPickAggregate>

    @Query("DELETE FROM random_opponent_picks")
    suspend fun deleteAll()
}
