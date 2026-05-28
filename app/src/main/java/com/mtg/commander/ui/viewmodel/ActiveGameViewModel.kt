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
    // Turn tracking
    val currentTurnParticipantId: Long? = null,
    val currentTurnNumber: Int = 0,
    val canUndo: Boolean = false,
    // Dice
    val isDiceRolling: Boolean = false,
    val diceAnimValue: Int = 1,
    val diceResult: Int? = null,
    // Randomizer
    val randomOpponentId: Long? = null,
    val isRandomizing: Boolean = false,
    val randomizingDisplayName: String = "",
    // Optional counters
    val optionalCounterLabels: Map<Long, String> = emptyMap(),
    val showCounterLabelDialogFor: Long? = null,
    val poisonCounters: Map<Long, Int> = emptyMap(),
    val optionalCounters: Map<Long, Int> = emptyMap(),
    // Crown/Trash
    val crownPlayerId: Long? = null,
    val trashPlayerId: Long? = null,
    // Per-player color theme
    val playerThemes: Map<Long, Int> = emptyMap(),
    // Victory overlay
    val showVictoryFor: Long? = null
) {
    val currentTurnPlayer: ParticipantUiState? get() =
        participants.find { it.participant.id == currentTurnParticipantId }
}

private data class TurnHistoryEntry(
    val playerId: Long,
    val committedDeltas: Map<Long, Int>  // participantId → delta that was written to life_change_events
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

    private var isFirstLoad = true
    private val pendingLifeDeltas = mutableMapOf<Long, Int>()
    private val turnHistory = ArrayDeque<TurnHistoryEntry>()

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

                val isNewGame = game != null &&
                    (System.currentTimeMillis() - game.startedAt) < 30_000L &&
                    participants.all { it.currentLife == it.startingLife }
                val showStart = isFirstLoad && isNewGame
                isFirstLoad = false

                // Restore persisted turn from DB if available
                val persistedTurn = game?.currentTurnParticipantId
                val currentTurn = when {
                    cur.currentTurnParticipantId != null -> cur.currentTurnParticipantId
                    persistedTurn != null -> persistedTurn
                    else -> null
                }

                val (crown, trash) = computeCrownTrash(participantUiStates, emptyMap())
                _uiState.value = cur.copy(
                    game = game, participants = participantUiStates,
                    commanderDamage = damage, kills = kills,
                    isLoading = false, startingPlayerId = startingId,
                    showStartDialog = if (showStart) true else cur.showStartDialog,
                    currentTurnParticipantId = currentTurn,
                    crownPlayerId = crown, trashPlayerId = trash
                )
            }
        }
        viewModelScope.launch { refreshLeaderboardRanks() }
    }

    private suspend fun refreshLeaderboardRanks() {
        try {
            val allPlayers = playerRepository.getAllPlayers().firstOrNull() ?: return
            val stats = allPlayers.map { statsRepository.getPlayerStats(it.id, it.name) }
                .sortedByDescending { it.winRate }
            val rankMap = stats.mapIndexed { i, s -> s.playerId to (i + 1) }.toMap()
            val cur = _uiState.value
            val (crown, trash) = computeCrownTrash(cur.participants, rankMap)
            _uiState.value = cur.copy(crownPlayerId = crown, trashPlayerId = trash)
        } catch (_: Exception) {}
    }

    private fun computeCrownTrash(
        participants: List<ParticipantUiState>,
        rankMap: Map<Long, Int>
    ): Pair<Long?, Long?> {
        val active = participants.filter { !it.participant.isEliminated }
        if (active.size < 2 || rankMap.isEmpty()) return Pair(null, null)
        val withRank = active.mapNotNull { p ->
            val rank = rankMap[p.player.id] ?: return@mapNotNull null
            p to rank
        }
        if (withRank.isEmpty()) return Pair(null, null)
        val crown = withRank.minByOrNull { it.second }?.first?.participant?.id
        val trash = withRank.maxByOrNull { it.second }?.first?.participant?.id
        return Pair(if (crown != trash) crown else null, if (crown != trash) trash else null)
    }

    // ─── Life changes ─────────────────────────────────────────────────────────

    fun updateLife(participantId: Long, delta: Int) {
        viewModelScope.launch {
            val p = _uiState.value.participants.find { it.participant.id == participantId }
                ?.participant ?: return@launch
            gameRepository.updateParticipant(p.copy(currentLife = p.currentLife + delta))
        }
        // Buffer for end-of-turn commit (written on nextPlayer)
        pendingLifeDeltas[participantId] = (pendingLifeDeltas[participantId] ?: 0) + delta
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

    // ─── Turn tracking ────────────────────────────────────────────────────────

    private fun buildTurnOrder(all: List<ParticipantUiState>, clockwise: Boolean): List<Int> {
        val count = all.size
        val base = when (count) {
            4 -> listOf(0, 1, 3, 2)
            3 -> listOf(0, 1, 2)
            else -> listOf(0, 1)
        }.filter { it < count }
        return if (clockwise) base else base.reversed()
    }

    fun nextPlayer() {
        val state = _uiState.value
        val all = state.participants
        val clockwise = state.game?.turnClockwise ?: true
        val order = buildTurnOrder(all, clockwise)

        val newTurnNumber = state.currentTurnNumber + 1
        val currentId = state.currentTurnParticipantId

        val currentPos = if (currentId == null) -1
            else order.indexOfFirst { all.getOrNull(it)?.participant?.id == currentId }

        var nextPos = -1
        for (i in 1..order.size) {
            val candidate = ((if (currentPos < 0) 0 else currentPos) + i) % order.size
            val p = all.getOrNull(order[candidate]) ?: continue
            if (!p.participant.isEliminated) { nextPos = candidate; break }
        }
        if (nextPos < 0) return
        val nextId = all.getOrNull(order[nextPos])?.participant?.id ?: return

        // Commit buffered life changes as LifeChangeEvents
        val committed = pendingLifeDeltas.toMap()
        if (committed.isNotEmpty()) {
            viewModelScope.launch {
                committed.forEach { (pid, delta) ->
                    if (delta != 0) {
                        gameRepository.logLifeChange(
                            gameId = gameId, targetParticipantId = pid,
                            activeTurnParticipantId = currentId,
                            delta = delta, turnNumber = state.currentTurnNumber
                        )
                    }
                }
            }
        }
        // Push to undo history
        if (currentId != null) {
            turnHistory.addLast(TurnHistoryEntry(currentId, committed))
        }
        pendingLifeDeltas.clear()

        _uiState.value = state.copy(
            currentTurnParticipantId = nextId,
            currentTurnNumber = newTurnNumber,
            canUndo = turnHistory.isNotEmpty()
        )
        viewModelScope.launch {
            gameRepository.setCurrentTurnParticipant(gameId, nextId)
            refreshLeaderboardRanks()
        }
    }

    fun previousPlayer() {
        val state = _uiState.value

        // First: undo any pending changes from the CURRENT turn
        if (pendingLifeDeltas.isNotEmpty()) {
            val toReverse = pendingLifeDeltas.toMap()
            pendingLifeDeltas.clear()
            viewModelScope.launch {
                toReverse.forEach { (pid, delta) ->
                    if (delta != 0) {
                        val p = _uiState.value.participants.find { it.participant.id == pid }
                            ?.participant ?: return@forEach
                        gameRepository.updateParticipant(p.copy(currentLife = p.currentLife - delta))
                    }
                }
            }
        }

        if (turnHistory.isEmpty()) return
        val entry = turnHistory.removeLast()

        // Reverse committed changes from the previous turn
        viewModelScope.launch {
            entry.committedDeltas.forEach { (pid, delta) ->
                if (delta != 0) {
                    val p = _uiState.value.participants.find { it.participant.id == pid }
                        ?.participant ?: return@forEach
                    gameRepository.updateParticipant(p.copy(currentLife = p.currentLife - delta))
                }
            }
            gameRepository.setCurrentTurnParticipant(gameId, entry.playerId)
        }

        _uiState.value = state.copy(
            currentTurnParticipantId = entry.playerId,
            currentTurnNumber = (state.currentTurnNumber - 1).coerceAtLeast(0),
            canUndo = turnHistory.isNotEmpty()
        )
    }

    fun dismissVictory() {
        _uiState.value = _uiState.value.copy(showVictoryFor = null)
    }

    fun cyclePlayerTheme(participantId: Long) {
        val themes = _uiState.value.playerThemes.toMutableMap()
        val current = themes[participantId] ?: 0
        themes[participantId] = (current + 1) % 5
        _uiState.value = _uiState.value.copy(playerThemes = themes)
    }

    fun dismissStartDialog() {
        val startingId = _uiState.value.startingPlayerId
        _uiState.value = _uiState.value.copy(
            showStartDialog = false,
            currentTurnParticipantId = startingId ?: _uiState.value.currentTurnParticipantId
        )
        if (startingId != null) {
            viewModelScope.launch {
                gameRepository.setStartingParticipant(gameId, startingId)
            }
        }
    }

    // ─── Eliminate ───────────────────────────────────────────────────────────

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
            val winner = if (remaining.size == 1) remaining.first() else null
            if (winner != null) {
                gameRepository.updateParticipant(winner.copy(placement = 1))
                val game = gameRepository.getGameById(gameId)!!
                gameRepository.updateGame(game.copy(
                    status = "FINISHED", endedAt = System.currentTimeMillis()))
            }
            // Advance turn if eliminated player was active
            if (_uiState.value.currentTurnParticipantId == victimId && winner == null) nextPlayer()
            _uiState.value = _uiState.value.copy(
                showEliminateDialogFor = null,
                showVictoryFor = winner?.id
            )
        }
    }

    // ─── Dice ────────────────────────────────────────────────────────────────

    fun rollDice(rollerParticipantId: Long? = null) {
        if (_uiState.value.isDiceRolling) return
        viewModelScope.launch {
            val finalResult = Random.nextInt(1, 7)
            _uiState.value = _uiState.value.copy(isDiceRolling = true, diceResult = null)
            val end = System.currentTimeMillis() + 2000L
            while (System.currentTimeMillis() < end) {
                val remaining = end - System.currentTimeMillis()
                val intervalMs = when {
                    remaining > 1000 -> 60L
                    remaining > 400  -> 110L
                    else             -> 200L
                }
                _uiState.value = _uiState.value.copy(diceAnimValue = Random.nextInt(1, 7))
                delay(intervalMs)
            }
            _uiState.value = _uiState.value.copy(
                isDiceRolling = false, diceAnimValue = finalResult, diceResult = finalResult)
            // Persist roll
            val pid = rollerParticipantId ?: _uiState.value.currentTurnParticipantId
            if (pid != null) {
                gameRepository.logDiceRoll(gameId, pid, finalResult)
            }
        }
    }

    fun clearDice() {
        if (!_uiState.value.isDiceRolling)
            _uiState.value = _uiState.value.copy(diceResult = null)
    }

    // ─── Random Opponent (slot-machine + DB logging) ──────────────────────────

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
            val end = System.currentTimeMillis() + 2000L
            while (System.currentTimeMillis() < end) {
                val elapsed = 2000L - (end - System.currentTimeMillis())
                val intervalMs = when {
                    elapsed < 600  -> 70L
                    elapsed < 1400 -> 140L
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
            // Persist the random pick
            gameRepository.logRandomOpponentPick(
                gameId = gameId,
                chooserParticipantId = myParticipantId,
                chosenParticipantId = finalOpponent.participant.id
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
