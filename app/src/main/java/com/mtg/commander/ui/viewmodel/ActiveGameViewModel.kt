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
import kotlin.random.Random

data class ParticipantUiState(
    val participant: GameParticipant,
    val player: Player,
    val deck: Deck,
    val commanderDamageReceived: Map<Long, Int> = emptyMap(),
    val poisonCounters: Int = 0,
    val optionalCounter: Int = 0
)

data class ActiveGameUiState(
    val game: Game? = null,
    val participants: List<ParticipantUiState> = emptyList(),
    val commanderDamage: List<CommanderDamage> = emptyList(),
    val kills: List<Kill> = emptyList(),
    val isLoading: Boolean = true,
    val showCommanderDamageFor: Long? = null,
    val showEliminateDialogFor: Long? = null,
    val showEndGameConfirm: Boolean = false,
    val startingPlayerId: Long? = null,
    val diceResult: Int? = null,
    val randomOpponentId: Long? = null,
    val optionalCounterLabel: String = "Bonus",
    val showOptionalCounterLabelDialog: Boolean = false,
    val poisonCounters: Map<Long, Int> = emptyMap(),
    val optionalCounters: Map<Long, Int> = emptyMap()
)

class ActiveGameViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveGameUiState())
    val uiState: StateFlow<ActiveGameUiState> = _uiState.asStateFlow()

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            combine(
                gameRepository.getParticipantsForGame(gameId),
                gameRepository.getCommanderDamageForGame(gameId),
                gameRepository.getKillsForGame(gameId)
            ) { participants, damage, kills ->
                Triple(participants, damage, kills)
            }.collect { (participants, damage, kills) ->
                val game = gameRepository.getGameById(gameId)
                val participantUiStates = participants.mapNotNull { p ->
                    val player = playerRepository.getPlayerById(p.playerId) ?: return@mapNotNull null
                    val deck = deckRepository.getDeckById(p.deckId) ?: return@mapNotNull null
                    val receivedDamage = damage
                        .filter { it.targetParticipantId == p.id }
                        .associate { it.attackerParticipantId to it.damage }
                    val poison = _uiState.value.poisonCounters[p.id] ?: 0
                    val optional = _uiState.value.optionalCounters[p.id] ?: 0
                    ParticipantUiState(p, player, deck, receivedDamage, poison, optional)
                }

                val currentState = _uiState.value
                val startingId = currentState.startingPlayerId
                    ?: if (participantUiStates.isNotEmpty())
                        participantUiStates.random().participant.id
                    else null

                _uiState.value = currentState.copy(
                    game = game,
                    participants = participantUiStates,
                    commanderDamage = damage,
                    kills = kills,
                    isLoading = false,
                    startingPlayerId = startingId
                )
            }
        }
    }

    fun updateLife(participantId: Long, delta: Int) {
        viewModelScope.launch {
            val p = _uiState.value.participants.find { it.participant.id == participantId }?.participant ?: return@launch
            gameRepository.updateParticipant(p.copy(currentLife = p.currentLife + delta))
        }
    }

    fun updatePoison(participantId: Long, delta: Int) {
        val current = _uiState.value.poisonCounters.toMutableMap()
        val newVal = maxOf(0, (current[participantId] ?: 0) + delta)
        current[participantId] = newVal
        val participants = _uiState.value.participants.map { p ->
            if (p.participant.id == participantId) p.copy(poisonCounters = newVal) else p
        }
        _uiState.value = _uiState.value.copy(poisonCounters = current, participants = participants)
    }

    fun updateOptionalCounter(participantId: Long, delta: Int) {
        val current = _uiState.value.optionalCounters.toMutableMap()
        val newVal = (current[participantId] ?: 0) + delta
        current[participantId] = newVal
        val participants = _uiState.value.participants.map { p ->
            if (p.participant.id == participantId) p.copy(optionalCounter = newVal) else p
        }
        _uiState.value = _uiState.value.copy(optionalCounters = current, participants = participants)
    }

    fun showCommanderDamagePanel(participantId: Long?) {
        _uiState.value = _uiState.value.copy(showCommanderDamageFor = participantId)
    }

    fun updateCommanderDamage(attackerId: Long, targetId: Long, delta: Int) {
        viewModelScope.launch {
            gameRepository.updateCommanderDamage(gameId, attackerId, targetId, delta)
        }
    }

    fun showEliminateDialog(participantId: Long) {
        _uiState.value = _uiState.value.copy(showEliminateDialogFor = participantId)
    }

    fun dismissEliminateDialog() {
        _uiState.value = _uiState.value.copy(showEliminateDialogFor = null)
    }

    fun eliminatePlayer(victimId: Long, killerId: Long?) {
        viewModelScope.launch {
            val allParticipants = gameRepository.getParticipantsForGameSync(gameId)
            val activePlayers = allParticipants.filter { !it.isEliminated }
            val placement = activePlayers.size
            val victim = activePlayers.find { it.id == victimId } ?: return@launch
            gameRepository.updateParticipant(
                victim.copy(isEliminated = true, placement = placement, eliminatedAt = System.currentTimeMillis())
            )
            gameRepository.insertKill(
                Kill(gameId = gameId, killerParticipantId = killerId, victimParticipantId = victimId)
            )
            val remaining = activePlayers.filter { it.id != victimId }
            if (remaining.size == 1) {
                val winner = remaining.first()
                gameRepository.updateParticipant(winner.copy(placement = 1))
                val game = gameRepository.getGameById(gameId)!!
                gameRepository.updateGame(game.copy(status = "FINISHED", endedAt = System.currentTimeMillis()))
            }
            _uiState.value = _uiState.value.copy(showEliminateDialogFor = null)
        }
    }

    fun rollDice() {
        _uiState.value = _uiState.value.copy(diceResult = Random.nextInt(1, 7))
    }

    fun clearDice() {
        _uiState.value = _uiState.value.copy(diceResult = null)
    }

    fun randomizeOpponent(myParticipantId: Long) {
        val others = _uiState.value.participants
            .filter { it.participant.id != myParticipantId && !it.participant.isEliminated }
        if (others.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(randomOpponentId = others.random().participant.id)
        }
    }

    fun clearRandomOpponent() {
        _uiState.value = _uiState.value.copy(randomOpponentId = null)
    }

    fun showOptionalCounterLabelDialog() {
        _uiState.value = _uiState.value.copy(showOptionalCounterLabelDialog = true)
    }

    fun setOptionalCounterLabel(label: String) {
        _uiState.value = _uiState.value.copy(
            optionalCounterLabel = label.ifBlank { "Bonus" },
            showOptionalCounterLabelDialog = false
        )
    }

    fun dismissOptionalCounterLabelDialog() {
        _uiState.value = _uiState.value.copy(showOptionalCounterLabelDialog = false)
    }

    fun showEndGameConfirm() {
        _uiState.value = _uiState.value.copy(showEndGameConfirm = true)
    }

    fun dismissEndGameConfirm() {
        _uiState.value = _uiState.value.copy(showEndGameConfirm = false)
    }

    fun endGame() {
        viewModelScope.launch {
            val game = gameRepository.getGameById(gameId) ?: return@launch
            gameRepository.updateGame(game.copy(status = "FINISHED", endedAt = System.currentTimeMillis()))
            _uiState.value = _uiState.value.copy(showEndGameConfirm = false)
        }
    }

    companion object {
        fun factory(gameId: Long, app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ActiveGameViewModel(gameId, app.gameRepository, app.playerRepository, app.deckRepository) as T
        }
    }
}
