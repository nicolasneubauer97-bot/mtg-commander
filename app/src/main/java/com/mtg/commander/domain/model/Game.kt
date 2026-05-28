package com.mtg.commander.domain.model

data class Game(
    val id: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val status: String = "IN_PROGRESS",
    val startingParticipantId: Long? = null,
    val currentTurnParticipantId: Long? = null,
    val turnClockwise: Boolean = true
) {
    val isFinished get() = status == "FINISHED"
    val isInProgress get() = status == "IN_PROGRESS"
}
