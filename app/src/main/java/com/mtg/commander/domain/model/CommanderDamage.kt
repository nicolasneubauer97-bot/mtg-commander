package com.mtg.commander.domain.model

data class CommanderDamage(
    val id: Long = 0,
    val gameId: Long,
    val attackerParticipantId: Long,
    val targetParticipantId: Long,
    val damage: Int = 0
)
