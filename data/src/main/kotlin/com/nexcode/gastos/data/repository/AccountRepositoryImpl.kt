package com.nexcode.gastos.data.repository

import com.nexcode.gastos.data.local.dao.AccountDao
import com.nexcode.gastos.data.local.entity.MetadatosDeSincronizacion
import com.nexcode.gastos.data.mapper.toDomain
import com.nexcode.gastos.data.mapper.toEntity
import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepositoryImpl(
    private val dao: AccountDao
) : AccountRepository {

    override suspend fun add(account: Account): Long = dao.insert(account.toEntity())


    /**
     * Al actualizar se CONSERVAN los metadatos de la fila y solo se marca como
     * modificada. Construir metadatos nuevos le daria un uuid distinto en cada
     * edicion, y la nube veria un registro nuevo cada vez en lugar de un cambio
     * sobre el mismo.
     */
    override suspend fun update(account: Account) {
        val actual = dao.findById(account.id)
        dao.update(
            account.toEntity(
                sync = actual?.sync?.marcarModificado() ?: MetadatosDeSincronizacion()
            )
        )
    }

    override suspend fun findById(accountId: Long): Account? =
        dao.findById(accountId)?.toDomain()

    override suspend fun getAll(): List<Account> = dao.getAll().map { it.toDomain() }

    override fun observeAll(): Flow<List<Account>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun count(): Int = dao.count()
}
