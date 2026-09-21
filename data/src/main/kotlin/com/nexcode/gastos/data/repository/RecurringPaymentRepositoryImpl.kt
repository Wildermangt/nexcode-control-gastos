package com.nexcode.gastos.data.repository

import com.nexcode.gastos.data.local.dao.RecurringPaymentDao
import com.nexcode.gastos.data.local.entity.MetadatosDeSincronizacion
import com.nexcode.gastos.data.mapper.toDomain
import com.nexcode.gastos.data.mapper.toEntity
import com.nexcode.gastos.domain.model.RecurringPayment
import com.nexcode.gastos.domain.repository.RecurringPaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecurringPaymentRepositoryImpl(
    private val dao: RecurringPaymentDao
) : RecurringPaymentRepository {

    override suspend fun add(payment: RecurringPayment): Long = dao.insert(payment.toEntity())

    /**
     * Al actualizar se CONSERVAN los metadatos de la fila y solo se marca como
     * modificada. Construir metadatos nuevos le daria un uuid distinto en cada
     * edicion, y la nube veria un registro nuevo cada vez en lugar de un cambio
     * sobre el mismo.
     */
    override suspend fun update(payment: RecurringPayment) {
        val actual = dao.findById(payment.id)
        dao.update(
            payment.toEntity(
                sync = actual?.sync?.marcarModificado() ?: MetadatosDeSincronizacion()
            )
        )
    }

    override suspend fun delete(paymentId: Long) =
        dao.deleteById(paymentId, System.currentTimeMillis())

    override suspend fun findById(paymentId: Long): RecurringPayment? =
        dao.findById(paymentId)?.toDomain()

    override fun observeAll(): Flow<List<RecurringPayment>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun count(): Int = dao.count()
}
