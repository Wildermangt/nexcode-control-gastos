package com.nexcode.gastos.domain.model

import com.nexcode.gastos.domain.util.diasHasta
import kotlinx.datetime.LocalDate

/**
 * Pago fijo recurrente del "bolsillo de recordatorios":
 * arriendo, internet, suscripciones, cuota de la universidad.
 */
data class RecurringPayment(
    val id: Long = 0L,
    val name: String,
    val amount: Money,
    val frequency: RecurringFrequency,
    val nextDueDate: LocalDate,
    val accountId: Long,
    val categoryId: Long? = null,
    val note: String? = null,
    val lastPaidDate: LocalDate? = null,
    val isActive: Boolean = true
) {
    init {
        require(name.isNotBlank()) { "El nombre del pago no puede estar vacio" }
        require(amount.isPositive) { "El monto del pago debe ser mayor que cero" }
        require(accountId > 0L) { "El pago debe estar asociado a una cuenta" }
    }

    /** Estado del recordatorio respecto a hoy. */
    enum class Status { OVERDUE, TODAY, SOON, SCHEDULED, PAUSED }

    fun daysUntilDue(today: LocalDate): Long = today.diasHasta(nextDueDate)

    fun status(today: LocalDate): Status {
        if (!isActive) return Status.PAUSED
        val days = daysUntilDue(today)
        return when {
            days < 0L -> Status.OVERDUE
            days == 0L -> Status.TODAY
            days <= DAYS_SOON -> Status.SOON
            else -> Status.SCHEDULED
        }
    }

    /** Cuanto pesa este pago en un mes promedio. */
    fun monthlyEquivalent(): Money =
        Money(amount.cents * frequency.timesPerYear() / 12L)

    /** Elvis: texto seguro cuando nunca se ha pagado. */
    fun lastPaidLabel(): String = lastPaidDate?.toString() ?: "Nunca registrado"

    /** Avanza el recordatorio despues de registrar el pago. */
    fun markPaid(paidOn: LocalDate): RecurringPayment = copy(
        lastPaidDate = paidOn,
        nextDueDate = frequency.nextDateAfter(maxOf(paidOn, nextDueDate))
    )

    private companion object {
        const val DAYS_SOON = 3L
    }
}

/**
 * Foto completa del bolsillo: la lista mas los totales ya calculados.
 * Se arma en el dominio para que la UI no haga cuentas.
 */
data class RecurringPocket(
    val payments: List<RecurringPayment> = emptyList(),
    val monthlyCommitment: Money = Money.ZERO,
    val overdueCount: Int = 0,
    val dueTodayCount: Int = 0
) {
    val isEmpty: Boolean get() = payments.isEmpty()

    /** Safe call encadenado: el proximo pago puede no existir. */
    val nextPayment: RecurringPayment? get() = payments.firstOrNull { it.isActive }
}
