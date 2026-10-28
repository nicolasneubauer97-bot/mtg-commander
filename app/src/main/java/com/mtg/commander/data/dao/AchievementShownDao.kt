package com.mtg.commander.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mtg.commander.data.entity.AchievementShownEntity

@Dao
interface AchievementShownDao {
    @Query("SELECT * FROM achievement_shown")
    suspend fun getAll(): List<AchievementShownEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AchievementShownEntity)

    @Query("DELETE FROM achievement_shown")
    suspend fun deleteAll()
}
