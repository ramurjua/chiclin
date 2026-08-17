package com.example.chiclin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chiclin.data.TipoRecurrencia
import com.example.chiclin.ui.components.HorizontalBarChart
import com.example.chiclin.ui.components.LegendDot
import com.example.chiclin.ui.components.LineChart
import com.example.chiclin.ui.components.LineSeries
import com.example.chiclin.ui.components.YearDropdown
import com.example.chiclin.ui.components.chartPalette

private val colorIngresos = Color(0xFF43A047)
private val colorGastos = Color(0xFFE53935)

@Composable
fun HistoricoScreen(viewModel: ChiclinViewModel, modifier: Modifier = Modifier) {
    val availableYears by viewModel.availableYears.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val evolucionGlobal by viewModel.evolucionGlobal.collectAsState()
    val (mesLabels, categoriaSeries) = viewModel.evolucionCategorias.collectAsState().value
    val recurrentes by viewModel.recurrentes.collectAsState()

    var visibleCategorias by remember(categoriaSeries.map { it.categoria }) {
        mutableStateOf(emptySet<String>())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        YearDropdown(
            label = "Año",
            years = availableYears,
            selected = selectedYear,
            onSelect = viewModel::selectYear,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1) Evolución global: ingresos vs gastos, un punto por mes.
        Text("Evolución de ingresos y gastos", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(colorIngresos, "Ingresos")
            LegendDot(colorGastos, "Gastos")
        }
        LineChart(
            xLabels = evolucionGlobal.map { it.label },
            series = listOf(
                LineSeries("Ingresos", evolucionGlobal.map { it.totalIngresos }, colorIngresos),
                LineSeries("Gastos", evolucionGlobal.map { it.totalGastos }, colorGastos)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 2) Evolución por categoría de gasto, con líneas activables/desactivables.
        Text("Evolución de gastos por categoría", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (mesLabels.size >= 2 && categoriaSeries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categoriaSeries.forEachIndexed { index, serie ->
                    val color = chartPalette[index % chartPalette.size]
                    val selected = serie.categoria in visibleCategorias
                    FilterChip(
                        selected = selected,
                        onClick = {
                            visibleCategorias = if (selected)
                                visibleCategorias - serie.categoria
                            else
                                visibleCategorias + serie.categoria
                        },
                        label = { Text(serie.categoria) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.25f)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        LineChart(
            xLabels = mesLabels,
            series = categoriaSeries.mapIndexed { index, serie ->
                LineSeries(
                    label = serie.categoria,
                    values = serie.valores,
                    color = chartPalette[index % chartPalette.size],
                    visible = serie.categoria in visibleCategorias
                )
            },
            modifier = Modifier.fillMaxWidth(),
            emptyLabel = if (mesLabels.size < 2)
                "Se necesitan al menos 2 meses con datos."
            else
                "Activa alguna categoría para verla en la gráfica."
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 3) Gastos recurrentes: media por categoría, clasificados en fijo/recurrente/ocasional.
        Text("Gastos recurrentes (${recurrentes.totalMeses} meses)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (recurrentes.totalMeses < 2) {
            Text("Se necesitan al menos 2 meses.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val filas = (recurrentes.fijos + recurrentes.recurrentes + recurrentes.ocasionales)
                .sortedByDescending { it.media }
            val colorFijo = Color(0xFFE74C3C)
            val colorRecurrente = Color(0xFFE67E22)
            val colorOcasional = Color(0xFF95A5A6)
            val colores = filas.map {
                when (it.tipo) {
                    TipoRecurrencia.FIJO -> colorFijo
                    TipoRecurrencia.RECURRENTE -> colorRecurrente
                    TipoRecurrencia.OCASIONAL -> colorOcasional
                }
            }

            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(colorFijo, "Fijo (${recurrentes.fijos.size})")
                LegendDot(colorRecurrente, "Recurrente (${recurrentes.recurrentes.size})")
                LegendDot(colorOcasional, "Ocasional (${recurrentes.ocasionales.size})")
            }

            HorizontalBarChart(
                labels = filas.map { it.cat },
                values = filas.map { it.media },
                colors = colores,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
