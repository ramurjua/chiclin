package com.example.chiclin.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: Entry): Long

    @Insert
    suspend fun insertAll(entries: List<Entry>)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("SELECT * FROM entries WHERE mes = :mes AND anio = :anio ORDER BY id")
    fun getEntriesForMonth(mes: Int, anio: Int): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE mes = :mes AND anio = :anio ORDER BY id")
    suspend fun getEntriesForMonthOnce(mes: Int, anio: Int): List<Entry>

    @Query("SELECT DISTINCT mes, anio FROM entries ORDER BY anio, mes")
    fun getAvailableMonths(): Flow<List<MonthYear>>

    @Query("SELECT * FROM entries ORDER BY anio, mes, id")
    fun getAllEntries(): Flow<List<Entry>>
}
