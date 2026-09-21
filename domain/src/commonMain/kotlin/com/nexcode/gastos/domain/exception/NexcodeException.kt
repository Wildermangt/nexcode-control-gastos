package com.nexcode.gastos.domain.exception

/**
 * Raiz de todos los errores de negocio de Nexcode.
 *
 * Hereda de [Exception] (herencia) y es `sealed`, por lo que el compilador
 * conoce la lista completa de errores posibles del dominio.
 */
sealed class NexcodeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** El texto escrito por el usuario no representa un monto valido. */
class InvalidAmountException(
    message: String,
    cause: Throwable? = null
) : NexcodeException(message, cause)

/** Un campo obligatorio llego vacio o en blanco. */
class BlankFieldException(
    val fieldName: String
) : NexcodeException("El campo \"" + fieldName + "\" es obligatorio")

/** No existe la cuenta indicada. */
class AccountNotFoundException(
    val accountId: Long
) : NexcodeException("No existe la cuenta con id " + accountId)

/** No existe la categoria indicada. */
class CategoryNotFoundException(
    val categoryId: Long
) : NexcodeException("No existe la categoria con id " + categoryId)
