package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameDetailUiState(
    val game: Game? = null,
    val participants: List<ParticipantUiState> = emptyList(),
    val kills: List<Kill> = emptyList(),
    val commanderDamage: List<CommanderDamage> = emptyList(),
    val isLoading: Boolean = true
)

class GameDetailViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                gameRepository.getParticipantsForGame(gameId),
                gameRepository.getKillsForGame(gameId),
                gameRepository.getCommanderDamageForGame(gameId)
            ) { participants, kills, damage ->
                Triple(participants, kills, damage)
            }.collect { (participants, kills, damage) ->
                val game = gameRepository.getGameById(gameId)
                val participantUiStates = participants.mapNotNull { p ->
                    val player = playerRepository.getPlayerById(p.playerId) ?: return@mapNotNull null
                    val deck = p.deckId?.let { deckRepository.getDeckById(it) }
                    val receivedDamage = damage
                        .filter { it.targetParticipantId == p.id }
                        .associate { it.attackerParticipantId to it.damage }
                    ParticipantUiState(p, player, deck, receivedDamage)
                }.sortedBy { it.participant.placement }
                _uiState.value = _uiState.value.copy(
                    game = game,
                    participants = participantUiStates,
                    kills = kills,
                    commanderDamage = damage,
                    isLoading = false
                )
            }
        }
    }

    companion object {
        fun factory(gameId: Long, app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GameDetailViewModel(gameId, app.gameRepository, app.playerRepository, app.deckRepository) as T
        }
    }
}
