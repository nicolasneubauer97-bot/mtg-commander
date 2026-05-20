package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.domain.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameHistoryUiState(
    val games: List<Game> = emptyList(),
    val isLoading: Boolean = true
)

class GameHistoryViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GameHistoryUiState())
    val uiState: StateFlow<GameHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gameRepository.getFinishedGames().collect { games ->
                _uiState.value = _uiState.value.copy(games = games, isLoading = false)
            }
        }
    }

    fun deleteGame(game: Game) {
        viewModelScope.launch { gameRepository.deleteGame(game) }
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GameHistoryViewModel(app.gameRepository) as T
        }
    }
}
