package com.nexcode.gastos.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Movimiento de dinero.
 *
 * [amount] es SIEMPRE positivo; el signo contable lo aporta [type].
 * Guardar el signo dentro del monto es una fuente clasica de errores de cuadre.
 */
data class Transaction(
    val id: Long = 0L,
    val accountId: Long,
    val categoryId: Long? = null,
    val type: TransactionType,
    val amount: Money,
    val dateTime: LocalDateTime,
    val title: String,
    val note: String? = null
) {
    init {
        require(amount.isPositive) { "El monto debe ser mayor que cero" }
        require(title.isNotBlank()) { "El titulo no puede estar vacio" }
        require(accountId > 0L) { "La transaccion debe pertenecer a una cuenta valida" }
    }

    /** Monto con signo contable: -50.000 para un gasto. */
    val signedAmount: Money get() = type.applySign(amount)

    /**
     * El mismo importe ya formateado.
     *
     * Existe por una razon de interoperabilidad, y conviene dejarla escrita:
     * [Money] es una `value class`, asi que el compilador mutila el nombre de
     * todo metodo que la devuelva y desde Java no se puede llamar. Devolver
     * texto cruza esa frontera sin exponer el tipo.
     */
    fun montoConSigno(): String = signedAmount.format(withSign = true)

    /** Elvis: valor por defecto seguro para la interfaz. */
    val displayNote: String get() = note ?: "Sin nota"

    val hasNote: Boolean get() = !note.isNullOrBlank()

    val day: LocalDate get() = dateTime.date

    fun belongsTo(date: LocalDate): Boolean = day == date

    fun withNote(newNote: String?): Transaction =
        copy(note = newNote?.trim()?.takeIf { it.isNotBlank() })
}
