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
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.ui.viewmodel.DecksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: DecksViewModel = viewModel(factory = DecksViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

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
                FloatingActionButton(onClick = vm::showAddDialog) {
                    Icon(Icons.Filled.Add, "Deck hinzufuegen")
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
                        Text("Kein Deck fuer diesen Spieler. Tippe auf +.")
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
                Text(deck.commanderName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                if (deck.colors.isNotBlank()) {
                    Text(deck.colors, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Bearbeiten") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Loeschen", tint = MaterialTheme.colorScheme.error) }
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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Deckname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = commander, onValueChange = { commander = it }, label = { Text("Commander") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = colors, onValueChange = { colors = it }, label = { Text("Farben (z.B. WUBGR)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
