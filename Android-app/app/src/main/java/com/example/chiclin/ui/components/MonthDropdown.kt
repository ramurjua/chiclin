package com.example.chiclin.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chiclin.data.MonthYear
import com.example.chiclin.data.monthLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthDropdown(
    label: String,
    months: List<MonthYear>,
    selected: MonthYear?,
    onSelect: (MonthYear) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.let { monthLabel(it.mes, it.anio) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            months.forEach { my ->
                DropdownMenuItem(
                    text = { Text(monthLabel(my.mes, my.anio)) },
                    onClick = { onSelect(my); expanded = false }
                )
            }
        }
    }
}
