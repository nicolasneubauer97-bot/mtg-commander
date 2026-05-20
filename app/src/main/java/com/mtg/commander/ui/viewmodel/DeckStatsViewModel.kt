package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.StatsRepository
import com.mtg.commander.domain.model.DeckStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DeckStatsSort { WINS, WIN_RATE, GAMES_PLAYED, AVG_PLACEMENT }

data class DeckStatsUiState(
    val stats: List<DeckStats> = emptyList(),
    val sortBy: DeckStatsSort = DeckStatsSort.WIN_RATE,
    val isLoading: Boolean = true
)

class DeckStatsViewModel(
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckStatsUiState())
    val uiState: StateFlow<DeckStatsUiState> = _uiState.asStateFlow()

    init { loadStats() }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            deckRepository.getAllDecks().collect { decks ->
                val stats = decks.mapNotNull { deck ->
                    val player = playerRepository.getPlayerById(deck.playerId) ?: return@mapNotNull null
                    statsRepository.getDeckStats(deck.id, deck.name, deck.commanderName, player.name)
                }
                _uiState.value = _uiState.value.copy(
                    stats = sorted(stats, _uiState.value.sortBy),
                    isLoading = false
                )
            }
        }
    }

    fun setSortBy(sort: DeckStatsSort) {
        _uiState.value = _uiState.value.copy(
            sortBy = sort,
            stats = sorted(_uiState.value.stats, sort)
        )
    }

    private fun sorted(list: List<DeckStats>, sort: DeckStatsSort) = when (sort) {
        DeckStatsSort.WINS -> list.sortedByDescending { it.wins }
        DeckStatsSort.WIN_RATE -> list.sortedByDescending { it.winRate }
        DeckStatsSort.GAMES_PLAYED -> list.sortedByDescending { it.gamesPlayed }
        DeckStatsSort.AVG_PLACEMENT -> list.sortedBy { it.averagePlacement }
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DeckStatsViewModel(app.playerRepository, app.deckRepository, app.statsRepository) as T
        }
    }
}
