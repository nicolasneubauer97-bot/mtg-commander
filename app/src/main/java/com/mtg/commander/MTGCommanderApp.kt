package com.mtg.commander

import android.app.Application
import com.mtg.commander.data.db.AppDatabase
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.PreconRepository
import com.mtg.commander.data.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MTGCommanderApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }
    val playerRepository by lazy { PlayerRepository(database.playerDao()) }
    val deckRepository by lazy { DeckRepository(database.deckDao()) }
    val gameRepository by lazy {
        GameRepository(
            database.gameDao(),
            database.gameParticipantDao(),
            database.commanderDamageDao(),
            database.killDao(),
            database.lifeChangeEventDao(),
            database.randomOpponentPickDao(),
            database.diceRollDao(),
            database.turnDurationDao()
        )
    }
    val statsRepository by lazy {
        StatsRepository(
            database.gameParticipantDao(),
            database.killDao(),
            database.gameDao(),
            database.lifeChangeEventDao(),
            database.randomOpponentPickDao(),
            database.diceRollDao()
        )
    }
    val preconRepository by lazy { PreconRepository(this) }

    override fun onCreate() {
        super.onCreate()
        backfillMissingGermanCommanderNames()
    }

    // Bestehende Decks aus der Zeit vor dem commanderNameDe-Feld (Migration 8->9)
    // haben keinen deutschen Namen. Einmalig pro App-Start nachladen, ohne UI zu blockieren.
    private fun backfillMissingGermanCommanderNames() {
        applicationScope.launch {
            try {
                val decks = deckRepository.getAllDecks().first()
                val missing = decks.filter { it.commanderNameDe.isBlank() && it.commanderName.isNotBlank() }
                missing.forEach { deck ->
                    try {
                        val de = preconRepository.fetchGermanNameByCommanderName(deck.commanderName)
                        if (de.isNotBlank() && !de.equals(deck.commanderName, ignoreCase = true)) {
                            deckRepository.updateDeck(deck.copy(commanderNameDe = de))
                        }
                    } catch (_: Exception) {
                        // Netzwerkfehler fuer einzelnes Deck ignorieren, mit dem naechsten weitermachen
                    }
                    delay(150) // Rate-Limit fuer Scryfall, analog PreconPickerViewModel
                }
            } catch (_: Exception) {
                // Kein Netzwerk o.ae. - naechster App-Start versucht es erneut
            }
        }
    }
}
