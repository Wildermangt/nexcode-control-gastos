package com.nexcode.gastos.domain.usecase.analytics

import com.nexcode.gastos.domain.model.Balance
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Calcula ingresos, gastos y saldo total.
 *
 * Evidencia del pilar "estructuras, arreglos y ciclos": recorre la lista con
 * funciones de orden superior (filter / sumOf) en lugar de ciclos manuales.
 */
class CalculateBalanceUseCase(
    private val transactionRepository: TransactionRepository
) {

    operator fun invoke(): Flow<Balance> =
        transactionRepository.observeAll().map { transactions -> calculate(transactions) }

    /** Version pura y sincrona: facil de probar en la JVM. */
    fun calculate(transactions: List<Transaction>): Balance {
        val income = transactions
            .filter { it.type is TransactionType.Income }
            .sumOf { it.amount.cents }

        val expense = transactions
            .filter { it.type is TransactionType.Expense }
            .sumOf { it.amount.cents }

        return Balance(income = Money(income), expense = Money(expense))
    }
}
