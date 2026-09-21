package com.nexcode.gastos.domain.usecase.recurring

import com.nexcode.gastos.domain.exception.AccountNotFoundException
import com.nexcode.gastos.domain.exception.BlankFieldException
import com.nexcode.gastos.domain.exception.InvalidAmountException
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.RecurringFrequency
import com.nexcode.gastos.domain.model.RecurringPayment
import com.nexcode.gastos.domain.model.RecurringPocket
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.repository.AccountRepository
import com.nexcode.gastos.domain.repository.RecurringPaymentRepository
import com.nexcode.gastos.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.nexcode.gastos.domain.util.RelojDominio
import com.nexcode.gastos.domain.util.hoy
import com.nexcode.gastos.domain.util.menosDias
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

// ---------------------------------------------------------------------------
// Observar el bolsillo
// ---------------------------------------------------------------------------

/**
 * Entrega el bolsillo completo: pagos ordenados por urgencia y totales
 * calculados con funciones de orden superior.
 */
class ObserveRecurringPocketUseCase(
    private val repository: RecurringPaymentRepository,
    private val reloj: RelojDominio
) {
    operator fun invoke(today: LocalDate? = null): Flow<RecurringPocket> {
        val dia = today ?: reloj.hoy()
        return repository.observeAll().map { payments -> build(payments, dia) }
    }

    /** Version pura: sin base de datos, probable con listas en memoria. */
    fun build(payments: List<RecurringPayment>, today: LocalDate): RecurringPocket {
        if (payments.isEmpty()) return RecurringPocket()

        val ordered = payments.sortedWith(
            compareByDescending<RecurringPayment> { it.isActive }
                .thenBy { it.nextDueDate }
                .thenBy { it.name }
        )

        val monthly = ordered
            .filter { it.isActive }
            .sumOf { it.monthlyEquivalent().cents }

        return RecurringPocket(
            payments = ordered,
            monthlyCommitment = Money(monthly),
            overdueCount = ordered.count { it.status(today) == RecurringPayment.Status.OVERDUE },
            dueTodayCount = ordered.count { it.status(today) == RecurringPayment.Status.TODAY }
        )
    }
}

// ---------------------------------------------------------------------------
// Crear un recordatorio
// ---------------------------------------------------------------------------

data class NewRecurringInput(
    val rawName: String?,
    val rawAmount: String?,
    val frequency: RecurringFrequency,
    val accountId: Long,
    val categoryId: Long? = null,
    val rawNote: String? = null,
    val startFrom: LocalDate? = null
)

sealed interface AddRecurringResult {
    data class Success(val paymentId: Long) : AddRecurringResult
    data class InvalidAmount(val message: String) : AddRecurringResult
    data class MissingField(val fieldName: String) : AddRecurringResult
    data class Failure(val message: String) : AddRecurringResult
}

/**
 * Registra un pago fijo. Repite el mismo patron que AddTransactionUseCase:
 * valida, atrapa excepciones y devuelve un resultado que la UI puede pintar.
 */
class AddRecurringPaymentUseCase(
    private val repository: RecurringPaymentRepository,
    private val accountRepository: AccountRepository,
    private val reloj: RelojDominio
) {
    suspend operator fun invoke(input: NewRecurringInput): AddRecurringResult {
        return try {
            // Null safety: trim + takeIf evitan guardar nombres en blanco.
            val name = input.rawName?.trim()?.takeIf { it.isNotBlank() }
                ?: throw BlankFieldException("nombre del pago")

            val amount = Money.parse(input.rawAmount)
            if (!amount.isPositive) {
                throw InvalidAmountException("El monto del pago debe ser mayor que cero")
            }

            accountRepository.findById(input.accountId)
                ?: throw AccountNotFoundException(input.accountId)

            val from = input.startFrom ?: reloj.hoy()
            val payment = RecurringPayment(
                name = name,
                amount = amount,
                frequency = input.frequency,
                nextDueDate = input.frequency.nextDateAfter(from.menosDias(1)),
                accountId = input.accountId,
                categoryId = input.categoryId,
                note = input.rawNote?.trim()?.takeIf { it.isNotBlank() }
            )

            AddRecurringResult.Success(repository.add(payment))
        } catch (e: InvalidAmountException) {
            AddRecurringResult.InvalidAmount(e.message ?: "Monto invalido")
        } catch (e: BlankFieldException) {
            AddRecurringResult.MissingField(e.fieldName)
        } catch (e: AccountNotFoundException) {
            AddRecurringResult.MissingField("cuenta")
        } catch (e: IllegalArgumentException) {
            AddRecurringResult.MissingField(e.message ?: "datos incompletos")
        } catch (e: Exception) {
            AddRecurringResult.Failure(
                "No se pudo crear el recordatorio: " + (e.message ?: "error desconocido")
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Marcar como pagado
// ---------------------------------------------------------------------------

/**
 * Registra el pago fijo como gasto real y adelanta el recordatorio a su
 * proxima fecha. Es la union entre el bolsillo y el historico de movimientos.
 */
class PayRecurringPaymentUseCase(
    private val repository: RecurringPaymentRepository,
    private val transactionRepository: TransactionRepository,
    private val reloj: RelojDominio
) {
    suspend operator fun invoke(
        paymentId: Long,
        paidOn: LocalDate? = null
    ): Boolean {
        val fecha = paidOn ?: reloj.hoy()
        return try {
            val payment = repository.findById(paymentId) ?: return false

            transactionRepository.add(
                Transaction(
                    accountId = payment.accountId,
                    categoryId = payment.categoryId,
                    type = TransactionType.Expense,
                    amount = payment.amount,
                    dateTime = LocalDateTime(fecha, reloj.ahora().time),
                    title = payment.name,
                    note = payment.note ?: "Pago fijo registrado desde el bolsillo"
                )
            )

            repository.update(payment.markPaid(fecha))
            true
        } catch (e: Exception) {
            false
        }
    }
}

// ---------------------------------------------------------------------------
// Pausar y eliminar
// ---------------------------------------------------------------------------

class ToggleRecurringPaymentUseCase(
    private val repository: RecurringPaymentRepository
) {
    suspend operator fun invoke(paymentId: Long): Boolean = try {
        val payment = repository.findById(paymentId)
        if (payment == null) {
            false
        } else {
            repository.update(payment.copy(isActive = !payment.isActive))
            true
        }
    } catch (e: Exception) {
        false
    }
}

class DeleteRecurringPaymentUseCase(
    private val repository: RecurringPaymentRepository
) {
    suspend operator fun invoke(paymentId: Long): Boolean = try {
        repository.delete(paymentId)
        true
    } catch (e: Exception) {
        false
    }
}

// ---------------------------------------------------------------------------
// Recordatorios del sistema
// ---------------------------------------------------------------------------

/**
 * Pagos que ya vencieron o que vencen hoy.
 *
 * Es la consulta que alimenta la notificacion del sistema: a diferencia de
 * [ObserveRecurringPocketUseCase], no devuelve un flujo sino una foto puntual,
 * porque el trabajo en segundo plano se ejecuta una vez y termina.
 */
class GetPagosPorAvisarUseCase(
    private val repository: RecurringPaymentRepository,
    private val reloj: RelojDominio
) {
    suspend operator fun invoke(today: LocalDate? = null): List<RecurringPayment> {
        val dia = today ?: reloj.hoy()
        return try {
            repository.observeAll().first()
                .filter { it.isActive }
                .filter { pago ->
                    val estado = pago.status(dia)
                    estado == RecurringPayment.Status.OVERDUE ||
                        estado == RecurringPayment.Status.TODAY
                }
                .sortedBy { it.nextDueDate }
        } catch (e: Exception) {
            // Si la consulta falla no se avisa nada, pero la app no se cae.
            emptyList()
        }
    }
}
