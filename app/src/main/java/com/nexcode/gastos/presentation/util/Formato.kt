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
