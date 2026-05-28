package com.mtg.commander.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "life_change_events",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("gameId"), Index("targetParticipantId"), Index("activeTurnParticipantId")]
)
data class LifeChangeEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val targetParticipantId: Long,
    val activeTurnParticipantId: Long?,  // whose turn was active; null = untracked
    val delta: Int,                       // positive = life gained, negative = damage
    val turnNumber: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
