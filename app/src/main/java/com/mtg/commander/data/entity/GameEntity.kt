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
    val startingParticipantId: Long? = null,
    val currentTurnParticipantId: Long? = null,
    val turnClockwise: Boolean = true            // true = clockwise, false = counter-clockwise
)
