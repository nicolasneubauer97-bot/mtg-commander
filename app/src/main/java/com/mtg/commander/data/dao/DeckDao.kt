package com.mtg.commander.data.dao

import androidx.room.*
import com.mtg.commander.data.entity.DeckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY name ASC")
    fun getAllDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE playerId = :playerId ORDER BY name ASC")
    fun getDecksByPlayer(playerId: Long): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: Long): DeckEntity?

    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Delete
    suspend fun deleteDeck(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE playerId = :playerId ORDER BY name ASC")
    suspend fun getDecksByPlayerSync(playerId: Long): List<DeckEntity>
}
