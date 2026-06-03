package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.TurnDurationEntity

@Dao
interface TurnDurationDao {
    @Insert
    suspend fun insert(entity: TurnDurationEntity): Long

    @Query("SELECT * FROM turn_durations WHERE gameId = :gameId ORDER BY turnNumber ASC")
    suspend fun getForGame(gameId: Long): List<TurnDurationEntity>

    @Query("""
        SELECT AVG(durationMs) FROM turn_durations
        WHERE gameId IN (SELECT id FROM games WHERE status = 'FINISHED')
    """)
    suspend fun globalAverageDurationMs(): Double?

    @Query("""
        SELECT AVG(durationMs) FROM turn_durations
        WHERE participantId IN (
            SELECT id FROM game_participants WHERE playerId = :playerId
        ) AND gameId IN (SELECT id FROM games WHERE status = 'FINISHED')
    """)
    suspend fun averageDurationMsForPlayer(playerId: Long): Double?

    @Query("DELETE FROM turn_durations")
    suspend fun deleteAll()
}
