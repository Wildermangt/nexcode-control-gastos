package com.nexcode.gastos.domain.usecase.analytics

import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.CategorySummary
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.repository.CategoryRepository
import com.nexcode.gastos.domain.repository.TransactionRepository
import kotlinx.datetime.LocalDate

/**
 * Agrupa y suma los gastos por categoria.
 *
 * Recorre las colecciones con filter, groupBy, map, sumOf y sortedByDescending:
 * es el caso de uso que documenta el pilar de estructuras y ciclos.
 */
class GetCategorySummaryUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {

    suspend operator fun invoke(from: LocalDate, to: LocalDate): List<CategorySummary> {
        val transactions: List<Transaction> = transactionRepository.findByRange(from, to)
        val categoriesById: Map<Long, Category> = categoryRepository.getAll().associateBy { it.id }
        return summarize(transactions, categoriesById)
    }

    /** Version pura: sin base de datos, se puede probar con listas en memoria. */
    fun summarize(
        transactions: List<Transaction>,
        categoriesById: Map<Long, Category>
    ): List<CategorySummary> {
        val expenses = transactions.filter { it.type is TransactionType.Expense }
        if (expenses.isEmpty()) return emptyList()

        val grandTotal: Long = expenses.sumOf { it.amount.cents }
        if (grandTotal == 0L) return emptyList()

        return expenses
            .groupBy { it.categoryId }                       // Map<Long?, List<Transaction>>
            .map { (categoryId, items) ->
                val total = items.sumOf { it.amount.cents }
                CategorySummary(
                    category = categoryId?.let { categoriesById[it] },   // safe call
                    total = Money(total),
                    transactionCount = items.size,
                    percentage = total.toFloat() / grandTotal.toFloat()
                )
            }
            .sortedByDescending { it.total.cents }
    }
}
