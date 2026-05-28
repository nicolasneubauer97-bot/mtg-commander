package com.mtg.commander.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val status: GameStatus = GameStatus.IN_PROGRESS,
    val startingParticipantId: Long? = null,     // who was randomly chosen first
    val currentTurnParticipantId: Long? = null   // whose turn it currently is
)
