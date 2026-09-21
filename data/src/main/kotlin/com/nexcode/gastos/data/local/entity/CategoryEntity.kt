package com.nexcode.gastos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Tabla de categorias. [parentId] nullable permite subcategorias. */
@Entity(
    tableName = "categories",
    indices = [Index("parent_id"), Index(value = ["uuid"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon_key")
    val iconKey: String,

    @ColumnInfo(name = "color_argb")
    val colorArgb: Long,

    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "monthly_budget_cents")
    val monthlyBudgetCents: Long? = null,

    @ColumnInfo(name = "applies_to")
    val appliesTo: String,

    /**
     * Metadatos de replica. Embebidos: sus columnas viven en esta misma tabla,
     * pero se declaran una sola vez para las cuatro entidades.
     */
    @Embedded
    val sync: MetadatosDeSincronizacion = MetadatosDeSincronizacion()
)
