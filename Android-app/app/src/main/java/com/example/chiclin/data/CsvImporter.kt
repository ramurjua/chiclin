package com.example.chiclin.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

data class CsvImportResult(
    val mes: Int,
    val anio: Int,
    val entries: List<Entry>,
    val skippedRows: Int
)

private val FILENAME_MONTH_YEAR = Regex("""^(\d{1,2})_(\d{4})\.csv$""", RegexOption.IGNORE_CASE)

/** Matches the desktop app's "MM_YYYY.csv" / "M_YYYY.csv" naming convention. */
fun parseMonthYearFromFilename(filename: String): MonthYear? {
    val m = FILENAME_MONTH_YEAR.find(filename.trim()) ?: return null
    val mes = m.groupValues[1].toIntOrNull() ?: return null
    val anio = m.groupValues[2].toIntOrNull() ?: return null
    if (mes !in 1..12) return null
    return MonthYear(mes, anio)
}

fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
    }
    return null
}

/** Splits a single CSV line into fields, honoring double-quoted fields (RFC 4180, no embedded newlines). */
private fun splitCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            inQuotes -> {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"'); i++
                    } else inQuotes = false
                } else current.append(c)
            }
            c == '"' -> inQuotes = true
            c == ',' -> { fields.add(current.toString()); current.clear() }
            else -> current.append(c)
        }
        i++
    }
    fields.add(current.toString())
    return fields
}

/**
 * Strips the "_N" suffix that the desktop app's FinanceManager.add_entry appends to allow
 * duplicate names within a month (e.g. "Salir_0", "Salir_1"). Mirrors logic.py's
 * `key.rsplit("_", 1)[0]` aggregation rule exactly, including its ambiguity with names that
 * genuinely end in a number.
 */
private fun stripDuplicateSuffix(nombre: String): String {
    val idx = nombre.lastIndexOf('_')
    if (idx <= 0 || idx == nombre.length - 1) return nombre
    val suffix = nombre.substring(idx + 1)
    return if (suffix.all { it.isDigit() }) nombre.substring(0, idx) else nombre
}

/**
 * Parses CSV content in the "tipo,nombre,valor" format written by the desktop app's
 * FinanceManager.save(). [mes]/[anio] must be supplied by the caller — derived from the
 * filename via [parseMonthYearFromFilename], or picked manually if that fails.
 */
fun parseCsv(content: String, mes: Int, anio: Int): CsvImportResult {
    val lines = content.split("\r\n", "\n", "\r").filter { it.isNotBlank() }
    val entries = mutableListOf<Entry>()
    var skipped = 0
    val tiposValidos = setOf(Tipo.GASTOS, Tipo.INGRESOS, Tipo.AHORROS)

    for ((index, line) in lines.withIndex()) {
        if (index == 0 && line.trim().equals("tipo,nombre,valor", ignoreCase = true)) continue
        val fields = splitCsvLine(line)
        if (fields.size < 3) { skipped++; continue }

        val tipo = fields[0].trim()
        val nombreRaw = fields[1].trim()
        val valor = fields[2].trim().toDoubleOrNull()

        if (tipo !in tiposValidos || nombreRaw.isEmpty() || valor == null) {
            skipped++
            continue
        }

        entries.add(
            Entry(
                tipo = tipo,
                nombre = stripDuplicateSuffix(nombreRaw),
                valor = valor,
                mes = mes,
                anio = anio
            )
        )
    }

    return CsvImportResult(mes, anio, entries, skipped)
}
