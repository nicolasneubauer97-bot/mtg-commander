package com.mtg.commander.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "turn_durations",
    foreignKeys = [ForeignKey(
        entity = GameEntity::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId"), Index("participantId")]
)
data class TurnDurationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val participantId: Long,
    val turnNumber: Int,
    val roundNumber: Int,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
