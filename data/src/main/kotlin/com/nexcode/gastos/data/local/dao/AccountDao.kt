package com.nexcode.gastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nexcode.gastos.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert
    suspend fun insert(entity: AccountEntity): Long

    /** Actualiza la fila cuyo id coincide. No altera el esquema. */
    @Update
    suspend fun update(entity: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :accountId AND borrado = 0")
    suspend fun findById(accountId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE borrado = 0 ORDER BY name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE borrado = 0 ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts WHERE borrado = 0")
    suspend fun count(): Int

    /**
     * Conteo FISICO: cuenta tambien las filas marcadas como borradas.
     *
     * Es el que decide si la base es nueva. El conteo visible diria que una
     * base donde el usuario borro todo esta vacia, y la siembra volveria a
     * insertar los datos de demostracion sobre unos uuid que ya existen.
     */
    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun contarTodas(): Int

    // ---------------- Sincronizacion ----------------
    //
    // Estas consultas NO filtran por `borrado`: el sincronizador tiene que ver
    // tambien las filas marcadas como borradas, porque justamente ese borrado
    // es lo que hay que propagar a la nube.

    /** Filas con cambios que la nube todavia no conoce. */
    @Query("SELECT * FROM accounts WHERE sincronizado = 0")
    suspend fun pendientes(): List<AccountEntity>

    /** Busca por identidad global, que es la que comparten los dispositivos. */
    @Query("SELECT * FROM accounts WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): AccountEntity?

    @Query("UPDATE accounts SET sincronizado = 1 WHERE uuid IN (:uuids)")
    suspend fun marcarSincronizados(uuids: List<String>)

    /** Todas las filas, borradas incluidas: sirve para resolver uuid a id local. */
    @Query("SELECT * FROM accounts")
    suspend fun todasParaSincronizar(): List<AccountEntity>

    /**
     * Vacia la tabla de verdad, sin borrado logico.
     *
     * Se usa al entrar en una cuenta existente desde otro telefono: lo que hay
     * aqui es la siembra de demostracion de esta instalacion y estorba. Un
     * borrado logico no serviria —se propagaria a la nube y borraria los datos
     * buenos del usuario en todos sus dispositivos—.
     */
    @Query("DELETE FROM accounts")
    suspend fun borrarTodo()
}
