package com.example.chiclin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.chiclin.data.Entry
import com.example.chiclin.data.MONTHS_ES
import com.example.chiclin.data.Summary
import com.example.chiclin.data.Tipo
import com.example.chiclin.data.monthLabel
import com.example.chiclin.data.parseCsv
import com.example.chiclin.data.parseMonthYearFromFilename
import com.example.chiclin.data.pieData
import com.example.chiclin.data.queryDisplayName
import com.example.chiclin.ui.components.MonthDropdown
import com.example.chiclin.ui.components.PieChart
import com.example.chiclin.ui.components.SimpleBarChart
import java.util.Calendar

private data class PendingCsvImport(val content: String, val filename: String)

@Composable
fun MesActualScreen(viewModel: ChiclinViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val availableMonths by viewModel.availableMonths.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    val currentEntries by viewModel.currentEntries.collectAsState()
    val availableCategorias by viewModel.availableCategorias.collectAsState()

    var nombre by remember { mutableStateOf("") }
    var valorText by remember { mutableStateOf("") }
    var tipoSeleccionado by remember { mutableStateOf(Tipo.GASTOS) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf<PendingCsvImport?>(null) }
    var showEntriesList by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<Entry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val content = try {
            resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            null
        }

        if (content == null) {
            viewModel.setImportMessage("⚠️ No se pudo leer el archivo.")
            return@rememberLauncherForActivityResult
        }

        val displayName = queryDisplayName(resolver, uri) ?: (uri.lastPathSegment ?: "archivo.csv")
        val monthYear = parseMonthYearFromFilename(displayName)
        if (monthYear != null) {
            val result = parseCsv(content, monthYear.mes, monthYear.anio)
            viewModel.importEntries(result.entries, result.skippedRows)
        } else {
            pendingCsv = PendingCsvImport(content, displayName)
        }
    }

    // Surface every import result (success or failure) as a Snackbar "pop-up".
    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Separate box: bulk-importing a whole month's CSV is a different action
            // from adding a single entry below, so it gets its own card.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Importar datos", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Carga un CSV de un mes anterior (uno a la vez).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        csvPicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/octet-stream"))
                    }) { Text("Importar CSV") }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MonthDropdown(
                    label = "Mes",
                    months = availableMonths,
                    selected = selectedMonth,
                    onSelect = viewModel::selectMonth,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showEntriesList = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar entradas del mes")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Añadir", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row {
                        FilterChip(
                            selected = tipoSeleccionado == Tipo.GASTOS,
                            onClick = { tipoSeleccionado = Tipo.GASTOS },
                            label = { Text("Gasto") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = tipoSeleccionado == Tipo.INGRESOS,
                            onClick = { tipoSeleccionado = Tipo.INGRESOS },
                            label = { Text("Ingreso") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = tipoSeleccionado == Tipo.AHORROS,
                            onClick = { tipoSeleccionado = Tipo.AHORROS },
                            label = { Text("Ahorro") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = valorText,
                        onValueChange = { valorText = it },
                        label = { Text("Valor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        val err = viewModel.addEntry(nombre, valorText, tipoSeleccionado)
                        if (err == null) {
                            nombre = ""
                            valorText = ""
                            errorMessage = null
                        } else {
                            errorMessage = err
                        }
                    }) { Text("OK") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SummaryPanel(summary, monthLabel(selectedMonth.mes, selectedMonth.anio))

            Spacer(modifier = Modifier.height(16.dp))
            Text("Distribución de gastos", style = MaterialTheme.typography.titleMedium)
            PieChart(data = pieData(summary), modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))
            Text("Ingresos vs Gastos", style = MaterialTheme.typography.titleMedium)
            SimpleBarChart(
                labels = listOf("Ingresos", "Gastos"),
                values = listOf(summary.totalIngresos, summary.totalGastos),
                colors = listOf(Color(0xFF43A047), Color(0xFFE53935)),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    pendingCsv?.let { pending ->
        ManualMonthYearDialog(
            filename = pending.filename,
            onDismiss = { pendingCsv = null },
            onConfirm = { mes, anio ->
                val result = parseCsv(pending.content, mes, anio)
                viewModel.importEntries(result.entries, result.skippedRows)
                pendingCsv = null
            }
        )
    }

    if (showEntriesList) {
        EntriesListDialog(
            entries = currentEntries,
            monthLabelText = monthLabel(selectedMonth.mes, selectedMonth.anio),
            onEditEntry = { editingEntry = it },
            onDeleteEntry = { viewModel.deleteEntry(it) },
            onDismiss = { showEntriesList = false }
        )
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            categoriaSuggestions = availableCategorias,
            onDismiss = { editingEntry = null },
            onSave = { nuevoNombre, nuevoValorText, nuevoTipo, nuevaCategoria ->
                val err = viewModel.updateEntry(entry, nuevoNombre, nuevoValorText, nuevoTipo, nuevaCategoria)
                if (err == null) editingEntry = null
                err
            },
            onDelete = {
                viewModel.deleteEntry(entry)
                editingEntry = null
            }
        )
    }
}

private fun tipoLabel(tipo: String): String = when (tipo) {
    Tipo.GASTOS -> "Gasto"
    Tipo.INGRESOS -> "Ingreso"
    Tipo.AHORROS -> "Ahorro"
    else -> tipo
}

@Composable
private fun EntriesListDialog(
    entries: List<Entry>,
    monthLabelText: String,
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entradas de $monthLabelText") },
        text = {
            if (entries.isEmpty()) {
                Text("Sin entradas este mes.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.nombre, style = MaterialTheme.typography.bodyMedium)
                                val categoriaSuffix = if (entry.categoria != entry.nombre) " · ${entry.categoria}" else ""
                                Text(
                                    "${tipoLabel(entry.tipo)}$categoriaSuffix · ${"%.2f".format(entry.valor)} €",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onEditEntry(entry) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Editar \"${entry.nombre}\"")
                            }
                            IconButton(onClick = { onDeleteEntry(entry) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar \"${entry.nombre}\"")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun EditEntryDialog(
    entry: Entry,
    categoriaSuggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (nombre: String, valorText: String, tipo: String, categoria: String) -> String?,
    onDelete: () -> Unit
) {
    var nombre by remember(entry.id) { mutableStateOf(entry.nombre) }
    var valorText by remember(entry.id) { mutableStateOf("%.2f".format(entry.valor)) }
    var tipo by remember(entry.id) { mutableStateOf(entry.tipo) }
    var categoria by remember(entry.id) { mutableStateOf(entry.categoria) }
    var errorMessage by remember(entry.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar entrada") },
        text = {
            Column {
                Row {
                    FilterChip(
                        selected = tipo == Tipo.GASTOS,
                        onClick = { tipo = Tipo.GASTOS },
                        label = { Text("Gasto") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = tipo == Tipo.INGRESOS,
                        onClick = { tipo = Tipo.INGRESOS },
                        label = { Text("Ingreso") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = tipo == Tipo.AHORROS,
                        onClick = { tipo = Tipo.AHORROS },
                        label = { Text("Ahorro") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = valorText,
                    onValueChange = { valorText = it },
                    label = { Text("Valor") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                CategoriaField(
                    value = categoria,
                    onValueChange = { categoria = it },
                    suggestions = categoriaSuggestions,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Agrupa esta entrada con otras de la misma categoría (p. ej. \"Viajes\"). Déjalo vacío para que sea su propia categoría.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDelete) {
                    Text("Eliminar entrada", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = onSave(nombre, valorText, tipo, categoria)
                if (err != null) errorMessage = err
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Editable text field with a dropdown of existing categorías matching what's typed so far. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriaField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("Categoría") },
            singleLine = true,
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange(""); expanded = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Borrar categoría")
                    }
                }
            },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = { onValueChange(suggestion); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SummaryPanel(summary: Summary, monthLabelText: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(monthLabelText, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ingresos: %.2f €".format(summary.totalIngresos), style = MaterialTheme.typography.bodyMedium)
            Text("Gastos: %.2f €".format(summary.totalGastos), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Balance: %.2f €".format(summary.balance),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            )

            if (summary.ingresosAgg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ingresos", style = MaterialTheme.typography.titleSmall)
                summary.ingresosAgg.forEach { (nombre, valor) ->
                    Text("· $nombre: ${"%.2f".format(valor)} €", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (summary.gastosAgg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Gastos", style = MaterialTheme.typography.titleSmall)
                val ti = summary.totalIngresos
                summary.gastosAgg.entries.sortedByDescending { it.value }.forEach { (nombre, valor) ->
                    val pct = if (ti > 0) valor / ti * 100 else 0.0
                    Text(
                        "· $nombre: ${"%.2f".format(valor)} € (${"%.1f".format(pct)}%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (summary.ahorrosAgg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ahorros", style = MaterialTheme.typography.titleSmall)
                summary.ahorrosAgg.forEach { (nombre, valor) ->
                    Text("· $nombre: ${"%.2f".format(valor)} €", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualMonthYearDialog(
    filename: String,
    onDismiss: () -> Unit,
    onConfirm: (mes: Int, anio: Int) -> Unit
) {
    var mes by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var anioText by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿A qué mes pertenece \"$filename\"?") },
        text = {
            Column {
                Text(
                    "No se pudo determinar el mes desde el nombre del archivo.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = MONTHS_ES[mes] ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mes") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        (1..12).forEach { m ->
                            DropdownMenuItem(
                                text = { Text(MONTHS_ES[m] ?: "") },
                                onClick = { mes = m; expanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = anioText,
                    onValueChange = { anioText = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Año") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val anio = anioText.toIntOrNull()
                if (anio != null) onConfirm(mes, anio)
            }) { Text("Importar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
