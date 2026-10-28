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
import com.mtg.commander.domain.model.AchievementId
import com.mtg.commander.domain.model.AchievementProgress
import com.mtg.commander.domain.model.AchievementTier
import com.mtg.commander.ui.components.AchievementHexBadge
import com.mtg.commander.ui.components.achievementIcon
import com.mtg.commander.ui.viewmodel.BadgesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(app: MTGCommanderApp, onBack: () -> Unit) {
    val vm: BadgesViewModel = viewModel(factory = BadgesViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badges") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") } },
                actions = {
                    TextButton(onClick = vm::requestResetConfirm) { Text("Animationen zurücksetzen") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.players.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lege zuerst Spieler an.")
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.players) { player ->
                        FilterChip(
                            selected = state.selectedPlayer?.id == player.id,
                            onClick = { vm.selectPlayer(player) },
                            label = { Text(player.name) }
                        )
                    }
                }
                HorizontalDivider()
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { RangCard(state.rang) }
                        items(state.achievements, key = { it.id }) { ach -> AchievementCard(ach) }
                    }
                }
            }
        }
    }

    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissResetConfirm,
            title = { Text("Animationen zurücksetzen?") },
            text = {
                Text(
                    "Die Fortschritte und Stufen aller Spieler bleiben unverändert. Nur die \"schon gezeigt\"-" +
                        "Markierung wird gelöscht — beim nächsten Spiel erscheint für jedes bereits erreichte " +
                        "Achievement noch einmal das Unlock-Popup mit Sound und Konfetti."
                )
            },
            confirmButton = { Button(onClick = vm::confirmResetAnimations) { Text("Zurücksetzen") } },
            dismissButton = { TextButton(onClick = vm::dismissResetConfirm) { Text("Abbrechen") } }
        )
    }

    if (state.resetDone) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            vm.clearResetDone()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Snackbar(modifier = Modifier.padding(16.dp)) {
                Text("Animationen wurden zurückgesetzt.")
            }
        }
    }
}

@Composable
private fun RangCard(rang: AchievementTier) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AchievementHexBadge(achievementIcon(AchievementId.RANG), rang, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text("Rang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Niedrigste Stufe über alle 10 Achievements",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                rang.germanLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AchievementCard(ach: AchievementProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AchievementHexBadge(achievementIcon(ach.id), ach.tier, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(ach.id.germanName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    ach.id.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val progressText = if (ach.nextThreshold != null)
                    "${ach.value} / ${ach.nextThreshold}"
                else
                    "${ach.value} — Maximalstufe erreicht"
                Text(
                    progressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                ach.tier.germanLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
