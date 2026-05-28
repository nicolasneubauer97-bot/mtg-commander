package com.mtg.commander.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
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
        if (state.participants.size >= 3) {
            RotatingLayout(vm, state, isFinished, onBack, W, H)
        } else {
            ScrollLayout(vm, state, isFinished, onBack)
        }
    }

    // ─── Würfel-Overlay ───────────────────────────────────────────────────────
    if (state.isDiceRolling || state.diceResult != null) {
        DiceOverlay(
            value = state.diceAnimValue,
            isRolling = state.isDiceRolling,
            onDismiss = { if (!state.isDiceRolling) vm.clearDice() }
        )
    }

    // ─── Dialoge ─────────────────────────────────────────────────────────────
    if (state.showEliminateDialogFor != null) {
        val victim = state.participants.find { it.participant.id == state.showEliminateDialogFor }
        if (victim != null) {
            EliminateDialog(
                victimName = victim.player.name,
                others = state.participants.filter {
                    it.participant.id != victim.participant.id && !it.participant.isEliminated
                },
                onConfirm = { vm.eliminatePlayer(victim.participant.id, it) },
                onDismiss = vm::dismissEliminateDialog
            )
        }
    }
    if (state.showEndGameConfirm) {
        AlertDialog(onDismissRequest = vm::dismissEndGameConfirm,
            title = { Text("Spiel beenden?") },
            text = { Text("Das Spiel wird als abgeschlossen markiert.") },
            confirmButton = { TextButton(onClick = vm::endGame) { Text("Beenden") } },
            dismissButton = { TextButton(onClick = vm::dismissEndGameConfirm) { Text("Abbrechen") } }
        )
    }
    if (state.showCounterLabelDialogFor != null) {
        val pid = state.showCounterLabelDialogFor!!
        val current = state.optionalCounterLabels[pid] ?: "Bonus"
        var label by remember(pid) { mutableStateOf(current) }
        AlertDialog(onDismissRequest = vm::dismissCounterLabelDialog,
            title = { Text("Bonus-Counter benennen") },
            text = {
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { vm.setOptionalCounterLabel(pid, label) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = vm::dismissCounterLabelDialog) { Text("Abbrechen") } }
        )
    }
    state.randomOpponentId?.let { id ->
        val opp = state.participants.find { it.participant.id == id }
        if (opp != null) {
            AlertDialog(onDismissRequest = vm::clearRandomOpponent,
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
                AlertDialog(onDismissRequest = { shown = false },
                    title = { Text("Wer beginnt?") },
                    text = {
                        Text("${starter.player.name} beginnt!", fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    },
                    confirmButton = { TextButton(onClick = { shown = false }) { Text("Los!") } }
                )
            }
        }
    }
}

// ─── Würfel-Overlay ──────────────────────────────────────────────────────────

@Composable
private fun DiceOverlay(value: Int, isRolling: Boolean, onDismiss: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isRolling) 1.1f else 1.0f,
        animationSpec = infiniteRepeatable(tween(200), RepeatMode.Reverse),
        label = "diceScale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !isRolling, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.size(220.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$value",
                    fontSize = 100.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.scale(if (isRolling) scale else 1f),
                    color = if (isRolling) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                )
                if (isRolling) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                    Spacer(Modifier.height(8.dp))
                    Text("Würfeln...", style = MaterialTheme.typography.bodySmall)
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text("Tippen zum Schließen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─── Rotierendes Layout ──────────────────────────────────────────────────────

@Composable
private fun RotatingLayout(
    vm: ActiveGameViewModel,
    state: ActiveGameUiState,
    isFinished: Boolean,
    onBack: () -> Unit,
    W: Dp, H: Dp
) {
    val halfW = W / 2
    val halfH = H / 2
    val p = state.participants
    var centerVisible by remember { mutableStateOf(true) }
    val centerSize = minOf(halfW, halfH) * 0.44f

    Box(Modifier.fillMaxSize()) {
        when (p.size) {
            4 -> {
                PlayerCell(vm, state, p[0], isFinished, 0f,
                    Modifier.size(halfW, halfH).align(Alignment.BottomStart))
                PlayerCell(vm, state, p[1], isFinished, 0f,
                    Modifier.size(halfW, halfH).align(Alignment.BottomEnd))
                PlayerCell(vm, state, p[2], isFinished, 180f,
                    Modifier.size(halfW, halfH).align(Alignment.TopStart))
                PlayerCell(vm, state, p[3], isFinished, 180f,
                    Modifier.size(halfW, halfH).align(Alignment.TopEnd))
            }
            3 -> {
                PlayerCell(vm, state, p[0], isFinished, 0f,
                    Modifier.size(halfW, halfH).align(Alignment.BottomStart))
                PlayerCell(vm, state, p[1], isFinished, 0f,
                    Modifier.size(halfW, halfH).align(Alignment.BottomEnd))
                PlayerCell(vm, state, p[2], isFinished, 180f,
                    Modifier.fillMaxWidth().height(halfH).align(Alignment.TopCenter))
            }
        }
        Box(Modifier.align(Alignment.Center)) {
            if (centerVisible) {
                CenterActions(vm, state, isFinished, onBack,
                    onHide = { centerVisible = false },
                    modifier = Modifier.size(centerSize))
            } else {
                FilledIconButton(
                    onClick = { centerVisible = true },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                ) {
                    Icon(Icons.Filled.Visibility, "Menü", Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerCell(
    vm: ActiveGameViewModel, state: ActiveGameUiState,
    pState: ParticipantUiState, isFinished: Boolean,
    rotation: Float, modifier: Modifier
) {
    Box(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxSize().rotate(rotation)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                MiniPlayerPanel(vm, state, pState, isFinished, maxWidth, maxHeight)
            }
        }
    }
}

@Composable
private fun MiniPlayerPanel(
    vm: ActiveGameViewModel, state: ActiveGameUiState,
    pState: ParticipantUiState, isFinished: Boolean,
    cellW: Dp, cellH: Dp
) {
    val p = pState.participant
    val isWinner = p.placement == 1
    val isRandom = state.randomOpponentId == p.id

    // Proportionale Grössen – nutzt verfügbaren Platz optimal
    val lifeFs  = (cellH.value * 0.20f).coerceIn(24f, 72f).sp
    val nameFs  = (cellH.value * 0.07f).coerceIn(10f, 20f).sp
    val smallFs = (cellH.value * 0.05f).coerceIn(8f, 14f).sp
    val tinyFs  = (cellH.value * 0.04f).coerceIn(7f, 11f).sp
    val btnH    = (cellH.value * 0.10f).coerceIn(22f, 44f).dp
    val iconSz  = (cellH.value * 0.08f).coerceIn(16f, 32f).dp

    val bgColor = when {
        isWinner -> WinnerColor.copy(alpha = 0.18f)
        p.isEliminated -> EliminatedColor.copy(alpha = 0.12f)
        isRandom -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        shape = RoundedCornerShape(8.dp), color = bgColor,
        border = when {
            isWinner       -> BorderStroke(2.dp, WinnerColor)
            p.isEliminated -> BorderStroke(1.dp, EliminatedColor.copy(alpha = 0.35f))
            isRandom       -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else           -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Name
            Row(horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                if (isWinner) Text("★ ", color = WinnerColor, fontWeight = FontWeight.Bold, fontSize = nameFs)
                if (state.startingPlayerId == p.id && !p.isEliminated)
                    Text("▶ ", color = MaterialTheme.colorScheme.primary, fontSize = tinyFs)
                Text(pState.player.name, fontWeight = FontWeight.Bold, fontSize = nameFs,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (p.isEliminated) EliminatedColor else MaterialTheme.colorScheme.onSurface)
                pState.deck?.let { d ->
                    Text(" · ${d.commanderName}", fontSize = tinyFs,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Lebenspunkte
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                if (!p.isEliminated && !isFinished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiniBtn("-10", btnH, smallFs) { vm.updateLife(p.id, -10) }
                        MiniBtn("-5",  btnH, smallFs) { vm.updateLife(p.id, -5) }
                        MiniBtn("-1",  btnH, smallFs) { vm.updateLife(p.id, -1) }
                    }
                }
                Text("${p.currentLife}", fontSize = lifeFs, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    color = when {
                        p.currentLife <= 0  -> MaterialTheme.colorScheme.error
                        p.currentLife <= 10 -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                        else -> MaterialTheme.colorScheme.onSurface
                    })
                if (!p.isEliminated && !isFinished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiniBtn("+10", btnH, smallFs) { vm.updateLife(p.id, +10) }
                        MiniBtn("+5",  btnH, smallFs) { vm.updateLife(p.id, +5) }
                        MiniBtn("+1",  btnH, smallFs) { vm.updateLife(p.id, +1) }
                    }
                }
            }

            // Counters: Gift / Bonus (mit Edit-Button pro Spieler)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                InlineCounter("Gift", pState.poisonCounters, pState.poisonCounters >= 10, smallFs,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, -1) }) else null,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, +1) }) else null)
                // Bonus mit Umbenennen-Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InlineCounter(pState.optionalCounterLabel, pState.optionalCounter, false, smallFs,
                        if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, -1) }) else null,
                        if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, +1) }) else null)
                    if (!isFinished) {
                        IconButton(onClick = { vm.showCounterLabelDialog(p.id) },
                            modifier = Modifier.size(iconSz)) {
                            Icon(Icons.Filled.Edit, "umbenennen",
                                modifier = Modifier.size(iconSz * 0.65f),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // CMD-Schaden-Summe
                val totalCmd = pState.commanderDamageReceived.values.sum()
                if (totalCmd > 0) {
                    Text("CMD:$totalCmd${if (totalCmd >= 21) "⚠" else ""}",
                        fontSize = tinyFs, fontWeight = FontWeight.Bold,
                        color = if (totalCmd >= 21) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Aktionsbuttons
            if (!p.isEliminated && !isFinished) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { vm.showCommanderDamagePanel(
                            if (state.showCommanderDamageFor == p.id) null else p.id) },
                        modifier = Modifier.height(btnH),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) { Text("CMD", fontSize = smallFs) }
                    Button(
                        onClick = { vm.showEliminateDialog(p.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(btnH),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) { Text("✕", fontSize = smallFs) }
                }
            }

            // Würfel / Randomizer (pro Spieler)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::rollDice,
                    modifier = Modifier.size(iconSz), enabled = !state.isDiceRolling) {
                    Icon(Icons.Filled.Casino, "Würfel", Modifier.size(iconSz * 0.75f))
                }
                IconButton(onClick = { vm.randomizeOpponent(p.id) },
                    modifier = Modifier.size(iconSz)) {
                    Icon(Icons.Filled.Shuffle, "Zufall", Modifier.size(iconSz * 0.75f))
                }
            }

            // CMD-Schaden-Detail (aufgeklappt)
            if (state.showCommanderDamageFor == p.id) {
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                state.participants.filter { it.participant.id != p.id }.forEach { att ->
                    val dmg = pState.commanderDamageReceived[att.participant.id] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(att.player.name, fontSize = tinyFs, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (dmg >= 21) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface)
                        Text("$dmg${if (dmg >= 21) "⚠" else ""}",
                            fontSize = tinyFs, fontWeight = FontWeight.Bold,
                            color = if (dmg >= 21) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface)
                        if (!p.isEliminated && !isFinished) {
                            MiniBtn("-", btnH * 0.75f, tinyFs) {
                                vm.updateCommanderDamage(att.participant.id, p.id, -1) }
                            MiniBtn("+", btnH * 0.75f, tinyFs) {
                                vm.updateCommanderDamage(att.participant.id, p.id, +1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterActions(
    vm: ActiveGameViewModel, state: ActiveGameUiState,
    isFinished: Boolean, onBack: () -> Unit,
    onHide: () -> Unit, modifier: Modifier
) {
    val hasWinner = state.participants.any { it.participant.placement == 1 }
    Card(modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(6.dp)) {
        Column(
            Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onHide, Modifier.size(24.dp)) {
                Icon(Icons.Filled.VisibilityOff, "Verstecken", Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isFinished) {
                IconButton(onClick = { if (hasWinner) vm.showEndGameConfirm() },
                    Modifier.size(28.dp), enabled = hasWinner) {
                    Icon(Icons.Filled.Stop, "Beenden", Modifier.fillMaxSize(),
                        tint = if (hasWinner) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onBack, Modifier.size(28.dp)) {
                Icon(Icons.Filled.ArrowBack, "Zurück", Modifier.fillMaxSize())
            }
        }
    }
}

// ─── Scroll-Layout (2 Spieler) ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollLayout(
    vm: ActiveGameViewModel, state: ActiveGameUiState,
    isFinished: Boolean, onBack: () -> Unit
) {
    val hasWinner = state.participants.any { it.participant.placement == 1 }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFinished) "Spiel beendet" else "Laufende Partie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                },
                actions = {
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
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(8.dp),
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
    vm: ActiveGameViewModel, state: ActiveGameUiState,
    pState: ParticipantUiState, isFinished: Boolean
) {
    val p = pState.participant
    val isWinner = p.placement == 1
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = when {
            isWinner -> WinnerColor.copy(alpha = 0.15f)
            p.isEliminated -> EliminatedColor.copy(alpha = 0.12f)
            state.randomOpponentId == p.id -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surface
        }),
        border = when {
            isWinner -> BorderStroke(2.dp, WinnerColor)
            state.randomOpponentId == p.id -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else -> null
        }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
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
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } ?: Text("Ohne Deck", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p.isEliminated) Text("Eliminiert – Platz ${p.placement}",
                        style = MaterialTheme.typography.bodySmall, color = EliminatedColor)
                }
            }
            // Leben
            Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                if (!p.isEliminated && !isFinished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LifeBtn("-10") { vm.updateLife(p.id, -10) }
                        LifeBtn("-5")  { vm.updateLife(p.id, -5) }
                        LifeBtn("-1")  { vm.updateLife(p.id, -1) }
                    }
                }
                Text("${p.currentLife}", fontSize = 72.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = if (p.currentLife <= 10) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface)
                if (!p.isEliminated && !isFinished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LifeBtn("+10") { vm.updateLife(p.id, +10) }
                        LifeBtn("+5")  { vm.updateLife(p.id, +5) }
                        LifeBtn("+1")  { vm.updateLife(p.id, +1) }
                    }
                }
            }
            // Counters
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                InlineCounter("Gift", pState.poisonCounters, pState.poisonCounters >= 10, 12.sp,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, -1) }) else null,
                    if (!p.isEliminated && !isFinished) ({ vm.updatePoison(p.id, +1) }) else null)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InlineCounter(pState.optionalCounterLabel, pState.optionalCounter, false, 12.sp,
                        if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, -1) }) else null,
                        if (!p.isEliminated && !isFinished) ({ vm.updateOptionalCounter(p.id, +1) }) else null)
                    if (!isFinished) {
                        IconButton(onClick = { vm.showCounterLabelDialog(p.id) },
                            Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Edit, "umbenennen", Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val totalCmd = pState.commanderDamageReceived.values.sum()
                if (totalCmd > 0) Text("CMD: $totalCmd${if (totalCmd >= 21) " ⚠" else ""}",
                    fontSize = 12.sp, color = if (totalCmd >= 21) MaterialTheme.colorScheme.error
                                             else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Aktionen
            if (!p.isEliminated && !isFinished) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.showCommanderDamagePanel(if (state.showCommanderDamageFor == p.id) null else p.id) },
                        modifier = Modifier.weight(1f)
                    ) { Text("CMD-Schaden") }
                    Button(
                        onClick = { vm.showEliminateDialog(p.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Eliminieren") }
                }
            }
            // Würfel + Randomizer (pro Spieler)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = vm::rollDice, enabled = !state.isDiceRolling) {
                    Icon(Icons.Filled.Casino, "Würfel")
                }
                IconButton(onClick = { vm.randomizeOpponent(p.id) }) {
                    Icon(Icons.Filled.Shuffle, "Zufall")
                }
            }
            // CMD-Detail
            if (state.showCommanderDamageFor == p.id) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Commander-Schaden von:", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                state.participants.filter { it.participant.id != p.id }.forEach { att ->
                    val dmg = pState.commanderDamageReceived[att.participant.id] ?: 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(att.player.name, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dmg >= 21) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text("$dmg${if (dmg >= 21) " ⚠" else ""}", fontWeight = FontWeight.Bold,
                            color = if (dmg >= 21) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
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
    label: String, value: Int, warn: Boolean,
    labelSize: androidx.compose.ui.unit.TextUnit,
    onMinus: (() -> Unit)?, onPlus: (() -> Unit)?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onMinus != null) {
            TextButton(onClick = onMinus, modifier = Modifier.size(22.dp), contentPadding = PaddingValues(0.dp)) {
                Text("-", fontSize = labelSize, fontWeight = FontWeight.Bold)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontWeight = FontWeight.Bold, fontSize = labelSize,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = (labelSize.value * 0.75f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onPlus != null) {
            TextButton(onClick = onPlus, modifier = Modifier.size(22.dp), contentPadding = PaddingValues(0.dp)) {
                Text("+", fontSize = labelSize, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniBtn(text: String, h: Dp, fs: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(h).widthIn(min = h),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
        Text(text, fontSize = fs, fontWeight = FontWeight.Bold)
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
    victimName: String, others: List<ParticipantUiState>,
    onConfirm: (Long?) -> Unit, onDismiss: () -> Unit
) {
    var killerId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("$victimName eliminieren") },
        text = {
            Column {
                Text("Wer hat $victimName eliminiert?")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = killerId == null, onClick = { killerId = null })
                    Text("Unbekannt / Selbst")
                }
                others.forEach { ps ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = killerId == ps.participant.id,
                            onClick = { killerId = ps.participant.id })
                        Text(ps.player.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(killerId) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Eliminieren")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
