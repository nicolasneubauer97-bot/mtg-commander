package com.mtg.commander.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val count = state.participants.size

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    when (count) {
        3, 4 -> RotatingLayout(vm = vm, state = state, isFinished = isFinished, onBack = onBack)
        else -> ScrollLayout(vm = vm, state = state, isFinished = isFinished, onBack = onBack)
    }

    // Dialoge
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
            confirmButton = { TextButton(onClick = vm::endGame) { Text("Beenden") } },
            dismissButton = { TextButton(onClick = vm::dismissEndGameConfirm) { Text("Abbrechen") } }
        )
    }

    if (state.showOptionalCounterLabelDialog) {
        var label by remember { mutableStateOf(state.optionalCounterLabel) }
        AlertDialog(
            onDismissRequest = vm::dismissOptionalCounterLabelDialog,
            title = { Text("Counter benennen") },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { vm.setOptionalCounterLabel(label) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = vm::dismissOptionalCounterLabelDialog) { Text("Abbrechen") } }
        )
    }

    state.diceResult?.let { result ->
        AlertDialog(
            onDismissRequest = vm::clearDice,
            title = { Text("Wuerfel", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Text(
                    text = "$result",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = vm::clearDice) { Text("OK") } }
        )
    }

    state.randomOpponentId?.let { id ->
        val opponent = state.participants.find { it.participant.id == id }
        if (opponent != null) {
            AlertDialog(
                onDismissRequest = vm::clearRandomOpponent,
                title = { Text("Zufaelliger Gegner") },
                text = {
                    Text(
                        opponent.player.name,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = { TextButton(onClick = vm::clearRandomOpponent) { Text("OK") } }
            )
        }
    }

    if (state.startingPlayerId != null && !isFinished) {
        val starter = state.participants.find { it.participant.id == state.startingPlayerId }
        if (starter != null && state.participants.none { it.participant.isEliminated }) {
            var shown by remember { mutableStateOf(true) }
            if (shown) {
                AlertDialog(
                    onDismissRequest = { shown = false },
                    title = { Text("Wer beginnt?") },
                    text = {
                        Text(
                            "${starter.player.name} beginnt!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = { TextButton(onClick = { shown = false }) { Text("Los geht's!") } }
                )
            }
        }
    }
}

// ─── Rotierendes 4-Spieler-Layout ───────────────────────────────────────────

@Composable
private fun RotatingLayout(
    vm: ActiveGameViewModel,
    state: com.mtg.commander.ui.viewmodel.ActiveGameUiState,
    isFinished: Boolean,
    onBack: () -> Unit
) {
    val participants = state.participants
    val count = participants.size

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (count) {
            4 -> {
                // Top (rotiert 180°)
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.TopCenter)
                        .rotate(180f)
                ) {
                    MiniPlayerPanel(vm, state, participants[1], isFinished, Modifier.fillMaxSize())
                }
                // Bottom
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
                ) {
                    MiniPlayerPanel(vm, state, participants[0], isFinished, Modifier.fillMaxSize())
                }
                // Left (rotiert 90°)
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight().align(Alignment.CenterStart)
                        .rotate(90f)
                ) {
                    MiniPlayerPanel(vm, state, participants[2], isFinished, Modifier.fillMaxSize())
                }
                // Right (rotiert 270°)
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight().align(Alignment.CenterEnd)
                        .rotate(270f)
                ) {
                    MiniPlayerPanel(vm, state, participants[3], isFinished, Modifier.fillMaxSize())
                }
            }
            3 -> {
                // Top (rotiert 180°)
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.TopCenter)
                        .rotate(180f)
                ) {
                    MiniPlayerPanel(vm, state, participants[1], isFinished, Modifier.fillMaxSize())
                }
                // Bottom-Left
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(0.5f).align(Alignment.BottomStart)
                ) {
                    MiniPlayerPanel(vm, state, participants[0], isFinished, Modifier.fillMaxSize())
                }
                // Bottom-Right
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(0.5f).align(Alignment.BottomEnd)
                ) {
                    MiniPlayerPanel(vm, state, participants[2], isFinished, Modifier.fillMaxSize())
                }
            }
        }
        // Mittiger Aktionsbereich
        CenterActions(vm = vm, state = state, isFinished = isFinished, onBack = onBack,
            modifier = Modifier.size(120.dp).align(Alignment.Center))
    }
}

@Composable
private fun MiniPlayerPanel(
    vm: ActiveGameViewModel,
    state: com.mtg.commander.ui.viewmodel.ActiveGameUiState,
    pState: ParticipantUiState,
    isFinished: Boolean,
    modifier: Modifier = Modifier
) {
    val p = pState.participant
    val isWinner = p.placement == 1
    val bgColor = when {
        isWinner -> WinnerColor.copy(alpha = 0.18f)
        p.isEliminated -> EliminatedColor.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier.padding(2.dp),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = if (isWinner) BorderStroke(2.dp, WinnerColor)
                 else if (state.randomOpponentId == p.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Name + Status
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (isWinner) Text("★ ", color = WinnerColor, fontWeight = FontWeight.Bold)
                if (state.startingPlayerId == p.id && !p.isEliminated)
                    Text("▶ ", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                Text(
                    pState.player.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    color = if (p.isEliminated) EliminatedColor else MaterialTheme.colorScheme.onSurface
                )
            }

            // Lebenspunkte
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!p.isEliminated && !isFinished) {
                    SmallCounterBtn("-5") { vm.updateLife(p.id, -5) }
                    SmallCounterBtn("-1") { vm.updateLife(p.id, -1) }
                }
                Text(
                    "${p.currentLife}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        p.currentLife <= 0 -> MaterialTheme.colorScheme.error
                        p.currentLife <= 10 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                if (!p.isEliminated && !isFinished) {
                    SmallCounterBtn("+1") { vm.updateLife(p.id, +1) }
                    SmallCounterBtn("+5") { vm.updateLife(p.id, +5) }
                }
            }

            // Counter-Reihe: Gift / Optional / CMDSchaden-Summe
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Gift
                CounterChip(
                    label = "Gift",
                    value = pState.poisonCounters,
                    warn = pState.poisonCounters >= 10,
                    onMinus = if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, -1) }) else null,
                    onPlus = if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, +1) }) else null
                )
                // Optionaler Counter
                CounterChip(
                    label = state.optionalCounterLabel,
                    value = pState.optionalCounter,
                    warn = false,
                    onMinus = if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, -1) }) else null,
                    onPlus = if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, +1) }) else null
                )
            }

            // Commander-Schaden empfangen
            val totalCmdDmg = pState.commanderDamageReceived.values.sum()
            if (totalCmdDmg > 0 || pState.commanderDamageReceived.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CMD: $totalCmdDmg", fontSize = 11.sp,
                        color = if (totalCmdDmg >= 21) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Aktionsbuttons
            if (!p.isEliminated && !isFinished) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallActionBtn("CMD") { vm.showCommanderDamagePanel(
                        if (state.showCommanderDamageFor == p.id) null else p.id
                    ) }
                    SmallActionBtn("X", isRed = true) { vm.showEliminateDialog(p.id) }
                }
            }

            // Commander-Schaden Detail
            if (state.showCommanderDamageFor == p.id) {
                CommanderDamageDetail(vm, state, pState, isFinished)
            }
        }
    }
}

@Composable
private fun CenterActions(
    vm: ActiveGameViewModel,
    state: com.mtg.commander.ui.viewmodel.ActiveGameUiState,
    isFinished: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasWinner = state.participants.any { it.participant.placement == 1 }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = vm::rollDice, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Casino, "Wuerfel", modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = {
                val active = state.participants.filter { !it.participant.isEliminated }
                if (active.isNotEmpty()) vm.randomizeOpponent(active.first().participant.id)
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Shuffle, "Zufaellig", modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = vm::showOptionalCounterLabelDialog, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Edit, "Counter benennen", modifier = Modifier.size(18.dp))
            }
            if (!isFinished) {
                IconButton(onClick = { if (hasWinner) vm.showEndGameConfirm() }, modifier = Modifier.size(36.dp),
                    enabled = hasWinner) {
                    Icon(Icons.Filled.Stop, "Beenden", modifier = Modifier.size(18.dp),
                        tint = if (hasWinner) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowBack, "Zurueck", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Scroll-Layout für 2 Spieler ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollLayout(
    vm: ActiveGameViewModel,
    state: com.mtg.commander.ui.viewmodel.ActiveGameUiState,
    isFinished: Boolean,
    onBack: () -> Unit
) {
    val hasWinner = state.participants.any { it.participant.placement == 1 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFinished) "Spiel beendet" else "Laufende Partie") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurueck") } },
                actions = {
                    IconButton(onClick = vm::rollDice) { Icon(Icons.Filled.Casino, "Wuerfel") }
                    IconButton(onClick = {
                        val active = state.participants.filter { !it.participant.isEliminated }
                        if (active.isNotEmpty()) vm.randomizeOpponent(active.first().participant.id)
                    }) { Icon(Icons.Filled.Shuffle, "Zufaellig") }
                    if (!isFinished) {
                        TextButton(onClick = vm::showEndGameConfirm, enabled = hasWinner) { Text("Beenden") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            state.participants.forEach { pState ->
                FullPlayerCard(vm, state, pState, isFinished, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FullPlayerCard(
    vm: ActiveGameViewModel,
    state: com.mtg.commander.ui.viewmodel.ActiveGameUiState,
    pState: ParticipantUiState,
    isFinished: Boolean,
    modifier: Modifier = Modifier
) {
    val p = pState.participant
    val isWinner = p.placement == 1
    val cardColor = when {
        isWinner -> WinnerColor.copy(alpha = 0.15f)
        p.isEliminated -> EliminatedColor.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = when {
            isWinner -> BorderStroke(2.dp, WinnerColor)
            state.randomOpponentId == p.id -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else -> null
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isWinner) Text("★ SIEGER  ", color = WinnerColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (p.isEliminated && !isWinner) Text("ELIMINIERT (P${p.placement})  ", color = EliminatedColor, fontSize = 11.sp)
                if (state.startingPlayerId == p.id && !p.isEliminated)
                    Text("▶ BEGINNT  ", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                Text(pState.player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(pState.deck.commanderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(8.dp))
            // Leben
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (!p.isEliminated && !isFinished) {
                    LifeBtn("-5") { vm.updateLife(p.id, -5) }
                    LifeBtn("-1") { vm.updateLife(p.id, -1) }
                }
                Text("${p.currentLife}", fontSize = 52.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = if (p.currentLife <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (!p.isEliminated && !isFinished) {
                    LifeBtn("+1") { vm.updateLife(p.id, +1) }
                    LifeBtn("+5") { vm.updateLife(p.id, +5) }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Counters
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CounterChip("Gift", pState.poisonCounters, pState.poisonCounters >= 10,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, -1) }) else null,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, +1) }) else null)
                CounterChip(state.optionalCounterLabel, pState.optionalCounter, false,
                    if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, -1) }) else null,
                    if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, +1) }) else null)
            }
            // Commander-Schaden Übersicht
            val totalCmdDmg = pState.commanderDamageReceived.values.sum()
            if (totalCmdDmg > 0) {
                Text("CMD-Schaden gesamt: $totalCmdDmg${if (totalCmdDmg >= 21) " ⚠" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (totalCmdDmg >= 21) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!p.isEliminated && !isFinished) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.showCommanderDamagePanel(if (state.showCommanderDamageFor == p.id) null else p.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Shield, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CMD-Schaden", fontSize = 12.sp)
                    }
                    Button(onClick = { vm.showEliminateDialog(p.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminieren", fontSize = 12.sp)
                    }
                }
            }
            if (state.showCommanderDamageFor == p.id) {
                Spacer(Modifier.height(8.dp))
                CommanderDamageDetail(vm, state, pState, isFinished)
            }
        }
    }
}

// ─── Gemeinsame Hilfkomponenten ──────────────────────────────────────────────

@Composable
private fun CommanderDamageDetail(
    vm: ActiveGameViewModel,
    state: com.mtg.commander.ui.viewmodel.ActiveGameUiState,
    pState: ParticipantUiState,
    isFinished: Boolean
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("Commander-Schaden von:", style = MaterialTheme.typography.labelSmall)
    state.participants.filter { it.participant.id != pState.participant.id }.forEach { attacker ->
        val dmg = pState.commanderDamageReceived[attacker.participant.id] ?: 0
        val over21 = dmg >= 21
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(attacker.player.name, modifier = Modifier.weight(1f), fontSize = 12.sp,
                color = if (over21) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (over21) Text("⚠ ", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("$dmg", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = if (over21) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (!pState.participant.isEliminated && !isFinished) {
                SmallCounterBtn("-") { vm.updateCommanderDamage(attacker.participant.id, pState.participant.id, -1) }
                SmallCounterBtn("+") { vm.updateCommanderDamage(attacker.participant.id, pState.participant.id, +1) }
            }
        }
    }
}

@Composable
private fun CounterChip(
    label: String,
    value: Int,
    warn: Boolean,
    onMinus: (() -> Unit)?,
    onPlus: (() -> Unit)?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onMinus != null) SmallCounterBtn("-") { onMinus() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onPlus != null) SmallCounterBtn("+") { onPlus() }
    }
}

@Composable
private fun SmallCounterBtn(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallActionBtn(text: String, isRed: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = if (isRed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                 else ButtonDefaults.buttonColors()
    ) {
        Text(text, fontSize = 11.sp)
    }
}

@Composable
private fun LifeBtn(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedKillerId == null, onClick = { selectedKillerId = null })
                    Text("Unbekannt / Selbst")
                }
                otherParticipants.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedKillerId == p.participant.id,
                            onClick = { selectedKillerId = p.participant.id })
                        Text(p.player.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedKillerId) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Eliminieren")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
