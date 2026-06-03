package com.mtg.commander.domain.model

data class TurnDuration(
    val id: Long = 0,
    val gameId: Long,
    val participantId: Long,
    val turnNumber: Int,
    val roundNumber: Int,
    val durationMs: Long
)
