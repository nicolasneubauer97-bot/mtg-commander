package com.mtg.commander.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.domain.model.Player
import com.mtg.commander.ui.viewmodel.NewGameViewModel
import com.mtg.commander.ui.viewmodel.PlayerSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(app: MTGCommanderApp, onBack: () -> Unit, onGameStarted: (Long) -> Unit) {
    val vm: NewGameViewModel = viewModel(factory = NewGameViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.gameId) {
        state.gameId?.let { onGameStarted(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neue Partie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurueck") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Spieler auswaehlen (2-4)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (state.allPlayers.isEmpty()) {
                Text("Keine Spieler gefunden. Lege zuerst Spieler an.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.allPlayers, key = { it.id }) { player ->
                        val selection = state.selectedPlayers.find { it.player.id == player.id }
                        PlayerSelectionCard(
                            player = player,
                            selection = selection,
                            isSelectable = selection != null || state.selectedPlayers.size < 4,
                            onToggle = { vm.togglePlayerSelection(player) },
                            onDeckSelected = { deck -> vm.selectDeck(player, deck) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = vm::startGame,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = state.selectedPlayers.size >= 2
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Partie starten (${state.selectedPlayers.size} Spieler)")
            }
        }
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("Fehler") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun PlayerSelectionCard(
    player: Player,
    selection: PlayerSelection?,
    isSelectable: Boolean,
    onToggle: () -> Unit,
    onDeckSelected: (Deck) -> Unit
) {
    val isSelected = selection != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { if (isSelectable || isSelected) onToggle() },
                    enabled = isSelectable || isSelected
                )
                Text(player.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            if (isSelected && selection != null) {
                if (selection.availableDecks.isEmpty()) {
                    Text("Kein Deck vorhanden!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Deck:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selection.selectedDeck?.name ?: "Kein Deck",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
}
