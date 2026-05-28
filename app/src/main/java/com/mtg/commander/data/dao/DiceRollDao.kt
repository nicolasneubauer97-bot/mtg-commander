package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.DiceRollEntity

data class DiceRollAggregate(
    val rollerPlayerId: Long,
    val value: Int,
    val rollCount: Int
)

@Dao
interface DiceRollDao {
    @Insert
    suspend fun insert(roll: DiceRollEntity)

    @Query("""
        SELECT gp.playerId AS rollerPlayerId, dr.value AS value, COUNT(*) AS rollCount
        FROM dice_rolls dr
        JOIN game_participants gp ON dr.rollerParticipantId = gp.id
        GROUP BY gp.playerId, dr.value
        ORDER BY gp.playerId, dr.value
    """)
    suspend fun getAggregates(): List<DiceRollAggregate>

    @Query("DELETE FROM dice_rolls")
    suspend fun deleteAll()
}
