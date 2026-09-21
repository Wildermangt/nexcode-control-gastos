package com.nexcode.gastos.domain

import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.RecurringPayment
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.repository.AccountRepository
import com.nexcode.gastos.domain.repository.CategoryRepository
import com.nexcode.gastos.domain.repository.RecurringPaymentRepository
import com.nexcode.gastos.domain.repository.TransactionRepository
import com.nexcode.gastos.domain.util.RelojDominio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Dobles de prueba compartidos por las pruebas del dominio.
 *
 * Al implementar los contratos del dominio permiten probar los casos de uso
 * sin base de datos, en cualquier plataforma.
 */

/** Reloj fijo: hace que las pruebas no dependan del dia en que se ejecuten. */
val RELOJ_FIJO = RelojDominio { LocalDateTime(2026, 8, 22, 12, 0) }

class FakeTransactionRepository : TransactionRepository {
    val saved = mutableListOf<Transaction>()

    override suspend fun add(transaction: Transaction): Long {
        saved.add(transaction)
        return saved.size.toLong()
    }

    override suspend fun update(transaction: Transaction) = Unit
    override suspend fun delete(transactionId: Long) = Unit
    override suspend fun findById(transactionId: Long): Transaction? = saved.firstOrNull()
    override fun observeAll(): Flow<List<Transaction>> = flowOf(saved)
    override fun observeRecent(limit: Int): Flow<List<Transaction>> = flowOf(saved.take(limit))
    override suspend fun findByRange(from: LocalDate, to: LocalDate): List<Transaction> = saved

    override suspend fun buscar(texto: String, limite: Int): List<Transaction> =
        saved.filter {
            it.title.contains(texto, ignoreCase = true) ||
                it.note?.contains(texto, ignoreCase = true) == true
        }.take(limite)
}

class FakeAccountRepository(accounts: List<Account>) : AccountRepository {

    /**
     * La lista es mutable para que las pruebas puedan comprobar QUE se guardo,
     * y no solo que la llamada no fallo.
     */
    val guardadas = accounts.toMutableList()

    override suspend fun add(account: Account): Long = 1L

    override suspend fun update(account: Account) {
        val i = guardadas.indexOfFirst { it.id == account.id }
        if (i >= 0) guardadas[i] = account
    }

    override suspend fun findById(accountId: Long): Account? =
        guardadas.firstOrNull { it.id == accountId }

    override suspend fun getAll(): List<Account> = guardadas
    override fun observeAll(): Flow<List<Account>> = flowOf(guardadas)
    override suspend fun count(): Int = guardadas.size
}

class FakeCategoryRepository(private val categories: List<Category>) : CategoryRepository {
    override suspend fun add(category: Category): Long = 1L
    override suspend fun findById(categoryId: Long): Category? =
        categories.firstOrNull { it.id == categoryId }

    override suspend fun getAll(): List<Category> = categories
    override fun observeAll(): Flow<List<Category>> = flowOf(categories)
    override suspend fun count(): Int = categories.size
}

class FakeRecurringPaymentRepository(
    pagos: List<RecurringPayment> = emptyList()
) : RecurringPaymentRepository {

    val guardados = pagos.toMutableList()

    override suspend fun add(payment: RecurringPayment): Long {
        guardados.add(payment)
        return guardados.size.toLong()
    }

    override suspend fun update(payment: RecurringPayment) {
        val i = guardados.indexOfFirst { it.id == payment.id }
        if (i >= 0) guardados[i] = payment
    }

    override suspend fun delete(paymentId: Long) {
        guardados.removeAll { it.id == paymentId }
    }

    override suspend fun findById(paymentId: Long): RecurringPayment? =
        guardados.firstOrNull { it.id == paymentId }

    override fun observeAll(): Flow<List<RecurringPayment>> = flowOf(guardados)

    override suspend fun count(): Int = guardados.size
}
