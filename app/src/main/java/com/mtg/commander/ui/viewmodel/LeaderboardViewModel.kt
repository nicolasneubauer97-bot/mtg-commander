package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.StatsRepository
import com.mtg.commander.domain.model.PlayerStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LeaderboardSort { WINS, WIN_RATE, GAMES_PLAYED, AVG_PLACEMENT, KILLS, DAMAGE }

data class LeaderboardUiState(
    val stats: List<PlayerStats> = emptyList(),
    val sortBy: LeaderboardSort = LeaderboardSort.WIN_RATE,
    val isLoading: Boolean = true
)

class LeaderboardViewModel(
    private val playerRepository: PlayerRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init { loadStats() }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            playerRepository.getAllPlayers().collect { players ->
                val stats = players.map { player ->
                    statsRepository.getPlayerStats(player.id, player.name)
                }
                _uiState.value = _uiState.value.copy(
                    stats = sorted(stats, _uiState.value.sortBy),
                    isLoading = false
                )
            }
        }
    }

    fun setSortBy(sort: LeaderboardSort) {
        _uiState.value = _uiState.value.copy(
            sortBy = sort,
            stats = sorted(_uiState.value.stats, sort)
        )
    }

    private fun sorted(list: List<PlayerStats>, sort: LeaderboardSort) = when (sort) {
        LeaderboardSort.WINS -> list.sortedByDescending { it.wins }
        LeaderboardSort.WIN_RATE -> list.sortedByDescending { it.winRate }
        LeaderboardSort.GAMES_PLAYED -> list.sortedByDescending { it.gamesPlayed }
        LeaderboardSort.AVG_PLACEMENT -> list.sortedBy { it.averagePlacement }
        LeaderboardSort.KILLS -> list.sortedByDescending { it.kills }
        LeaderboardSort.DAMAGE -> list.sortedByDescending { it.totalDamageDealtToOthers }
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LeaderboardViewModel(app.playerRepository, app.statsRepository) as T
        }
    }
}
