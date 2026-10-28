package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.LifeChangeEventEntity

data class GlobalDamageAggregate(
    val attackerPlayerId: Long,
    val victimPlayerId: Long,
    val totalDamage: Int
)

@Dao
interface LifeChangeEventDao {
    @Insert
    suspend fun insert(event: LifeChangeEventEntity)

    @Query("SELECT * FROM life_change_events WHERE gameId = :gameId ORDER BY turnNumber, createdAt")
    suspend fun getEventsForGame(gameId: Long): List<LifeChangeEventEntity>

    @Query("""
        SELECT
            gp1.playerId AS attackerPlayerId,
            gp2.playerId AS victimPlayerId,
            SUM(ABS(lce.delta)) AS totalDamage
        FROM life_change_events lce
        JOIN game_participants gp1 ON lce.activeTurnParticipantId = gp1.id
        JOIN game_participants gp2 ON lce.targetParticipantId = gp2.id
        JOIN games g ON lce.gameId = g.id
        WHERE g.status = 'FINISHED'
        AND lce.delta < 0
        AND gp1.id != gp2.id
        GROUP BY gp1.playerId, gp2.playerId
        ORDER BY SUM(ABS(lce.delta)) DESC
    """)
    suspend fun getGlobalDamageAggregates(): List<GlobalDamageAggregate>

    @Query("DELETE FROM life_change_events")
    suspend fun deleteAll()

    // ─── Achievements: ueber ALLE Spiele (auch laufende), damit Fortschritt live sichtbar ist ──

    @Query("""
        SELECT COALESCE(SUM(lce.delta), 0) FROM life_change_events lce
        INNER JOIN game_participants gp ON lce.targetParticipantId = gp.id
        WHERE gp.playerId = :playerId AND lce.delta > 0
    """)
    suspend fun sumLifeGainedForPlayer(playerId: Long): Int

    @Query("""
        SELECT COALESCE(SUM(-lce.delta), 0) FROM life_change_events lce
        INNER JOIN game_participants gp ON lce.activeTurnParticipantId = gp.id
        WHERE gp.playerId = :playerId AND lce.targetParticipantId != lce.activeTurnParticipantId AND lce.delta < 0
    """)
    suspend fun sumDamageDealtByPlayer(playerId: Long): Int
}
