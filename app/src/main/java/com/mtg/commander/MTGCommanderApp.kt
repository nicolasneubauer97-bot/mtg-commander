package com.mtg.commander

import android.app.Application
import com.mtg.commander.data.db.AppDatabase
import com.mtg.commander.data.repository.AchievementRepository
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.PreconRepository
import com.mtg.commander.data.repository.StatsRepository
import com.mtg.commander.data.repository.syncGermanCommanderNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val achievementRepository by lazy {
        AchievementRepository(
            database.gameParticipantDao(), database.killDao(), database.commanderDamageDao(),
            database.lifeChangeEventDao(), database.randomOpponentPickDao(), database.achievementShownDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Bestehende Decks aus der Zeit vor dem commanderNameDe-Feld (Migration 8->9)
        // haben keinen deutschen Namen. Einmalig pro App-Start nachladen, ohne UI zu blockieren.
        // Zusaetzlich manuell ueber den "Decks aktualisieren"-Button auf dem Homescreen ausloesbar.
        applicationScope.launch {
            try {
                syncGermanCommanderNames(deckRepository, preconRepository)
            } catch (_: Exception) {
                // Kein Netzwerk o.ae. - naechster App-Start oder manueller Button versucht es erneut
            }
        }
        // Einmalige Aufraeumaktion: abgebrochene Partien gab es frueher noch als Status statt
        // geloescht - entfernt alte Reste, damit sie nicht mehr in der Spielhistorie auftauchen.
        applicationScope.launch {
            try {
                gameRepository.deleteAllAbandonedGames()
            } catch (_: Exception) {}
        }
    }
}
