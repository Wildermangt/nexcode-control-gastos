package com.nexcode.gastos.domain.usecase.transaction

import com.nexcode.gastos.domain.repository.TransactionRepository

/** Elimina una transaccion. Devuelve false si el id no existe. */
class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transactionId: Long): Boolean {
        return try {
            // Safe call: si no existe, findById devuelve null y no se borra nada.
            val existing = transactionRepository.findById(transactionId) ?: return false
            transactionRepository.delete(existing.id)
            true
        } catch (e: Exception) {
            false
        }
    }
}
