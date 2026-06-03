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

data class SeatSlot(
    val player: Player? = null,
    val availableDecks: List<Deck> = emptyList(),
    val selectedDeck: Deck? = null
)

data class SeatSetupUiState(
    val playerCount: Int,
    val allPlayers: List<Player> = emptyList(),
    val seats: List<SeatSlot>,           // size == playerCount
    val clockwise: Boolean = true,
    val showPickerForSeat: Int? = null,  // which seat is being assigned
    val pickerSelectedPlayer: Player? = null,
    val gameId: Long? = null
) {
    val filledSeats: Int get() = seats.count { it.player != null }
    val canStart: Boolean get() = filledSeats >= 2
    // Players already assigned to another seat
    val assignedPlayerIds: Set<Long> get() = seats.mapNotNull { it.player?.id }.toSet()
}

class SeatSetupViewModel(
    playerCount: Int,
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SeatSetupUiState(
            playerCount = playerCount,
            seats = List(playerCount) { SeatSlot() }
        )
    )
    val uiState: StateFlow<SeatSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playerRepository.getAllPlayers().collect { players ->
                _uiState.value = _uiState.value.copy(allPlayers = players)
            }
        }
    }

    fun openPickerForSeat(seatIndex: Int) {
        _uiState.value = _uiState.value.copy(
            showPickerForSeat = seatIndex,
            pickerSelectedPlayer = _uiState.value.seats[seatIndex].player
        )
    }

    fun pickerSelectPlayer(player: Player) {
        viewModelScope.launch {
            val decks = deckRepository.getDecksByPlayerSync(player.id)
            _uiState.value = _uiState.value.copy(
                pickerSelectedPlayer = player,
                // Update the seat's availableDecks already so deck picker is ready
                seats = _uiState.value.seats.mapIndexed { i, slot ->
                    if (i == _uiState.value.showPickerForSeat)
                        slot.copy(availableDecks = decks, selectedDeck = decks.firstOrNull())
                    else slot
                }
            )
        }
    }

    fun pickerSelectDeck(deck: Deck?) {
        val seatIdx = _uiState.value.showPickerForSeat ?: return
        _uiState.value = _uiState.value.copy(
            seats = _uiState.value.seats.mapIndexed { i, slot ->
                if (i == seatIdx) slot.copy(selectedDeck = deck) else slot
            }
        )
    }

    fun confirmPickerSelection() {
        val seatIdx = _uiState.value.showPickerForSeat ?: return
        val player = _uiState.value.pickerSelectedPlayer
        val currentSeat = _uiState.value.seats[seatIdx]
        _uiState.value = _uiState.value.copy(
            seats = _uiState.value.seats.mapIndexed { i, slot ->
                if (i == seatIdx) slot.copy(player = player) else slot
            },
            showPickerForSeat = null,
            pickerSelectedPlayer = null
        )
    }

    fun clearSeat(seatIndex: Int) {
        _uiState.value = _uiState.value.copy(
            seats = _uiState.value.seats.mapIndexed { i, slot ->
                if (i == seatIndex) SeatSlot() else slot
            }
        )
    }

    fun dismissPicker() {
        _uiState.value = _uiState.value.copy(
            showPickerForSeat = null,
            pickerSelectedPlayer = null
        )
    }

    fun setClockwise(clockwise: Boolean) {
        _uiState.value = _uiState.value.copy(clockwise = clockwise)
    }

    fun startGame() {
        val state = _uiState.value
        if (!state.canStart) return
        viewModelScope.launch {
            val pairs = state.seats
                .filter { it.player != null }
                .map { it.player!!.id to it.selectedDeck?.id }
            val gameId = gameRepository.createGameWithDirection(pairs, state.clockwise)
            _uiState.value = _uiState.value.copy(gameId = gameId)
        }
    }

    companion object {
        fun factory(playerCount: Int, app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SeatSetupViewModel(playerCount, app.playerRepository, app.deckRepository, app.gameRepository) as T
        }
    }
}
