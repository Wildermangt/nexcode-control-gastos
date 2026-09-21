package com.nexcode.gastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nexcode.gastos.data.local.entity.RecurringPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringPaymentDao {

    @Insert
    suspend fun insert(entity: RecurringPaymentEntity): Long

    @Update
    suspend fun update(entity: RecurringPaymentEntity)

    /** Borrado LOGICO, por el mismo motivo que en los movimientos. */
    @Query(
        "UPDATE recurring_payments SET borrado = 1, actualizado_en = :ahora, sincronizado = 0 " +
            "WHERE id = :paymentId"
    )
    suspend fun deleteById(paymentId: Long, ahora: Long)

    @Query("SELECT * FROM recurring_payments WHERE id = :paymentId AND borrado = 0")
    suspend fun findById(paymentId: Long): RecurringPaymentEntity?

    @Query("SELECT * FROM recurring_payments WHERE borrado = 0 ORDER BY next_due_date ASC")
    fun observeAll(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT COUNT(*) FROM recurring_payments WHERE borrado = 0")
    suspend fun count(): Int

    /** Conteo fisico, borradas incluidas. Ver AccountDao.contarTodas. */
    @Query("SELECT COUNT(*) FROM recurring_payments")
    suspend fun contarTodas(): Int

    // ---------------- Sincronizacion ----------------
    //
    // Estas consultas NO filtran por `borrado`: el sincronizador tiene que ver
    // tambien las filas marcadas como borradas, porque justamente ese borrado
    // es lo que hay que propagar a la nube.

    /** Filas con cambios que la nube todavia no conoce. */
    @Query("SELECT * FROM recurring_payments WHERE sincronizado = 0")
    suspend fun pendientes(): List<RecurringPaymentEntity>

    /** Busca por identidad global, que es la que comparten los dispositivos. */
    @Query("SELECT * FROM recurring_payments WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): RecurringPaymentEntity?

    @Query("UPDATE recurring_payments SET sincronizado = 1 WHERE uuid IN (:uuids)")
    suspend fun marcarSincronizados(uuids: List<String>)

    /** Todas las filas, borradas incluidas: sirve para resolver uuid a id local. */
    @Query("SELECT * FROM recurring_payments")
    suspend fun todasParaSincronizar(): List<RecurringPaymentEntity>

    /** Vacia la tabla de verdad, sin borrado logico. Ver AccountDao.borrarTodo. */
    @Query("DELETE FROM recurring_payments")
    suspend fun borrarTodo()
}
