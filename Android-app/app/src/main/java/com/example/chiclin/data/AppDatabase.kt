package com.example.chiclin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds `categoria`, the grouping used for analysis (see [Entry]). Existing rows are
 * backfilled with categoria = nombre so every entry starts out as its own category, unchanged
 * from how aggregation already behaved before this column existed.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN categoria TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE entries SET categoria = nombre")
    }
}

@Database(entities = [Entry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                // No fallbackToDestructiveMigration(): a future schema change without a real
                // Migration should crash loudly during development, not silently wipe data.
                Room.databaseBuilder(context, AppDatabase::class.java, "chiclin_database")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
