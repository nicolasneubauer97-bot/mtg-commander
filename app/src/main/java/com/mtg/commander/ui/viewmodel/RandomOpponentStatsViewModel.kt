package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.StatsRepository
import com.mtg.commander.domain.model.RandomOpponentStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class RandomOpponentStatsUiState(
    val stats: List<RandomOpponentStat> = emptyList(),
    val isLoading: Boolean = true
)

class RandomOpponentStatsViewModel(
    private val playerRepository: PlayerRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RandomOpponentStatsUiState())
    val uiState: StateFlow<RandomOpponentStatsUiState> = _uiState.asStateFlow()

    init { loadStats() }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val players = playerRepository.getAllPlayers().firstOrNull() ?: emptyList()
            val nameById = players.associate { it.id to it.name }
            val stats = statsRepository.getRandomOpponentStats(nameById)
            _uiState.value = RandomOpponentStatsUiState(stats = stats, isLoading = false)
        }
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RandomOpponentStatsViewModel(app.playerRepository, app.statsRepository) as T
        }
    }
}
