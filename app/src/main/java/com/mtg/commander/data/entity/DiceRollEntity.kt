package com.mtg.commander.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dice_rolls",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("gameId"), Index("rollerParticipantId")]
)
data class DiceRollEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rollerParticipantId: Long,
    val value: Int,
    val createdAt: Long = System.currentTimeMillis()
)
