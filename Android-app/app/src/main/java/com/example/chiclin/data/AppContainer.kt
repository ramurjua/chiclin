package com.example.chiclin.data

import android.content.Context

interface AppContainer {
    val entriesRepository: EntriesRepository
}

class AppDataContainer(private val context: Context) : AppContainer {
    override val entriesRepository: EntriesRepository by lazy {
        OfflineEntriesRepository(AppDatabase.getDatabase(context).entryDao())
    }
}
