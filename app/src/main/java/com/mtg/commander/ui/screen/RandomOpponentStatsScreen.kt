package com.mtg.commander.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zufall & Würfel Statistiken") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Zufalls-Gegner") },
                    icon = { Icon(Icons.Filled.Shuffle, null, Modifier.size(16.dp)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Würfel") },
                    icon = { Icon(Icons.Filled.Casino, null, Modifier.size(16.dp)) })
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MTGGold)
                }
            } else when (selectedTab) {
                0 -> RandomOpponentTab(state.picks)
                1 -> DiceStatsTab(state.diceStats)
            }
        }
    }
}

// ─── Tab 1: Zufalls-Gegner ───────────────────────────────────────────────────

@Composable
private fun RandomOpponentTab(stats: List<RandomOpponentStat>) {
    if (stats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Shuffle, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Noch keine Picks vorhanden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nutze den 🔀-Button im Spielscreen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val grouped = stats.groupBy { it.chooserPlayerId }
    val maxCount = stats.maxOf { it.count }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Wer wen wie oft als Zufalls-Gegner gewählt hat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        grouped.forEach { (_, picks) ->
            item(key = picks.first().chooserPlayerId) {
                ChooserCard(picks = picks.sortedByDescending { it.count }, maxCount = maxCount)
            }
        }
    }
}

@Composable
private fun ChooserCard(picks: List<RandomOpponentStat>, maxCount: Int) {
    val chooserName = picks.firstOrNull()?.chooserName ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(chooserName, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${picks.sumOf { it.count }} Picks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            picks.forEach { stat ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Icon(Icons.Filled.ArrowForward, null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stat.chosenName, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Text("${stat.count}×", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MTGGold)
                }
                LinearProgressIndicator(
                    progress = { if (maxCount > 0) stat.count.toFloat() / maxCount else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).padding(start = 18.dp),
                    color = MTGGold,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// ─── Tab 2: Würfel-Stats-Matrix ──────────────────────────────────────────────

@Composable
private fun DiceStatsTab(diceStats: Map<String, Map<Int, Int>>) {
    if (diceStats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Casino, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Noch keine Würfelwürfe aufgezeichnet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Benutze den Würfel-Button im Spielscreen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val players = diceStats.keys.sorted()
    val allValues = (1..6).toList()
    val maxCount = diceStats.values.flatMap { it.values }.maxOrNull() ?: 1

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Welcher Spieler welche Zahlen wie oft gewürfelt hat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        // Matrix header
        item {
            DiceMatrixHeader(allValues)
        }

        items(players) { player ->
            DiceMatrixRow(
                playerName = player,
                counts = diceStats[player] ?: emptyMap(),
                allValues = allValues,
                maxCount = maxCount
            )
        }
    }
}

@Composable
private fun DiceMatrixHeader(values: List<Int>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Spieler", modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEach { v ->
            Text(diceFaceChar(v), modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text("Ges.", modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun DiceMatrixRow(
    playerName: String,
    counts: Map<Int, Int>,
    allValues: List<Int>,
    maxCount: Int
) {
    val total = counts.values.sum()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(playerName, modifier = Modifier.weight(2f),
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                allValues.forEach { v ->
                    val count = counts[v] ?: 0
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(if (count > 0) "$count" else "–",
                            fontSize = 13.sp,
                            color = if (count > 0) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontWeight = if (count == maxCount && count > 0) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center)
                    }
                }
                Text("$total", modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                    color = MTGGold, fontSize = 13.sp)
            }
            // Mini sparkline
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().height(12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                allValues.forEach { v ->
                    val count = counts[v] ?: 0
                    val frac = if (maxCount > 0) count.toFloat() / maxCount else 0f
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .background(
                                MTGGold.copy(alpha = 0.15f + frac * 0.7f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

private fun diceFaceChar(v: Int) = when (v) {
    1 -> "⚀"; 2 -> "⚁"; 3 -> "⚂"; 4 -> "⚃"; 5 -> "⚄"; 6 -> "⚅"; else -> "$v"
}
