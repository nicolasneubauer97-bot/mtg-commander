package com.mtg.commander.data.repository

import com.mtg.commander.data.dao.AchievementShownDao
import com.mtg.commander.data.dao.CommanderDamageDao
import com.mtg.commander.data.dao.GameParticipantDao
import com.mtg.commander.data.dao.KillDao
import com.mtg.commander.data.dao.LifeChangeEventDao
import com.mtg.commander.data.dao.RandomOpponentPickDao
import com.mtg.commander.data.entity.AchievementShownEntity
import com.mtg.commander.domain.model.AchievementId
import com.mtg.commander.domain.model.AchievementProgress
import com.mtg.commander.domain.model.AchievementTier

// Bronze, Silber, Gold, Platin thresholds for each count-based achievement
private val THRESHOLDS: Map<AchievementId, IntArray> = mapOf(
    AchievementId.SERIENSIEGER to intArrayOf(5, 15, 30, 50),
    AchievementId.SCHARFRICHTER to intArrayOf(10, 30, 60, 100),
    AchievementId.KOMMANDANT_DES_SCHADENS to intArrayOf(100, 300, 750, 1500),
    AchievementId.VETERAN to intArrayOf(10, 25, 50, 100),
    AchievementId.LEBENSRETTER to intArrayOf(100, 300, 750, 1500),
    AchievementId.NEMESIS to intArrayOf(3, 8, 15, 25),
    AchievementId.DAUERGAST to intArrayOf(5, 15, 30, 50),
    AchievementId.VERWUESTER to intArrayOf(500, 1500, 3500, 7000),
    AchievementId.ZIELSCHEIBE to intArrayOf(20, 50, 100, 200)
)

private fun tierForValue(value: Int, thresholds: IntArray): AchievementTier = when {
    value >= thresholds[3] -> AchievementTier.PLATINUM
    value >= thresholds[2] -> AchievementTier.GOLD
    value >= thresholds[1] -> AchievementTier.SILVER
    value >= thresholds[0] -> AchievementTier.BRONZE
    else -> AchievementTier.NONE
}

private fun nextThreshold(value: Int, thresholds: IntArray): Int? =
    thresholds.firstOrNull { it > value }

private fun progress(id: AchievementId, value: Int): AchievementProgress {
    val thresholds = THRESHOLDS.getValue(id)
    return AchievementProgress(id, tierForValue(value, thresholds), value, nextThreshold(value, thresholds))
}

class AchievementRepository(
    private val participantDao: GameParticipantDao,
    private val killDao: KillDao,
    private val commanderDamageDao: CommanderDamageDao,
    private val lifeChangeEventDao: LifeChangeEventDao,
    private val randomOpponentPickDao: RandomOpponentPickDao,
    private val achievementShownDao: AchievementShownDao
) {
    /** The 10 regular achievements for this player, in display order. */
    suspend fun getAchievements(playerId: Long): List<AchievementProgress> {
        val wins = participantDao.countWinsForPlayer(playerId)
        val kills = killDao.countAllKillsForPlayer(playerId)
        val cmdDamage = commanderDamageDao.sumCommanderDamageForPlayer(playerId)
        val gamesPlayed = participantDao.countAllGamesForPlayer(playerId)
        val lifeHealed = lifeChangeEventDao.sumLifeGainedForPlayer(playerId)
        val nemesisMax = killDao.getKillCountsPerOpponent(playerId).maxOfOrNull { it.killCount } ?: 0
        val starterCount = participantDao.countStarterGamesForPlayer(playerId)
        val immortalWins = participantDao.countImmortalWins(playerId)
        val totalDamage = lifeChangeEventDao.sumDamageDealtByPlayer(playerId)
        val chosenCount = randomOpponentPickDao.countTimesChosenForPlayer(playerId)

        return listOf(
            progress(AchievementId.SERIENSIEGER, wins),
            progress(AchievementId.SCHARFRICHTER, kills),
            progress(AchievementId.KOMMANDANT_DES_SCHADENS, cmdDamage),
            progress(AchievementId.VETERAN, gamesPlayed),
            progress(AchievementId.LEBENSRETTER, lifeHealed),
            progress(AchievementId.NEMESIS, nemesisMax),
            progress(AchievementId.DAUERGAST, starterCount),
            AchievementProgress(
                AchievementId.UNSTERBLICH,
                if (immortalWins > 0) AchievementTier.PLATINUM else AchievementTier.NONE,
                immortalWins.coerceAtMost(1), null
            ),
            progress(AchievementId.VERWUESTER, totalDamage),
            progress(AchievementId.ZIELSCHEIBE, chosenCount)
        )
    }

    /** "Rang": the lowest tier across all 10 regular achievements. */
    fun computeRang(achievements: List<AchievementProgress>): AchievementTier =
        achievements.minOfOrNull { it.tier.order }
            ?.let { order -> AchievementTier.entries.first { it.order == order } }
            ?: AchievementTier.NONE

    suspend fun getShownTiers(playerId: Long): Map<AchievementId, AchievementTier> =
        achievementShownDao.getAll()
            .filter { it.playerId == playerId }
            .mapNotNull { entry ->
                val id = AchievementId.entries.find { it.name == entry.achievementId } ?: return@mapNotNull null
                val tier = AchievementTier.entries.find { it.name == entry.shownTier } ?: AchievementTier.NONE
                id to tier
            }.toMap()

    suspend fun markShown(playerId: Long, achievementId: AchievementId, tier: AchievementTier) {
        achievementShownDao.upsert(AchievementShownEntity(playerId, achievementId.name, tier.name))
    }

    /** "Animationen zurücksetzen" — clears only the shown-flag, never the actual progress. */
    suspend fun resetShownAnimations() {
        achievementShownDao.deleteAll()
    }
}
