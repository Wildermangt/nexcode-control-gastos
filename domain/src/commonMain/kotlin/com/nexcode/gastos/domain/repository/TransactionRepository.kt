package com.nexcode.gastos.domain.repository

import com.nexcode.gastos.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * CONTRATO de persistencia de transacciones.
 *
 * Vive en el dominio y no sabe nada de Room ni de SQLite: la capa de datos es
 * la que decide como cumplirlo. Gracias a esto el dominio se puede probar con
 * implementaciones falsas en la JVM.
 */
interface TransactionRepository {

    suspend fun add(transaction: Transaction): Long

    suspend fun update(transaction: Transaction)

    suspend fun delete(transactionId: Long)

    suspend fun findById(transactionId: Long): Transaction?

    /** Flujo reactivo: la UI se redibuja sola cuando cambia la base de datos. */
    fun observeAll(): Flow<List<Transaction>>

    fun observeRecent(limit: Int): Flow<List<Transaction>>

    suspend fun findByRange(from: LocalDate, to: LocalDate): List<Transaction>

    /**
     * Movimientos cuyo titulo o nota contienen [texto], sin distinguir
     * mayusculas. La busqueda se delega a la base de datos en vez de traer la
     * tabla entera y filtrarla en memoria.
     */
    suspend fun buscar(texto: String, limite: Int): List<Transaction>
}
