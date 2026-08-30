package com.example.chiclin.data

import kotlinx.coroutines.flow.Flow

/**
 * Provides access to [Entry] records. A single implementation ([OfflineEntriesRepository])
 * backs it with Room today; the interface exists so a future sync-backed repository
 * (mirroring MiAlacena's SwitchableItemsRepository) can be swapped in later without
 * touching the ViewModel or UI.
 */
interface EntriesRepository {
    fun getEntriesForMonthStream(mes: Int, anio: Int): Flow<List<Entry>>
    fun getAvailableMonthsStream(): Flow<List<MonthYear>>
    fun getAllEntriesStream(): Flow<List<Entry>>
    suspend fun getEntriesForMonthOnce(mes: Int, anio: Int): List<Entry>
    suspend fun insertEntry(entry: Entry)
    suspend fun insertEntries(entries: List<Entry>)
    suspend fun updateEntry(entry: Entry)
    suspend fun deleteEntry(entry: Entry)
}
