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

/**
 * Edita un movimiento ya registrado.
 *
 * Aplica exactamente las mismas validaciones que [AddTransactionUseCase] y
 * devuelve el mismo tipo de resultado, para que el formulario de la interfaz
 * sirva igual para crear y para editar sin duplicar codigo.
 */
class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {

    suspend operator fun invoke(
        transactionId: Long,
        input: NewTransactionInput
    ): AddTransactionResult {
        return try {
            // El movimiento debe existir: si no, no hay nada que editar.
            val existing = transactionRepository.findById(transactionId)
                ?: return AddTransactionResult.NotFound(
                    "El movimiento ya no existe"
                )

            val amount = Money.parse(input.rawAmount)
            if (!amount.isPositive) {
                throw InvalidAmountException("El monto debe ser mayor que cero")
            }

            val account = accountRepository.findById(input.accountId)
                ?: throw AccountNotFoundException(input.accountId)

            val category: Category? = input.categoryId?.let { id ->
                categoryRepository.findById(id) ?: throw CategoryNotFoundException(id)
            }

            val title: String = input.rawTitle?.trim()?.takeIf { it.isNotBlank() }
                ?: category?.name
                ?: input.type.defaultTitle()

            val note: String? = input.rawNote?.trim()?.takeIf { it.isNotBlank() }

            val type = input.type
            if (type is TransactionType.Transfer && type.targetAccountId == account.id) {
                throw BlankFieldException("cuenta destino")
            }

            // copy conserva el id y la fecha original si la UI no envia otra.
            val updated: Transaction = existing.copy(
                accountId = account.id,
                categoryId = category?.id,
                type = type,
                amount = amount,
                dateTime = input.dateTime ?: existing.dateTime,
                title = title,
                note = note
            )

            transactionRepository.update(updated)
            AddTransactionResult.Success(updated.id, updated)

        } catch (e: InvalidAmountException) {
            AddTransactionResult.InvalidAmount(e.message ?: "Monto invalido")
        } catch (e: BlankFieldException) {
            AddTransactionResult.MissingField(e.fieldName)
        } catch (e: AccountNotFoundException) {
            AddTransactionResult.NotFound(e.message ?: "Cuenta no encontrada")
        } catch (e: CategoryNotFoundException) {
            AddTransactionResult.NotFound(e.message ?: "Categoria no encontrada")
        } catch (e: IllegalArgumentException) {
            AddTransactionResult.MissingField(e.message ?: "Datos incompletos")
        } catch (e: Exception) {
            AddTransactionResult.Failure(
                "No se pudo actualizar el movimiento: " + (e.message ?: "error desconocido")
            )
        }
    }
}

/** Carga un movimiento para llenar el formulario de edicion. */
class GetTransactionByIdUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transactionId: Long): Transaction? = try {
        transactionRepository.findById(transactionId)
    } catch (e: Exception) {
        null
    }
}
