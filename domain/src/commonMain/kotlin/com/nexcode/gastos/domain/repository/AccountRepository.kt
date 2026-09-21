package com.nexcode.gastos.domain.repository

import com.nexcode.gastos.domain.model.Account
import kotlinx.coroutines.flow.Flow

/** Contrato de persistencia de cuentas. */
interface AccountRepository {

    suspend fun add(account: Account): Long

    /** Guarda los cambios de una cuenta que ya existe, identificada por su id. */
    suspend fun update(account: Account)

    /** Devuelve null si la cuenta no existe: null safety desde la capa de datos. */
    suspend fun findById(accountId: Long): Account?

    suspend fun getAll(): List<Account>

    fun observeAll(): Flow<List<Account>>

    suspend fun count(): Int
}
