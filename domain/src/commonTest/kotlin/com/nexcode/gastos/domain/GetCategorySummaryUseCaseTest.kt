package com.nexcode.gastos.domain

import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.usecase.analytics.GetCategorySummaryUseCase
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas de la funcion pura que alimenta el grafico de dona.
 * No toca la base de datos: recibe listas en memoria.
 */
class GetCategorySummaryUseCaseTest {

    private val comida = Category(id = 1L, name = "Comida")
    private val transporte = Category(id = 2L, name = "Transporte")
    private val categoriesById = mapOf(1L to comida, 2L to transporte)

    private val useCase = GetCategorySummaryUseCase(
        transactionRepository = FakeTransactionRepository(),
        categoryRepository = FakeCategoryRepository(emptyList())
    )

    private fun expense(categoryId: Long?, pesos: Long) = Transaction(
        accountId = 1L,
        categoryId = categoryId,
        type = TransactionType.Expense,
        amount = Money(pesos * 100L),
        dateTime = LocalDateTime(2026, 8, 22, 10, 0),
        title = "Gasto"
    )

    @Test
    fun agrupa_y_suma_los_gastos_por_categoria() {
        val result = useCase.summarize(
            listOf(
                expense(1L, 30_000),
                expense(1L, 10_000),
                expense(2L, 10_000)
            ),
            categoriesById
        )

        assertEquals(2, result.size)
        assertEquals("Comida", result[0].displayName)
        assertEquals(40_000L * 100L, result[0].total.cents)
        assertEquals(2, result[0].transactionCount)
    }

    @Test
    fun ordena_de_mayor_a_menor_gasto() {
        val result = useCase.summarize(
            listOf(expense(2L, 90_000), expense(1L, 10_000)),
            categoriesById
        )
        assertEquals("Transporte", result.first().displayName)
    }

    @Test
    fun los_porcentajes_suman_uno() {
        val result = useCase.summarize(
            listOf(expense(1L, 25_000), expense(2L, 75_000)),
            categoriesById
        )
        val total = result.sumOf { it.percentage.toDouble() }
        assertTrue(abs(total - 1.0) < 0.0001)
    }

    @Test
    fun los_movimientos_sin_categoria_quedan_agrupados_aparte() {
        val result = useCase.summarize(listOf(expense(null, 5_000)), categoriesById)

        assertEquals(1, result.size)
        assertNull(result.first().category)
        assertEquals("Sin categoria", result.first().displayName)
    }

    @Test
    fun los_ingresos_no_entran_en_el_reparto_de_gastos() {
        val income = Transaction(
            accountId = 1L,
            categoryId = 1L,
            type = TransactionType.Income,
            amount = Money(500_000L),
            dateTime = LocalDateTime(2026, 8, 22, 10, 0),
            title = "Salario"
        )
        val result = useCase.summarize(listOf(income), categoriesById)
        assertTrue(result.isEmpty())
    }
}
