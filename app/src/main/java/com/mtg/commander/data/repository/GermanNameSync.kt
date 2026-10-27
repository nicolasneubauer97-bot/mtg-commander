package com.mtg.commander.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Loads the German commander name for every deck that is still missing one
 * (via Scryfall) and persists it. Used both automatically on app start and
 * from a manual "refresh" action, so it must stay side-effect-safe to re-run.
 * Returns the number of decks actually updated.
 */
suspend fun syncGermanCommanderNames(
    deckRepository: DeckRepository,
    preconRepository: PreconRepository
): Int {
    var updated = 0
    val decks = deckRepository.getAllDecks().first()
    val missing = decks.filter { it.commanderNameDe.isBlank() && it.commanderName.isNotBlank() }
    missing.forEach { deck ->
        try {
            val de = preconRepository.fetchGermanNameByCommanderName(deck.commanderName)
            if (de.isNotBlank() && !de.equals(deck.commanderName, ignoreCase = true)) {
                deckRepository.updateDeck(deck.copy(commanderNameDe = de))
                updated++
            }
        } catch (_: Exception) {
            // Einzelnes Deck ignorieren, mit dem naechsten weitermachen
        }
        delay(150) // Rate-Limit fuer Scryfall
    }
    return updated
}
