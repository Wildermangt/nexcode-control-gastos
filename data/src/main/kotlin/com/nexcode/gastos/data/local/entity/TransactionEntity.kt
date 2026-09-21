package com.nexcode.gastos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

/**
 * Tabla de transacciones.
 *
 * Claves foraneas reales: si se borra una cuenta caen sus movimientos (CASCADE),
 * y si se borra una categoria los movimientos quedan sin categoria (SET NULL),
 * que es exactamente el caso que el dominio modela como `categoryId: Long?`.
 */
@Entity(
    tableName = "transactions",
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
        Index("account_id"), Index("category_id"), Index("date_time"),
        Index(value = ["uuid"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "type_code")
    val typeCode: String,

    @ColumnInfo(name = "target_account_id")
    val targetAccountId: Long? = null,

    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,

    @ColumnInfo(name = "date_time")
    val dateTime: LocalDateTime,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "note")
    val note: String? = null,

    /**
     * Metadatos de replica. Embebidos: sus columnas viven en esta misma tabla,
     * pero se declaran una sola vez para las cuatro entidades.
     */
    @Embedded
    val sync: MetadatosDeSincronizacion = MetadatosDeSincronizacion()
)
