package com.nexcode.gastos.domain

import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.usecase.catalog.UpdateAccountBalanceUseCase
import com.nexcode.gastos.domain.usecase.catalog.UpdateAccountResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pruebas del ajuste de cupo de una cuenta.
 *
 * Viven en `commonTest`, asi que se ejecutan en la JVM y en Node.js.
 */
class UpdateAccountBalanceUseCaseTest {

    private fun cuenta(saldo: Long = 100_000_00L) = Account(
        id = 1L,
        name = "Efectivo",
        initialBalance = Money(saldo)
    )

    @Test
    fun fija_el_cupo_con_el_texto_que_escribe_el_usuario() = runTest {
        val repo = FakeAccountRepository(listOf(cuenta()))
        val caso = UpdateAccountBalanceUseCase(repo)

        val resultado = caso(accountId = 1L, rawAmount = "250.000")

        assertTrue(resultado is UpdateAccountResult.Success)
        assertEquals(Money.parse("250.000").cents, repo.guardadas.first().initialBalance.cents)
    }

    @Test
    fun aumentar_suma_sobre_el_cupo_actual() = runTest {
        val repo = FakeAccountRepository(listOf(cuenta(saldo = 100_000_00L)))
        val caso = UpdateAccountBalanceUseCase(repo)

        caso.ajustar(accountId = 1L, delta = Money.parse("50.000"))

        assertEquals(Money.parse("150.000").cents, repo.guardadas.first().initialBalance.cents)
    }

    @Test
    fun disminuir_resta_del_cupo_actual() = runTest {
        val repo = FakeAccountRepository(listOf(cuenta(saldo = 100_000_00L)))
        val caso = UpdateAccountBalanceUseCase(repo)

        caso.ajustar(accountId = 1L, delta = -Money.parse("30.000"))

        assertEquals(Money.parse("70.000").cents, repo.guardadas.first().initialBalance.cents)
    }

    @Test
    fun una_resta_mayor_que_el_cupo_lo_deja_en_cero_y_no_en_negativo() = runTest {
        val repo = FakeAccountRepository(listOf(cuenta(saldo = 20_000_00L)))
        val caso = UpdateAccountBalanceUseCase(repo)

        caso.ajustar(accountId = 1L, delta = -Money.parse("90.000"))

        assertEquals(0L, repo.guardadas.first().initialBalance.cents)
    }

    @Test
    fun un_texto_no_numerico_devuelve_InvalidAmount() = runTest {
        val repo = FakeAccountRepository(listOf(cuenta()))
        val caso = UpdateAccountBalanceUseCase(repo)

        val resultado = caso(accountId = 1L, rawAmount = "mucho dinero")

        assertTrue(resultado is UpdateAccountResult.InvalidAmount)
    }

    @Test
    fun un_monto_vacio_devuelve_InvalidAmount_y_no_toca_la_cuenta() = runTest {
        val repo = FakeAccountRepository(listOf(cuenta(saldo = 100_000_00L)))
        val caso = UpdateAccountBalanceUseCase(repo)

        val resultado = caso(accountId = 1L, rawAmount = null)

        assertTrue(resultado is UpdateAccountResult.InvalidAmount)
        assertEquals(100_000_00L, repo.guardadas.first().initialBalance.cents)
    }

    @Test
    fun una_cuenta_inexistente_devuelve_NotFound() = runTest {
        val repo = FakeAccountRepository(listOf(cuenta()))
        val caso = UpdateAccountBalanceUseCase(repo)

        val resultado = caso(accountId = 99L, rawAmount = "10.000")

        assertTrue(resultado is UpdateAccountResult.NotFound)
    }

    @Test
    fun un_aumento_por_encima_del_limite_se_rechaza() = runTest {
        val repo = FakeAccountRepository(listOf(cuenta(saldo = Money.MAXIMO.cents)))
        val caso = UpdateAccountBalanceUseCase(repo)

        val resultado = caso.ajustar(accountId = 1L, delta = Money.parse("1.000"))

        assertTrue(resultado is UpdateAccountResult.InvalidAmount)
        assertEquals(Money.MAXIMO.cents, repo.guardadas.first().initialBalance.cents)
    }
}
