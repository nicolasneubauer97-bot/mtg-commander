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
    val isPreloadingImages: Boolean = false,
    val preloadProgress: String = "",
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

    private fun loadDecks(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                var list = repo.getDeckList(forceRefresh)
                _uiState.value = _uiState.value.copy(decks = list, isLoading = false)

                // Phase 1: Load MTGJSON deck details (commander names) for decks that need it
                list.filter { it.commanderName.isBlank() }.forEach { deck ->
                    launch {
                        val detailed = repo.loadDeckDetails(deck)
                        val updated = _uiState.value.decks.map { if (it.fileName == deck.fileName) detailed else it }
                        _uiState.value = _uiState.value.copy(decks = updated)
                    }
                }

                // Phase 2: Pre-load art URLs — once resolved, stored permanently in cache
                launch {
                    kotlinx.coroutines.delay(500)
                    val decksNeedingArt = _uiState.value.decks.filter {
                        it.artUrl.isBlank() && it.commanderName.isNotBlank()
                    }
                    if (decksNeedingArt.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isPreloadingImages = true,
                            preloadProgress = "Lade Bilder… 0/${decksNeedingArt.size}"
                        )
                        decksNeedingArt.forEachIndexed { idx, deck ->
                            val resolved = repo.resolveArtUrl(deck.commanderName)
                            if (resolved.isNotBlank()) {
                                val updated = _uiState.value.decks.map {
                                    if (it.fileName == deck.fileName) it.copy(artUrl = resolved) else it
                                }
                                _uiState.value = _uiState.value.copy(
                                    decks = updated,
                                    preloadProgress = "Lade Bilder… ${idx + 1}/${decksNeedingArt.size}"
                                )
                            }
                            kotlinx.coroutines.delay(120) // polite rate limiting for Scryfall
                        }
                        _uiState.value = _uiState.value.copy(
                            isPreloadingImages = false, preloadProgress = ""
                        )
                    }
                }

                // Phase 3: German names (optional, for MTGJSON decks with scryfallId)
                launch {
                    list.filter { it.scryfallId.isNotBlank() && it.commanderNameDe.isBlank() }.forEach { deck ->
                        val de = repo.fetchGermanName(deck.scryfallId)
                        if (de.isNotBlank()) {
                            val updated = _uiState.value.decks.map {
                                if (it.fileName == deck.fileName) it.copy(commanderNameDe = de) else it
                            }
                            _uiState.value = _uiState.value.copy(decks = updated)
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
        loadDecks(forceRefresh = true)
    }

    companion object {
        fun factory(repo: PreconRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PreconPickerViewModel(repo) as T
        }
    }
}
