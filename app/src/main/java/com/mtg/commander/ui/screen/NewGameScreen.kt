package com.mtg.commander.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtg.commander.ui.theme.MTGGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(onBack: () -> Unit, onCountSelected: (Int) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neue Partie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Wie viele Spieler?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 40.dp))

            listOf(2, 3, 4).forEach { count ->
                Button(
                    onClick = { onCountSelected(count) },
                    modifier = Modifier.fillMaxWidth().height(72.dp).padding(vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MTGGold)
                ) {
                    Text("$count Spieler", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
