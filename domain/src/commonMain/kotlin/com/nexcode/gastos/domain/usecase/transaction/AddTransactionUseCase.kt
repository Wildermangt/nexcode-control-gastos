package com.nexcode.gastos.domain.usecase.transaction

import com.nexcode.gastos.domain.exception.AccountNotFoundException
import com.nexcode.gastos.domain.exception.BlankFieldException
import com.nexcode.gastos.domain.exception.CategoryNotFoundException
import com.nexcode.gastos.domain.exception.InvalidAmountException
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.repository.AccountRepository
import com.nexcode.gastos.domain.repository.CategoryRepository
import com.nexcode.gastos.domain.repository.TransactionRepository
import com.nexcode.gastos.domain.util.RelojDominio
import kotlinx.datetime.LocalDateTime

/** Datos crudos tal como llegan de la UI: todo es texto y puede venir vacio o nulo. */
data class NewTransactionInput(
    val rawAmount: String?,
    val rawTitle: String? = null,
    val rawNote: String? = null,
    val accountId: Long,
    val categoryId: Long? = null,
    val type: TransactionType = TransactionType.Expense,
    val dateTime: LocalDateTime? = null
)

/** Resultado explicito: la UI no maneja excepciones, maneja estados. */
sealed interface AddTransactionResult {
    data class Success(val transactionId: Long, val transaction: Transaction) : AddTransactionResult
    data class InvalidAmount(val message: String) : AddTransactionResult
    data class MissingField(val fieldName: String) : AddTransactionResult
    data class NotFound(val message: String) : AddTransactionResult
    data class Failure(val message: String) : AddTransactionResult
}

/**
 * Registra una transaccion validando la entrada del usuario.
 *
 * Regla de oro: las excepciones NO cruzan hacia la capa de presentacion.
 * Aqui se atrapan y se traducen a un [AddTransactionResult] que el ViewModel
 * puede pintar directamente.
 */
class AddTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val reloj: RelojDominio   // inyectable: pruebas deterministas
) {

    suspend operator fun invoke(input: NewTransactionInput): AddTransactionResult {
        return try {
            // 1. Monto: conversion de texto a numero (puede lanzar InvalidAmountException).
            val amount = Money.parse(input.rawAmount)
            if (!amount.isPositive) {
                throw InvalidAmountException("El monto debe ser mayor que cero")
            }
            if (amount.cents > MAX_CENTS) {
                throw InvalidAmountException("El monto supera el limite permitido")
            }

            // 2. Cuenta: Elvis con throw como valor de la expresion.
            val account = accountRepository.findById(input.accountId)
                ?: throw AccountNotFoundException(input.accountId)

            // 3. Categoria: safe call. Si categoryId es null NO se consulta la base.
            val category: Category? = input.categoryId?.let { id ->
                categoryRepository.findById(id) ?: throw CategoryNotFoundException(id)
            }

            // 4. Titulo: cadena de Elvis con tres niveles de respaldo.
            val title: String = input.rawTitle?.trim()?.takeIf { it.isNotBlank() }
                ?: category?.name
                ?: input.type.defaultTitle()

            // 5. Nota opcional: "   " se normaliza a null, no a cadena vacia.
            val note: String? = input.rawNote?.trim()?.takeIf { it.isNotBlank() }

            // 6. Una transferencia no puede tener como destino su propia cuenta origen.
            val type = input.type
            if (type is TransactionType.Transfer && type.targetAccountId == account.id) {
                throw BlankFieldException("cuenta destino")
            }

            // 7. Construccion del modelo. El bloque init revalida como ultima barrera.
            val transaction = Transaction(
                accountId = account.id,
                categoryId = category?.id,
                type = type,
                amount = amount,
                dateTime = input.dateTime ?: reloj.ahora(),
                title = title,
                note = note
            )

            val newId = transactionRepository.add(transaction)
            AddTransactionResult.Success(newId, transaction.copy(id = newId))

            // MANEJO DE EXCEPCIONES: de la mas especifica a la mas general.
        } catch (e: InvalidAmountException) {
            AddTransactionResult.InvalidAmount(e.message ?: "Monto invalido")
        } catch (e: BlankFieldException) {
            AddTransactionResult.MissingField(e.fieldName)
        } catch (e: AccountNotFoundException) {
            AddTransactionResult.NotFound(e.message ?: "Cuenta no encontrada")
        } catch (e: CategoryNotFoundException) {
            AddTransactionResult.NotFound(e.message ?: "Categoria no encontrada")
        } catch (e: IllegalArgumentException) {
            // Disparada por los require() del modelo de dominio.
            AddTransactionResult.MissingField(e.message ?: "Datos incompletos")
        } catch (e: Exception) {
            // Red de seguridad: fallo de Room, disco lleno, etc.
            AddTransactionResult.Failure(
                "No se pudo guardar la transaccion: " + (e.message ?: "error desconocido")
            )
        }
    }

    private companion object {
        /** 999.999.999,00 en centavos. */
        const val MAX_CENTS = 99_999_999_900L
    }
}
