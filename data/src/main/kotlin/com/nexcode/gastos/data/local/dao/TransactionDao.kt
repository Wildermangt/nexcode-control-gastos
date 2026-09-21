package com.nexcode.gastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nexcode.gastos.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    /**
     * Borrado LOGICO.
     *
     * La fila no se elimina: se marca. Una fila que desaparece de verdad no se
     * puede replicar, porque el otro extremo no puede distinguir "esto se
     * borro" de "esto todavia no me ha llegado".
     */
    @Query(
        "UPDATE transactions SET borrado = 1, actualizado_en = :ahora, sincronizado = 0 " +
            "WHERE id = :transactionId"
    )
    suspend fun deleteById(transactionId: Long, ahora: Long)

    @Query("SELECT * FROM transactions WHERE id = :transactionId AND borrado = 0")
    suspend fun findById(transactionId: Long): TransactionEntity?

    /** Flow: Room reemite la lista sola cada vez que cambia la tabla. */
    @Query("SELECT * FROM transactions WHERE borrado = 0 ORDER BY date_time DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE borrado = 0 ORDER BY date_time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE borrado = 0 AND date_time BETWEEN :fromMillis AND :toMillis ORDER BY date_time DESC")
    suspend fun findByRange(fromMillis: Long, toMillis: Long): List<TransactionEntity>

    /**
     * Busqueda por titulo o nota. El patron llega ya con los comodines desde
     * el repositorio, y LIKE en SQLite no distingue mayusculas para ASCII.
     */
    @Query(
        "SELECT * FROM transactions WHERE borrado = 0 AND (title LIKE :patron OR note LIKE :patron) " +
            "ORDER BY date_time DESC LIMIT :limite"
    )
    suspend fun buscar(patron: String, limite: Int): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE borrado = 0")
    suspend fun count(): Int

    /** Conteo fisico, borradas incluidas. Ver AccountDao.contarTodas. */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun contarTodas(): Int

    // ---------------- Sincronizacion ----------------
    //
    // Estas consultas NO filtran por `borrado`: el sincronizador tiene que ver
    // tambien las filas marcadas como borradas, porque justamente ese borrado
    // es lo que hay que propagar a la nube.

    /** Filas con cambios que la nube todavia no conoce. */
    @Query("SELECT * FROM transactions WHERE sincronizado = 0")
    suspend fun pendientes(): List<TransactionEntity>

    /** Busca por identidad global, que es la que comparten los dispositivos. */
    @Query("SELECT * FROM transactions WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): TransactionEntity?

    @Query("UPDATE transactions SET sincronizado = 1 WHERE uuid IN (:uuids)")
    suspend fun marcarSincronizados(uuids: List<String>)

    /** Todas las filas, borradas incluidas: sirve para resolver uuid a id local. */
    @Query("SELECT * FROM transactions")
    suspend fun todasParaSincronizar(): List<TransactionEntity>

    /** Vacia la tabla de verdad, sin borrado logico. Ver AccountDao.borrarTodo. */
    @Query("DELETE FROM transactions")
    suspend fun borrarTodo()
}
