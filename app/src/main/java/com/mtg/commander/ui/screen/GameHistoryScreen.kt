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
import com.mtg.commander.domain.model.Game
import com.mtg.commander.ui.viewmodel.GameHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryScreen(
    app: MTGCommanderApp,
    onBack: () -> Unit,
    onGameDetail: (Long) -> Unit
) {
    val vm: GameHistoryViewModel = viewModel(factory = GameHistoryViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Hoist delete confirmation outside LazyColumn to prevent scroll crashes
    var deleteTarget by remember { mutableStateOf<Game?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spielhistorie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.games.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Noch keine abgeschlossenen Spiele.")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.games, key = { it.id }) { game ->
                        GameHistoryCard(
                            game = game,
                            onClick = { onGameDetail(game.id) },
                            onDeleteRequest = { deleteTarget = game }
                        )
                    }
                }
            }
        }
    }

    // Delete confirm dialog – outside LazyColumn
    deleteTarget?.let { game ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Spiel löschen?") },
            text = { Text("Dieses Spiel und alle zugehörigen Daten werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteGame(game); deleteTarget = null }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHistoryCard(game: Game, onClick: () -> Unit, onDeleteRequest: () -> Unit) {
    val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fmt.format(Date(game.startedAt)), style = MaterialTheme.typography.bodyLarge)
                game.endedAt?.let { Text("Beendet: ${fmt.format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
                Text("Spiel #${game.id}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDeleteRequest) {
                Icon(Icons.Filled.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
