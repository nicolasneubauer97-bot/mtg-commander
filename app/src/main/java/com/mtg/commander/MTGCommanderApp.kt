package com.mtg.commander

import android.app.Application
import com.mtg.commander.data.db.AppDatabase
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.PreconRepository
import com.mtg.commander.data.repository.StatsRepository

class MTGCommanderApp : Application() {
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
            database.randomOpponentPickDao()
        )
    }
    val statsRepository by lazy {
        StatsRepository(
            database.gameParticipantDao(),
            database.killDao(),
            database.gameDao(),
            database.lifeChangeEventDao(),
            database.randomOpponentPickDao()
        )
    }
    val preconRepository by lazy { PreconRepository(this) }
}
