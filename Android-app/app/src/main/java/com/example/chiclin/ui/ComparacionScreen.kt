package com.example.chiclin.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chiclin.data.CategoriaComparacion
import com.example.chiclin.data.Comparacion
import com.example.chiclin.data.EstadoCategoria
import com.example.chiclin.ui.components.GroupedBarChart
import com.example.chiclin.ui.components.MonthDropdown
import kotlin.math.abs

@Composable
fun ComparacionScreen(viewModel: ChiclinViewModel, modifier: Modifier = Modifier) {
    val availableMonths by viewModel.availableMonths.collectAsState()
    val compMesA by viewModel.compMesA.collectAsState()
    val compMesB by viewModel.compMesB.collectAsState()
    val comparacion by viewModel.comparacion.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MonthDropdown(
                label = "Mes A",
                months = availableMonths,
                selected = compMesA,
                onSelect = viewModel::selectCompMesA,
                modifier = Modifier.weight(1f)
            )
            MonthDropdown(
                label = "Mes B",
                months = availableMonths,
                selected = compMesB,
                onSelect = viewModel::selectCompMesB,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (availableMonths.size < 2) {
            Text(
                "Necesitas al menos 2 meses con datos para comparar.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            val comp = comparacion
            if (comp == null) {
                Text("Selecciona dos meses distintos.", style = MaterialTheme.typography.bodyMedium)
            } else {
                ComparacionResumen(comp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Gastos por categoría", style = MaterialTheme.typography.titleMedium)
                GroupedBarChart(
                    categories = comp.categorias,
                    valuesA = comp.valores1,
                    valuesB = comp.valores2,
                    labelA = comp.mes1,
                    labelB = comp.mes2,
                    colorA = Color(0xFF5B9BD5),
                    colorB = Color(0xFFED7D31),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ComparacionResumen(comp: Comparacion) {
    Column {
        Text("${comp.mes1} vs ${comp.mes2}", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonthTotalsColumn(comp.mes1, comp.totalIngresos1, comp.totalGastos1)
            MonthTotalsColumn(comp.mes2, comp.totalIngresos2, comp.totalGastos2)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Por categoría", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        comp.detalle.sortedByDescending { abs(it.diff) }.forEach { d ->
            CategoriaComparacionRow(d)
        }
    }
}

@Composable
private fun MonthTotalsColumn(label: String, ingresos: Double, gastos: Double) {
    val balance = ingresos - gastos
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text("Ingresos: ${"%.2f".format(ingresos)} €", style = MaterialTheme.typography.bodySmall)
        Text("Gastos: ${"%.2f".format(gastos)} €", style = MaterialTheme.typography.bodySmall)
        Text(
            "Balance: ${"%.2f".format(balance)} €",
            style = MaterialTheme.typography.bodySmall,
            color = if (balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
    }
}

@Composable
private fun CategoriaComparacionRow(d: CategoriaComparacion) {
    val v1 = "%.2f".format(d.val1)
    val v2 = "%.2f".format(d.val2)

    val (text, color) = when (d.estado) {
        EstadoCategoria.NUEVO ->
            "🆕 ${d.cat}: — → $v2 €" to MaterialTheme.colorScheme.onSurface
        EstadoCategoria.ELIMINADO ->
            "❌ ${d.cat}: $v1 € → —" to MaterialTheme.colorScheme.onSurface
        else -> {
            val arrow = when (d.estado) {
                EstadoCategoria.SUBE -> "▲"
                EstadoCategoria.BAJA -> "▼"
                else -> "="
            }
            val pctStr = d.pctCambio?.let { " (${"%+.1f".format(it)}%)" } ?: ""
            val color = when (d.estado) {
                EstadoCategoria.SUBE -> Color(0xFFC62828)
                EstadoCategoria.BAJA -> Color(0xFF2E7D32)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            "$arrow ${d.cat}: $v1 → $v2 €$pctStr" to color
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(vertical = 2.dp))
}
