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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spielhistorie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurueck") }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.games.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Noch keine abgeschlossenen Spiele.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.games, key = { it.id }) { game ->
                    GameHistoryCard(
                        game = game,
                        onClick = { onGameDetail(game.id) },
                        onDelete = { vm.deleteGame(game) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHistoryCard(game: Game, onClick: () -> Unit, onDelete: () -> Unit) {
    val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                if (game.endedAt != null) {
                    Text("Beendet: ${fmt.format(Date(game.endedAt))}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Spiel #${game.id}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Delete, "Loeschen", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Spiel loeschen?") },
            text = { Text("Dieses Spiel und alle zugehoerigen Daten werden geloescht.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Loeschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}
