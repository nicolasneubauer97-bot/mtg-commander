package com.mtg.commander.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.navigation.NavBackStackEntry
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.ui.viewmodel.DecksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(
    app: MTGCommanderApp,
    onBack: () -> Unit,
    onPickPrecon: (playerId: Long) -> Unit,
    backStackEntry: NavBackStackEntry? = null
) {
    val vm: DecksViewModel = viewModel(factory = DecksViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Observe precon picked from the picker screen via SavedStateHandle
    val pickedName = backStackEntry?.savedStateHandle?.getStateFlow<String?>("picked_precon_name", null)
        ?.collectAsState()?.value
    val pickedCommander = backStackEntry?.savedStateHandle?.getStateFlow<String?>("picked_commander", null)
        ?.collectAsState()?.value
    val pickedColors = backStackEntry?.savedStateHandle?.getStateFlow<String?>("picked_colors", null)
        ?.collectAsState()?.value
    val pickedImageUrl = backStackEntry?.savedStateHandle?.getStateFlow<String?>("picked_image_url", null)
        ?.collectAsState()?.value
    val pickedPlayerId = backStackEntry?.savedStateHandle?.getStateFlow<Long?>("picked_player_id", null)
        ?.collectAsState()?.value

    LaunchedEffect(pickedName, pickedPlayerId) {
        if (pickedName != null && pickedCommander != null && pickedPlayerId != null) {
            val player = state.players.find { it.id == pickedPlayerId }
            if (player != null) {
                vm.selectPlayer(player)
                vm.importPrecon(
                    name = pickedName,
                    commanderName = pickedCommander,
                    colors = pickedColors ?: "",
                    imageUrl = pickedImageUrl ?: ""
                )
            }
            backStackEntry.savedStateHandle.remove<String>("picked_precon_name")
            backStackEntry.savedStateHandle.remove<String>("picked_commander")
            backStackEntry.savedStateHandle.remove<String>("picked_colors")
            backStackEntry.savedStateHandle.remove<String>("picked_image_url")
            backStackEntry.savedStateHandle.remove<Long>("picked_player_id")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decks") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurueck") }
                }
            )
        },
        floatingActionButton = {
            if (state.selectedPlayer != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(
                        onClick = { onPickPrecon(state.selectedPlayer!!.id) },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(Icons.Filled.LibraryAdd, "Von Precon hinzufügen")
                    }
                    FloatingActionButton(onClick = vm::showAddDialog) {
                        Icon(Icons.Filled.Add, "Deck hinzufuegen")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.players.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lege zuerst Spieler an.")
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.players) { player ->
                        FilterChip(
                            selected = state.selectedPlayer?.id == player.id,
                            onClick = { vm.selectPlayer(player) },
                            label = { Text(player.name) }
                        )
                    }
                }
                HorizontalDivider()
                if (state.decks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kein Deck fuer diesen Spieler.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Tippe auf + für ein eigenes Deck\noder auf 📚 für ein Precon-Deck.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.decks, key = { it.id }) { deck ->
                            DeckListItem(
                                deck = deck,
                                onEdit = { vm.showEditDialog(deck) },
                                onDelete = { vm.deleteDeck(deck) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showAddDialog) {
        DeckDialog(
            initialDeck = state.editingDeck,
            title = if (state.editingDeck == null) "Deck anlegen" else "Deck bearbeiten",
            onConfirm = { name, commander, colors -> vm.saveDeck(name, commander, colors) },
            onDismiss = vm::dismissDialog
        )
    }
}

@Composable
private fun DeckListItem(deck: Deck, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.bodyLarge)
                Text(deck.commanderName, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary)
                if (deck.colors.isNotBlank()) {
                    Text(deck.colors, style = MaterialTheme.typography.bodySmall)
                }
                if (deck.imageUrl.isNotBlank()) {
                    Text("🖼 Precon-Deck", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Bearbeiten") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Loeschen", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DeckDialog(
    initialDeck: Deck?,
    title: String,
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialDeck?.name ?: "") }
    var commander by remember { mutableStateOf(initialDeck?.commanderName ?: "") }
    var colors by remember { mutableStateOf(initialDeck?.colors ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Deckname") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = commander, onValueChange = { commander = it },
                    label = { Text("Commander") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = colors, onValueChange = { colors = it },
                    label = { Text("Farben (z.B. WUBGR)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && commander.isNotBlank()) onConfirm(name, commander, colors) },
                enabled = name.isNotBlank() && commander.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
