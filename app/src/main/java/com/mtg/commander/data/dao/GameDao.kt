package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.GameEntity
import com.mtg.commander.data.entity.GameStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY startedAt DESC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGameById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestActiveGame(status: GameStatus): GameEntity?

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Delete
    suspend fun deleteGame(game: GameEntity)

    @Query("SELECT * FROM games WHERE status = :status ORDER BY startedAt DESC")
    fun getGamesByStatus(status: GameStatus): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeGameById(id: Long): Flow<GameEntity?>

    @Query("DELETE FROM games WHERE status = :status")
    suspend fun deleteGamesByStatus(status: GameStatus)

    @Query("SELECT * FROM games WHERE status = :status ORDER BY startedAt DESC")
    suspend fun getFinishedGamesSync(status: GameStatus): List<GameEntity>

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()
}
