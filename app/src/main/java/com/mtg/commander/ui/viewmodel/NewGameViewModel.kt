package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.domain.model.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerSelection(
    val player: Player,
    val availableDecks: List<Deck> = emptyList(),
    val selectedDeck: Deck? = null
)

// Sitzposition im 4-Spieler-Layout
// 0 = Unten-Links, 1 = Unten-Rechts, 2 = Oben-Links (180°), 3 = Oben-Rechts (180°)
data class SeatAssignment(
    val seatIndex: Int,
    val label: String
)

val SEAT_LABELS_4 = listOf("Unten-Links", "Unten-Rechts", "Oben-Links", "Oben-Rechts")
val SEAT_LABELS_3 = listOf("Unten-Links", "Unten-Rechts", "Oben-Mitte")
val SEAT_LABELS_2 = listOf("Oben", "Unten")

data class NewGameUiState(
    val allPlayers: List<Player> = emptyList(),
    val selectedPlayers: List<PlayerSelection> = emptyList(),
    val seatAssignments: List<PlayerSelection?> = List(4) { null },
    val showSeatDialog: Boolean = false,
    val clockwiseTurns: Boolean = true,
    val gameId: Long? = null,
    val error: String? = null
)

class NewGameViewModel(
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGameUiState())
    val uiState: StateFlow<NewGameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playerRepository.getAllPlayers().collect { players ->
                _uiState.value = _uiState.value.copy(allPlayers = players)
            }
        }
    }

    fun togglePlayerSelection(player: Player) {
        val current = _uiState.value.selectedPlayers
        if (current.any { it.player.id == player.id }) {
            _uiState.value = _uiState.value.copy(
                selectedPlayers = current.filter { it.player.id != player.id }
            )
        } else if (current.size < 4) {
            viewModelScope.launch {
                val decks = deckRepository.getDecksByPlayerSync(player.id)
                val updated = current + PlayerSelection(
                    player = player,
                    availableDecks = decks,
                    selectedDeck = decks.firstOrNull()
                )
                _uiState.value = _uiState.value.copy(selectedPlayers = updated)
            }
        }
    }

    fun selectDeck(player: Player, deck: Deck?) {
        _uiState.value = _uiState.value.copy(
            selectedPlayers = _uiState.value.selectedPlayers.map { sel ->
                if (sel.player.id == player.id) sel.copy(selectedDeck = deck) else sel
            }
        )
    }

    // Sitzordnung: Spieler an Sitz-Index schieben
    fun assignSeat(seatIndex: Int, selection: PlayerSelection?) {
        val seats = _uiState.value.seatAssignments.toMutableList()
        // Alten Sitz desselben Spielers leeren
        if (selection != null) {
            for (i in seats.indices) {
                if (seats[i]?.player?.id == selection.player.id) seats[i] = null
            }
        }
        seats[seatIndex] = selection
        _uiState.value = _uiState.value.copy(seatAssignments = seats)
    }

    fun showSeatDialog() {
        _uiState.value = _uiState.value.copy(showSeatDialog = true)
    }

    fun dismissSeatDialog() {
        _uiState.value = _uiState.value.copy(showSeatDialog = false)
    }

    fun startGame() {
        val selections = _uiState.value.selectedPlayers
        if (selections.size < 2) {
            _uiState.value = _uiState.value.copy(error = "Mindestens 2 Spieler erforderlich")
            return
        }

        // Sitzordnung auflösen: Slots mit Zuweisungen zuerst, dann Rest auffüllen
        val count = selections.size
        val seats = _uiState.value.seatAssignments.take(count)
        val assigned = seats.take(count).toMutableList()

        // Nicht zugewiesene Slots mit verbleibenden Spielern füllen
        val unassignedPlayers = selections.filter { sel ->
            assigned.none { it?.player?.id == sel.player.id }
        }.toMutableList()
        for (i in assigned.indices) {
            if (assigned[i] == null && unassignedPlayers.isNotEmpty()) {
                assigned[i] = unassignedPlayers.removeAt(0)
            }
        }

        viewModelScope.launch {
            val pairs = assigned.mapNotNull { it }.map { it.player.id to it.selectedDeck?.id }
            val gameId = gameRepository.createGameWithDirection(pairs, _uiState.value.clockwiseTurns)
            _uiState.value = _uiState.value.copy(gameId = gameId)
        }
    }

    fun setClockwiseTurns(clockwise: Boolean) {
        _uiState.value = _uiState.value.copy(clockwiseTurns = clockwise)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        fun factory(app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NewGameViewModel(app.playerRepository, app.deckRepository, app.gameRepository) as T
        }
    }
}
