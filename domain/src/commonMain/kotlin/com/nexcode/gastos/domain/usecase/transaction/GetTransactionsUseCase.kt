package com.nexcode.gastos.domain.usecase.transaction

import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Entrega las transacciones AGRUPADAS POR FECHA, listas para pintarse como
 * una lista con encabezados de dia (estilo Ivy Wallet).
 *
 * Uso de colecciones y funciones de orden superior: groupBy, toSortedMap, map.
 */
class GetTransactionsUseCase(
    private val transactionRepository: TransactionRepository
) {

    operator fun invoke(limit: Int? = null): Flow<Map<LocalDate, List<Transaction>>> {
        val source = if (limit != null) {
            transactionRepository.observeRecent(limit)
        } else {
            transactionRepository.observeAll()
        }

        return source.map { transactions ->
            transactions
                .groupBy { it.day }                       // Map<LocalDate, List<Transaction>>
                .toList()
                .sortedByDescending { (dia, _) -> dia }   // dia mas reciente primero
                .associate { (dia, movimientos) ->
                    dia to movimientos.sortedByDescending { it.dateTime }
                }
        }
    }
}
