package com.nexcode.gastos.domain.usecase.catalog

import com.nexcode.gastos.domain.exception.InvalidAmountException
import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.repository.AccountRepository

/**
 * Resultado de cambiar el cupo de una cuenta.
 *
 * Como en el resto del dominio, los fallos viajan como variantes de un tipo
 * sellado y no como excepciones: la capa de presentacion recibe un estado que
 * el compilador la obliga a cubrir entero, y nunca tiene que envolver la
 * llamada en un `try`.
 */
sealed interface UpdateAccountResult {
    data class Success(val account: Account) : UpdateAccountResult
    data class InvalidAmount(val message: String) : UpdateAccountResult
    data class NotFound(val message: String) : UpdateAccountResult
    data class Failure(val message: String) : UpdateAccountResult
}

/**
 * Sube o baja el cupo de una cuenta.
 *
 * Ofrece dos entradas porque son dos gestos distintos del usuario:
 *  - [invoke] fija una cantidad exacta, la que se escribe en el campo;
 *  - [ajustar] suma o resta sobre lo que ya hay, que es lo que hacen los
 *    botones de mas y menos.
 *
 * La aritmetica vive aqui y no en la pantalla a proposito: si la resta la
 * hiciera la interfaz, el limite de no bajar de cero seria una regla escrita
 * en un boton, imposible de probar sin arrancar la aplicacion.
 */
class UpdateAccountBalanceUseCase(
    private val accountRepository: AccountRepository
) {

    /** Fija el cupo al valor que escribio el usuario. */
    suspend operator fun invoke(accountId: Long, rawAmount: String?): UpdateAccountResult {
        val monto = try {
            Money.parse(rawAmount)
        } catch (e: InvalidAmountException) {
            return UpdateAccountResult.InvalidAmount(e.message ?: "Monto invalido")
        }
        return guardar(accountId) { monto }
    }

    /** Suma [delta] al cupo actual; con un valor negativo, lo resta. */
    suspend fun ajustar(accountId: Long, delta: Money): UpdateAccountResult =
        guardar(accountId) { actual ->
            val nuevo = actual + delta
            // Una cuenta no puede quedar en negativo por un ajuste manual:
            // eso seria una deuda, y este proyecto no modela deudas.
            if (nuevo.cents < 0) Money.ZERO else nuevo
        }

    private suspend fun guardar(
        accountId: Long,
        calcular: (actual: Money) -> Money
    ): UpdateAccountResult {
        val cuenta = accountRepository.findById(accountId)
            ?: return UpdateAccountResult.NotFound("La cuenta no existe")

        val nuevo = calcular(cuenta.initialBalance)
        if (nuevo.cents > Money.MAXIMO.cents) {
            return UpdateAccountResult.InvalidAmount("El cupo supera el maximo permitido")
        }

        return try {
            val actualizada = cuenta.copy(initialBalance = nuevo)
            accountRepository.update(actualizada)
            UpdateAccountResult.Success(actualizada)
        } catch (e: Exception) {
            UpdateAccountResult.Failure(e.message ?: "No se pudo guardar el cupo")
        }
    }
}
