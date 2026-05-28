package com.mtg.commander.domain.model

data class RandomOpponentStat(
    val chooserPlayerId: Long,
    val chooserName: String,
    val chosenPlayerId: Long,
    val chosenName: String,
    val count: Int
)
