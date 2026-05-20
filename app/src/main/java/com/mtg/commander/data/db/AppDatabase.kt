package com.mtg.commander.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE `game_participants_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `playerId` INTEGER NOT NULL,
                        `deckId` INTEGER,
                        `startingLife` INTEGER NOT NULL DEFAULT 40,
                        `currentLife` INTEGER NOT NULL DEFAULT 40,
                        `placement` INTEGER,
                        `isEliminated` INTEGER NOT NULL DEFAULT 0,
                        `eliminatedAt` INTEGER,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`playerId`) REFERENCES `players`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """)
                database.execSQL("""
                    INSERT INTO `game_participants_new`
                    SELECT `id`,`gameId`,`playerId`,`deckId`,`startingLife`,`currentLife`,`placement`,`isEliminated`,`eliminatedAt`
                    FROM `game_participants`
                """)
                database.execSQL("DROP TABLE `game_participants`")
                database.execSQL("ALTER TABLE `game_participants_new` RENAME TO `game_participants`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_game_participants_gameId` ON `game_participants` (`gameId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_game_participants_playerId` ON `game_participants` (`playerId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_game_participants_deckId` ON `game_participants` (`deckId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mtg_commander.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
    }
}
