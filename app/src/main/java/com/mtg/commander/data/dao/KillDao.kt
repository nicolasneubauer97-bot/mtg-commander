package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.KillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KillDao {
    @Query("SELECT * FROM kills WHERE gameId = :gameId")
    fun getKillsForGame(gameId: Long): Flow<List<KillEntity>>

    @Query("SELECT * FROM kills WHERE gameId = :gameId")
    suspend fun getKillsForGameSync(gameId: Long): List<KillEntity>

    @Query("""
        SELECT COUNT(*) FROM kills
        WHERE killerParticipantId IN (SELECT id FROM game_participants WHERE playerId = :playerId)
        AND gameId IN (SELECT id FROM games WHERE status = 'FINISHED')
    """)
    suspend fun getKillCountForPlayer(playerId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM kills
        WHERE victimParticipantId IN (SELECT id FROM game_participants WHERE playerId = :playerId)
        AND gameId IN (SELECT id FROM games WHERE status = 'FINISHED')
    """)
    suspend fun getDeathCountForPlayer(playerId: Long): Int

    @Insert
    suspend fun insertKill(kill: KillEntity): Long

    @Delete
    suspend fun deleteKill(kill: KillEntity)
}
