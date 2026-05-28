package com.mtg.commander.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.data.repository.StatsRepository
import com.mtg.commander.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ParticipantUiState(
    val participant: GameParticipant,
    val player: Player,
    val deck: Deck?,
    val commanderDamageReceived: Map<Long, Int> = emptyMap(),
    val poisonCounters: Int = 0,
    val optionalCounter: Int = 0,
    val optionalCounterLabel: String = "Bonus"
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
    val showStartDialog: Boolean = false,
    val isDiceRolling: Boolean = false,
    val diceAnimValue: Int = 1,
    val diceResult: Int? = null,
    // Randomizer with slot-machine animation
    val randomOpponentId: Long? = null,
    val isRandomizing: Boolean = false,
    val randomizingDisplayName: String = "",
    // Optional counter labels
    val optionalCounterLabels: Map<Long, String> = emptyMap(),
    val showCounterLabelDialogFor: Long? = null,
    val poisonCounters: Map<Long, Int> = emptyMap(),
    val optionalCounters: Map<Long, Int> = emptyMap(),
    // Crown = top leaderboard rank among active players; Trash = bottom
    val crownPlayerId: Long? = null,
    val trashPlayerId: Long? = null
)

class ActiveGameViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveGameUiState())
    val uiState: StateFlow<ActiveGameUiState> = _uiState.asStateFlow()

    // Only true on the very first collection – used to decide whether to show start dialog
    private var isFirstLoad = true

    init { loadGame() }

    private fun loadGame() {
        viewModelScope.launch {
            combine(
                gameRepository.getParticipantsForGame(gameId),
                gameRepository.getCommanderDamageForGame(gameId),
                gameRepository.getKillsForGame(gameId)
            ) { p, d, k -> Triple(p, d, k) }.collect { (participants, damage, kills) ->
                val game = gameRepository.getGameById(gameId)
                val cur = _uiState.value
                val participantUiStates = participants.mapNotNull { p ->
                    val player = playerRepository.getPlayerById(p.playerId) ?: return@mapNotNull null
                    val deck = p.deckId?.let { deckRepository.getDeckById(it) }
                    val receivedDmg = damage.filter { it.targetParticipantId == p.id }
                        .associate { it.attackerParticipantId to it.damage }
                    ParticipantUiState(
                        participant = p, player = player, deck = deck,
                        commanderDamageReceived = receivedDmg,
                        poisonCounters = cur.poisonCounters[p.id] ?: 0,
                        optionalCounter = cur.optionalCounters[p.id] ?: 0,
                        optionalCounterLabel = cur.optionalCounterLabels[p.id] ?: "Bonus"
                    )
                }

                val startingId = cur.startingPlayerId
                    ?: participantUiStates.randomOrNull()?.participant?.id

                // Show start dialog only for fresh games (started < 30 seconds ago)
                val isNewGame = game != null &&
                    (System.currentTimeMillis() - game.startedAt) < 30_000L &&
                    participants.all { it.currentLife == it.startingLife }
                val showStart = isFirstLoad && isNewGame
                isFirstLoad = false

                val (crown, trash) = computeCrownTrash(participantUiStates)

                _uiState.value = cur.copy(
                    game = game, participants = participantUiStates,
                    commanderDamage = damage, kills = kills,
                    isLoading = false, startingPlayerId = startingId,
                    showStartDialog = if (showStart) true else cur.showStartDialog,
                    crownPlayerId = crown, trashPlayerId = trash
                )
            }
        }
        // Load leaderboard ranks in background
        viewModelScope.launch { refreshLeaderboardRanks() }
    }

    private suspend fun refreshLeaderboardRanks() {
        try {
            val allPlayers = playerRepository.getAllPlayers().firstOrNull() ?: return
            val stats = allPlayers.map { statsRepository.getPlayerStats(it.id, it.name) }
                .sortedByDescending { it.winRate }
            // Map playerId → rank (1 = best)
            val rankMap = stats.mapIndexed { i, s -> s.playerId to (i + 1) }.toMap()
            val cur = _uiState.value
            val (crown, trash) = computeCrownTrash(cur.participants, rankMap)
            _uiState.value = cur.copy(crownPlayerId = crown, trashPlayerId = trash)
        } catch (_: Exception) {}
    }

    private fun computeCrownTrash(
        participants: List<ParticipantUiState>,
        rankMap: Map<Long, Int>? = null
    ): Pair<Long?, Long?> {
        val active = participants.filter { !it.participant.isEliminated }
        if (active.size < 2) return Pair(null, null)
        if (rankMap == null || rankMap.isEmpty()) return Pair(null, null)
        val withRank = active.mapNotNull { p ->
            val rank = rankMap[p.player.id] ?: return@mapNotNull null
            p to rank
        }
        if (withRank.isEmpty()) return Pair(null, null)
        val crown = withRank.minByOrNull { it.second }?.first?.participant?.id
        val trash = withRank.maxByOrNull { it.second }?.first?.participant?.id
        return Pair(if (crown != trash) crown else null, if (crown != trash) trash else null)
    }

    fun updateLife(participantId: Long, delta: Int) {
        viewModelScope.launch {
            val p = _uiState.value.participants.find { it.participant.id == participantId }
                ?.participant ?: return@launch
            gameRepository.updateParticipant(p.copy(currentLife = p.currentLife + delta))
        }
    }

    fun updatePoison(participantId: Long, delta: Int) {
        val current = _uiState.value.poisonCounters.toMutableMap()
        val newVal = maxOf(0, (current[participantId] ?: 0) + delta)
        current[participantId] = newVal
        _uiState.value = _uiState.value.copy(
            poisonCounters = current,
            participants = _uiState.value.participants.map { p ->
                if (p.participant.id == participantId) p.copy(poisonCounters = newVal) else p
            }
        )
    }

    fun updateOptionalCounter(participantId: Long, delta: Int) {
        val current = _uiState.value.optionalCounters.toMutableMap()
        val newVal = (current[participantId] ?: 0) + delta
        current[participantId] = newVal
        _uiState.value = _uiState.value.copy(
            optionalCounters = current,
            participants = _uiState.value.participants.map { p ->
                if (p.participant.id == participantId) p.copy(optionalCounter = newVal) else p
            }
        )
    }

    fun showCounterLabelDialog(participantId: Long) {
        _uiState.value = _uiState.value.copy(showCounterLabelDialogFor = participantId)
    }

    fun setOptionalCounterLabel(participantId: Long, label: String) {
        val updated = _uiState.value.optionalCounterLabels.toMutableMap()
        updated[participantId] = label.ifBlank { "Bonus" }
        _uiState.value = _uiState.value.copy(
            optionalCounterLabels = updated, showCounterLabelDialogFor = null,
            participants = _uiState.value.participants.map { p ->
                if (p.participant.id == participantId)
                    p.copy(optionalCounterLabel = label.ifBlank { "Bonus" })
                else p
            }
        )
    }

    fun dismissCounterLabelDialog() {
        _uiState.value = _uiState.value.copy(showCounterLabelDialogFor = null)
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
            val all = gameRepository.getParticipantsForGameSync(gameId)
            val active = all.filter { !it.isEliminated }
            val placement = active.size
            val victim = active.find { it.id == victimId } ?: return@launch
            gameRepository.updateParticipant(
                victim.copy(isEliminated = true, placement = placement,
                    eliminatedAt = System.currentTimeMillis())
            )
            gameRepository.insertKill(Kill(gameId = gameId,
                killerParticipantId = killerId, victimParticipantId = victimId))
            val remaining = active.filter { it.id != victimId }
            if (remaining.size == 1) {
                gameRepository.updateParticipant(remaining.first().copy(placement = 1))
                val game = gameRepository.getGameById(gameId)!!
                gameRepository.updateGame(game.copy(
                    status = "FINISHED", endedAt = System.currentTimeMillis()))
            }
            _uiState.value = _uiState.value.copy(showEliminateDialogFor = null)
        }
    }

    fun dismissStartDialog() {
        _uiState.value = _uiState.value.copy(showStartDialog = false)
    }

    // ─── Dice ────────────────────────────────────────────────────────────────

    fun rollDice() {
        if (_uiState.value.isDiceRolling) return
        viewModelScope.launch {
            val finalResult = Random.nextInt(1, 7)
            _uiState.value = _uiState.value.copy(isDiceRolling = true, diceResult = null)
            val end = System.currentTimeMillis() + 3000L
            while (System.currentTimeMillis() < end) {
                val remaining = end - System.currentTimeMillis()
                val intervalMs = when {
                    remaining > 1500 -> 60L
                    remaining > 600  -> 110L
                    else             -> 200L
                }
                _uiState.value = _uiState.value.copy(diceAnimValue = Random.nextInt(1, 7))
                delay(intervalMs)
            }
            _uiState.value = _uiState.value.copy(
                isDiceRolling = false, diceAnimValue = finalResult, diceResult = finalResult)
        }
    }

    fun clearDice() {
        if (!_uiState.value.isDiceRolling)
            _uiState.value = _uiState.value.copy(diceResult = null)
    }

    // ─── Random Opponent (slot-machine animation) ─────────────────────────────

    fun randomizeOpponent(myParticipantId: Long) {
        if (_uiState.value.isRandomizing) return
        val others = _uiState.value.participants
            .filter { it.participant.id != myParticipantId && !it.participant.isEliminated }
        if (others.isEmpty()) return
        val finalOpponent = others.random()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRandomizing = true, randomOpponentId = null,
                randomizingDisplayName = "???"
            )
            val end = System.currentTimeMillis() + 2500L
            while (System.currentTimeMillis() < end) {
                val elapsed = 2500L - (end - System.currentTimeMillis())
                val intervalMs = when {
                    elapsed < 800  -> 70L
                    elapsed < 1700 -> 140L
                    else           -> 260L
                }
                _uiState.value = _uiState.value.copy(
                    randomizingDisplayName = others.random().player.name)
                delay(intervalMs)
            }
            _uiState.value = _uiState.value.copy(
                isRandomizing = false,
                randomOpponentId = finalOpponent.participant.id,
                randomizingDisplayName = finalOpponent.player.name
            )
        }
    }

    fun clearRandomOpponent() {
        _uiState.value = _uiState.value.copy(randomOpponentId = null, randomizingDisplayName = "")
    }

    // ─── Game end ────────────────────────────────────────────────────────────

    fun showEndGameConfirm() {
        _uiState.value = _uiState.value.copy(showEndGameConfirm = true)
    }

    fun dismissEndGameConfirm() {
        _uiState.value = _uiState.value.copy(showEndGameConfirm = false)
    }

    fun endGame() {
        viewModelScope.launch {
            val game = gameRepository.getGameById(gameId) ?: return@launch
            gameRepository.updateGame(
                game.copy(status = "FINISHED", endedAt = System.currentTimeMillis()))
            _uiState.value = _uiState.value.copy(showEndGameConfirm = false)
        }
    }

    companion object {
        fun factory(gameId: Long, app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ActiveGameViewModel(gameId, app.gameRepository,
                    app.playerRepository, app.deckRepository,
                    app.statsRepository) as T
        }
    }
}
