package com.mtg.commander.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.domain.model.PlayerStats
import com.mtg.commander.ui.theme.MTGGold
import com.mtg.commander.ui.theme.WinnerColor
import com.mtg.commander.ui.viewmodel.LeaderboardSort
import com.mtg.commander.ui.viewmodel.LeaderboardViewModel

private val RankSilver = Color(0xFFB0B8C8)
private val RankBronze = Color(0xFFB87333)

// Column widths
private val W_RANK = 36.dp
private val W_NAME = 96.dp
private val W_NUM  = 40.dp
private val W_PCT  = 52.dp
private val W_DMG  = 52.dp
private val W_NET  = 56.dp

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
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Sort filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LeaderboardSort.values()) { sort ->
                    FilterChip(
                        selected = state.sortBy == sort,
                        onClick = { vm.setSortBy(sort) },
                        label = { Text(sort.label(), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            HorizontalDivider()

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MTGGold)
                    }
                }
                state.stats.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Noch keine Statistiken verfügbar.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    val hScroll = rememberScrollState()

                    // Sticky header + scrollable body share the same horizontal scroll
                    Column(Modifier.fillMaxSize()) {
                        // ─── Tabellenkopf ────────────────────────────────────────────
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                Modifier
                                    .horizontalScroll(hScroll)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HeaderCell("#", W_RANK)
                                HeaderCell("Name", W_NAME, leftAlign = true)
                                HeaderCell("S", W_NUM, tooltip = "Siege")
                                HeaderCell("WR", W_PCT, tooltip = "Winrate")
                                HeaderCell("Sp", W_NUM, tooltip = "Spiele")
                                HeaderCell("Ø-Pl", W_PCT, tooltip = "Ø Platzierung")
                                HeaderCell("K", W_NUM, tooltip = "Kills")
                                HeaderCell("T", W_NUM, tooltip = "Tode")
                                HeaderCell("1.Sp", W_NUM, tooltip = "Als 1. Spieler gewählt")
                                HeaderCell("Dmg", W_DMG, tooltip = "Schaden an Gegnern")
                                HeaderCell("Netto-LP", W_NET, tooltip = "Netto Lebenspunkte")
                            }
                        }
                        HorizontalDivider()

                        // ─── Tabellenzeilen ──────────────────────────────────────────
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(state.stats) { index, stats ->
                                val rank = index + 1
                                val rankColor = when (rank) {
                                    1 -> WinnerColor; 2 -> RankSilver; 3 -> RankBronze
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val rowBg = when (rank) {
                                    1 -> WinnerColor.copy(alpha = 0.10f)
                                    2 -> RankSilver.copy(alpha = 0.05f)
                                    3 -> RankBronze.copy(alpha = 0.05f)
                                    else -> Color.Transparent
                                }

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .horizontalScroll(hScroll)
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Rang
                                    Box(Modifier.width(W_RANK), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = rankEmoji(rank),
                                            fontSize = if (rank <= 3) 18.sp else 14.sp,
                                            fontWeight = if (rank <= 3) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = rankColor,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    // Name
                                    Box(Modifier.width(W_NAME)) {
                                        Text(
                                            stats.playerName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (rank == 1) WinnerColor
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    // Siege
                                    DataCell("${stats.wins}", W_NUM,
                                        highlight = state.sortBy == LeaderboardSort.WINS)
                                    // Winrate
                                    DataCell("${String.format("%.0f", stats.winRate * 100)}%", W_PCT,
                                        highlight = state.sortBy == LeaderboardSort.WIN_RATE)
                                    // Spiele
                                    DataCell("${stats.gamesPlayed}", W_NUM,
                                        highlight = state.sortBy == LeaderboardSort.GAMES_PLAYED)
                                    // Ø-Platz
                                    DataCell(String.format("%.1f", stats.averagePlacement), W_PCT,
                                        highlight = state.sortBy == LeaderboardSort.AVG_PLACEMENT)
                                    // Kills
                                    DataCell("${stats.kills}", W_NUM,
                                        highlight = state.sortBy == LeaderboardSort.KILLS)
                                    // Tode
                                    DataCell("${stats.deaths}", W_NUM)
                                    // 1. Spieler
                                    DataCell("${stats.timesChosenAsStarter}", W_NUM)
                                    // Schaden
                                    DataCell("${stats.totalDamageDealtToOthers}", W_DMG,
                                        highlight = state.sortBy == LeaderboardSort.DAMAGE)
                                    // Netto-LP
                                    val net = stats.netLifeChange
                                    val netSign = if (net >= 0) "+" else ""
                                    DataCell(
                                        "$netSign$net", W_NET,
                                        color = when {
                                            net > 0 -> Color(0xFF4CAF50)
                                            net < 0 -> MaterialTheme.colorScheme.error
                                            else    -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                HorizontalDivider(thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(
    label: String,
    width: Dp,
    leftAlign: Boolean = false,
    tooltip: String? = null
) {
    Box(Modifier.width(width), contentAlignment = if (leftAlign) Alignment.CenterStart else Alignment.Center) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (leftAlign) TextAlign.Start else TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun DataCell(
    value: String,
    width: Dp,
    highlight: Boolean = false,
    color: Color = Color.Unspecified
) {
    Box(Modifier.width(width), contentAlignment = Alignment.Center) {
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Normal,
            color = when {
                color != Color.Unspecified -> color
                highlight -> MTGGold
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun rankEmoji(rank: Int) = when (rank) {
    1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank"
}

private fun LeaderboardSort.label() = when (this) {
    LeaderboardSort.WINS -> "Siege"
    LeaderboardSort.WIN_RATE -> "Winrate"
    LeaderboardSort.GAMES_PLAYED -> "Spiele"
    LeaderboardSort.AVG_PLACEMENT -> "Platz"
    LeaderboardSort.KILLS -> "Kills"
    LeaderboardSort.DAMAGE -> "Schaden"
}
