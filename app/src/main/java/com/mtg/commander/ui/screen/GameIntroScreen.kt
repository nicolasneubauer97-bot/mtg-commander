package com.mtg.commander.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.commander.MTGCommanderApp
import com.mtg.commander.data.repository.DeckRepository
import com.mtg.commander.data.repository.GameRepository
import com.mtg.commander.data.repository.PlayerRepository
import com.mtg.commander.domain.model.Deck
import com.mtg.commander.domain.model.Player
import com.mtg.commander.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── ViewModel ───────────────────────────────────────────────────────────────

data class IntroPlayerInfo(
    val player: Player,
    val deck: Deck?,
    val wins: Int,
    val totalGames: Int
) {
    val winRate: Float get() = if (totalGames > 0) wins.toFloat() / totalGames else 0f
    val colors: String get() = deck?.colors ?: ""
}

data class GameIntroUiState(
    val players: List<IntroPlayerInfo> = emptyList(),
    val isLoading: Boolean = true
)

class GameIntroViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository,
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameIntroUiState())
    val uiState: StateFlow<GameIntroUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val participants = gameRepository.getParticipantsForGameSync(gameId)
            val players = participants.mapNotNull { p ->
                val player = playerRepository.getPlayerById(p.playerId) ?: return@mapNotNull null
                val deck = p.deckId?.let { deckRepository.getDeckById(it) }
                val (wins, total) = if (deck != null) gameRepository.getDeckWinStats(deck.id) else Pair(0, 0)
                IntroPlayerInfo(player = player, deck = deck, wins = wins, totalGames = total)
            }
            _uiState.value = GameIntroUiState(players = players, isLoading = false)
        }
    }

    companion object {
        fun factory(gameId: Long, app: MTGCommanderApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GameIntroViewModel(gameId, app.gameRepository, app.playerRepository, app.deckRepository) as T
        }
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun GameIntroScreen(
    gameId: Long,
    app: MTGCommanderApp,
    onStartGame: () -> Unit
) {
    val vm: GameIntroViewModel = viewModel(
        key = "game_intro_$gameId",
        factory = GameIntroViewModel.factory(gameId, app)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var revealed by remember { mutableStateOf(false) }

    // Trigger staggered animation once loading is done
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) revealed = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(MTGDark, MTGBlack),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(color = MTGGold)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                AnimatedVisibility(
                    visible = revealed,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
                ) {
                    Text(
                        "Commander",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MTGGold,
                        letterSpacing = 4.sp
                    )
                }

                // Player cards — staggered
                state.players.forEachIndexed { index, info ->
                    val delayMs = 200 + index * 180
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(revealed) {
                        if (revealed) {
                            kotlinx.coroutines.delay(delayMs.toLong())
                            visible = true
                        }
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(500)) + slideInHorizontally(tween(500)) { if (index % 2 == 0) -80 else 80 }
                    ) {
                        IntroPlayerCard(info)
                    }
                }

                // Start button
                val btnDelay = 200 + state.players.size * 180 + 400
                var btnVisible by remember { mutableStateOf(false) }
                LaunchedEffect(revealed) {
                    if (revealed) {
                        kotlinx.coroutines.delay(btnDelay.toLong())
                        btnVisible = true
                    }
                }
                AnimatedVisibility(
                    visible = btnVisible,
                    enter = fadeIn(tween(400)) + scaleIn(tween(400))
                ) {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val scale by pulse.animateFloat(
                        initialValue = 1f, targetValue = 1.04f,
                        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "scale"
                    )
                    Button(
                        onClick = onStartGame,
                        modifier = Modifier.scale(scale).height(52.dp).widthIn(min = 180.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MTGGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Los!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MTGBlack)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroPlayerCard(info: IntroPlayerInfo) {
    Surface(
        color = MTGSurface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mana symbols
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.width(48.dp)
            ) {
                val colors = info.colors
                if (colors.isBlank()) {
                    ColorlessManaSymbol()
                } else {
                    // Split into rows of max 3
                    val chunks = colors.chunked(3)
                    chunks.forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            chunk.forEach { c -> IntroManaSymbol(c) }
                        }
                    }
                }
            }

            // Player + deck info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info.player.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MTGOnDark
                )
                if (info.deck != null) {
                    Text(
                        info.deck.name,
                        fontSize = 12.sp,
                        color = MTGOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        info.deck.commanderName,
                        fontSize = 11.sp,
                        color = MTGGold.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text("Kein Deck", fontSize = 12.sp, color = MTGOnSurfaceVar)
                }
            }

            // Win rate badge — always shown (0% for new decks)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val pct = if (info.totalGames > 0) (info.winRate * 100).toInt() else 0
                val badgeColor = when {
                    info.totalGames == 0 -> MTGOnSurfaceVar
                    pct >= 50            -> MTGGold
                    pct >= 30            -> Color(0xFF90CAF9)
                    else                 -> MTGOnSurfaceVar
                }
                Text(
                    "$pct%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
                Text(
                    "${info.wins}/${info.totalGames}",
                    fontSize = 10.sp,
                    color = MTGOnSurfaceVar,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Siege",
                    fontSize = 9.sp,
                    color = MTGOnSurfaceVar,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun IntroManaSymbol(color: Char) {
    val (bg, letter) = when (color.uppercaseChar()) {
        'W' -> Color(0xFFF9FAF4) to "W"
        'U' -> Color(0xFF0E68AB) to "U"
        'B' -> Color(0xFF21160F) to "B"
        'R' -> Color(0xFFD3202A) to "R"
        'G' -> Color(0xFF00733E) to "G"
        'C' -> Color(0xFF888888) to "C"
        else -> return
    }
    val textColor = if (color.uppercaseChar() in listOf('W')) Color.Black else Color.White
    Box(
        modifier = Modifier.size(20.dp).clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun ColorlessManaSymbol() {
    Box(
        modifier = Modifier.size(20.dp).clip(CircleShape)
            .background(Color(0xFF888888)),
        contentAlignment = Alignment.Center
    ) {
        Text("C", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
