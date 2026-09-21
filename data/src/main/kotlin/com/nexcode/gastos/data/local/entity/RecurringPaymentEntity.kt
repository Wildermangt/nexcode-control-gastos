package com.nexcode.gastos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

/** Tabla del bolsillo de recordatorios: pagos fijos recurrentes. */
@Entity(
    tableName = "recurring_payments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("account_id"), Index("category_id"), Index("next_due_date"),
        Index(value = ["uuid"], unique = true)
    ]
)
data class RecurringPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,

    @ColumnInfo(name = "frequency_code")
    val frequencyCode: String,

    @ColumnInfo(name = "frequency_value1")
    val frequencyValue1: Int? = null,

    @ColumnInfo(name = "frequency_value2")
    val frequencyValue2: Int? = null,

    @ColumnInfo(name = "next_due_date")
    val nextDueDate: LocalDate,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "last_paid_date")
    val lastPaidDate: LocalDate? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    /**
     * Metadatos de replica. Embebidos: sus columnas viven en esta misma tabla,
     * pero se declaran una sola vez para las cuatro entidades.
     */
    @Embedded
    val sync: MetadatosDeSincronizacion = MetadatosDeSincronizacion()
)
