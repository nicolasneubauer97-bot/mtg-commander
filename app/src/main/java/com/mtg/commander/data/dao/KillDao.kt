package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.GameStatus
import com.mtg.commander.data.entity.KillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KillDao {
    @Query("SELECT * FROM kills WHERE gameId = :gameId")
    fun getKillsForGame(gameId: Long): Flow<List<KillEntity>>

    @Query("SELECT * FROM kills WHERE gameId = :gameId")
    suspend fun getKillsForGameSync(gameId: Long): List<KillEntity>

    @Query("""
        SELECT COUNT(*) FROM kills k
        INNER JOIN game_participants gp ON k.killerParticipantId = gp.id
        INNER JOIN games g ON k.gameId = g.id
        WHERE gp.playerId = :playerId AND g.status = :status
    """)
    suspend fun getKillCountForPlayerWithStatus(playerId: Long, status: GameStatus): Int

    @Query("""
        SELECT COUNT(*) FROM kills k
        INNER JOIN game_participants gp ON k.victimParticipantId = gp.id
        INNER JOIN games g ON k.gameId = g.id
        WHERE gp.playerId = :playerId AND g.status = :status
    """)
    suspend fun getDeathCountForPlayerWithStatus(playerId: Long, status: GameStatus): Int

    @Insert
    suspend fun insertKill(kill: KillEntity): Long

    @Delete
    suspend fun deleteKill(kill: KillEntity)

    @Query("DELETE FROM kills")
    suspend fun deleteAllKills()
}
