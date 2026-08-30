package com.example.chiclin.data

/**
 * Pure calculation functions ported from the desktop app's logic.py (FinanceManager).
 * No Android/UI dependencies here so they stay trivially testable.
 */

val MONTHS_ES = mapOf(
    1 to "Enero", 2 to "Febrero", 3 to "Marzo", 4 to "Abril",
    5 to "Mayo", 6 to "Junio", 7 to "Julio", 8 to "Agosto",
    9 to "Septiembre", 10 to "Octubre", 11 to "Noviembre", 12 to "Diciembre"
)

fun monthLabel(mes: Int, anio: Int): String = "${MONTHS_ES[mes]} $anio"

private fun aggregateBy(entries: List<Entry>, tipo: String, key: (Entry) -> String): Map<String, Double> {
    val result = LinkedHashMap<String, Double>()
    for (e in entries) {
        if (e.tipo != tipo) continue
        val k = key(e)
        result[k] = (result[k] ?: 0.0) + e.valor
    }
    return result
}

/** Sums [entries] of the given [tipo], grouped by categoria. Used by every cross-month analysis
 *  (Comparación, Histórico) so that categorized entries (e.g. "Japón Vuelos" + "Japón Compras"
 *  under "Viajes") get combined. */
fun aggregate(entries: List<Entry>, tipo: String): Map<String, Double> = aggregateBy(entries, tipo) { it.categoria }

/** Sums [entries] of the given [tipo], grouped by nombre (the literal entry name). Used by the
 *  Mes actual tab, where the user is looking at what they entered this month, not the category
 *  it's been folded into for longer-term analysis. */
fun aggregateByNombre(entries: List<Entry>, tipo: String): Map<String, Double> = aggregateBy(entries, tipo) { it.nombre }

data class Summary(
    val totalIngresos: Double,
    val totalGastos: Double,
    val totalAhorros: Double,
    val balance: Double,
    val gastosAgg: Map<String, Double>,
    val ingresosAgg: Map<String, Double>,
    val ahorrosAgg: Map<String, Double>
)

/** [porCategoria] selects the grouping key for [Summary.gastosAgg]/[ingresosAgg]/[ahorrosAgg] —
 *  categoria (default, for cross-month analysis) or nombre (Mes actual). Totals/balance are the
 *  same either way. */
fun summaryFor(entries: List<Entry>, porCategoria: Boolean = true): Summary {
    val aggFn = if (porCategoria) ::aggregate else ::aggregateByNombre
    val gastosAgg = aggFn(entries, Tipo.GASTOS)
    val ingresosAgg = aggFn(entries, Tipo.INGRESOS)
    val ahorrosAgg = aggFn(entries, Tipo.AHORROS)

    val totalIngresos = ingresosAgg.values.sum()
    val totalGastos = gastosAgg.values.sum()
    val totalAhorros = ahorrosAgg.values.sum()

    return Summary(
        totalIngresos = totalIngresos,
        totalGastos = totalGastos,
        totalAhorros = totalAhorros,
        balance = totalIngresos - totalGastos,
        gastosAgg = gastosAgg,
        ingresosAgg = ingresosAgg,
        ahorrosAgg = ahorrosAgg
    )
}

/** Pie-chart-ready data: gastos slices, plus "Resto del ingreso" if ingresos > gastos. */
fun pieData(summary: Summary): List<Pair<String, Double>> {
    val slices = summary.gastosAgg.entries.map { it.key to it.value }.toMutableList()
    val resto = summary.totalIngresos - summary.totalGastos
    if (resto > 0) slices.add("Resto del ingreso" to resto)
    return slices
}

enum class EstadoCategoria { NUEVO, ELIMINADO, SUBE, BAJA, IGUAL }

data class CategoriaComparacion(
    val cat: String,
    val val1: Double,
    val val2: Double,
    val diff: Double,
    val pctCambio: Double?,
    val estado: EstadoCategoria
)

data class Comparacion(
    val mes1: String,
    val mes2: String,
    val totalGastos1: Double,
    val totalGastos2: Double,
    val totalIngresos1: Double,
    val totalIngresos2: Double,
    val diffGastos: Double,
    val diffIngresos: Double,
    val categorias: List<String>,
    val valores1: List<Double>,
    val valores2: List<Double>,
    val detalle: List<CategoriaComparacion>
)

fun compararDosMeses(
    entries1: List<Entry>, label1: String,
    entries2: List<Entry>, label2: String
): Comparacion {
    val g1 = aggregate(entries1, Tipo.GASTOS)
    val g2 = aggregate(entries2, Tipo.GASTOS)
    val i1 = aggregate(entries1, Tipo.INGRESOS)
    val i2 = aggregate(entries2, Tipo.INGRESOS)

    val tg1 = g1.values.sum(); val tg2 = g2.values.sum()
    val ti1 = i1.values.sum(); val ti2 = i2.values.sum()

    val todas = (g1.keys + g2.keys).toSortedSet().toList()
    val detalle = todas.map { cat ->
        val v1 = g1[cat] ?: 0.0
        val v2 = g2[cat] ?: 0.0
        val diff = v2 - v1
        val estado: EstadoCategoria
        val pct: Double?
        when {
            v1 == 0.0 -> { estado = EstadoCategoria.NUEVO; pct = null }
            v2 == 0.0 -> { estado = EstadoCategoria.ELIMINADO; pct = null }
            else -> {
                pct = (diff / v1) * 100
                estado = when {
                    diff > 0 -> EstadoCategoria.SUBE
                    diff < 0 -> EstadoCategoria.BAJA
                    else -> EstadoCategoria.IGUAL
                }
            }
        }
        CategoriaComparacion(cat, v1, v2, diff, pct, estado)
    }

    return Comparacion(
        mes1 = label1, mes2 = label2,
        totalGastos1 = tg1, totalGastos2 = tg2,
        totalIngresos1 = ti1, totalIngresos2 = ti2,
        diffGastos = tg2 - tg1, diffIngresos = ti2 - ti1,
        categorias = todas,
        valores1 = todas.map { g1[it] ?: 0.0 },
        valores2 = todas.map { g2[it] ?: 0.0 },
        detalle = detalle
    )
}

enum class TipoRecurrencia { FIJO, RECURRENTE, OCASIONAL }

data class Aparicion(val mes: String, val valor: Double)

data class CategoriaRecurrente(
    val cat: String,
    val apariciones: List<Aparicion>,
    val tipo: TipoRecurrencia
) {
    val media: Double get() = apariciones.sumOf { it.valor } / apariciones.size
}

data class Recurrentes(
    val fijos: List<CategoriaRecurrente>,
    val recurrentes: List<CategoriaRecurrente>,
    val ocasionales: List<CategoriaRecurrente>,
    val totalMeses: Int
)

/** One point per month for the global ingresos/gastos evolution line chart. */
data class SeriePorMes(
    val mes: MonthYear,
    val label: String,
    val totalIngresos: Double,
    val totalGastos: Double
)

/** [entriesPorMes] must be sorted chronologically, one entry per distinct (mes, anio). */
fun evolucionGlobal(entriesPorMes: List<Pair<MonthYear, List<Entry>>>): List<SeriePorMes> =
    entriesPorMes.map { (my, entries) ->
        val s = summaryFor(entries)
        SeriePorMes(my, MONTHS_ES[my.mes] ?: "", s.totalIngresos, s.totalGastos)
    }

/** One gasto category's value across every month (0 for months it didn't appear in). */
data class SerieCategoria(val categoria: String, val valores: List<Double>)

/**
 * Per-category gasto evolution within a single year (caller is expected to have already
 * filtered [entriesPorMes] to one año — labels are month names only, no year). Returns the
 * chronological month labels alongside one [SerieCategoria] per category that appeared in at
 * least two distinct months (a single occurrence isn't a trend worth plotting), values aligned
 * index-for-index with the returned labels.
 */
fun evolucionPorCategoria(entriesPorMes: List<Pair<MonthYear, List<Entry>>>): Pair<List<String>, List<SerieCategoria>> {
    val labels = entriesPorMes.map { MONTHS_ES[it.first.mes] ?: "" }
    val aggPerMonth = entriesPorMes.map { aggregate(it.second, Tipo.GASTOS) }
    val apariciones = aggPerMonth.flatMap { it.keys }.groupingBy { it }.eachCount()
    val allCats = apariciones.filterValues { it >= 2 }.keys.sorted()
    val series = allCats.map { cat -> SerieCategoria(cat, aggPerMonth.map { it[cat] ?: 0.0 }) }
    return labels to series
}

/** [entriesPorMes] must contain one entry per distinct (mes, anio) with all its Entry rows. */
fun analizarGastosRecurrentes(entriesPorMes: List<Pair<MonthYear, List<Entry>>>): Recurrentes {
    val totalMeses = entriesPorMes.size
    if (totalMeses < 2) return Recurrentes(emptyList(), emptyList(), emptyList(), totalMeses)

    val apariciones = LinkedHashMap<String, MutableList<Aparicion>>()
    for ((my, entries) in entriesPorMes) {
        val label = monthLabel(my.mes, my.anio)
        for ((cat, valor) in aggregate(entries, Tipo.GASTOS)) {
            apariciones.getOrPut(cat) { mutableListOf() }.add(Aparicion(label, valor))
        }
    }

    val fijos = mutableListOf<CategoriaRecurrente>()
    val recurrentes = mutableListOf<CategoriaRecurrente>()
    val ocasionales = mutableListOf<CategoriaRecurrente>()

    for ((cat, aps) in apariciones) {
        when {
            aps.size == totalMeses -> fijos.add(CategoriaRecurrente(cat, aps, TipoRecurrencia.FIJO))
            aps.size >= totalMeses / 2.0 -> recurrentes.add(CategoriaRecurrente(cat, aps, TipoRecurrencia.RECURRENTE))
            else -> ocasionales.add(CategoriaRecurrente(cat, aps, TipoRecurrencia.OCASIONAL))
        }
    }

    return Recurrentes(
        fijos.sortedBy { it.cat },
        recurrentes.sortedBy { it.cat },
        ocasionales.sortedBy { it.cat },
        totalMeses
    )
}
