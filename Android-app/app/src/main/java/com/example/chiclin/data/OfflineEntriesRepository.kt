package com.example.chiclin.data

import kotlinx.coroutines.flow.Flow

class OfflineEntriesRepository(private val entryDao: EntryDao) : EntriesRepository {
    override fun getEntriesForMonthStream(mes: Int, anio: Int): Flow<List<Entry>> =
        entryDao.getEntriesForMonth(mes, anio)

    override fun getAvailableMonthsStream(): Flow<List<MonthYear>> =
        entryDao.getAvailableMonths()

    override fun getAllEntriesStream(): Flow<List<Entry>> =
        entryDao.getAllEntries()

    override suspend fun getEntriesForMonthOnce(mes: Int, anio: Int): List<Entry> =
        entryDao.getEntriesForMonthOnce(mes, anio)

    override suspend fun insertEntry(entry: Entry) = entryDao.insert(entry).let { }

    override suspend fun insertEntries(entries: List<Entry>) = entryDao.insertAll(entries)

    override suspend fun deleteEntry(entry: Entry) = entryDao.delete(entry)
}
