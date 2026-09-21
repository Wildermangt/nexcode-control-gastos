package com.nexcode.gastos.domain.usecase.analytics

import com.nexcode.gastos.domain.model.Balance
import com.nexcode.gastos.domain.model.CategorySummary
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.repository.CategoryRepository
import com.nexcode.gastos.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.nexcode.gastos.domain.util.MesDelAnio
import com.nexcode.gastos.domain.util.RelojDominio
import com.nexcode.gastos.domain.util.hoy

/** Foto de un mes: saldo y reparto del gasto por categoria. */
data class MonthlyReport(
    val month: MesDelAnio,
    val balance: Balance = Balance(),
    val categories: List<CategorySummary> = emptyList()
) {
    val hasExpenses: Boolean get() = categories.isNotEmpty()

    /** Categoria donde mas se gasto. Null si el mes esta vacio. */
    val topCategory: CategorySummary? get() = categories.firstOrNull()
}

/**
 * Compone dos casos de uso existentes ([CalculateBalanceUseCase] y
 * [GetCategorySummaryUseCase]) sobre el mismo conjunto de movimientos, para
 * que el saldo del encabezado y el grafico de dona nunca se contradigan.
 *
 * Filtra por mes con una funcion de orden superior en vez de una consulta SQL
 * distinta: asi el mismo flujo reactivo alimenta las dos vistas.
 */
class ObserveMonthlyReportUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val reloj: RelojDominio
) {
    private val balanceCalculator = CalculateBalanceUseCase(transactionRepository)
    private val summarizer = GetCategorySummaryUseCase(transactionRepository, categoryRepository)

    operator fun invoke(month: MesDelAnio? = null): Flow<MonthlyReport> {
        val mes = month ?: MesDelAnio.de(reloj.hoy())
        return combine(
            transactionRepository.observeAll(),
            categoryRepository.observeAll()
        ) { transactions, categories ->
            val ofMonth: List<Transaction> = transactions.filter {
                MesDelAnio.de(it.dateTime) == mes
            }

            MonthlyReport(
                month = mes,
                balance = balanceCalculator.calculate(ofMonth),
                categories = summarizer.summarize(ofMonth, categories.associateBy { it.id })
            )
        }
    }
}
