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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val RankSilver = Color(0xFFB0B8C8)
private val RankBronze = Color(0xFFB87333)

@Composable
private fun LeaderboardCard(rank: Int, stats: PlayerStats) {
    val rankColor = when (rank) {
        1    -> WinnerColor
        2    -> RankSilver
        3    -> RankBronze
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val cardColor = when (rank) {
        1    -> WinnerColor.copy(alpha = 0.13f)
        2    -> RankSilver.copy(alpha = 0.07f)
        3    -> RankBronze.copy(alpha = 0.07f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderAlpha = when (rank) { 1 -> 0.5f; 2 -> 0.3f; 3 -> 0.25f; else -> 0.0f }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = cardColor),
        border   = if (rank <= 3)
            androidx.compose.foundation.BorderStroke(1.dp, rankColor.copy(alpha = borderAlpha))
        else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#$rank",
                fontWeight = if (rank <= 3) FontWeight.ExtraBold else FontWeight.Bold,
                fontSize   = if (rank == 1) 20.sp else 16.sp,
                color      = rankColor,
                modifier   = Modifier.width(38.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stats.playerName, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (rank == 1) WinnerColor else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip("Siege",  "${stats.wins}")
                    StatChip("WR",     "${String.format("%.0f", stats.winRate * 100)}%")
                    StatChip("Spiele", "${stats.gamesPlayed}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip("Platz",  String.format("%.1f", stats.averagePlacement))
                    StatChip("Kills",  "${stats.kills}")
                    StatChip("Tode",   "${stats.deaths}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip("1.Spieler", "${stats.timesChosenAsStarter}")
                    StatChip("Dmg",       "${stats.totalDamageDealtToOthers}")
                    val sign = if (stats.netLifeChange >= 0) "+" else ""
                    StatChip("Netto-LP",  "$sign${stats.netLifeChange}")
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
    LeaderboardSort.DAMAGE -> "Schaden"
}
