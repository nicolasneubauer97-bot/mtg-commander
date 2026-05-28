package com.mtg.commander.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "random_opponent_picks",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("gameId"), Index("chooserParticipantId"), Index("chosenParticipantId")]
)
data class RandomOpponentPickEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val chooserParticipantId: Long,
    val chosenParticipantId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
