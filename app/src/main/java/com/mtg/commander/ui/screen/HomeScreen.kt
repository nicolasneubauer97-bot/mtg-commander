package com.mtg.commander.ui.screen

import androidx.compose.foundation.layout.*
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
import com.mtg.commander.ui.viewmodel.HomeViewModel

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MTG Commander",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Tracker",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        if (state.activeGame != null) {
            FilledTonalButton(
                onClick = { onResume(state.activeGame!!.id) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Laufende Partie fortsetzen", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Neue Partie", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        HomeNavButton(icon = Icons.Filled.Person, label = "Spieler", onClick = onPlayers)
        Spacer(modifier = Modifier.height(8.dp))
        HomeNavButton(icon = Icons.Filled.Style, label = "Decks", onClick = onDecks)
        Spacer(modifier = Modifier.height(8.dp))
        HomeNavButton(icon = Icons.Filled.Leaderboard, label = "Leaderboard", onClick = onLeaderboard)
        Spacer(modifier = Modifier.height(8.dp))
        HomeNavButton(icon = Icons.Filled.BarChart, label = "Deck-Statistiken", onClick = onDeckStats)
        Spacer(modifier = Modifier.height(8.dp))
        // HomeNavButton(icon = Icons.Filled.History, label = "Spielhistorie", onClick = onHistory)
    }
}

@Composable
private fun HomeNavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(label)
    }
}
