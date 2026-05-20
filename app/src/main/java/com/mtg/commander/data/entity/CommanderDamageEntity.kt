package com.mtg.commander.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "commander_damage",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GameParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["attackerParticipantId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GameParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetParticipantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId"), Index("attackerParticipantId"), Index("targetParticipantId")]
)
data class CommanderDamageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val attackerParticipantId: Long,
    val targetParticipantId: Long,
    val damage: Int = 0
)
