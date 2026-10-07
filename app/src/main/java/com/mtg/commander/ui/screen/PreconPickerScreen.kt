package com.mtg.commander.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.PreconRepository
import com.mtg.commander.domain.model.PreconDeck
import com.mtg.commander.ui.theme.*
import com.mtg.commander.ui.utils.ColorUtils
import com.mtg.commander.ui.viewmodel.PreconPickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreconPickerScreen(
    repo: PreconRepository,
    deckRepository: DeckRepository,
    playerId: Long,
    onPicked: (PreconDeck) -> Unit,
    onBack: () -> Unit
) {
    val vm: PreconPickerViewModel = viewModel(
        factory = PreconPickerViewModel.factory(repo, deckRepository, playerId)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Precon-Deck wählen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::setSearch,
                placeholder = { Text("Suchen (Deck, Commander, Set…)") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty())
                        IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Filled.Clear, null) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )

            if (state.networkWarning) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.WifiOff, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Kein Netzwerk – nur gespeicherte Decks verfügbar. Tippe auf Aktualisieren wenn online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MTGGold)
                            Spacer(Modifier.height(12.dp))
                            Text("Lade Precon-Decks…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = vm::refresh) { Text("Wiederholen") }
                        }
                    }
                }
                else -> {
                    // Bild-Vorlade-Fortschritt
                if (state.isPreloadingImages) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.weight(1f).height(3.dp),
                            color = MTGGold
                        )
                        Text(state.preloadProgress, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val list = state.filtered
                    if (list.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Keine Decks gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            "${list.size} Decks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(list, key = { it.fileName }) { deck ->
                                val alreadyOwned = state.isAlreadyOwned(deck)
                                PreconCard(
                                    deck = deck,
                                    alreadyOwned = alreadyOwned,
                                    onClick = { if (!alreadyOwned) onPicked(deck) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreconCard(deck: PreconDeck, alreadyOwned: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !alreadyOwned, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MTGGold.copy(alpha = if (alreadyOwned) 0.1f else 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (alreadyOwned) 0.5f else 1f)
        )
    ) {
        Box(Modifier.fillMaxWidth().height(130.dp)) {
            val artUrl = deck.displayArtUrl
            val context = LocalContext.current
            if (artUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUrl)
                        .addHeader("User-Agent", "MTGCommander/1.0 Android")
                        .addHeader("Accept", "image/*")
                        .crossfade(true)
                        .build(),
                    contentDescription = deck.commanderName,
                    modifier = Modifier.fillMaxSize().let {
                        if (alreadyOwned) it.alpha(0.4f) else it
                    },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoStories, null,
                        tint = MTGGold.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                }
            }
            // Gradient overlay at bottom for text readability
            Box(
                Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
            )
            // Set code badge
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(deck.setCode, fontSize = 9.sp, color = MTGGold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
            if (alreadyOwned) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Check, null, tint = MTGGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Bereits hinzugefügt", fontSize = 10.sp, color = MTGGold)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                deck.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (deck.commanderName.isNotBlank()) {
                // English name always shown as primary
                Text(
                    deck.commanderName,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.secondary
                )
                // German name below, if different from English
                val deName = (deck.commanderNameDe ?: "").trim()
                if (deName.isNotBlank() && deName != deck.commanderName) {
                    Text(
                        deName,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
                // Second commander (partner / background) — English + German
                if (deck.commanderName2.isNotBlank()) {
                    Text(
                        "& ${deck.commanderName2}",
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
                    )
                    val deName2 = (deck.commanderNameDe2 ?: "").trim()
                    if (deName2.isNotBlank() && deName2 != deck.commanderName2) {
                        Text(
                            "& $deName2",
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                Text("Lade…", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (deck.colors.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    deck.colors.forEach { c -> ManaSymbol(c) }
                }
                Text(ColorUtils.toGerman(deck.colors), fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ManaSymbol(color: Char) {
    val (bg, letter) = when (color.uppercaseChar()) {
        'W' -> Color(0xFFF9FAF4) to "W"
        'U' -> Color(0xFF0E68AB) to "U"
        'B' -> Color(0xFF21160F) to "B"
        'R' -> Color(0xFFD3202A) to "R"
        'G' -> Color(0xFF00733E) to "G"
        'C' -> Color(0xFF888888) to "C"
        else -> return
    }
    val textColor = if (color.uppercaseChar() in listOf('W', 'G')) Color.Black else Color.White
    Box(
        modifier = Modifier.size(14.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
