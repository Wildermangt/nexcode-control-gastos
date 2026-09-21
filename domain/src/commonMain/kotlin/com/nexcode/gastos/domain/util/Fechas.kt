package com.nexcode.gastos.domain.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Utilidades de fecha para codigo comun.
 *
 * `commonMain` no puede usar `java.time`, asi que la aritmetica de fechas se
 * apoya en kotlinx-datetime. Estas funciones concentran las operaciones que el
 * dominio necesita para que ningun caso de uso repita el calculo.
 */

/**
 * Reloj del dominio.
 *
 * Obtener la hora actual es una dependencia del entorno, no una regla de
 * negocio: por eso se inyecta. En produccion la implementa la capa de
 * aplicacion; en las pruebas se sustituye por una fecha fija.
 */
fun interface RelojDominio {
    fun ahora(): LocalDateTime
}

/** Fecha de hoy segun el reloj inyectado. */
fun RelojDominio.hoy(): LocalDate = ahora().date

fun LocalDate.masDias(dias: Int): LocalDate = plus(DatePeriod(days = dias))

fun LocalDate.menosDias(dias: Int): LocalDate = minus(DatePeriod(days = dias))

fun LocalDate.masMeses(meses: Int): LocalDate = plus(DatePeriod(months = meses))

fun LocalDate.menosMeses(meses: Int): LocalDate = minus(DatePeriod(months = meses))

fun LocalDate.masAnios(anios: Int): LocalDate = plus(DatePeriod(years = anios))

/** Dias calendario entre esta fecha y [otra]. Negativo si [otra] ya paso. */
fun LocalDate.diasHasta(otra: LocalDate): Long =
    (otra.toEpochDays() - this.toEpochDays()).toLong()

/** Ultimo dia del mes indicado, contemplando anios bisiestos. */
fun ultimoDiaDelMes(anio: Int, mes: Int): Int =
    LocalDate(anio, mes, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth

/** Misma fecha con otro dia del mes, recortado si el mes es mas corto. */
fun LocalDate.conDia(dia: Int): LocalDate =
    LocalDate(year, monthNumber, dia.coerceAtMost(ultimoDiaDelMes(year, monthNumber)))

/** Primer dia del mes de esta fecha. */
fun LocalDate.primerDiaDelMes(): LocalDate = LocalDate(year, monthNumber, 1)

/** Identifica un mes concreto del calendario. Sustituye a java.time.YearMonth. */
data class MesDelAnio(val anio: Int, val mes: Int) {
    init {
        require(mes in 1..12) { "El mes debe estar entre 1 y 12" }
    }

    /** Etiqueta de tres letras para los ejes de los graficos: "sep". */
    fun nombreCorto(): String = NOMBRES_CORTOS[mes - 1]

    companion object {
        fun de(fecha: LocalDate): MesDelAnio = MesDelAnio(fecha.year, fecha.monthNumber)
        fun de(fechaHora: LocalDateTime): MesDelAnio = de(fechaHora.date)

        /**
         * Nombres en espanol resueltos en el dominio.
         *
         * `commonMain` no tiene acceso a las utilidades de formato de Java ni
         * a los recursos de Android, y la aplicacion es de una sola region:
         * una tabla de doce entradas es mas honesta que una dependencia.
         */
        private val NOMBRES_CORTOS = listOf(
            "ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"
        )
    }
}
