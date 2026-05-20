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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.domain.model.DeckStats
import com.mtg.commander.ui.viewmodel.DeckStatsSort
import com.mtg.commander.ui.viewmodel.DeckStatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckStatsScreen(app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: DeckStatsViewModel = viewModel(factory = DeckStatsViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deck-Statistiken") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurueck") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DeckStatsSort.values()) { sort ->
                    FilterChip(
                        selected = state.sortBy == sort,
                        onClick = { vm.setSortBy(sort) },
                        label = { Text(sort.label()) }
                    )
                }
            }
            HorizontalDivider()
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.stats.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Deck-Statistiken.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.stats, key = { it.deckId }) { stats ->
                        DeckStatsCard(stats)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckStatsCard(stats: DeckStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stats.deckName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text("${stats.commanderName} | ${stats.playerName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatItem("Siege", "${stats.wins}")
                StatItem("Spiele", "${stats.gamesPlayed}")
                StatItem("Winrate", "${String.format("%.0f", stats.winRate * 100)}%")
                StatItem("Platz", String.format("%.1f", stats.averagePlacement))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DeckStatsSort.label() = when (this) {
    DeckStatsSort.WINS -> "Siege"
    DeckStatsSort.WIN_RATE -> "Winrate"
    DeckStatsSort.GAMES_PLAYED -> "Spiele"
    DeckStatsSort.AVG_PLACEMENT -> "Platzierung"
}
