package com.mtg.commander.data.db

import androidx.room.TypeConverter
import com.mtg.commander.data.entity.GameStatus

class Converters {
    @TypeConverter
    fun fromGameStatus(value: GameStatus): String = value.name

    @TypeConverter
    fun toGameStatus(value: String): GameStatus = GameStatus.valueOf(value)
}
