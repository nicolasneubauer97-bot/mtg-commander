package com.mtg.commander.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.ui.theme.MTGGold
import com.mtg.commander.ui.viewmodel.GlobalDamageStatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalDamageStatsScreen(app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: GlobalDamageStatsViewModel = viewModel(factory = GlobalDamageStatsViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schaden-Statistiken") },
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
            state.players.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FlashOn, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Noch keine Schadensdaten vorhanden.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Nutze den Nächster-Spieler-Button um Züge zu tracken.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        "Schaden dealt während eigener Züge (Angreifer → Opfer)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    DamageMatrix(
                        players = state.players,
                        matrix = state.matrix,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DamageMatrix(
    players: List<String>,
    matrix: Map<String, Map<String, Int>>,
    modifier: Modifier = Modifier
) {
    val maxDmg = matrix.values.flatMap { it.values }.maxOrNull() ?: 1
    val cellW = 64.dp
    val nameW = 90.dp

    Column(modifier = modifier.horizontalScroll(rememberScrollState())
        .verticalScroll(rememberScrollState())) {

        // Header row (victims)
        Row {
            Box(Modifier.width(nameW + 8.dp)) {
                Text("↓ Angreifer\n→ Opfer",
                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomStart))
            }
            players.forEach { victim ->
                Box(Modifier.width(cellW), contentAlignment = Alignment.Center) {
                    Text(victim.take(8), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
            Box(Modifier.width(cellW), contentAlignment = Alignment.Center) {
                Text("Total", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MTGGold)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)
            .width(nameW + 8.dp + cellW * (players.size + 1)))

        // Data rows (attackers)
        players.forEach { attacker ->
            val row = matrix[attacker] ?: emptyMap()
            val rowTotal = row.values.sum()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(attacker.take(11), modifier = Modifier.width(nameW),
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                players.forEach { victim ->
                    val dmg = row[victim] ?: 0
                    val isSelf = attacker == victim
                    val intensity = if (isSelf || dmg == 0) 0f else dmg.toFloat() / maxDmg
                    Box(
                        modifier = Modifier.width(cellW).height(32.dp)
                            .padding(2.dp)
                            .background(
                                if (isSelf) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.08f + intensity * 0.55f),
                                RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isSelf) "—" else if (dmg > 0) "$dmg" else "",
                            fontSize = 12.sp,
                            fontWeight = if (dmg == maxDmg && dmg > 0) FontWeight.ExtraBold else FontWeight.Normal,
                            color = when {
                                isSelf -> MaterialTheme.colorScheme.onSurfaceVariant
                                dmg > 0 -> MaterialTheme.colorScheme.onSurface
                                else -> Color.Transparent
                            }
                        )
                    }
                }
                Box(Modifier.width(cellW).height(32.dp).padding(2.dp),
                    contentAlignment = Alignment.Center) {
                    Text(if (rowTotal > 0) "$rowTotal" else "",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MTGGold)
                }
            }
        }
    }
}
