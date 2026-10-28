package com.mtg.commander.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

data class GermanNameSyncResult(
    val attempted: Int,
    val updated: Int,
    val errors: Int,
    val failedNames: List<String>
)

/**
 * Loads the German commander name for every deck that is still missing one
 * (via Scryfall) and persists it. Used both automatically on app start and
 * from a manual "refresh" action, so it must stay side-effect-safe to re-run.
 * Reports attempted/updated/errors separately so a silent network failure
 * doesn't look identical to "nothing needed updating", and names a few of the
 * commanders that didn't resolve so a real pattern can be diagnosed from the
 * UI alone (no logcat access needed).
 */
suspend fun syncGermanCommanderNames(
    deckRepository: DeckRepository,
    preconRepository: PreconRepository
): GermanNameSyncResult {
    var updated = 0
    var errors = 0
    val failedNames = mutableListOf<String>()
    val decks = deckRepository.getAllDecks().first()
    val missing = decks.filter { it.commanderNameDe.isBlank() && it.commanderName.isNotBlank() }
    missing.forEach { deck ->
        try {
            val de = preconRepository.fetchGermanNameByCommanderName(deck.commanderName)
            if (de.isNotBlank()) {
                deckRepository.updateDeck(deck.copy(commanderNameDe = de))
                updated++
            } else {
                failedNames.add(deck.commanderName)
            }
        } catch (_: Exception) {
            errors++
            failedNames.add(deck.commanderName)
        }
        delay(150) // Rate-Limit fuer Scryfall
    }
    return GermanNameSyncResult(attempted = missing.size, updated = updated, errors = errors, failedNames = failedNames)
}
