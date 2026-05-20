package com.mtg.commander.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.mtg.commander.domain.model.PlayerStats
import com.mtg.commander.ui.theme.WinnerColor
import com.mtg.commander.ui.viewmodel.LeaderboardSort
import com.mtg.commander.ui.viewmodel.LeaderboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: LeaderboardViewModel = viewModel(factory = LeaderboardViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard") },
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
                items(LeaderboardSort.values()) { sort ->
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
                    Text("Noch keine Statistiken verfuegbar.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.stats) { index, stats ->
                        LeaderboardCard(rank = index + 1, stats = stats)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardCard(rank: Int, stats: PlayerStats) {
    val isFirst = rank == 1
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFirst) WinnerColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#$rank",
                fontWeight = FontWeight.Bold,
                color = if (isFirst) WinnerColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stats.playerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip("Siege", "${stats.wins}")
                    StatChip("WR", "${String.format("%.0f", stats.winRate * 100)}%")
                    StatChip("Spiele", "${stats.gamesPlayed}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip("Platz", String.format("%.1f", stats.averagePlacement))
                    StatChip("Kills", "${stats.kills}")
                    StatChip("Tode", "${stats.deaths}")
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LeaderboardSort.label() = when (this) {
    LeaderboardSort.WINS -> "Siege"
    LeaderboardSort.WIN_RATE -> "Winrate"
    LeaderboardSort.GAMES_PLAYED -> "Spiele"
    LeaderboardSort.AVG_PLACEMENT -> "Platzierung"
    LeaderboardSort.KILLS -> "Kills"
}
