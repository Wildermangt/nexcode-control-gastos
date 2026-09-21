package com.nexcode.gastos.domain.usecase.catalog

import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.repository.AccountRepository
import com.nexcode.gastos.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Categorias ordenadas, listas para pintar los chips del formulario. */
class ObserveCategoriesUseCase(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(): Flow<List<Category>> =
        categoryRepository.observeAll().map { categories ->
            categories.sortedBy { it.name }
        }
}

/** Cuentas activas (las archivadas no se ofrecen al registrar). */
class ObserveAccountsUseCase(
    private val accountRepository: AccountRepository
) {
    operator fun invoke(): Flow<List<Account>> =
        accountRepository.observeAll().map { accounts ->
            accounts.filter { !it.isArchived }
        }
}
