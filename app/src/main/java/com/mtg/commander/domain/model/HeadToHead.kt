package com.mtg.commander.domain.model

data class HeadToHead(
    val playerAId: Long,
    val playerAName: String,
    val playerBId: Long,
    val playerBName: String,
    val gamesTogether: Int,
    val winsA: Int,
    val winsB: Int,
    val killsAOnB: Int,
    val killsBOnA: Int
)
