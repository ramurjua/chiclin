package com.example.chiclin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object Tipo {
    const val GASTOS = "gastos"
    const val INGRESOS = "ingresos"
    const val AHORROS = "ahorros"
}

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val nombre: String,
    val valor: Double,
    val mes: Int,
    val anio: Int
)

data class MonthYear(val mes: Int, val anio: Int)
