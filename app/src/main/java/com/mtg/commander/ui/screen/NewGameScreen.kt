package com.mtg.commander.ui.screen

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.domain.model.Player
import com.mtg.commander.ui.viewmodel.NewGameViewModel
import com.mtg.commander.ui.viewmodel.PlayerSelection
import com.mtg.commander.ui.viewmodel.SEAT_LABELS_2
import com.mtg.commander.ui.viewmodel.SEAT_LABELS_3
import com.mtg.commander.ui.viewmodel.SEAT_LABELS_4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(app: MTGCommanderApp, onBack: () -> Unit, onGameStarted: (Long) -> Unit) {
    val vm: NewGameViewModel = viewModel(factory = NewGameViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.gameId) { state.gameId?.let { onGameStarted(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neue Partie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    if (state.selectedPlayers.size >= 2) {
                        IconButton(onClick = vm::showSeatDialog) {
                            Icon(Icons.Filled.TableRestaurant, "Sitzordnung")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Spieler auswählen (2–4)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp))

            if (state.allPlayers.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Keine Spieler gefunden. Lege zuerst Spieler an.")
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.allPlayers, key = { it.id }) { player ->
                        val selection = state.selectedPlayers.find { it.player.id == player.id }
                        PlayerSelectionCard(
                            player = player, selection = selection,
                            isSelectable = selection != null || state.selectedPlayers.size < 4,
                            onToggle = { vm.togglePlayerSelection(player) },
                            onDeckSelected = { deck -> vm.selectDeck(player, deck) }
                        )
                    }
                }
            }

            // Sitzordnung-Vorschau
            if (state.selectedPlayers.size >= 2) {
                Spacer(Modifier.height(8.dp))
                val seatLabels = when (state.selectedPlayers.size) {
                    4 -> SEAT_LABELS_4
                    3 -> SEAT_LABELS_3
                    else -> SEAT_LABELS_2
                }
                val assigned = state.seatAssignments.take(state.selectedPlayers.size)
                val allAssigned = assigned.none { it == null }
                if (allAssigned) {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Sitzordnung:", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(bottom = 4.dp))
                            assigned.forEachIndexed { i, sel ->
                                Text("${seatLabels[i]}: ${sel?.player?.name ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = vm::showSeatDialog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.TableRestaurant, null, Modifier.padding(end = 8.dp))
                        Text("Sitzordnung festlegen (optional)")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = vm::startGame,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = state.selectedPlayers.size >= 2
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.padding(end = 8.dp))
                Text("Partie starten (${state.selectedPlayers.size} Spieler)")
            }
        }
    }

    // Sitzordnungs-Dialog
    if (state.showSeatDialog) {
        SeatOrderDialog(
            selectedPlayers = state.selectedPlayers,
            seatAssignments = state.seatAssignments,
            onAssign = { seat, sel -> vm.assignSeat(seat, sel) },
            onDismiss = vm::dismissSeatDialog
        )
    }

    state.error?.let { error ->
        AlertDialog(onDismissRequest = vm::clearError,
            title = { Text("Fehler") }, text = { Text(error) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeatOrderDialog(
    selectedPlayers: List<PlayerSelection>,
    seatAssignments: List<PlayerSelection?>,
    onAssign: (Int, PlayerSelection?) -> Unit,
    onDismiss: () -> Unit
) {
    val count = selectedPlayers.size
    val seatLabels = when (count) {
        4 -> SEAT_LABELS_4
        3 -> SEAT_LABELS_3
        else -> SEAT_LABELS_2
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sitzordnung festlegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Weise jedem Sitzplatz einen Spieler zu:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Visuelle Vorschau des Layouts
                SeatPreview(count, seatAssignments, seatLabels)

                // Dropdown pro Sitz
                seatLabels.forEachIndexed { seatIdx, label ->
                    val current = seatAssignments.getOrNull(seatIdx)
                    var expanded by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.width(120.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = current?.player?.name ?: "– nicht belegt –",
                                onValueChange = {},
                                readOnly = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("– frei lassen –") },
                                    onClick = { onAssign(seatIdx, null); expanded = false })
                                selectedPlayers.forEach { sel ->
                                    DropdownMenuItem(
                                        text = { Text(sel.player.name) },
                                        onClick = { onAssign(seatIdx, sel); expanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } }
    )
}

@Composable
private fun SeatPreview(
    count: Int,
    assignments: List<PlayerSelection?>,
    labels: List<String>
) {
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val playerColor = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        when (count) {
            4 -> {
                // Oben-Links (rot. 180°)
                SeatChip(assignments.getOrNull(2), Modifier.align(Alignment.TopStart).rotate(180f), playerColor)
                // Oben-Rechts (rot. 180°)
                SeatChip(assignments.getOrNull(3), Modifier.align(Alignment.TopEnd).rotate(180f), playerColor)
                // Unten-Links
                SeatChip(assignments.getOrNull(0), Modifier.align(Alignment.BottomStart), playerColor)
                // Unten-Rechts
                SeatChip(assignments.getOrNull(1), Modifier.align(Alignment.BottomEnd), playerColor)
            }
            3 -> {
                // Oben-Mitte (rot. 180°)
                SeatChip(assignments.getOrNull(2), Modifier.align(Alignment.TopCenter).rotate(180f), playerColor)
                // Unten-Links
                SeatChip(assignments.getOrNull(0), Modifier.align(Alignment.BottomStart), playerColor)
                // Unten-Rechts
                SeatChip(assignments.getOrNull(1), Modifier.align(Alignment.BottomEnd), playerColor)
            }
            else -> {
                // Oben
                SeatChip(assignments.getOrNull(1), Modifier.align(Alignment.TopCenter).rotate(180f), playerColor)
                // Unten
                SeatChip(assignments.getOrNull(0), Modifier.align(Alignment.BottomCenter), playerColor)
            }
        }
        // Tisch-Symbol in der Mitte
        Text("◎", modifier = Modifier.align(Alignment.Center), fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SeatChip(sel: PlayerSelection?, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = modifier.widthIn(min = 60.dp, max = 90.dp).height(24.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (sel != null) color else color.copy(alpha = 0.3f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = sel?.player?.name?.take(8) ?: "?",
                fontSize = 9.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSelectionCard(
    player: Player, selection: PlayerSelection?,
    isSelectable: Boolean, onToggle: () -> Unit,
    onDeckSelected: (Deck?) -> Unit
) {
    val isSelected = selection != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { if (isSelectable || isSelected) onToggle() },
                    enabled = isSelectable || isSelected
                )
                Text(player.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            if (isSelected && selection != null) {
                Text("Deck (optional):", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp))
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selection.selectedDeck?.name ?: "Ohne Deck",
                        onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Ohne Deck") },
                            onClick = { onDeckSelected(null); expanded = false })
                        selection.availableDecks.forEach { deck ->
                            DropdownMenuItem(
                                text = { Text("${deck.name} (${deck.commanderName})") },
                                onClick = { onDeckSelected(deck); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
