package com.droidlens.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.droidlens.db.entities.BaselineSnapshot
import com.droidlens.db.entities.DetectedIssue

/**
 * Main database for Droid Lens library.
 */
@Database(
    entities = [BaselineSnapshot::class, DetectedIssue::class],
    version = 1,
    exportSchema = false
)
abstract class DroidLensDatabase : RoomDatabase() {
    abstract fun baselineDao(): BaselineDao
    abstract fun detectedIssueDao(): DetectedIssueDao

    companion object {
        private const val DB_NAME = "droidlens_database"

        @Volatile
        private var instance: DroidLensDatabase? = null

        fun getInstance(context: Context): DroidLensDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): DroidLensDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                DroidLensDatabase::class.java,
                DB_NAME
            ).addMigrations(MIGRATION_1_2)

            // Destructive migration for debug builds to handle schema changes easily
            // Note: In a real library, this would be more carefully managed.
            builder.fallbackToDestructiveMigration()

            return builder.build()
        }

        /**
         * Migration stub for future-proofing.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Future migration logic
            }
        }
    }
}
