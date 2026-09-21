package com.nexcode.gastos.domain.model

/** Total gastado en una categoria durante un periodo. */
data class CategorySummary(
    val category: Category?,        // null = movimientos sin categoria
    val total: Money,
    val transactionCount: Int,
    val percentage: Float
) {
    /** Elvis: nombre seguro incluso cuando no hay categoria asociada. */
    val displayName: String get() = category?.name ?: "Sin categoria"

    val colorArgb: Long get() = category?.colorArgb ?: 0xFF9A9AA5L
}

/** Saldo consolidado de un periodo. */
data class Balance(
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO
) {
    val total: Money get() = income - expense

    /** Porcentaje del ingreso que NO se gasto. Null si no hubo ingresos. */
    val savingsRate: Float?
        get() = if (income.isZero) null
        else ((income.cents - expense.cents).toFloat() / income.cents.toFloat())
}
