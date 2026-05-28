package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class GlobalDamageStatsUiState(
    // attacker → victim → total damage
    val matrix: Map<String, Map<String, Int>> = emptyMap(),
    val players: List<String> = emptyList(),
    val isLoading: Boolean = true
)

class GlobalDamageStatsViewModel(
    private val playerRepository: PlayerRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalDamageStatsUiState())
    val uiState: StateFlow<GlobalDamageStatsUiState> = _uiState.asStateFlow()

    init { loadStats() }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val players = playerRepository.getAllPlayers().firstOrNull() ?: emptyList()
            val nameById = players.associate { it.id to it.name }
            val triples = try { statsRepository.getGlobalDamageStats(nameById) } catch (_: Exception) { emptyList() }

            // Build matrix: attacker → (victim → totalDamage)
            val matrix = mutableMapOf<String, MutableMap<String, Int>>()
            for ((attacker, victim, dmg) in triples) {
                matrix.getOrPut(attacker) { mutableMapOf() }[victim] = dmg
            }
            val allPlayers = (matrix.keys + matrix.values.flatMap { it.keys }).toSortedSet().toList()

            _uiState.value = GlobalDamageStatsUiState(matrix = matrix, players = allPlayers, isLoading = false)
        }
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GlobalDamageStatsViewModel(app.playerRepository, app.statsRepository) as T
        }
    }
}
