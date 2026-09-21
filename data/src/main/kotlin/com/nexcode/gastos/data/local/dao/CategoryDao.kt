package com.nexcode.gastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nexcode.gastos.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(entity: CategoryEntity): Long

    @Update
    suspend fun update(entity: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :categoryId AND borrado = 0")
    suspend fun findById(categoryId: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE borrado = 0 ORDER BY name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE borrado = 0 ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories WHERE borrado = 0")
    suspend fun count(): Int

    /** Conteo fisico, borradas incluidas. Ver AccountDao.contarTodas. */
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun contarTodas(): Int

    // ---------------- Sincronizacion ----------------
    //
    // Estas consultas NO filtran por `borrado`: el sincronizador tiene que ver
    // tambien las filas marcadas como borradas, porque justamente ese borrado
    // es lo que hay que propagar a la nube.

    /** Filas con cambios que la nube todavia no conoce. */
    @Query("SELECT * FROM categories WHERE sincronizado = 0")
    suspend fun pendientes(): List<CategoryEntity>

    /** Busca por identidad global, que es la que comparten los dispositivos. */
    @Query("SELECT * FROM categories WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): CategoryEntity?

    @Query("UPDATE categories SET sincronizado = 1 WHERE uuid IN (:uuids)")
    suspend fun marcarSincronizados(uuids: List<String>)

    /** Todas las filas, borradas incluidas: sirve para resolver uuid a id local. */
    @Query("SELECT * FROM categories")
    suspend fun todasParaSincronizar(): List<CategoryEntity>

    /** Vacia la tabla de verdad, sin borrado logico. Ver AccountDao.borrarTodo. */
    @Query("DELETE FROM categories")
    suspend fun borrarTodo()
}
