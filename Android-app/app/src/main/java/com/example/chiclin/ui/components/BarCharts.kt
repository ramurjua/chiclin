package com.example.chiclin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Vertical bars, one series — e.g. Ingresos vs Gastos. */
@Composable
fun SimpleBarChart(
    labels: List<String>,
    values: List<Double>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 160.dp,
    emptyLabel: String = "Sin datos"
) {
    if (values.isEmpty() || values.all { it == 0.0 }) {
        Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(16.dp))
        return
    }
    val maxVal = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
    var selectedIndex by remember(labels) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        selectedIndex?.let { idx ->
            ChartTooltipCard(
                title = null,
                rows = listOf(colors.getOrElse(idx) { Color.Gray } to "${labels[idx]}: ${"%.2f".format(values[idx])} €")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(barHeight),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            labels.indices.forEach { i ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selectedIndex = if (selectedIndex == i) null else i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text("%.2f €".format(values[i]), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    val ratio = (values[i] / maxVal).toFloat().coerceIn(0f, 1f)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height((barHeight.value * ratio).dp.coerceAtLeast(2.dp))
                    ) {
                        drawRect(color = colors.getOrElse(i) { Color.Gray })
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Vertical bars, two series per category — e.g. category totals for mes A vs mes B. */
@Composable
fun GroupedBarChart(
    categories: List<String>,
    valuesA: List<Double>,
    valuesB: List<Double>,
    labelA: String,
    labelB: String,
    colorA: Color,
    colorB: Color,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 200.dp,
    emptyLabel: String = "Sin categorías"
) {
    if (categories.isEmpty()) {
        Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(16.dp))
        return
    }
    val maxVal = (valuesA + valuesB).maxOrNull()?.coerceAtLeast(0.0001) ?: 0.0001
    var selectedIndex by remember(categories) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            LegendDot(colorA, labelA)
            Spacer(modifier = Modifier.width(16.dp))
            LegendDot(colorB, labelB)
        }

        selectedIndex?.let { idx ->
            ChartTooltipCard(
                title = categories[idx],
                rows = listOf(
                    colorA to "$labelA: ${"%.2f".format(valuesA[idx])} €",
                    colorB to "$labelB: ${"%.2f".format(valuesB[idx])} €"
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(barHeight + 36.dp)
        ) {
            categories.indices.forEach { i ->
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .fillMaxHeight()
                        .clickable { selectedIndex = if (selectedIndex == i) null else i },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val ratioA = (valuesA[i] / maxVal).toFloat().coerceIn(0f, 1f)
                        val ratioB = (valuesB[i] / maxVal).toFloat().coerceIn(0f, 1f)
                        Canvas(
                            modifier = Modifier
                                .width(24.dp)
                                .height((barHeight.value * ratioA).dp.coerceAtLeast(2.dp))
                        ) { drawRect(color = colorA) }
                        Spacer(modifier = Modifier.width(4.dp))
                        Canvas(
                            modifier = Modifier
                                .width(24.dp)
                                .height((barHeight.value * ratioB).dp.coerceAtLeast(2.dp))
                        ) { drawRect(color = colorB) }
                    }
                    Text(
                        categories[i],
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/** Horizontal bars, one series — e.g. recurring-expense averages, ranked. */
@Composable
fun HorizontalBarChart(
    labels: List<String>,
    values: List<Double>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    emptyLabel: String = "Sin datos"
) {
    if (labels.isEmpty()) {
        Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(16.dp))
        return
    }
    val maxVal = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
    var selectedIndex by remember(labels) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        selectedIndex?.let { idx ->
            ChartTooltipCard(
                title = null,
                rows = listOf(colors.getOrElse(idx) { Color.Gray } to "${labels[idx]}: ${"%.2f".format(values[idx])} €")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        labels.indices.forEach { i ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedIndex = if (selectedIndex == i) null else i }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    labels[i],
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(110.dp),
                    maxLines = 1
                )
                val ratio = (values[i] / maxVal).toFloat().coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    drawRect(color = colors.getOrElse(i) { Color.Gray }, size = size.copy(width = size.width * ratio))
                }
                Text("%.2f €".format(values[i]), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color = color) }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
