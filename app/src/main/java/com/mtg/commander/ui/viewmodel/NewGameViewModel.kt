package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.domain.model.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerSelection(
    val player: Player,
    val availableDecks: List<Deck> = emptyList(),
    val selectedDeck: Deck? = null
)

data class NewGameUiState(
    val allPlayers: List<Player> = emptyList(),
    val selectedPlayers: List<PlayerSelection> = emptyList(),
    val gameId: Long? = null,
    val error: String? = null
)

class NewGameViewModel(
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGameUiState())
    val uiState: StateFlow<NewGameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playerRepository.getAllPlayers().collect { players ->
                _uiState.value = _uiState.value.copy(allPlayers = players)
            }
        }
    }

    fun togglePlayerSelection(player: Player) {
        val current = _uiState.value.selectedPlayers
        if (current.any { it.player.id == player.id }) {
            _uiState.value = _uiState.value.copy(
                selectedPlayers = current.filter { it.player.id != player.id }
            )
        } else if (current.size < 4) {
            viewModelScope.launch {
                val decks = deckRepository.getDecksByPlayerSync(player.id)
                val updated = current + PlayerSelection(
                    player = player,
                    availableDecks = decks,
                    selectedDeck = decks.firstOrNull()
                )
                _uiState.value = _uiState.value.copy(selectedPlayers = updated)
            }
        }
    }

    fun selectDeck(player: Player, deck: Deck) {
        val updated = _uiState.value.selectedPlayers.map { sel ->
            if (sel.player.id == player.id) sel.copy(selectedDeck = deck) else sel
        }
        _uiState.value = _uiState.value.copy(selectedPlayers = updated)
    }

    fun startGame() {
        val selections = _uiState.value.selectedPlayers
        if (selections.size < 2) {
            _uiState.value = _uiState.value.copy(error = "Mindestens 2 Spieler erforderlich")
            return
        }
        if (selections.any { it.selectedDeck == null }) {
            _uiState.value = _uiState.value.copy(error = "Jeder Spieler braucht ein Deck")
            return
        }
        viewModelScope.launch {
            val pairs = selections.map { it.player.id to it.selectedDeck!!.id }
            val gameId = gameRepository.createGame(pairs)
            _uiState.value = _uiState.value.copy(gameId = gameId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NewGameViewModel(app.playerRepository, app.deckRepository, app.gameRepository) as T
        }
    }
}
