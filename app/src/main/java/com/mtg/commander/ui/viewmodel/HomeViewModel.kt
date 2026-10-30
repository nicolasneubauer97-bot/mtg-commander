package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.PreconRepository
import com.mtg.commander.data.repository.syncGermanCommanderNames
import com.mtg.commander.domain.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ResetOption {
    FINISHED_GAMES,
    KILLS_ONLY,
    COMMANDER_DAMAGE_ONLY,
    EVERYTHING
}

data class HomeUiState(
    val activeGame: Game? = null,
    val showResetDialog: Boolean = false,
    val selectedReset: ResetOption = ResetOption.FINISHED_GAMES,
    val showResetConfirm: Boolean = false,
    val resetDone: Boolean = false,
    val isSyncingDeckNames: Boolean = false,
    val syncMessage: String? = null,
    val syncProgressCurrent: Int = 0,
    val syncProgressTotal: Int = 0,
    val updatedDeckSummaries: List<String> = emptyList(),
    val showUpdatedDecksDialog: Boolean = false
)

class HomeViewModel(
    private val gameRepository: GameRepository,
    private val deckRepository: DeckRepository,
    private val preconRepository: PreconRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gameRepository.getActiveGames().collect { activeGames ->
                val candidate = activeGames.firstOrNull()
                // Defensive check: a game with a winner already decided must never be
                // offered as "resume", even if its status was somehow not flipped to FINISHED.
                val resumable = candidate?.let { game ->
                    val participants = gameRepository.getParticipantsForGameSync(game.id)
                    val hasWinner = participants.any { it.placement == 1 }
                    if (hasWinner) {
                        gameRepository.updateGame(game.copy(status = "FINISHED", endedAt = System.currentTimeMillis()))
                        null
                    } else game
                }
                _uiState.value = _uiState.value.copy(activeGame = resumable)
            }
        }
    }

    fun showResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = true, resetDone = false)
    }

    fun dismissResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = false, showResetConfirm = false)
    }

    fun selectResetOption(option: ResetOption) {
        _uiState.value = _uiState.value.copy(selectedReset = option)
    }

    fun requestResetConfirm() {
        _uiState.value = _uiState.value.copy(showResetConfirm = true)
    }

    fun dismissResetConfirm() {
        _uiState.value = _uiState.value.copy(showResetConfirm = false)
    }

    fun executeReset() {
        viewModelScope.launch {
            when (_uiState.value.selectedReset) {
                ResetOption.FINISHED_GAMES -> gameRepository.deleteAllFinishedGames()
                ResetOption.KILLS_ONLY -> gameRepository.deleteAllKills()
                ResetOption.COMMANDER_DAMAGE_ONLY -> gameRepository.deleteAllCommanderDamage()
                ResetOption.EVERYTHING -> {
                    gameRepository.deleteAllGames()
                    gameRepository.deleteAllKills()
                    gameRepository.deleteAllCommanderDamage()
                    gameRepository.deleteAllLifeChangeEvents()
                    gameRepository.deleteAllRandomOpponentPicks()
                    gameRepository.deleteAllDiceRolls()
                }
            }
            _uiState.value = _uiState.value.copy(
                showResetDialog = false,
                showResetConfirm = false,
                resetDone = true
            )
        }
    }

    fun clearResetDone() {
        _uiState.value = _uiState.value.copy(resetDone = false)
    }

    fun syncDeckNames() {
        if (_uiState.value.isSyncingDeckNames) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncingDeckNames = true, syncMessage = null,
                syncProgressCurrent = 0, syncProgressTotal = 0
            )
            var summaries: List<String> = emptyList()
            val message = try {
                val result = syncGermanCommanderNames(deckRepository, preconRepository) { current, total, _ ->
                    _uiState.value = _uiState.value.copy(syncProgressCurrent = current, syncProgressTotal = total)
                }
                if (result.updated > 0) {
                    val playerNames = result.updatedDecks.map { it.playerId }.distinct()
                        .associateWith { playerRepository.getPlayerById(it)?.name ?: "?" }
                    summaries = result.updatedDecks.map {
                        "${it.deckName} (${playerNames[it.playerId]}): ${it.commanderName} → ${it.commanderNameDe}"
                    }
                }
                when {
                    result.updated > 0 -> "${result.updated} Deck(s) aktualisiert" +
                        if (result.errors > 0) " (${result.errors} Fehler)" else ""
                    result.attempted == 0 -> "Alle Decks sind bereits aktuell"
                    result.errors == result.attempted ->
                        "Aktualisierung fehlgeschlagen: ${result.errorSample ?: "Internetverbindung prüfen"}"
                    result.errors > 0 -> {
                        "${result.errors}/${result.attempted} Fehler (${result.errorSample}), Rest ohne deutsche Version"
                    }
                    else -> {
                        val examples = result.failedNames.take(3).joinToString(", ")
                        "${result.attempted} geprüft, keine deutsche Version gefunden (z. B. $examples)"
                    }
                }
            } catch (_: Exception) {
                "Aktualisierung fehlgeschlagen – Internetverbindung prüfen"
            }
            _uiState.value = _uiState.value.copy(
                isSyncingDeckNames = false, syncMessage = message,
                updatedDeckSummaries = summaries, showUpdatedDecksDialog = summaries.isNotEmpty()
            )
        }
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }

    fun dismissUpdatedDecksDialog() {
        _uiState.value = _uiState.value.copy(showUpdatedDecksDialog = false)
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(
                    app.gameRepository, app.deckRepository,
                    app.preconRepository, app.playerRepository
                ) as T
        }
    }
}
