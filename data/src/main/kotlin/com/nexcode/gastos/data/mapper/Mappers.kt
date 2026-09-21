package com.nexcode.gastos.data.mapper

import com.nexcode.gastos.data.local.entity.AccountEntity
import com.nexcode.gastos.data.local.entity.CategoryEntity
import com.nexcode.gastos.data.local.entity.MetadatosDeSincronizacion
import com.nexcode.gastos.data.local.entity.RecurringPaymentEntity
import com.nexcode.gastos.data.local.entity.TransactionEntity
import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.RecurringFrequency
import com.nexcode.gastos.domain.model.RecurringPayment
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType

/**
 * Traductores entre las ENTIDADES de Room y los MODELOS del dominio.
 *
 * Son la frontera de las dos capas: gracias a ellos el dominio nunca importa
 * androidx.room, y un cambio de motor de base de datos solo afecta este archivo.
 */

// ------------------------------ Account ------------------------------

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    initialBalance = Money(initialBalanceCents),
    colorArgb = colorArgb,
    iconKey = iconKey,
    isArchived = isArchived
)

fun Account.toEntity(
    sync: MetadatosDeSincronizacion = MetadatosDeSincronizacion()
): AccountEntity = AccountEntity(
    id = id,
    name = name,
    initialBalanceCents = initialBalance.cents,
    colorArgb = colorArgb,
    iconKey = iconKey,
    isArchived = isArchived,
    sync = sync
)

// ------------------------------ Category ------------------------------

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    parentId = parentId,
    // Safe call + let: solo se envuelve en Money si hay presupuesto.
    monthlyBudget = monthlyBudgetCents?.let { Money(it) },
    appliesTo = TransactionType.fromCode(appliesTo)
)

fun Category.toEntity(
    sync: MetadatosDeSincronizacion = MetadatosDeSincronizacion()
): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    parentId = parentId,
    monthlyBudgetCents = monthlyBudget?.cents,
    appliesTo = appliesTo.code,
    sync = sync
)

// ---------------------------- Transaction ----------------------------

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    type = TransactionType.fromCode(typeCode, targetAccountId),
    amount = Money(amountCents),
    dateTime = dateTime,
    title = title,
    note = note
)

fun Transaction.toEntity(
    sync: MetadatosDeSincronizacion = MetadatosDeSincronizacion()
): TransactionEntity = TransactionEntity(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    typeCode = type.code,
    targetAccountId = type.targetAccountIdOrNull,
    amountCents = amount.cents,
    dateTime = dateTime,
    title = title,
    note = note,
    sync = sync
)

// ------------------------- RecurringPayment -------------------------

fun RecurringPaymentEntity.toDomain(): RecurringPayment = RecurringPayment(
    id = id,
    name = name,
    amount = Money(amountCents),
    frequency = RecurringFrequency.fromCode(frequencyCode, frequencyValue1, frequencyValue2),
    nextDueDate = nextDueDate,
    accountId = accountId,
    categoryId = categoryId,
    note = note,
    lastPaidDate = lastPaidDate,
    isActive = isActive
)

fun RecurringPayment.toEntity(
    sync: MetadatosDeSincronizacion = MetadatosDeSincronizacion()
): RecurringPaymentEntity {
    // when exhaustivo sobre la sealed class: cada frecuencia guarda sus valores.
    val value1: Int?
    val value2: Int?
    when (val f = frequency) {
        is RecurringFrequency.Monthly -> {
            value1 = f.dayOfMonth
            value2 = null
        }
        is RecurringFrequency.Weekly -> {
            value1 = f.dayOfWeek
            value2 = null
        }
        is RecurringFrequency.Yearly -> {
            value1 = f.month
            value2 = f.dayOfMonth
        }
    }

    return RecurringPaymentEntity(
        id = id,
        name = name,
        amountCents = amount.cents,
        frequencyCode = frequency.code,
        frequencyValue1 = value1,
        frequencyValue2 = value2,
        nextDueDate = nextDueDate,
        accountId = accountId,
        categoryId = categoryId,
        note = note,
        lastPaidDate = lastPaidDate,
        isActive = isActive,
        sync = sync
    )
}
