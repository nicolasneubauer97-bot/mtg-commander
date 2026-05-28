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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.domain.model.RandomOpponentStat
import com.mtg.commander.ui.theme.MTGGold
import com.mtg.commander.ui.viewmodel.RandomOpponentStatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomOpponentStatsScreen(app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: RandomOpponentStatsViewModel = viewModel(factory = RandomOpponentStatsViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zufalls-Gegner Statistiken") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MTGGold)
                }
            }
            state.stats.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Shuffle, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Noch keine Zufalls-Gegner Picks vorhanden.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Nutze den 🔀-Button im Spielscreen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                // Group by chooser
                val grouped = state.stats.groupBy { it.chooserPlayerId }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    item {
                        Text("Wer wen am häufigsten als Gegner gewählt hat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }

                    grouped.forEach { (chooserId, picks) ->
                        item(key = chooserId) {
                            ChooserCard(picks = picks.sortedByDescending { it.count })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooserCard(picks: List<RandomOpponentStat>) {
    val chooserName = picks.firstOrNull()?.chooserName ?: return
    val total = picks.sumOf { it.count }
    val maxCount = picks.maxOf { it.count }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(chooserName, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("$total Picks", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))

            picks.forEach { stat ->
                PickRow(stat = stat, maxCount = maxCount)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun PickRow(stat: RandomOpponentStat, maxCount: Int) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ArrowForward, null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stat.chosenName, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            Text("${stat.count}×", fontWeight = FontWeight.Bold,
                fontSize = 15.sp, color = MTGGold)
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { if (maxCount > 0) stat.count.toFloat() / maxCount else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MTGGold,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
