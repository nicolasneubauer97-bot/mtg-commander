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
        KillEntity::class,
        LifeChangeEventEntity::class,
        RandomOpponentPickEntity::class
    ],
    version = 4,
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
    abstract fun lifeChangeEventDao(): LifeChangeEventDao
    abstract fun randomOpponentPickDao(): RandomOpponentPickDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `decks` ADD COLUMN `imageUrl` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // New turn-tracking columns on games table
                database.execSQL("ALTER TABLE `games` ADD COLUMN `startingParticipantId` INTEGER")
                database.execSQL("ALTER TABLE `games` ADD COLUMN `currentTurnParticipantId` INTEGER")
                // Life change events table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `life_change_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `targetParticipantId` INTEGER NOT NULL,
                        `activeTurnParticipantId` INTEGER,
                        `delta` INTEGER NOT NULL,
                        `turnNumber` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_life_change_events_gameId` ON `life_change_events` (`gameId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_life_change_events_targetParticipantId` ON `life_change_events` (`targetParticipantId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_life_change_events_activeTurnParticipantId` ON `life_change_events` (`activeTurnParticipantId`)")
                // Random opponent picks table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `random_opponent_picks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `chooserParticipantId` INTEGER NOT NULL,
                        `chosenParticipantId` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_random_opponent_picks_gameId` ON `random_opponent_picks` (`gameId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_random_opponent_picks_chooserParticipantId` ON `random_opponent_picks` (`chooserParticipantId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_random_opponent_picks_chosenParticipantId` ON `random_opponent_picks` (`chosenParticipantId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mtg_commander.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { INSTANCE = it }
            }
    }
}
