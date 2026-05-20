package com.mtg.commander.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mtg.commander.data.dao.*
import com.mtg.commander.data.entity.*

@Database(
    entities = [
        PlayerEntity::class,
        DeckEntity::class,
        GameEntity::class,
        GameParticipantEntity::class,
        CommanderDamageEntity::class,
        KillEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun deckDao(): DeckDao
    abstract fun gameDao(): GameDao
    abstract fun gameParticipantDao(): GameParticipantDao
    abstract fun commanderDamageDao(): CommanderDamageDao
    abstract fun killDao(): KillDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mtg_commander.db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
