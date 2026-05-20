package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.domain.model.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayersUiState(
    val players: List<Player> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingPlayer: Player? = null
)

class PlayersViewModel(private val playerRepository: PlayerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayersUiState())
    val uiState: StateFlow<PlayersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playerRepository.getAllPlayers().collect { players ->
                _uiState.value = _uiState.value.copy(players = players)
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingPlayer = null)
    }

    fun showEditDialog(player: Player) {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingPlayer = player)
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingPlayer = null)
    }

    fun savePlayer(name: String) {
        viewModelScope.launch {
            val editing = _uiState.value.editingPlayer
            if (editing == null) {
                playerRepository.insertPlayer(name)
            } else {
                playerRepository.updatePlayer(editing.copy(name = name.trim()))
            }
            dismissDialog()
        }
    }

    fun deletePlayer(player: Player) {
        viewModelScope.launch { playerRepository.deletePlayer(player) }
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayersViewModel(app.playerRepository) as T
        }
    }
}
