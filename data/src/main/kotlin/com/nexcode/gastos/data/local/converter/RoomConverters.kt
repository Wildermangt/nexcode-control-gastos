package com.nexcode.gastos.data.local.converter

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.Instant
import java.time.ZoneId

/**
 * SQLite no sabe de fechas: guarda numeros y texto.
 *
 * El dominio trabaja con los tipos de kotlinx-datetime, que son
 * multiplataforma. Esta capa sí puede usar `java.time`, de modo que aquí se
 * hace la traduccion entre ambos mundos: es exactamente la frontera donde
 * corresponde hacerla.
 */
class RoomConverters {

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): Long? =
        value?.toJavaLocalDateTime()
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()

    @TypeConverter
    fun toLocalDateTime(value: Long?): LocalDateTime? =
        value?.let {
            java.time.LocalDateTime
                .ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                .toKotlinLocalDateTime()
        }

    /** LocalDate se guarda como texto ISO (2026-08-22): legible y ordenable. */
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }
}
