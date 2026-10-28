package com.mtg.commander.data.entity

import androidx.room.Entity

/**
 * Persists which achievement tier was last shown via the in-game popup for a
 * player, separately from the tier itself (which is always derived live from
 * stats). Lets "Animationen zurücksetzen" re-trigger popups for already-earned
 * tiers without touching any actual progress.
 */
@Entity(tableName = "achievement_shown", primaryKeys = ["playerId", "achievementId"])
data class AchievementShownEntity(
    val playerId: Long,
    val achievementId: String,
    val shownTier: String
)
