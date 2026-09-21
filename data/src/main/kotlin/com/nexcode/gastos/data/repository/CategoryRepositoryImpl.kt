package com.nexcode.gastos.data.repository

import com.nexcode.gastos.data.local.dao.CategoryDao
import com.nexcode.gastos.data.mapper.toDomain
import com.nexcode.gastos.data.mapper.toEntity
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl(
    private val dao: CategoryDao
) : CategoryRepository {

    override suspend fun add(category: Category): Long = dao.insert(category.toEntity())

    override suspend fun findById(categoryId: Long): Category? =
        dao.findById(categoryId)?.toDomain()

    override suspend fun getAll(): List<Category> = dao.getAll().map { it.toDomain() }

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun count(): Int = dao.count()
}
