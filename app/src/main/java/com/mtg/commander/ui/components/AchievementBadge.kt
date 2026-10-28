package com.mtg.commander.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.mtg.commander.domain.model.AchievementId
import com.mtg.commander.domain.model.AchievementTier

fun achievementIcon(id: AchievementId): ImageVector = when (id) {
    AchievementId.SERIENSIEGER -> Icons.Filled.EmojiEvents
    AchievementId.SCHARFRICHTER -> Icons.Filled.Gavel
    AchievementId.KOMMANDANT_DES_SCHADENS -> Icons.Filled.Bolt
    AchievementId.VETERAN -> Icons.Filled.Schedule
    AchievementId.LEBENSRETTER -> Icons.Filled.Favorite
    AchievementId.NEMESIS -> Icons.Filled.Close
    AchievementId.DAUERGAST -> Icons.Filled.Flag
    AchievementId.UNSTERBLICH -> Icons.Filled.Shield
    AchievementId.VERWUESTER -> Icons.Filled.LocalFireDepartment
    AchievementId.ZIELSCHEIBE -> Icons.Filled.GpsFixed
    AchievementId.RANG -> Icons.Filled.MilitaryTech
}

private fun tierColors(tier: AchievementTier): Pair<Color, Color> = when (tier) {
    AchievementTier.NONE -> Color(0xFF3A3D44) to Color(0xFF2C2E34)
    AchievementTier.BRONZE -> Color(0xFFB8803F) to Color(0xFF7A4F23)
    AchievementTier.SILVER -> Color(0xFFC9CDD3) to Color(0xFF8D9299)
    AchievementTier.GOLD -> Color(0xFFE3B341) to Color(0xFF9C7420)
    AchievementTier.PLATINUM -> Color(0xFFDFF3F8) to Color(0xFF6FB3C2)
}

private fun hexagonPath(size: Size): Path {
    val w = size.width; val h = size.height
    return Path().apply {
        moveTo(w * 0.5f, 0f)
        lineTo(w * 0.95f, h * 0.25f)
        lineTo(w * 0.95f, h * 0.75f)
        lineTo(w * 0.5f, h)
        lineTo(w * 0.05f, h * 0.75f)
        lineTo(w * 0.05f, h * 0.25f)
        close()
    }
}

/** A hexagonal achievement medallion: tier-colored finish with the achievement's icon, or a lock when NONE. */
@Composable
fun AchievementHexBadge(
    icon: ImageVector,
    tier: AchievementTier,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val (c1, c2) = tierColors(tier)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawPath(hexagonPath(this.size), brush = Brush.linearGradient(listOf(c1, c2)))
        }
        Icon(
            imageVector = if (tier == AchievementTier.NONE) Icons.Filled.Lock else icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.52f),
            tint = if (tier == AchievementTier.NONE) Color(0xFF8A8F99) else Color.Black.copy(alpha = 0.58f)
        )
    }
}
