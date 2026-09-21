package com.nexcode.gastos.domain.repository

import com.nexcode.gastos.domain.model.Category
import kotlinx.coroutines.flow.Flow

/** Contrato de persistencia de categorias. */
interface CategoryRepository {

    suspend fun add(category: Category): Long

    suspend fun findById(categoryId: Long): Category?

    suspend fun getAll(): List<Category>

    fun observeAll(): Flow<List<Category>>

    suspend fun count(): Int
}
