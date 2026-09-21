package com.nexcode.gastos.domain.usecase.transaction

import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.repository.TransactionRepository
import kotlinx.datetime.LocalDate

/**
 * Busca movimientos por texto y los devuelve agrupados por dia, con la misma
 * forma que [GetTransactionsUseCase].
 *
 * Que las dos entreguen la estructura identica no es casualidad: la pantalla
 * pinta la misma lista este filtrando o no, y asi no necesita dos caminos de
 * dibujo distintos.
 */
class BuscarTransaccionesUseCase(
    private val transactionRepository: TransactionRepository
) {

    suspend operator fun invoke(
        texto: String,
        limite: Int = LIMITE
    ): Map<LocalDate, List<Transaction>> {
        val consulta = texto.trim()
        if (consulta.length < MINIMO) return emptyMap()

        return transactionRepository.buscar(consulta, limite)
            .groupBy { it.day }
            .toList()
            .sortedByDescending { (dia, _) -> dia }
            .associate { (dia, movimientos) ->
                dia to movimientos.sortedByDescending { it.dateTime }
            }
    }

    private companion object {
        /** Con una letra la busqueda devolveria casi todo y no ayudaria. */
        const val MINIMO = 2
        const val LIMITE = 100
    }
}
