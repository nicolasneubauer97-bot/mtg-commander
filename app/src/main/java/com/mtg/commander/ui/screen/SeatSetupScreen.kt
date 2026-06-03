package com.mtg.commander.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.domain.model.Player
import com.mtg.commander.ui.theme.*
import com.mtg.commander.ui.viewmodel.SeatSetupViewModel
import com.mtg.commander.ui.viewmodel.SeatSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatSetupScreen(
    playerCount: Int,
    app: MTGCommanderApp,
    onBack: () -> Unit,
    onGameStarted: (Long) -> Unit
) {
    val vm: SeatSetupViewModel = viewModel(
        key = "seat_setup_$playerCount",
        factory = SeatSetupViewModel.factory(playerCount, app)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.gameId) { state.gameId?.let { onGameStarted(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sitzplätze zuweisen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Turn direction toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Zugreihenfolge:", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(
                        selected = state.clockwise,
                        onClick = { vm.setClockwise(true) },
                        label = { Text("↻ Uhrzeigersinn") }
                    )
                    FilterChip(
                        selected = !state.clockwise,
                        onClick = { vm.setClockwise(false) },
                        label = { Text("↺ Gegen") }
                    )
                }
                Button(
                    onClick = vm::startGame,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = state.canStart,
                    colors = ButtonDefaults.buttonColors(containerColor = MTGGold)
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.padding(end = 6.dp))
                    Text(
                        if (state.canStart) "Spiel starten (${state.filledSeats} Spieler)"
                        else "Mindestens 2 Sitzplätze belegen",
                        fontWeight = FontWeight.Bold,
                        color = MTGBlack
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
        ) {
            val W = maxWidth
            val H = maxHeight
            val halfW = W / 2
            val halfH = H / 2

            when (playerCount) {
                4 -> {
                    SeatCell(state.seats[0], 0, 0f,
                        Modifier.size(halfW, halfH).align(Alignment.BottomStart),
                        vm::openPickerForSeat, vm::clearSeat)
                    SeatCell(state.seats[1], 1, 0f,
                        Modifier.size(halfW, halfH).align(Alignment.BottomEnd),
                        vm::openPickerForSeat, vm::clearSeat)
                    SeatCell(state.seats[2], 2, 180f,
                        Modifier.size(halfW, halfH).align(Alignment.TopStart),
                        vm::openPickerForSeat, vm::clearSeat)
                    SeatCell(state.seats[3], 3, 180f,
                        Modifier.size(halfW, halfH).align(Alignment.TopEnd),
                        vm::openPickerForSeat, vm::clearSeat)
                }
                3 -> {
                    SeatCell(state.seats[0], 0, 0f,
                        Modifier.size(halfW, halfH).align(Alignment.BottomStart),
                        vm::openPickerForSeat, vm::clearSeat)
                    SeatCell(state.seats[1], 1, 0f,
                        Modifier.size(halfW, halfH).align(Alignment.BottomEnd),
                        vm::openPickerForSeat, vm::clearSeat)
                    SeatCell(state.seats[2], 2, 180f,
                        Modifier.fillMaxWidth().height(halfH).align(Alignment.TopCenter),
                        vm::openPickerForSeat, vm::clearSeat)
                }
                else -> {
                    SeatCell(state.seats[0], 0, 0f,
                        Modifier.fillMaxWidth().height(halfH).align(Alignment.BottomCenter),
                        vm::openPickerForSeat, vm::clearSeat)
                    SeatCell(state.seats[1], 1, 180f,
                        Modifier.fillMaxWidth().height(halfH).align(Alignment.TopCenter),
                        vm::openPickerForSeat, vm::clearSeat)
                }
            }

            // Center: table symbol
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("⊙", fontSize = 22.sp, color = MTGGold)
            }
        }
    }

    // Seat picker dialog
    if (state.showPickerForSeat != null) {
        SeatPickerDialog(
            seatIndex = state.showPickerForSeat!!,
            allPlayers = state.allPlayers,
            assignedIds = state.assignedPlayerIds,
            currentSlot = state.seats[state.showPickerForSeat!!],
            selectedPlayer = state.pickerSelectedPlayer,
            onSelectPlayer = vm::pickerSelectPlayer,
            onSelectDeck = vm::pickerSelectDeck,
            onConfirm = vm::confirmPickerSelection,
            onDismiss = vm::dismissPicker
        )
    }
}

@Composable
private fun SeatCell(
    slot: SeatSlot,
    seatIndex: Int,
    rotation: Float,
    modifier: Modifier,
    onOpen: (Int) -> Unit,
    onClear: (Int) -> Unit
) {
    val isEmpty = slot.player == null
    val borderColor = if (isEmpty)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    else
        MTGGold.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(
                if (isEmpty) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MTGDark.copy(alpha = 0.9f)
            )
            .clickable { onOpen(seatIndex) }
    ) {
        Box(modifier = Modifier.fillMaxSize().rotate(rotation)) {
            if (isEmpty) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.PersonAdd, null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Spieler wählen", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Text("Sitz ${seatIndex + 1}", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            } else {
                // Assigned state
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        slot.player!!.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MTGOnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (slot.selectedDeck != null) {
                        Text(
                            slot.selectedDeck.name,
                            fontSize = 11.sp,
                            color = MTGGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            slot.selectedDeck.commanderName,
                            fontSize = 9.sp,
                            color = MTGOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        // Mana symbols
                        if (slot.selectedDeck.colors.isNotBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                slot.selectedDeck.colors.forEach { c ->
                                    SetupManaSymbol(c)
                                }
                            }
                        }
                    } else {
                        Text("Kein Deck", fontSize = 10.sp,
                            color = MTGOnSurfaceVar, textAlign = TextAlign.Center)
                    }
                    // Clear button
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { onClear(seatIndex) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("✕", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupManaSymbol(color: Char) {
    val (bg, letter) = when (color.uppercaseChar()) {
        'W' -> Color(0xFFF9FAF4) to "W"
        'U' -> Color(0xFF0E68AB) to "U"
        'B' -> Color(0xFF21160F) to "B"
        'R' -> Color(0xFFD3202A) to "R"
        'G' -> Color(0xFF00733E) to "G"
        'C' -> Color(0xFF888888) to "C"
        else -> return
    }
    val textColor = if (color.uppercaseChar() == 'W') Color.Black else Color.White
    Box(
        modifier = Modifier.size(16.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeatPickerDialog(
    seatIndex: Int,
    allPlayers: List<Player>,
    assignedIds: Set<Long>,
    currentSlot: SeatSlot,
    selectedPlayer: Player?,
    onSelectPlayer: (Player) -> Unit,
    onSelectDeck: (Deck?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sitz ${seatIndex + 1} belegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Player selection
                Text("Spieler:", style = MaterialTheme.typography.labelMedium)
                val availablePlayers = allPlayers.filter {
                    it.id !in assignedIds || it.id == currentSlot.player?.id
                }
                if (availablePlayers.isEmpty()) {
                    Text("Alle Spieler sind bereits zugewiesen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    availablePlayers.forEach { player ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedPlayer?.id == player.id,
                                onClick = { onSelectPlayer(player) }
                            )
                            Text(player.name, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Deck selection (only if player chosen and has decks)
                if (selectedPlayer != null && currentSlot.availableDecks.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Deck (optional):", style = MaterialTheme.typography.labelMedium)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentSlot.selectedDeck?.name ?: "Kein Deck",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Kein Deck") },
                                onClick = { onSelectDeck(null); expanded = false }
                            )
                            currentSlot.availableDecks.forEach { deck ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(deck.name, fontWeight = FontWeight.SemiBold)
                                            Text(deck.commanderName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { onSelectDeck(deck); expanded = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedPlayer != null
            ) { Text("Bestätigen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
