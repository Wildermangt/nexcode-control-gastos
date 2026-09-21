package com.nexcode.gastos.domain

import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.usecase.transaction.AddTransactionResult
import com.nexcode.gastos.domain.usecase.transaction.AddTransactionUseCase
import com.nexcode.gastos.domain.usecase.transaction.NewTransactionInput
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddTransactionUseCaseTest {

    private val account = Account(id = 1L, name = "Efectivo")
    private val category = Category(id = 7L, name = "Comida")

    private fun useCase(
        transactions: FakeTransactionRepository = FakeTransactionRepository()
    ) = AddTransactionUseCase(
        transactionRepository = transactions,
        accountRepository = FakeAccountRepository(listOf(account)),
        categoryRepository = FakeCategoryRepository(listOf(category)),
        reloj = RELOJ_FIJO
    )

    @Test
    fun registra_un_gasto_valido() = runTest {
        val repo = FakeTransactionRepository()
        val result = useCase(repo).invoke(
            NewTransactionInput(
                rawAmount = "25.000",
                rawTitle = "Almuerzo",
                accountId = 1L,
                categoryId = 7L,
                type = TransactionType.Expense
            )
        )

        assertTrue(result is AddTransactionResult.Success)
        assertEquals(1, repo.saved.size)
        assertEquals(Money(2_500_000L).cents, repo.saved[0].amount.cents)
    }

    @Test
    fun usa_la_fecha_del_reloj_inyectado() = runTest {
        val repo = FakeTransactionRepository()
        useCase(repo).invoke(NewTransactionInput(rawAmount = "1000", accountId = 1L))
        assertEquals(2026, repo.saved[0].dateTime.year)
        assertEquals(22, repo.saved[0].dateTime.dayOfMonth)
    }

    @Test
    fun un_monto_no_numerico_devuelve_InvalidAmount() = runTest {
        val result = useCase().invoke(
            NewTransactionInput(rawAmount = "cien mil", accountId = 1L)
        )
        assertTrue(result is AddTransactionResult.InvalidAmount)
    }

    @Test
    fun un_monto_negativo_devuelve_InvalidAmount() = runTest {
        val result = useCase().invoke(
            NewTransactionInput(rawAmount = "-5000", accountId = 1L)
        )
        assertTrue(result is AddTransactionResult.InvalidAmount)
    }

    @Test
    fun una_cuenta_inexistente_devuelve_NotFound() = runTest {
        val result = useCase().invoke(
            NewTransactionInput(rawAmount = "1000", accountId = 99L)
        )
        assertTrue(result is AddTransactionResult.NotFound)
    }

    @Test
    fun sin_titulo_toma_el_nombre_de_la_categoria() = runTest {
        val repo = FakeTransactionRepository()
        useCase(repo).invoke(
            NewTransactionInput(
                rawAmount = "1000",
                rawTitle = "   ",
                accountId = 1L,
                categoryId = 7L
            )
        )
        assertEquals("Comida", repo.saved[0].title)
    }

    @Test
    fun una_nota_en_blanco_se_guarda_como_null() = runTest {
        val repo = FakeTransactionRepository()
        useCase(repo).invoke(
            NewTransactionInput(rawAmount = "1000", rawNote = "    ", accountId = 1L)
        )
        assertNull(repo.saved[0].note)
        assertEquals("Sin nota", repo.saved[0].displayNote)
    }
}
