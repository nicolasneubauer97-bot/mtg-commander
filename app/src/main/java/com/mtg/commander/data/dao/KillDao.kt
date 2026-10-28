package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.GameStatus
import com.mtg.commander.data.entity.KillEntity
import kotlinx.coroutines.flow.Flow

data class OpponentKillCount(
    val opponentPlayerId: Long,
    val killCount: Int
)

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

    @Query("""
        SELECT COUNT(*) FROM kills k
        INNER JOIN game_participants killerGp ON k.killerParticipantId = killerGp.id
        INNER JOIN game_participants victimGp ON k.victimParticipantId = victimGp.id
        INNER JOIN games g ON k.gameId = g.id
        WHERE killerGp.playerId = :killerPlayerId AND victimGp.playerId = :victimPlayerId
          AND g.status = 'FINISHED'
    """)
    suspend fun countKillsBetweenPlayers(killerPlayerId: Long, victimPlayerId: Long): Int

    // ─── Achievements: ueber ALLE Spiele (auch laufende), damit Fortschritt live sichtbar ist ──

    @Query("""
        SELECT COUNT(*) FROM kills k
        INNER JOIN game_participants gp ON k.killerParticipantId = gp.id
        WHERE gp.playerId = :playerId
    """)
    suspend fun countAllKillsForPlayer(playerId: Long): Int

    @Query("""
        SELECT victimGp.playerId AS opponentPlayerId, COUNT(*) AS killCount
        FROM kills k
        INNER JOIN game_participants killerGp ON k.killerParticipantId = killerGp.id
        INNER JOIN game_participants victimGp ON k.victimParticipantId = victimGp.id
        WHERE killerGp.playerId = :playerId
        GROUP BY victimGp.playerId
    """)
    suspend fun getKillCountsPerOpponent(playerId: Long): List<OpponentKillCount>

    @Insert
    suspend fun insertKill(kill: KillEntity): Long

    @Delete
    suspend fun deleteKill(kill: KillEntity)

    @Query("DELETE FROM kills")
    suspend fun deleteAllKills()
}
