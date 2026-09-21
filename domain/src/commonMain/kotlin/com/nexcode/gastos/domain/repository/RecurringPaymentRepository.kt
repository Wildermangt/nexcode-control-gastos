package com.nexcode.gastos.domain.repository

import com.nexcode.gastos.domain.model.RecurringPayment
import kotlinx.coroutines.flow.Flow

/** Contrato de persistencia de los pagos fijos recurrentes. */
interface RecurringPaymentRepository {

    suspend fun add(payment: RecurringPayment): Long

    suspend fun update(payment: RecurringPayment)

    suspend fun delete(paymentId: Long)

    suspend fun findById(paymentId: Long): RecurringPayment?

    fun observeAll(): Flow<List<RecurringPayment>>

    suspend fun count(): Int
}
