package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.LifeChangeEventEntity

@Dao
interface LifeChangeEventDao {
    @Insert
    suspend fun insert(event: LifeChangeEventEntity)

    @Query("SELECT * FROM life_change_events WHERE gameId = :gameId ORDER BY turnNumber, createdAt")
    suspend fun getEventsForGame(gameId: Long): List<LifeChangeEventEntity>

    @Query("DELETE FROM life_change_events WHERE gameId IN (SELECT id FROM games WHERE status = 'FINISHED')")
    suspend fun deleteAllForFinishedGames()

    @Query("DELETE FROM life_change_events")
    suspend fun deleteAll()
}
