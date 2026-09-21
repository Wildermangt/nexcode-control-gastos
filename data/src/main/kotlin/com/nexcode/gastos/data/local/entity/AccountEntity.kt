package com.nexcode.gastos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla de cuentas.
 *
 * La entidad es una clase de la CAPA DE DATOS: conoce Room y los nombres de
 * columnas. El dominio nunca la ve, por eso existen los mappers.
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "initial_balance_cents")
    val initialBalanceCents: Long,

    @ColumnInfo(name = "color_argb")
    val colorArgb: Long,

    @ColumnInfo(name = "icon_key")
    val iconKey: String,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    /**
     * Metadatos de replica. Embebidos: sus columnas viven en esta misma tabla,
     * pero se declaran una sola vez para las cuatro entidades.
     */
    @Embedded
    val sync: MetadatosDeSincronizacion = MetadatosDeSincronizacion()
)
