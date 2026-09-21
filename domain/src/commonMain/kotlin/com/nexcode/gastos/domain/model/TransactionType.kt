package com.nexcode.gastos.domain.model

/**
 * Tipo de movimiento.
 *
 * Jerarquia SELLADA: el compilador conoce todos los subtipos, por lo que un
 * `when` sobre ella no necesita rama `else` y ningun caso puede quedar sin
 * cubrir por accidente.
 *
 * Evidencia de POO: herencia, encapsulamiento (propiedades de solo lectura)
 * y polimorfismo (cada subtipo aporta su propio signo contable).
 */
sealed class TransactionType(
    val code: String,
    val sign: Int
) {
    /** Ingreso: suma al saldo. */
    data object Income : TransactionType(code = "INCOME", sign = +1)

    /** Gasto: resta del saldo. */
    data object Expense : TransactionType(code = "EXPENSE", sign = -1)

    /** Transferencia entre cuentas propias: no altera el patrimonio total. */
    data class Transfer(val targetAccountId: Long) : TransactionType(code = "TRANSFER", sign = 0)

    /** Aplica el signo contable al monto. */
    fun applySign(amount: Money): Money = Money(amount.cents * sign)

    /** Titulo por defecto cuando el usuario no escribe uno. */
    fun defaultTitle(): String = when (this) {
        is Income -> "Ingreso"
        is Expense -> "Gasto"
        is Transfer -> "Transferencia"
    } // sin `else`: la sealed class garantiza exhaustividad

    /** Cuenta destino, solo presente en transferencias. */
    val targetAccountIdOrNull: Long?
        get() = (this as? Transfer)?.targetAccountId

    companion object {
        const val CODE_INCOME = "INCOME"
        const val CODE_EXPENSE = "EXPENSE"
        const val CODE_TRANSFER = "TRANSFER"

        fun fromCode(code: String, targetAccountId: Long? = null): TransactionType =
            when (code.uppercase()) {
                CODE_INCOME -> Income
                CODE_EXPENSE -> Expense
                // Elvis: una transferencia sin destino se degrada a gasto.
                CODE_TRANSFER -> targetAccountId?.let { Transfer(it) } ?: Expense
                else -> Expense
            }
    }
}
