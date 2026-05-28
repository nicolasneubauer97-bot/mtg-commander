package com.mtg.commander.data.repository

import com.mtg.commander.data.dao.DeckDao
import com.mtg.commander.data.entity.DeckEntity
import com.mtg.commander.domain.model.Deck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeckRepository(private val deckDao: DeckDao) {

    fun getAllDecks(): Flow<List<Deck>> =
        deckDao.getAllDecks().map { it.map(DeckEntity::toDomain) }

    fun getDecksByPlayer(playerId: Long): Flow<List<Deck>> =
        deckDao.getDecksByPlayer(playerId).map { it.map(DeckEntity::toDomain) }

    suspend fun getDeckById(id: Long): Deck? =
        deckDao.getDeckById(id)?.toDomain()

    suspend fun getDecksByPlayerSync(playerId: Long): List<Deck> =
        deckDao.getDecksByPlayerSync(playerId).map(DeckEntity::toDomain)

    suspend fun insertDeck(deck: Deck): Long =
        deckDao.insertDeck(deck.toEntity())

    suspend fun updateDeck(deck: Deck) =
        deckDao.updateDeck(deck.toEntity())

    suspend fun deleteDeck(deck: Deck) =
        deckDao.deleteDeck(deck.toEntity())
}

private fun DeckEntity.toDomain() = Deck(
    id = id, playerId = playerId, name = name,
    commanderName = commanderName, colors = colors,
    imageUrl = imageUrl, createdAt = createdAt
)

private fun Deck.toEntity() = DeckEntity(
    id = id, playerId = playerId, name = name,
    commanderName = commanderName, colors = colors,
    imageUrl = imageUrl, createdAt = createdAt
)
