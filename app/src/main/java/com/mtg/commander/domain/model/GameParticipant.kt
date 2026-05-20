package com.mtg.commander.domain.model

data class GameParticipant(
    val id: Long = 0,
    val gameId: Long,
    val playerId: Long,
    val deckId: Long,
    val startingLife: Int = 40,
    val currentLife: Int = 40,
    val placement: Int? = null,
    val isEliminated: Boolean = false,
    val eliminatedAt: Long? = null
)
