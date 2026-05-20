package com.mtg.commander.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = MTGGreenLight,
    onPrimary = MTGDark,
    primaryContainer = MTGGreen,
    secondary = MTGGold,
    onSecondary = MTGDark,
    background = MTGDark,
    surface = MTGSurface,
    error = MTGError
)

@Composable
fun MTGCommanderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
