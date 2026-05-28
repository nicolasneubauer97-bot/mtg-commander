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
import com.mtg.commander.ui.theme.WinnerColor
import com.mtg.commander.ui.viewmodel.GameDetailViewModel
import com.mtg.commander.ui.viewmodel.ParticipantUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(gameId: Long, app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: GameDetailViewModel = viewModel(
        key = "game_detail_$gameId",
        factory = GameDetailViewModel.factory(gameId, app)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spieldetails") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Game header ──────────────────────────────────────────────────
                item {
                    state.game?.let { game ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Spiel #${game.id}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("Gestartet: ${fmt.format(Date(game.startedAt))}")
                                game.endedAt?.let { Text("Beendet: ${fmt.format(Date(it))}") }
                            }
                        }
                    }
                }

                // ─── Placements ───────────────────────────────────────────────────
                item {
                    Text("Platzierungen", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(state.participants, key = { "p_${it.participant.id}" }) { pState ->
                    PlacementCard(
                        pState = pState,
                        commanderDamageByAttacker = buildCommanderDamageMap(pState, state.participants),
                        lifeSummary = state.lifeSummary[pState.participant.id]
                    )
                }

                // ─── Kills ────────────────────────────────────────────────────────
                if (state.kills.isNotEmpty()) {
                    item {
                        Text("Kills", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(state.kills, key = { "k_${it.id}" }) { kill ->
                        val victim = state.participants.find { it.participant.id == kill.victimParticipantId }
                        val killer = state.participants.find { it.participant.id == kill.killerParticipantId }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Close, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(victim?.player?.name ?: "?", fontWeight = FontWeight.Bold)
                                    Text(
                                        if (killer != null) "Eliminiert von ${killer.player.name}"
                                        else "Selbstverschuldet / Unbekannt",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Damage breakdown (per attacker, only if life events exist) ──
                if (state.damageByAttacker.isNotEmpty()) {
                    item {
                        Text("Schaden pro Spieler (während eigenen Zuges)",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(
                        items = state.damageByAttacker.entries.toList(),
                        key = { "dmg_${it.key}" }
                    ) { (attackerId, targetMap) ->
                        val attacker = state.participants.find { it.participant.id == attackerId }
                        DamageCard(
                            attackerName = attacker?.player?.name ?: "?",
                            targetMap = targetMap,
                            participants = state.participants
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacementCard(
    pState: ParticipantUiState,
    commanderDamageByAttacker: Map<String, Int>,
    lifeSummary: Pair<Int, Int>?  // (gained, lost)
) {
    val p = pState.participant
    val isWinner = p.placement == 1
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWinner) WinnerColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("#${p.placement ?: "?"}",
                fontWeight = FontWeight.Bold,
                color = if (isWinner) WinnerColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(36.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pState.player.name, fontWeight = FontWeight.Bold)
                Text(pState.deck?.let { "${it.name} · ${it.commanderName}" } ?: "Ohne Deck",
                    style = MaterialTheme.typography.bodySmall)
                Text("Leben: ${p.currentLife} / ${p.startingLife}",
                    style = MaterialTheme.typography.bodySmall)
                if (lifeSummary != null) {
                    val (gained, lost) = lifeSummary
                    val net = gained - lost
                    val sign = if (net >= 0) "+" else ""
                    Text("LP: +$gained erhalten / −$lost verloren (netto ${sign}${net})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                if (commanderDamageByAttacker.isNotEmpty()) {
                    Text(
                        "Cmdr-Schaden: " + commanderDamageByAttacker.entries
                            .joinToString { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun DamageCard(
    attackerName: String,
    targetMap: Map<Long, Int>,
    participants: List<ParticipantUiState>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowForward, null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(attackerName, fontWeight = FontWeight.SemiBold)
                Text(" → ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${targetMap.values.sum()} Gesamtschaden",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            targetMap.forEach { (targetId, dmg) ->
                val target = participants.find { it.participant.id == targetId }
                Row(Modifier.padding(start = 20.dp, top = 2.dp)) {
                    Text("${target?.player?.name ?: "?"}: ", fontSize = 12.sp)
                    Text("$dmg Schaden", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun buildCommanderDamageMap(
    target: ParticipantUiState,
    allParticipants: List<ParticipantUiState>
): Map<String, Int> {
    return target.commanderDamageReceived
        .filter { it.value > 0 }
        .mapKeys { (attackerId, _) ->
            allParticipants.find { it.participant.id == attackerId }?.player?.name ?: "?"
        }
}
