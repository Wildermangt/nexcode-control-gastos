package com.nexcode.gastos.data.repository

import com.nexcode.gastos.data.local.dao.TransactionDao
import com.nexcode.gastos.data.local.entity.MetadatosDeSincronizacion
import com.nexcode.gastos.data.mapper.toDomain
import com.nexcode.gastos.data.mapper.toEntity
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * IMPLEMENTACION concreta del contrato del dominio usando Room.
 *
 * Toda la traduccion entidad <-> modelo ocurre aqui: hacia arriba solo salen
 * objetos de dominio.
 */
class TransactionRepositoryImpl(
    private val dao: TransactionDao
) : TransactionRepository {

    override suspend fun add(transaction: Transaction): Long =
        dao.insert(transaction.toEntity())


    /**
     * Al actualizar se CONSERVAN los metadatos de la fila y solo se marca como
     * modificada. Construir metadatos nuevos le daria un uuid distinto en cada
     * edicion, y la nube veria un registro nuevo cada vez en lugar de un cambio
     * sobre el mismo.
     */
    override suspend fun update(transaction: Transaction) {
        val actual = dao.findById(transaction.id)
        dao.update(
            transaction.toEntity(
                sync = actual?.sync?.marcarModificado() ?: MetadatosDeSincronizacion()
            )
        )
    }

    override suspend fun delete(transactionId: Long) =
        dao.deleteById(transactionId, System.currentTimeMillis())

    override suspend fun findById(transactionId: Long): Transaction? =
        dao.findById(transactionId)?.toDomain()      // safe call: null si no existe

    override fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun findByRange(from: LocalDate, to: LocalDate): List<Transaction> {
        // Frontera entre el dominio (kotlinx-datetime) y la plataforma (java.time).
        val zone = ZoneId.systemDefault()
        val fromMillis = from.toJavaLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = to.toJavaLocalDate().atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        return dao.findByRange(fromMillis, toMillis).map { it.toDomain() }
    }

    override suspend fun buscar(texto: String, limite: Int): List<Transaction> {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return emptyList()
        // Los comodines se ponen aqui y no en la consulta: si viajaran dentro
        // del @Query, el texto del usuario acabaria concatenado en el SQL.
        return dao.buscar("%" + limpio + "%", limite).map { it.toDomain() }
    }
}
