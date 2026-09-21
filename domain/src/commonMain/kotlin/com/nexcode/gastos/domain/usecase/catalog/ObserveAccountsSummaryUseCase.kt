package com.nexcode.gastos.domain.usecase.catalog

import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.repository.AccountRepository
import com.nexcode.gastos.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Una cuenta con lo que realmente tiene ahora.
 *
 * @param cupo lo que el usuario fijo como punto de partida.
 * @param movimientos suma con signo de lo que entro y salio de esa cuenta.
 * @param disponible cupo mas movimientos: el dinero que queda.
 */
data class AccountSummary(
    val account: Account,
    val cupo: Money,
    val movimientos: Money,
    val disponible: Money
)

/**
 * Observa las cuentas junto con su saldo real.
 *
 * Distingue dos cifras que es facil confundir. El **cupo** es lo que el usuario
 * declara que tiene o que se asigna; los **movimientos** son lo que la
 * aplicacion ha registrado despues. Mostrar solo el cupo enganaria —diria que
 * hay dinero que ya se gasto— y mostrar solo el saldo escondiria de donde sale.
 *
 * El calculo esta aqui, en el dominio, y no en la pantalla: es una regla del
 * negocio y se prueba sin emulador.
 */
class ObserveAccountsSummaryUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {

    operator fun invoke(): Flow<List<AccountSummary>> =
        combine(
            accountRepository.observeAll(),
            transactionRepository.observeAll()
        ) { cuentas, movimientos ->
            // Se agrupan una sola vez los movimientos por cuenta para no
            // recorrer la lista completa dentro del ciclo de cuentas.
            val porCuenta = movimientos
                .groupBy { it.accountId }
                .mapValues { (_, lista) ->
                    Money(lista.sumOf { it.signedAmount.cents })
                }

            cuentas.map { cuenta ->
                val suma = porCuenta[cuenta.id] ?: Money.ZERO
                AccountSummary(
                    account = cuenta,
                    cupo = cuenta.initialBalance,
                    movimientos = suma,
                    disponible = cuenta.initialBalance + suma
                )
            }
        }
}
