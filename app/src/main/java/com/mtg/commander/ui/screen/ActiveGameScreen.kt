package com.mtg.commander.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.ui.theme.EliminatedColor
import com.mtg.commander.ui.theme.WinnerColor
import com.mtg.commander.ui.viewmodel.ActiveGameViewModel
import com.mtg.commander.ui.viewmodel.ParticipantUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveGameScreen(gameId: Long, app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: ActiveGameViewModel = viewModel(
        key = "active_game_$gameId",
        factory = ActiveGameViewModel.factory(gameId, app)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val isFinished = state.game?.isFinished == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFinished) "Spiel beendet" else "Laufende Partie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurueck") }
                },
                actions = {
                    if (!isFinished) {
                        val hasWinner = state.participants.any { it.participant.placement == 1 }
                        TextButton(
                            onClick = vm::showEndGameConfirm,
                            enabled = hasWinner
                        ) { Text("Beenden") }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.participants, key = { it.participant.id }) { pState ->
                    ParticipantCard(
                        pState = pState,
                        allParticipants = state.participants,
                        isGameFinished = isFinished,
                        showCommanderDamage = state.showCommanderDamageFor == pState.participant.id,
                        onLifeChange = { delta -> vm.updateLife(pState.participant.id, delta) },
                        onToggleCommanderDamage = {
                            vm.showCommanderDamagePanel(
                                if (state.showCommanderDamageFor == pState.participant.id) null
                                else pState.participant.id
                            )
                        },
                        onCommanderDamage = { attackerId, delta ->
                            vm.updateCommanderDamage(attackerId, pState.participant.id, delta)
                        },
                        onEliminate = { vm.showEliminateDialog(pState.participant.id) }
                    )
                }
            }
        }
    }

    if (state.showEliminateDialogFor != null) {
        val victim = state.participants.find { it.participant.id == state.showEliminateDialogFor }
        if (victim != null) {
            EliminateDialog(
                victimName = victim.player.name,
                otherParticipants = state.participants.filter {
                    it.participant.id != victim.participant.id && !it.participant.isEliminated
                },
                onConfirm = { killerId -> vm.eliminatePlayer(victim.participant.id, killerId) },
                onDismiss = vm::dismissEliminateDialog
            )
        }
    }

    if (state.showEndGameConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissEndGameConfirm,
            title = { Text("Spiel beenden?") },
            text = { Text("Das Spiel wird als abgeschlossen markiert.") },
            confirmButton = {
                TextButton(onClick = vm::endGame) { Text("Beenden") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissEndGameConfirm) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun ParticipantCard(
    pState: ParticipantUiState,
    allParticipants: List<ParticipantUiState>,
    isGameFinished: Boolean,
    showCommanderDamage: Boolean,
    onLifeChange: (Int) -> Unit,
    onToggleCommanderDamage: () -> Unit,
    onCommanderDamage: (Long, Int) -> Unit,
    onEliminate: () -> Unit
) {
    val p = pState.participant
    val isWinner = p.placement == 1
    val cardColor = when {
        isWinner -> WinnerColor.copy(alpha = 0.15f)
        p.isEliminated -> EliminatedColor.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isWinner -> WinnerColor
        p.isEliminated -> EliminatedColor
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (borderColor != Color.Transparent) BorderStroke(2.dp, borderColor) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isWinner) Text("SIEGER  ", color = WinnerColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (p.isEliminated && !isWinner) Text("ELIMINIERT (Platz ${p.placement})  ", color = EliminatedColor, fontSize = 12.sp)
                        Text(pState.player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("${pState.deck.name} | ${pState.deck.commanderName}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!p.isEliminated && !isGameFinished) {
                    IconButton(onClick = { onLifeChange(-5) }) {
                        Text("-5", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { onLifeChange(-1) }) {
                        Text("-1", color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    text = "${p.currentLife}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = when {
                        p.currentLife <= 0 -> MaterialTheme.colorScheme.error
                        p.currentLife <= 10 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (!p.isEliminated && !isGameFinished) {
                    IconButton(onClick = { onLifeChange(1) }) {
                        Text("+1", color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onLifeChange(5) }) {
                        Text("+5", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (!p.isEliminated && !isGameFinished) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleCommanderDamage,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cmdr-Schaden", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onEliminate,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminieren", fontSize = 12.sp)
                    }
                }
            }

            if (showCommanderDamage) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Commander-Schaden erhalten:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                allParticipants.filter { it.participant.id != p.id }.forEach { attacker ->
                    val dmg = pState.commanderDamageReceived[attacker.participant.id] ?: 0
                    val isOver21 = dmg >= 21
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            attacker.player.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOver21) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        if (isOver21) Text("!! ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(
                            "$dmg",
                            fontWeight = FontWeight.Bold,
                            color = if (isOver21) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        if (!p.isEliminated && !isGameFinished) {
                            IconButton(onClick = { onCommanderDamage(attacker.participant.id, -1) }, modifier = Modifier.size(32.dp)) {
                                Text("-", fontSize = 18.sp)
                            }
                            IconButton(onClick = { onCommanderDamage(attacker.participant.id, 1) }, modifier = Modifier.size(32.dp)) {
                                Text("+", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            if (pState.commanderDamageReceived.any { it.value >= 21 }) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Warnung: 21+ Commander-Schaden!",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EliminateDialog(
    victimName: String,
    otherParticipants: List<ParticipantUiState>,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKillerId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$victimName eliminieren") },
        text = {
            Column {
                Text("Wer hat $victimName eliminiert?")
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedKillerId == null, onClick = { selectedKillerId = null })
                    Text("Unbekannt / Selbstverschuldet")
                }
                otherParticipants.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedKillerId == p.participant.id,
                            onClick = { selectedKillerId = p.participant.id }
                        )
                        Text(p.player.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedKillerId) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Eliminieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
