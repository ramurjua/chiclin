package com.example.chiclin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Entry::class], version = 1, exportSchema = false)
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
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
