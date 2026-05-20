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
import com.mtg.commander.domain.model.Player
import com.mtg.commander.ui.viewmodel.PlayersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: PlayersViewModel = viewModel(factory = PlayersViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spieler") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurueck") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::showAddDialog) {
                Icon(Icons.Filled.Add, "Spieler hinzufuegen")
            }
        }
    ) { padding ->
        if (state.players.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Noch keine Spieler. Tippe auf + um einen anzulegen.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.players, key = { it.id }) { player ->
                    PlayerListItem(
                        player = player,
                        onEdit = { vm.showEditDialog(player) },
                        onDelete = { vm.deletePlayer(player) }
                    )
                }
            }
        }
    }

    if (state.showAddDialog) {
        PlayerDialog(
            initialName = state.editingPlayer?.name ?: "",
            title = if (state.editingPlayer == null) "Spieler anlegen" else "Spieler bearbeiten",
            onConfirm = { name -> vm.savePlayer(name) },
            onDismiss = vm::dismissDialog
        )
    }
}

@Composable
private fun PlayerListItem(player: Player, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Text(player.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Bearbeiten") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Loeschen", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PlayerDialog(initialName: String, title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
