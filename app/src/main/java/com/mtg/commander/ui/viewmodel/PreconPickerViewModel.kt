package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.PreconRepository
import com.mtg.commander.domain.model.PreconDeck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private fun normalizeDeckName(name: String) = name.trim().lowercase()

data class PreconPickerUiState(
    val decks: List<PreconDeck> = emptyList(),
    val isLoading: Boolean = true,
    val isPreloadingImages: Boolean = false,
    val preloadProgress: String = "",
    val error: String? = null,
    val networkWarning: Boolean = false,
    val searchQuery: String = "",
    // Normalized names of decks this player already owns — those precons can't be picked again
    val ownedDeckNames: Set<String> = emptySet()
) {
    fun isAlreadyOwned(deck: PreconDeck): Boolean =
        normalizeDeckName(deck.name ?: "") in ownedDeckNames

    val filtered: List<PreconDeck> get() {
        if (searchQuery.isBlank()) return decks
        // Split query into words — every word must appear somewhere in the deck's data
        val words = searchQuery.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return decks.filter { deck ->
            val haystack = buildString {
                append(deck.name ?: ""); append(' ')
                append(deck.commanderName ?: ""); append(' ')
                append(deck.commanderName2 ?: ""); append(' ')
                append(deck.commanderNameDe ?: ""); append(' ')
                append(deck.commanderNameDe2 ?: ""); append(' ')
                append(deck.setCode ?: ""); append(' ')
                PRECON_ALIASES[(deck.commanderName ?: "").lowercase()]?.let { append(it); append(' ') }
                PRECON_ALIASES[(deck.commanderName2 ?: "").lowercase()]?.let { append(it); append(' ') }
            }.lowercase()
            words.all { word -> haystack.contains(word) }
        }
    }
}

/**
 * Alias map: commanderName.lowercase() → extra search terms.
 * Add entries when a commander is commonly known by a different name,
 * nickname, or when players confuse a card in the deck for the commander.
 */
val PRECON_ALIASES: Map<String, String> = mapOf(
    // Bloomburrow
    "hazel of the rootbloom"          to "squirreled away eichhörnchen",
    "zinnia, valley's voice"          to "family matters zinnia",
    "warren soultrader"               to "animated army warren",
    "bello, bard of the brambles"     to "peace offering bello",
    // Duskmourn
    "zimone, all-questioning"         to "endless punishment zimone",
    "the haunting of heretat"         to "jump scare horror",
    // Doctor Who
    "the fourth doctor"               to "blast from the past vierter doktor",
    "the tenth doctor"                to "paradox power zehnter doktor",
    "davros, dalek creator"           to "masters of evil dalek",
    "the thirteenth doctor"           to "timey wimey dreizehnte",
    // Commander Legends
    "aesi, tyrant of gyre strait"     to "reap the tides flut",
    "wyleth, soul of steel"           to "arm for battle stahl",
    // Kaldheim
    "lathril, blade of the elves"     to "elven empire elfen elfenreich",
    "ranar the ever-watchful"         to "phantom premonition geist",
    // Popular nicknames
    "atraxa, praetors' voice"         to "breed lethality praetor gift",
    "edgar markov"                    to "vampiric bloodlust vampir vampyr eddi",
    "the ur-dragon"                   to "draconic domination drache ur-drache",
    "breya, etherium shaper"          to "invent superiority artefakt artefakte",
    "yidris, maelstrom wielder"       to "entropic uprising chaos",
    "nekusar, the mindrazer"          to "mind seize räder gedanken",
    "kaalia of the vast"              to "heavenly inferno engel dämon drachen",
    "ghave, guru of spores"           to "eternal vigilance pilz token sporen",
    "the mimeoplasm"                  to "planted fear zombie imitator",
    "oloro, ageless ascetic"          to "eternal bargain lebens gain",
    "meren of clan nel toth"          to "plunder the graves friedhof selbstmühle",
    "mizzix of the izmagnus"          to "seize control zauberer instant",
    "inalla, archmage ritualist"      to "arcane wizardry zauberer ritual",
    "nicol bolas, the ravager"        to "faceless menace morphe",
    "prosper, tome-bound"             to "planar portal tiefling exil",
    "wilhelt, the rotcleaver"         to "undead unleashed zombie untot",
)

class PreconPickerViewModel(
    private val repo: PreconRepository,
    private val deckRepository: DeckRepository,
    private val playerId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreconPickerUiState())
    val uiState: StateFlow<PreconPickerUiState> = _uiState.asStateFlow()

    init {
        loadDecks()
        deckRepository.getDecksByPlayer(playerId)
            .onEach { decks ->
                _uiState.value = _uiState.value.copy(
                    ownedDeckNames = decks.map { normalizeDeckName(it.name) }.toSet()
                )
            }
            .launchIn(viewModelScope)
    }

    private fun loadDecks(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, networkWarning = false)
            try {
                val result = repo.getDeckList(forceRefresh)
                var list = result.decks
                _uiState.value = _uiState.value.copy(
                    decks = list,
                    isLoading = false,
                    networkWarning = result.networkFailed
                )

                // Phase 1: Load MTGJSON deck details (commander names) for decks that need it
                list.filter { it.commanderName.isBlank() }.forEach { deck ->
                    launch {
                        val detailed = repo.loadDeckDetails(deck)
                        val updated = _uiState.value.decks.map { if (it.fileName == deck.fileName) detailed else it }
                        _uiState.value = _uiState.value.copy(decks = updated)
                    }
                }

                // Phase 2: Download missing art images locally (persistent offline cache)
                // If artUrls are pre-filled from the assets bundle, this is fast (just download).
                // If not (new decks), it also resolves from Scryfall first.
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
                            val resolved = repo.resolveArtUrl(deck.commanderName, deck.scryfallId)
                            if (resolved.isNotBlank()) {
                                val updated = _uiState.value.decks.map {
                                    if (it.fileName == deck.fileName) it.copy(artUrl = resolved) else it
                                }
                                _uiState.value = _uiState.value.copy(
                                    decks = updated,
                                    preloadProgress = "Lade Bilder… ${idx + 1}/${decksNeedingArt.size}"
                                )
                            }
                            kotlinx.coroutines.delay(120)
                        }
                        _uiState.value = _uiState.value.copy(
                            isPreloadingImages = false, preloadProgress = ""
                        )
                    }
                }

                // Phase 3: German names for all decks that still need them
                launch {
                    list.filter { it.commanderName.isNotBlank() && it.commanderNameDe.isBlank() }
                        .forEach { deck ->
                            // Use scryfallId if available (faster), else fuzzy-search by name
                            val de = if (deck.scryfallId.isNotBlank())
                                repo.fetchGermanName(deck.scryfallId)
                            else
                                repo.fetchGermanNameByCommanderName(deck.commanderName)

                            val de2 = if (deck.commanderName2.isNotBlank() && deck.commanderNameDe2.isBlank()) {
                                if (deck.scryfallId.isNotBlank()) "" // scryfallId2 not stored separately here
                                else repo.fetchGermanNameByCommanderName(deck.commanderName2)
                            } else ""

                            if (de.isNotBlank() || de2.isNotBlank()) {
                                val updated = _uiState.value.decks.map {
                                    if (it.fileName == deck.fileName)
                                        it.copy(
                                            commanderNameDe = de.ifBlank { it.commanderNameDe },
                                            commanderNameDe2 = de2.ifBlank { it.commanderNameDe2 }
                                        )
                                    else it
                                }
                                _uiState.value = _uiState.value.copy(decks = updated)
                            }
                            kotlinx.coroutines.delay(150) // rate limiting for Scryfall
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
        fun factory(repo: PreconRepository, deckRepository: DeckRepository, playerId: Long) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PreconPickerViewModel(repo, deckRepository, playerId) as T
            }
    }
}
