package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.data.repository.PreconRepository
import com.mtg.commander.domain.model.PreconDeck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PreconPickerUiState(
    val decks: List<PreconDeck> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = ""
) {
    val filtered: List<PreconDeck> get() = if (searchQuery.isBlank()) decks else
        decks.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.commanderName.contains(searchQuery, ignoreCase = true) ||
            it.commanderNameDe.contains(searchQuery, ignoreCase = true) ||
            it.setCode.contains(searchQuery, ignoreCase = true)
        }
}

class PreconPickerViewModel(private val repo: PreconRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PreconPickerUiState())
    val uiState: StateFlow<PreconPickerUiState> = _uiState.asStateFlow()

    init { loadDecks() }

    private fun loadDecks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val list = repo.getDeckList()
                _uiState.value = _uiState.value.copy(decks = list, isLoading = false)
                // Background: load details for decks without commander info
                list.filter { it.commanderName.isBlank() }.forEach { deck ->
                    launch {
                        val detailed = repo.loadDeckDetails(deck)
                        val updated = _uiState.value.decks.map { if (it.fileName == deck.fileName) detailed else it }
                        _uiState.value = _uiState.value.copy(decks = updated)
                        // Also load German name if we got a scryfallId
                        if (detailed.scryfallId.isNotBlank() && detailed.commanderNameDe.isBlank()) {
                            val de = repo.fetchGermanName(detailed.scryfallId)
                            if (de.isNotBlank()) {
                                val withDe = _uiState.value.decks.map {
                                    if (it.fileName == deck.fileName) it.copy(commanderNameDe = de) else it
                                }
                                _uiState.value = _uiState.value.copy(decks = withDe)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false,
                    error = "Laden fehlgeschlagen: ${e.message}")
            }
        }
    }

    fun setSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun refresh() {
        repo.clearCache()
        loadDecks()
    }

    companion object {
        fun factory(repo: PreconRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PreconPickerViewModel(repo) as T
        }
    }
}
