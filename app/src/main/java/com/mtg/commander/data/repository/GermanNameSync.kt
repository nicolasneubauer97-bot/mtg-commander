package com.mtg.commander.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

data class UpdatedDeckInfo(
    val deckName: String,
    val commanderName: String,
    val commanderNameDe: String,
    val playerId: Long
)

data class GermanNameSyncResult(
    val attempted: Int,
    val updated: Int,
    val errors: Int,
    val failedNames: List<String>,
    val errorSample: String?,
    val updatedDecks: List<UpdatedDeckInfo>
)

/**
 * Loads the German commander name for every deck that is still missing one
 * (via Scryfall) and persists it. Used both automatically on app start and
 * from a manual "refresh" action, so it must stay side-effect-safe to re-run.
 * Reports attempted/updated/errors separately so a silent network failure
 * doesn't look identical to "nothing needed updating", and names a few of the
 * commanders that didn't resolve plus a sample exception (type: message) so a
 * real pattern can be diagnosed from the UI alone, no logcat access needed.
 */
suspend fun syncGermanCommanderNames(
    deckRepository: DeckRepository,
    preconRepository: PreconRepository,
    onProgress: (current: Int, total: Int, commanderName: String) -> Unit = { _, _, _ -> }
): GermanNameSyncResult {
    var updated = 0
    var errors = 0
    val failedNames = mutableListOf<String>()
    val updatedDecks = mutableListOf<UpdatedDeckInfo>()
    var errorSample: String? = null
    val decks = deckRepository.getAllDecks().first()
    val missing = decks.filter { it.commanderNameDe.isBlank() && it.commanderName.isNotBlank() }
    missing.forEachIndexed { index, deck ->
        onProgress(index + 1, missing.size, deck.commanderName)
        try {
            val de = preconRepository.fetchGermanNameByCommanderNameOrThrow(deck.commanderName)
            if (de.isNotBlank()) {
                deckRepository.updateDeck(deck.copy(commanderNameDe = de))
                updated++
                updatedDecks.add(UpdatedDeckInfo(deck.name, deck.commanderName, de, deck.playerId))
            } else {
                failedNames.add(deck.commanderName)
            }
        } catch (e: Exception) {
            errors++
            failedNames.add(deck.commanderName)
            if (errorSample == null) errorSample = "${e.javaClass.simpleName}: ${e.message}"
        }
        delay(150) // Rate-Limit fuer Scryfall
    }
    return GermanNameSyncResult(
        attempted = missing.size, updated = updated, errors = errors,
        failedNames = failedNames, errorSample = errorSample, updatedDecks = updatedDecks
    )
}
