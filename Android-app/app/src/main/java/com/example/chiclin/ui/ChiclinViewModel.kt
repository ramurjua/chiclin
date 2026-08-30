@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.chiclin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chiclin.data.Comparacion
import com.example.chiclin.data.Entry
import com.example.chiclin.data.EntriesRepository
import com.example.chiclin.data.MonthYear
import com.example.chiclin.data.Recurrentes
import com.example.chiclin.data.SerieCategoria
import com.example.chiclin.data.SeriePorMes
import com.example.chiclin.data.Summary
import com.example.chiclin.data.analizarGastosRecurrentes
import com.example.chiclin.data.compararDosMeses
import com.example.chiclin.data.evolucionGlobal
import com.example.chiclin.data.evolucionPorCategoria
import com.example.chiclin.data.monthLabel
import com.example.chiclin.data.summaryFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ChiclinViewModel(private val repository: EntriesRepository) : ViewModel() {

    private val now = Calendar.getInstance()
    private val currentCalendarMonth = MonthYear(now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR))

    private val _selectedMonth = MutableStateFlow(currentCalendarMonth)
    val selectedMonth: StateFlow<MonthYear> = _selectedMonth

    /** All months with data, plus the current calendar month even if it has no entries yet. */
    val availableMonths: StateFlow<List<MonthYear>> = repository.getAvailableMonthsStream()
        .map { months ->
            val withCurrent = if (months.any { it == currentCalendarMonth }) months
                               else months + currentCalendarMonth
            withCurrent.sortedWith(compareBy({ it.anio }, { it.mes }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(currentCalendarMonth))

    val currentEntries: StateFlow<List<Entry>> = _selectedMonth
        .flatMapLatest { my -> repository.getEntriesForMonthStream(my.mes, my.anio) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<Summary> = currentEntries
        .map { summaryFor(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), summaryFor(emptyList()))

    private val allEntries: StateFlow<List<Entry>> = repository.getAllEntriesStream()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val entriesByMonth: StateFlow<List<Pair<MonthYear, List<Entry>>>> = allEntries
        .map { entries ->
            entries.groupBy { MonthYear(it.mes, it.anio) }
                .toList()
                .sortedWith(compareBy({ it.first.anio }, { it.first.mes }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Years that have at least one month of data, ascending. */
    val availableYears: StateFlow<List<Int>> = entriesByMonth
        .map { byMonth -> byMonth.map { it.first.anio }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = _selectedYear

    fun selectYear(year: Int) { _selectedYear.value = year }

    /** The Histórico tab always shows a single año at a time. */
    private val entriesByMonthForYear: StateFlow<List<Pair<MonthYear, List<Entry>>>> =
        combine(entriesByMonth, _selectedYear) { byMonth, year ->
            if (year == null) emptyList() else byMonth.filter { it.first.anio == year }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurrentes: StateFlow<Recurrentes> = entriesByMonthForYear
        .map { analizarGastosRecurrentes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), analizarGastosRecurrentes(emptyList()))

    val evolucionGlobal: StateFlow<List<SeriePorMes>> = entriesByMonthForYear
        .map { evolucionGlobal(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val evolucionCategorias: StateFlow<Pair<List<String>, List<SerieCategoria>>> = entriesByMonthForYear
        .map { evolucionPorCategoria(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<String>() to emptyList())

    private val _compMesA = MutableStateFlow<MonthYear?>(null)
    val compMesA: StateFlow<MonthYear?> = _compMesA

    private val _compMesB = MutableStateFlow<MonthYear?>(null)
    val compMesB: StateFlow<MonthYear?> = _compMesB

    val comparacion: StateFlow<Comparacion?> = combine(_compMesA, _compMesB, entriesByMonth) { a, b, byMonth ->
        if (a == null || b == null || a == b) return@combine null
        val entriesA = byMonth.firstOrNull { it.first == a }?.second ?: emptyList()
        val entriesB = byMonth.firstOrNull { it.first == b }?.second ?: emptyList()
        compararDosMeses(entriesA, monthLabel(a.mes, a.anio), entriesB, monthLabel(b.mes, b.anio))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage

    init {
        // Default the comparison pickers to the two most recent months once we know them.
        viewModelScope.launch {
            availableMonths.collect { months ->
                if (months.size >= 2) {
                    if (_compMesA.value == null) _compMesA.value = months[months.size - 2]
                    if (_compMesB.value == null) _compMesB.value = months.last()
                } else if (months.size == 1 && _compMesA.value == null) {
                    _compMesA.value = months.first()
                    _compMesB.value = months.first()
                }
            }
        }
        // Default Histórico to the current año if it has data, otherwise the most recent one.
        viewModelScope.launch {
            availableYears.collect { years ->
                if (_selectedYear.value == null && years.isNotEmpty()) {
                    val currentYear = now.get(Calendar.YEAR)
                    _selectedYear.value = if (currentYear in years) currentYear else years.last()
                }
            }
        }
    }

    fun selectMonth(monthYear: MonthYear) {
        _selectedMonth.value = monthYear
    }

    fun selectCompMesA(monthYear: MonthYear) { _compMesA.value = monthYear }
    fun selectCompMesB(monthYear: MonthYear) { _compMesB.value = monthYear }

    /** Returns an error message to show the user, or null on success. */
    fun addEntry(nombre: String, valorText: String, tipo: String): String? {
        val nombreTrim = nombre.trim()
        if (nombreTrim.isEmpty()) return "Introduce un nombre."
        val valor = valorText.trim().replace(",", ".").toDoubleOrNull()
            ?: return "\"$valorText\" no es un número válido."
        if (valor <= 0) return "El valor debe ser mayor que 0."

        val my = _selectedMonth.value
        viewModelScope.launch {
            repository.insertEntry(
                Entry(tipo = tipo, nombre = nombreTrim, valor = valor, mes = my.mes, anio = my.anio)
            )
        }
        return null
    }

    /** Returns an error message to show the user, or null on success. */
    fun updateEntry(entry: Entry, nombre: String, valorText: String, tipo: String): String? {
        val nombreTrim = nombre.trim()
        if (nombreTrim.isEmpty()) return "Introduce un nombre."
        val valor = valorText.trim().replace(",", ".").toDoubleOrNull()
            ?: return "\"$valorText\" no es un número válido."
        if (valor <= 0) return "El valor debe ser mayor que 0."

        viewModelScope.launch {
            repository.updateEntry(entry.copy(nombre = nombreTrim, valor = valor, tipo = tipo))
        }
        return null
    }

    fun deleteEntry(entry: Entry) {
        viewModelScope.launch { repository.deleteEntry(entry) }
    }

    fun importEntries(entries: List<Entry>, skippedRows: Int) {
        viewModelScope.launch {
            if (entries.isEmpty()) {
                _importMessage.value = "⚠️ No se importó ninguna entrada válida" +
                    (if (skippedRows > 0) " ($skippedRows filas ignoradas)." else ".")
                return@launch
            }
            repository.insertEntries(entries)
            _importMessage.value = if (skippedRows > 0)
                "✅ Importadas ${entries.size} entradas ($skippedRows filas ignoradas)."
            else
                "✅ Importadas ${entries.size} entradas."
        }
    }

    fun setImportMessage(message: String) {
        _importMessage.value = message
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }
}

class ChiclinViewModelFactory(private val repository: EntriesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChiclinViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChiclinViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
