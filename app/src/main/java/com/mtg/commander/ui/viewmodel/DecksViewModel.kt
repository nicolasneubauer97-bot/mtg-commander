package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.domain.model.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DecksUiState(
    val players: List<Player> = emptyList(),
    val selectedPlayer: Player? = null,
    val decks: List<Deck> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingDeck: Deck? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DecksViewModel(
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _selectedPlayer = MutableStateFlow<Player?>(null)
    private val _uiState = MutableStateFlow(DecksUiState())
    val uiState: StateFlow<DecksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playerRepository.getAllPlayers().collect { players ->
                val current = _selectedPlayer.value
                val newSelected = current?.let { sel -> players.find { it.id == sel.id } }
                    ?: players.firstOrNull()
                _selectedPlayer.value = newSelected
                _uiState.value = _uiState.value.copy(players = players, selectedPlayer = newSelected)
            }
        }

        viewModelScope.launch {
            _selectedPlayer.flatMapLatest { player ->
                if (player == null) flowOf(emptyList())
                else deckRepository.getDecksByPlayer(player.id)
            }.collect { decks ->
                _uiState.value = _uiState.value.copy(decks = decks)
            }
        }
    }

    fun selectPlayer(player: Player) {
        _selectedPlayer.value = player
        _uiState.value = _uiState.value.copy(selectedPlayer = player)
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingDeck = null)
    }

    fun showEditDialog(deck: Deck) {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingDeck = deck)
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingDeck = null)
    }

    fun saveDeck(name: String, commanderName: String, colors: String) {
        viewModelScope.launch {
            val player = _uiState.value.selectedPlayer ?: return@launch
            val editing = _uiState.value.editingDeck
            if (editing == null) {
                deckRepository.insertDeck(
                    Deck(playerId = player.id, name = name.trim(), commanderName = commanderName.trim(), colors = colors.trim())
                )
            } else {
                deckRepository.updateDeck(
                    editing.copy(name = name.trim(), commanderName = commanderName.trim(), colors = colors.trim())
                )
            }
            dismissDialog()
        }
    }

    fun deleteDeck(deck: Deck) {
        viewModelScope.launch { deckRepository.deleteDeck(deck) }
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DecksViewModel(app.playerRepository, app.deckRepository) as T
        }
    }
}
