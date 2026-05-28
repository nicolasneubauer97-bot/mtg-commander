package com.mtg.commander.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.ui.theme.MTGBlack
import com.mtg.commander.ui.theme.MTGDark
import com.mtg.commander.ui.viewmodel.HomeViewModel
import com.mtg.commander.ui.viewmodel.ResetOption

@Composable
fun HomeScreen(
    app: MTGCommanderApp,
    onNewGame: () -> Unit,
    onResume: (Long) -> Unit,
    onPlayers: () -> Unit,
    onDecks: () -> Unit,
    onLeaderboard: () -> Unit,
    onDeckStats: () -> Unit,
    onHistory: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MTGBlack, MTGDark)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Titel ───────────────────────────────────────────────────────
            Text(
                "MTG Commander",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                "T R A C K E R",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 6.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp)
            )

            // ─── Primäre Aktionen ─────────────────────────────────────────────
            if (state.activeGame != null) {
                FilledTonalButton(
                    onClick = { onResume(state.activeGame!!.id) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.padding(end = 8.dp))
                    Text("Laufende Partie fortsetzen", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 8.dp))
                Text("Neue Partie", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(32.dp))

            // ─── Navigations-Grid ─────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeNavCard(Icons.Filled.Person,      "Spieler",      Modifier.weight(1f), onPlayers)
                HomeNavCard(Icons.Filled.Style,       "Decks",        Modifier.weight(1f), onDecks)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeNavCard(Icons.Filled.Leaderboard, "Leaderboard",  Modifier.weight(1f), onLeaderboard)
                HomeNavCard(Icons.Filled.BarChart,    "Statistiken",  Modifier.weight(1f), onDeckStats)
            }

            Spacer(Modifier.height(32.dp))

            TextButton(
                onClick = vm::showResetDialog,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                )
            ) {
                Icon(Icons.Filled.DeleteSweep, null,
                    modifier = Modifier.size(15.dp).padding(end = 0.dp))
                Spacer(Modifier.width(6.dp))
                Text("Statistiken zurücksetzen", fontSize = 13.sp)
            }
        }
    }

    // ─── Reset-Option-Dialog ──────────────────────────────────────────────────
    if (state.showResetDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissResetDialog,
            title = { Text("Statistiken zurücksetzen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Was soll zurückgesetzt werden?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp))
                    ResetOptionRow(
                        selected    = state.selectedReset == ResetOption.FINISHED_GAMES,
                        label       = "Spielhistorie löschen",
                        description = "Alle abgeschlossenen Spiele inkl. Platzierungen, Kills und Commander-Schaden.",
                        onClick     = { vm.selectResetOption(ResetOption.FINISHED_GAMES) }
                    )
                    ResetOptionRow(
                        selected    = state.selectedReset == ResetOption.KILLS_ONLY,
                        label       = "Nur Kills zurücksetzen",
                        description = "Kill-Einträge löschen. Spielhistorie und Platzierungen bleiben erhalten.",
                        onClick     = { vm.selectResetOption(ResetOption.KILLS_ONLY) }
                    )
                    ResetOptionRow(
                        selected    = state.selectedReset == ResetOption.COMMANDER_DAMAGE_ONLY,
                        label       = "Nur Commander-Schaden zurücksetzen",
                        description = "Commander-Schaden-Einträge löschen. Spielhistorie bleibt erhalten.",
                        onClick     = { vm.selectResetOption(ResetOption.COMMANDER_DAMAGE_ONLY) }
                    )
                    ResetOptionRow(
                        selected      = state.selectedReset == ResetOption.EVERYTHING,
                        label         = "Alles zurücksetzen",
                        description   = "Alle Spiele (inkl. laufende), Kills und Commander-Schaden. Spieler und Decks bleiben.",
                        isDestructive = true,
                        onClick       = { vm.selectResetOption(ResetOption.EVERYTHING) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = vm::requestResetConfirm,
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Weiter") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissResetDialog) { Text("Abbrechen") }
            }
        )
    }

    // ─── Bestätigungs-Dialog ──────────────────────────────────────────────────
    if (state.showResetConfirm) {
        val label = when (state.selectedReset) {
            ResetOption.FINISHED_GAMES       -> "Spielhistorie löschen"
            ResetOption.KILLS_ONLY           -> "Alle Kills zurücksetzen"
            ResetOption.COMMANDER_DAMAGE_ONLY -> "Commander-Schaden zurücksetzen"
            ResetOption.EVERYTHING           -> "Alles zurücksetzen"
        }
        AlertDialog(
            onDismissRequest = vm::dismissResetConfirm,
            title = { Text("Sicher?") },
            text  = { Text("\"$label\" kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                Button(
                    onClick = vm::executeReset,
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Jetzt zurücksetzen") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissResetConfirm) { Text("Abbrechen") }
            }
        )
    }

    // ─── Erfolgs-Snackbar ─────────────────────────────────────────────────────
    if (state.resetDone) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            vm.clearResetDone()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Snackbar(modifier = Modifier.padding(16.dp)) {
                Text("Statistiken wurden zurückgesetzt.")
            }
        }
    }
}

@Composable
private fun HomeNavCard(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(82.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label,
                style     = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ResetOptionRow(
    selected: Boolean,
    label: String,
    description: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val labelColor = if (isDestructive) MaterialTheme.colorScheme.error
                     else MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, color = labelColor,
                style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
