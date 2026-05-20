package com.mtg.commander.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.ui.theme.EliminatedColor
import com.mtg.commander.ui.theme.WinnerColor
import com.mtg.commander.ui.viewmodel.ActiveGameUiState
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

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight
        val count = state.participants.size

        if (count >= 3) {
            RotatingLayout(vm, state, isFinished, onBack, W, H)
        } else {
            ScrollLayout(vm, state, isFinished, onBack)
        }
    }

    // ─── Globale Dialoge ──────────────────────────────────────────────────────

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
                    value = label, onValueChange = { label = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { vm.setOptionalCounterLabel(label) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = vm::dismissOptionalCounterLabelDialog) { Text("Abbrechen") } }
        )
    }

    state.diceResult?.let { result ->
        AlertDialog(
            onDismissRequest = vm::clearDice,
            title = { Text("Würfel", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Text("$result", fontSize = 72.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = vm::clearDice) { Text("OK") } }
        )
    }

    state.randomOpponentId?.let { id ->
        val opp = state.participants.find { it.participant.id == id }
        if (opp != null) {
            AlertDialog(
                onDismissRequest = vm::clearRandomOpponent,
                title = { Text("Zufälliger Gegner") },
                text = {
                    Text(opp.player.name, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                        Text("${starter.player.name} beginnt!", fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    },
                    confirmButton = { TextButton(onClick = { shown = false }) { Text("Los geht's!") } }
                )
            }
        }
    }
}

// ─── Rotierendes Layout (3-4 Spieler) ────────────────────────────────────────

@Composable
private fun RotatingLayout(
    vm: ActiveGameViewModel,
    state: ActiveGameUiState,
    isFinished: Boolean,
    onBack: () -> Unit,
    W: Dp,
    H: Dp
) {
    val halfW = W / 2
    val halfH = H / 2
    val p = state.participants

    Box(modifier = Modifier.fillMaxSize()) {
        when (p.size) {
            4 -> {
                // Unten-Links (0°)
                PlayerCell(vm, state, p[0], isFinished, 0f,
                    Modifier.size(halfW, halfH).align(Alignment.BottomStart))
                // Unten-Rechts (270° — Spieler sitzt rechts)
                PlayerCell(vm, state, p[1], isFinished, 270f,
                    Modifier.size(halfW, halfH).align(Alignment.BottomEnd))
                // Oben-Links (90° — Spieler sitzt links)
                PlayerCell(vm, state, p[2], isFinished, 90f,
                    Modifier.size(halfW, halfH).align(Alignment.TopStart))
                // Oben-Rechts (180°)
                PlayerCell(vm, state, p[3], isFinished, 180f,
                    Modifier.size(halfW, halfH).align(Alignment.TopEnd))
            }
            3 -> {
                // Unten-Links (0°)
                PlayerCell(vm, state, p[0], isFinished, 0f,
                    Modifier.size(halfW, halfH).align(Alignment.BottomStart))
                // Unten-Rechts (0°)
                PlayerCell(vm, state, p[1], isFinished, 0f,
                    Modifier.size(halfW, halfH).align(Alignment.BottomEnd))
                // Oben-Mitte (180°)
                PlayerCell(vm, state, p[2], isFinished, 180f,
                    Modifier.fillMaxWidth().height(halfH).align(Alignment.TopCenter))
            }
        }

        // Mittiger Aktionsbereich
        val centerSize = minOf(halfW, halfH) * 0.55f
        CenterActions(
            vm = vm, state = state, isFinished = isFinished, onBack = onBack,
            modifier = Modifier.size(centerSize).align(Alignment.Center)
        )
    }
}

@Composable
private fun PlayerCell(
    vm: ActiveGameViewModel,
    state: ActiveGameUiState,
    pState: ParticipantUiState,
    isFinished: Boolean,
    rotation: Float,
    modifier: Modifier
) {
    Box(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        Box(modifier = Modifier.fillMaxSize().rotate(rotation)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                MiniPlayerPanel(
                    vm = vm, state = state, pState = pState,
                    isFinished = isFinished,
                    cellW = maxWidth, cellH = maxHeight
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerPanel(
    vm: ActiveGameViewModel,
    state: ActiveGameUiState,
    pState: ParticipantUiState,
    isFinished: Boolean,
    cellW: Dp,
    cellH: Dp
) {
    val p = pState.participant
    val isWinner = p.placement == 1
    val isRandom = state.randomOpponentId == p.id

    val lifeSize = (cellH.value * 0.13f).coerceIn(20f, 52f).sp
    val nameSize = (cellH.value * 0.055f).coerceIn(9f, 16f).sp
    val smallSize = (cellH.value * 0.038f).coerceIn(7f, 12f).sp
    val btnSize = (cellH.value * 0.09f).coerceIn(20f, 40f).dp

    val bgColor = when {
        isWinner -> WinnerColor.copy(alpha = 0.18f)
        p.isEliminated -> EliminatedColor.copy(alpha = 0.12f)
        isRandom -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        isWinner -> BorderStroke(2.dp, WinnerColor)
        isRandom -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    Surface(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = border
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Name + Status
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()) {
                if (isWinner) Text("★ ", color = WinnerColor, fontWeight = FontWeight.Bold, fontSize = nameSize)
                if (state.startingPlayerId == p.id && !p.isEliminated)
                    Text("▶ ", color = MaterialTheme.colorScheme.primary, fontSize = smallSize)
                Text(pState.player.name, fontWeight = FontWeight.Bold, fontSize = nameSize,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (p.isEliminated) EliminatedColor else MaterialTheme.colorScheme.onSurface)
                pState.deck?.let { d ->
                    Text(" · ${d.commanderName}", fontSize = smallSize,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Lebenspunkte
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                if (!p.isEliminated && !isFinished) {
                    MiniBtn("-5", btnSize, smallSize) { vm.updateLife(p.id, -5) }
                    MiniBtn("-1", btnSize, smallSize) { vm.updateLife(p.id, -1) }
                }
                Text(
                    "${p.currentLife}",
                    fontSize = lifeSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = when {
                        p.currentLife <= 0 -> MaterialTheme.colorScheme.error
                        p.currentLife <= 10 -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (!p.isEliminated && !isFinished) {
                    MiniBtn("+1", btnSize, smallSize) { vm.updateLife(p.id, +1) }
                    MiniBtn("+5", btnSize, smallSize) { vm.updateLife(p.id, +5) }
                }
            }

            // Counters: Gift / Optional / CMD
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                InlineCounter("Gift", pState.poisonCounters, pState.poisonCounters >= 10, smallSize,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, -1) }) else null,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, +1) }) else null)
                InlineCounter(state.optionalCounterLabel, pState.optionalCounter, false, smallSize,
                    if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, -1) }) else null,
                    if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, +1) }) else null)
                val totalCmd = pState.commanderDamageReceived.values.sum()
                if (totalCmd > 0) {
                    Text("CMD:$totalCmd", fontSize = smallSize,
                        color = if (totalCmd >= 21) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (totalCmd >= 21) FontWeight.Bold else FontWeight.Normal)
                }
            }

            // Aktions-Buttons
            if (!p.isEliminated && !isFinished) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { vm.showCommanderDamagePanel(if (state.showCommanderDamageFor == p.id) null else p.id) },
                        modifier = Modifier.height(btnSize),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) { Text("CMD", fontSize = smallSize) }
                    Button(
                        onClick = { vm.showEliminateDialog(p.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(btnSize),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) { Text("✕", fontSize = smallSize) }
                }
            }

            // Commander-Schaden Detail (aufgeklappt)
            if (state.showCommanderDamageFor == p.id) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                state.participants.filter { it.participant.id != p.id }.forEach { att ->
                    val dmg = pState.commanderDamageReceived[att.participant.id] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(att.player.name, fontSize = smallSize, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (dmg >= 21) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface)
                        Text("$dmg${if (dmg >= 21) "⚠" else ""}",
                            fontSize = smallSize, fontWeight = FontWeight.Bold,
                            color = if (dmg >= 21) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface)
                        if (!p.isEliminated && !isFinished) {
                            MiniBtn("-", btnSize * 0.75f, smallSize) {
                                vm.updateCommanderDamage(att.participant.id, p.id, -1)
                            }
                            MiniBtn("+", btnSize * 0.75f, smallSize) {
                                vm.updateCommanderDamage(att.participant.id, p.id, +1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterActions(
    vm: ActiveGameViewModel,
    state: ActiveGameUiState,
    isFinished: Boolean,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val hasWinner = state.participants.any { it.participant.placement == 1 }
    val active = state.participants.filter { !it.participant.isEliminated }

    Card(modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(4.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = vm::rollDice, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Casino, "Würfel", modifier = Modifier.fillMaxSize())
            }
            IconButton(onClick = {
                if (active.isNotEmpty()) vm.randomizeOpponent(active.first().participant.id)
            }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Shuffle, "Zufall", modifier = Modifier.fillMaxSize())
            }
            IconButton(onClick = vm::showOptionalCounterLabelDialog,
                modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Edit, "Counter", modifier = Modifier.fillMaxSize())
            }
            if (!isFinished) {
                IconButton(onClick = { if (hasWinner) vm.showEndGameConfirm() },
                    modifier = Modifier.size(28.dp), enabled = hasWinner) {
                    Icon(Icons.Filled.Stop, "Beenden",
                        modifier = Modifier.fillMaxSize(),
                        tint = if (hasWinner) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ArrowBack, "Zurück", modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// ─── Scroll-Layout (2 Spieler) ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollLayout(
    vm: ActiveGameViewModel,
    state: ActiveGameUiState,
    isFinished: Boolean,
    onBack: () -> Unit
) {
    val hasWinner = state.participants.any { it.participant.placement == 1 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFinished) "Spiel beendet" else "Laufende Partie") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = vm::rollDice) { Icon(Icons.Filled.Casino, "Würfel") }
                    IconButton(onClick = {
                        val active = state.participants.filter { !it.participant.isEliminated }
                        if (active.isNotEmpty()) vm.randomizeOpponent(active.first().participant.id)
                    }) { Icon(Icons.Filled.Shuffle, "Zufall") }
                    IconButton(onClick = vm::showOptionalCounterLabelDialog) {
                        Icon(Icons.Filled.Edit, "Counter")
                    }
                    if (!isFinished) {
                        TextButton(onClick = vm::showEndGameConfirm, enabled = hasWinner) {
                            Text("Beenden")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.participants.forEach { pState ->
                FullPlayerCard(vm, state, pState, isFinished)
            }
        }
    }
}

@Composable
private fun FullPlayerCard(
    vm: ActiveGameViewModel,
    state: ActiveGameUiState,
    pState: ParticipantUiState,
    isFinished: Boolean
) {
    val p = pState.participant
    val isWinner = p.placement == 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isWinner -> WinnerColor.copy(alpha = 0.15f)
                p.isEliminated -> EliminatedColor.copy(alpha = 0.12f)
                state.randomOpponentId == p.id -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = when {
            isWinner -> BorderStroke(2.dp, WinnerColor)
            state.randomOpponentId == p.id -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else -> null
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isWinner) Text("★ ", color = WinnerColor, fontWeight = FontWeight.Bold)
                        if (state.startingPlayerId == p.id && !p.isEliminated)
                            Text("▶ ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        Text(pState.player.name, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    pState.deck?.let { d ->
                        Text("${d.name} · ${d.commanderName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    } ?: Text("Ohne Deck", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p.isEliminated) Text("Eliminiert – Platz ${p.placement}",
                        style = MaterialTheme.typography.bodySmall, color = EliminatedColor)
                }
            }

            // Leben
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                if (!p.isEliminated && !isFinished) {
                    LifeBtn("-5") { vm.updateLife(p.id, -5) }
                    LifeBtn("-1") { vm.updateLife(p.id, -1) }
                }
                Text("${p.currentLife}", fontSize = 52.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = if (p.currentLife <= 10) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface)
                if (!p.isEliminated && !isFinished) {
                    LifeBtn("+1") { vm.updateLife(p.id, +1) }
                    LifeBtn("+5") { vm.updateLife(p.id, +5) }
                }
            }

            // Counters
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                InlineCounter("Gift", pState.poisonCounters, pState.poisonCounters >= 10, 12.sp,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, -1) }) else null,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, +1) }) else null)
                InlineCounter(state.optionalCounterLabel, pState.optionalCounter, false, 12.sp,
                    if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, -1) }) else null,
                    if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, +1) }) else null)
                val totalCmd = pState.commanderDamageReceived.values.sum()
                if (totalCmd > 0) Text("CMD: $totalCmd${if (totalCmd >= 21) " ⚠" else ""}",
                    fontSize = 12.sp,
                    color = if (totalCmd >= 21) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!p.isEliminated && !isFinished) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.showCommanderDamagePanel(if (state.showCommanderDamageFor == p.id) null else p.id) },
                        modifier = Modifier.weight(1f)
                    ) { Text("CMD-Schaden") }
                    Button(
                        onClick = { vm.showEliminateDialog(p.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Eliminieren") }
                }
            }

            if (state.showCommanderDamageFor == p.id) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Commander-Schaden von:",
                    style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                state.participants.filter { it.participant.id != p.id }.forEach { att ->
                    val dmg = pState.commanderDamageReceived[att.participant.id] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(att.player.name, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dmg >= 21) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface)
                        Text("$dmg${if (dmg >= 21) " ⚠" else ""}", fontWeight = FontWeight.Bold,
                            color = if (dmg >= 21) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface)
                        if (!p.isEliminated && !isFinished) {
                            LifeBtn("-") { vm.updateCommanderDamage(att.participant.id, p.id, -1) }
                            LifeBtn("+") { vm.updateCommanderDamage(att.participant.id, p.id, +1) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Hilfkomponenten ─────────────────────────────────────────────────────────

@Composable
private fun InlineCounter(
    label: String,
    value: Int,
    warn: Boolean,
    labelSize: androidx.compose.ui.unit.TextUnit,
    onMinus: (() -> Unit)?,
    onPlus: (() -> Unit)?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onMinus != null) {
            TextButton(onClick = onMinus, modifier = Modifier.size(22.dp),
                contentPadding = PaddingValues(0.dp)) {
                Text("-", fontSize = labelSize, fontWeight = FontWeight.Bold)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontWeight = FontWeight.Bold, fontSize = labelSize,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = (labelSize.value * 0.8f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onPlus != null) {
            TextButton(onClick = onPlus, modifier = Modifier.size(22.dp),
                contentPadding = PaddingValues(0.dp)) {
                Text("+", fontSize = labelSize, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniBtn(text: String, size: Dp, textSize: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit) {
    TextButton(onClick = onClick,
        modifier = Modifier.size(size),
        contentPadding = PaddingValues(0.dp)) {
        Text(text, fontSize = textSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LifeBtn(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.size(44.dp),
        contentPadding = PaddingValues(0.dp)) {
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
                otherParticipants.forEach { ps ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedKillerId == ps.participant.id,
                            onClick = { selectedKillerId = ps.participant.id })
                        Text(ps.player.name)
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
