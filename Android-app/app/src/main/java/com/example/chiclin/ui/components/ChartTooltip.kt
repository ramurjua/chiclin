package com.example.chiclin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared "tap a chart to see the exact value" tooltip, used by every chart in the app. */
@Composable
fun ChartTooltipCard(
    title: String?,
    rows: List<Pair<Color, String>>,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            rows.forEach { (color, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.width(10.dp).height(10.dp)) {
                        drawCircle(color = color)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
