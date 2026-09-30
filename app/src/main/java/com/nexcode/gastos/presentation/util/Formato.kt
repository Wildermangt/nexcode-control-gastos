package com.nexcode.gastos.presentation.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import java.time.format.DateTimeFormatter

/**
 * Frontera de formato entre el dominio y Android.
 *
 * El dominio usa kotlinx-datetime porque debe compilar en cualquier
 * plataforma; Android trae `DateTimeFormatter` de java.time, que sabe
 * escribir los nombres de meses y dias en espanol. Aqui se traduce entre
 * ambos, y solo aqui.
 */

fun LocalDate.formatear(patron: DateTimeFormatter): String =
    toJavaLocalDate().format(patron)

fun LocalDateTime.formatear(patron: DateTimeFormatter): String =
    toJavaLocalDateTime().format(patron)

/** Fecha de hoy segun el dispositivo, ya en el tipo del dominio. */
fun hoy(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate()

/**
 * Formato de la bitacora en la nube: `2026-09-30 15:20`.
 *
 * Va en orden ano-mes-dia a proposito. Es el unico que Google Sheets
 * interpreta como fecha sin depender del idioma de la hoja, y ademas ordena
 * bien como texto si alguien lo pega en otra herramienta.
 */
private val PATRON_BITACORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun LocalDateTime.paraBitacora(): String = formatear(PATRON_BITACORA)
