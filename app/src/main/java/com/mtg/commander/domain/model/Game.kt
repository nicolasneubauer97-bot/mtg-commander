package com.mtg.commander.domain.model

data class Game(
    val id: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val status: String = "IN_PROGRESS"
) {
    val isFinished get() = status == "FINISHED"
    val isInProgress get() = status == "IN_PROGRESS"
}
