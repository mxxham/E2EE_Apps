package com.securechat.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.securechat.core.database.dao.ConversationDao
import com.securechat.core.database.dao.MessageDao
import com.securechat.core.database.entity.ConversationEntity
import com.securechat.core.database.entity.MessageEntity

/**
 * Room database.
 *
 * Security note: Use `androidx.security:security-crypto` `EncryptedFile` or
 * SQLCipher to encrypt the database file at rest. The room builder is configured
 * here to make it straightforward to swap in a `SupportFactory` from SQLCipher
 * without changing any DAO code.
 *
 * Schema migrations are explicit — no `fallbackToDestructiveMigration()` to
 * avoid accidentally wiping user message history.
 */
@Database(
    entities = [MessageEntity::class, ConversationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        private const val DB_NAME = "securechat.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton [AppDatabase].
         *
         * In production, swap `Room.databaseBuilder` with a SQLCipher-backed
         * `SupportFactory` here:
         * ```
         * val passphrase = SQLiteDatabase.getBytes(masterKey)
         * val factory = SupportFactory(passphrase)
         * builder.openHelperFactory(factory)
         * ```
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2 /* placeholder for future */)
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * Placeholder migration — will add new columns in v2 without destroying
         * existing message history.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Example: db.execSQL("ALTER TABLE messages ADD COLUMN edited INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** In-memory variant used for unit tests via Robolectric or instrumented tests. */
        fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
