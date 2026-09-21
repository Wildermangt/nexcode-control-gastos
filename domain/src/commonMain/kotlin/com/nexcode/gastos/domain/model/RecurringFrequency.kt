package com.nexcode.gastos.domain.model

import com.nexcode.gastos.domain.util.conDia
import com.nexcode.gastos.domain.util.masAnios
import com.nexcode.gastos.domain.util.masDias
import com.nexcode.gastos.domain.util.masMeses
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * Periodicidad de un pago fijo.
 *
 * Otra jerarquia sellada: cada subtipo sabe calcular su propia proxima fecha,
 * asi que agregar una periodicidad nueva no obliga a tocar ningun `when`
 * disperso por la app (polimorfismo).
 */
sealed class RecurringFrequency(val code: String) {

    /** Todos los meses el dia [dayOfMonth]. Se ajusta si el mes es mas corto. */
    data class Monthly(val dayOfMonth: Int) : RecurringFrequency(CODE_MONTHLY) {
        init {
            require(dayOfMonth in 1..31) { "El dia del mes debe estar entre 1 y 31" }
        }
    }

    /** Todas las semanas el mismo dia (1 = lunes ... 7 = domingo). */
    data class Weekly(val dayOfWeek: Int) : RecurringFrequency(CODE_WEEKLY) {
        init {
            require(dayOfWeek in 1..7) { "El dia de la semana debe estar entre 1 y 7" }
        }
    }

    /** Una vez al ano. */
    data class Yearly(val month: Int, val dayOfMonth: Int) : RecurringFrequency(CODE_YEARLY) {
        init {
            require(month in 1..12) { "El mes debe estar entre 1 y 12" }
            require(dayOfMonth in 1..31) { "El dia del mes debe estar entre 1 y 31" }
        }
    }

    /** Etiqueta legible para la interfaz. */
    fun label(): String = when (this) {
        is Monthly -> "Cada mes el dia " + dayOfMonth
        is Weekly -> "Cada " + dayName(dayOfWeek)
        is Yearly -> "Cada ano el " + dayOfMonth + "/" + month
    }

    /** Cuantas veces ocurre al ano: sirve para el equivalente mensual. */
    fun timesPerYear(): Int = when (this) {
        is Monthly -> 12
        is Weekly -> 52
        is Yearly -> 1
    }

    /** Primera ocurrencia estrictamente posterior a [after]. */
    fun nextDateAfter(after: LocalDate): LocalDate = when (this) {
        is Monthly -> nextMonthly(after, dayOfMonth)
        is Weekly -> nextWeekly(after, dayOfWeek)
        is Yearly -> nextYearly(after, month, dayOfMonth)
    }

    companion object {
        const val CODE_MONTHLY = "MONTHLY"
        const val CODE_WEEKLY = "WEEKLY"
        const val CODE_YEARLY = "YEARLY"

        /**
         * Reconstruye la frecuencia desde la base de datos.
         * Elvis: si el valor guardado viene nulo o corrupto se cae a mensual el dia 1.
         */
        fun fromCode(code: String, value1: Int?, value2: Int?): RecurringFrequency =
            when (code.uppercase()) {
                CODE_WEEKLY -> Weekly((value1 ?: 1).coerceIn(1, 7))
                CODE_YEARLY -> Yearly((value1 ?: 1).coerceIn(1, 12), (value2 ?: 1).coerceIn(1, 31))
                else -> Monthly((value1 ?: 1).coerceIn(1, 31))
            }

        private fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
            1 -> "lunes"
            2 -> "martes"
            3 -> "miercoles"
            4 -> "jueves"
            5 -> "viernes"
            6 -> "sabado"
            else -> "domingo"
        }

        private fun nextMonthly(after: LocalDate, dayOfMonth: Int): LocalDate {
            val esteMes = after.conDia(dayOfMonth)
            if (esteMes > after) return esteMes
            return after.masMeses(1).conDia(dayOfMonth)
        }

        private fun nextWeekly(after: LocalDate, dayOfWeek: Int): LocalDate {
            var candidata = after.masDias(1)
            // Ciclo acotado: como maximo siete intentos.
            repeat(7) {
                if (candidata.dayOfWeek.isoDayNumber == dayOfWeek) return candidata
                candidata = candidata.masDias(1)
            }
            return candidata
        }

        private fun nextYearly(after: LocalDate, month: Int, dayOfMonth: Int): LocalDate {
            val esteAnio = LocalDate(after.year, month, 1).conDia(dayOfMonth)
            if (esteAnio > after) return esteAnio
            return LocalDate(after.year, month, 1).masAnios(1).conDia(dayOfMonth)
        }
    }
}
