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
    val resetDone: Boolean = false
)

class HomeViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gameRepository.getActiveGames().collect { activeGames ->
                _uiState.value = _uiState.value.copy(activeGame = activeGames.firstOrNull())
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

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(app.gameRepository) as T
        }
    }
}
