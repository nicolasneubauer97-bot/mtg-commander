package com.mtg.commander.domain.model

data class Kill(
    val id: Long = 0,
    val gameId: Long,
    val killerParticipantId: Long?,
    val victimParticipantId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
