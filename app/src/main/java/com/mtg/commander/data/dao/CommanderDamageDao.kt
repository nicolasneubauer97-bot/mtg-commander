package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.CommanderDamageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommanderDamageDao {
    @Query("SELECT * FROM commander_damage WHERE gameId = :gameId")
    fun getCommanderDamageForGame(gameId: Long): Flow<List<CommanderDamageEntity>>

    @Query("SELECT * FROM commander_damage WHERE gameId = :gameId")
    suspend fun getCommanderDamageForGameSync(gameId: Long): List<CommanderDamageEntity>

    @Query("""
        SELECT * FROM commander_damage
        WHERE attackerParticipantId = :attackerId
        AND targetParticipantId = :targetId
        AND gameId = :gameId
    """)
    suspend fun getCommanderDamage(gameId: Long, attackerId: Long, targetId: Long): CommanderDamageEntity?

    @Insert
    suspend fun insertCommanderDamage(damage: CommanderDamageEntity): Long

    @Update
    suspend fun updateCommanderDamage(damage: CommanderDamageEntity)

    @Query("DELETE FROM commander_damage")
    suspend fun deleteAllCommanderDamage()

    // Achievements: ueber ALLE Spiele (auch laufende), damit Fortschritt live sichtbar ist
    @Query("""
        SELECT COALESCE(SUM(cd.damage), 0) FROM commander_damage cd
        INNER JOIN game_participants gp ON cd.attackerParticipantId = gp.id
        WHERE gp.playerId = :playerId
    """)
    suspend fun sumCommanderDamageForPlayer(playerId: Long): Int
}
