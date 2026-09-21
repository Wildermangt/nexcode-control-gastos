package com.nexcode.gastos.data.local.entity

import androidx.room.ColumnInfo
import java.util.UUID

/**
 * Datos que cada fila necesita para poder replicarse en la nube.
 *
 * Van embebidos en las cuatro tablas y NO suben al dominio. Que una fila este
 * pendiente de sincronizar o marcada como borrada es un detalle de como se
 * guarda, no una regla de negocio: los casos de uso no deben poder consultarlo
 * ni les hace falta.
 *
 * Los tres campos resuelven los tres problemas clasicos de una replica:
 *
 *  - [uuid] evita las colisiones de identificadores. El `id` autoincremental de
 *    SQLite empieza en 1 en cada dispositivo, asi que dos telefonos sin
 *    conexion generan el mismo id para movimientos distintos y al subirlos uno
 *    pisaria al otro. El uuid se genera en el dispositivo y es unico de por si.
 *
 *  - [actualizadoEn] decide los conflictos y permite descargar solo lo que
 *    cambio desde la ultima sincronizacion, en vez de la tabla entera.
 *
 *  - [borrado] hace que los borrados se puedan propagar. Una fila eliminada de
 *    verdad simplemente deja de estar, y el otro extremo no tiene forma de
 *    distinguir "esto se borro" de "esto todavia no me ha llegado".
 */
data class MetadatosDeSincronizacion(

    /** Identificador global de la fila. Se genera una vez y no cambia nunca. */
    @ColumnInfo(name = "uuid", defaultValue = "")
    val uuid: String = UUID.randomUUID().toString(),

    /** Momento del ultimo cambio, en milisegundos desde la epoca. */
    @ColumnInfo(name = "actualizado_en", defaultValue = "0")
    val actualizadoEn: Long = System.currentTimeMillis(),

    /** Borrado logico: la fila sigue en la tabla pero ya no se muestra. */
    @ColumnInfo(name = "borrado", defaultValue = "0")
    val borrado: Boolean = false,

    /** Falso mientras el cambio no haya llegado a la nube. */
    @ColumnInfo(name = "sincronizado", defaultValue = "0")
    val sincronizado: Boolean = false
) {

    /**
     * Marca la fila como modificada conservando su identidad.
     *
     * Es la operacion que hay que usar al actualizar: si en su lugar se
     * construyeran metadatos nuevos, la fila estrenaria uuid en cada edicion y
     * la nube la veria como un registro distinto cada vez.
     */
    fun marcarModificado(ahora: Long = System.currentTimeMillis()) = copy(
        actualizadoEn = ahora,
        sincronizado = false
    )

    /** Marca la fila como borrada sin quitarla de la tabla. */
    fun marcarBorrado(ahora: Long = System.currentTimeMillis()) = copy(
        actualizadoEn = ahora,
        borrado = true,
        sincronizado = false
    )
}
