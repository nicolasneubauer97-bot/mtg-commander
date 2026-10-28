package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.AchievementRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.domain.model.AchievementProgress
import com.mtg.commander.domain.model.AchievementTier
import com.mtg.commander.domain.model.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BadgesUiState(
    val players: List<Player> = emptyList(),
    val selectedPlayer: Player? = null,
    val achievements: List<AchievementProgress> = emptyList(),
    val rang: AchievementTier = AchievementTier.NONE,
    val isLoading: Boolean = true,
    val showResetConfirm: Boolean = false,
    val resetDone: Boolean = false
)

class BadgesViewModel(
    private val playerRepository: PlayerRepository,
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BadgesUiState())
    val uiState: StateFlow<BadgesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playerRepository.getAllPlayers().collect { players ->
                val current = _uiState.value.selectedPlayer
                val selected = current?.let { sel -> players.find { it.id == sel.id } } ?: players.firstOrNull()
                _uiState.value = _uiState.value.copy(players = players)
                if (selected != null) selectPlayer(selected) else _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun selectPlayer(player: Player) {
        _uiState.value = _uiState.value.copy(selectedPlayer = player, isLoading = true)
        viewModelScope.launch {
            val achievements = achievementRepository.getAchievements(player.id)
            val rang = achievementRepository.computeRang(achievements)
            _uiState.value = _uiState.value.copy(achievements = achievements, rang = rang, isLoading = false)
        }
    }

    fun requestResetConfirm() {
        _uiState.value = _uiState.value.copy(showResetConfirm = true)
    }

    fun dismissResetConfirm() {
        _uiState.value = _uiState.value.copy(showResetConfirm = false)
    }

    fun confirmResetAnimations() {
        viewModelScope.launch {
            achievementRepository.resetShownAnimations()
            _uiState.value = _uiState.value.copy(showResetConfirm = false, resetDone = true)
        }
    }

    fun clearResetDone() {
        _uiState.value = _uiState.value.copy(resetDone = false)
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BadgesViewModel(app.playerRepository, app.achievementRepository) as T
        }
    }
}
