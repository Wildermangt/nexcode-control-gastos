package com.nexcode.gastos.data.sync

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexcode.gastos.data.local.entity.MetadatosDeSincronizacion
import kotlinx.coroutines.tasks.await

/**
 * Sabe replicar UNA tabla entre el dispositivo y la nube.
 *
 * El algoritmo de subida y bajada es identico para las cuatro tablas —tomar lo
 * pendiente, escribirlo por lotes, marcarlo; pedir lo que cambio, aplicarlo— y
 * lo unico que varia es como se traduce cada fila a documento y al reves. Por
 * eso el algoritmo vive aqui una sola vez y las subclases aportan nada mas la
 * traduccion.
 *
 * [subir] y [bajar] no mencionan el tipo [E] en su firma a proposito: eso
 * permite que el motor guarde los cuatro adaptadores en una misma lista y los
 * recorra sin saber con que entidad trabaja cada uno.
 */
abstract class AdaptadorDeTabla<E : Any> {

    /** Nombre de la coleccion en la nube. */
    abstract val coleccion: String

    // --- Lo que cada tabla debe aportar --------------------------------

    protected abstract suspend fun pendientes(): List<E>

    /** Todas las filas, borradas incluidas: sirve para traducir identificadores. */
    abstract suspend fun todas(): List<E>

    abstract fun metadatosDe(fila: E): MetadatosDeSincronizacion

    abstract fun idDe(fila: E): Long

    /** Convierte una fila en el documento que viaja a la nube. */
    protected abstract fun aDocumento(fila: E, referencias: Referencias): Map<String, Any?>

    /**
     * Inserta o actualiza una fila que llego de la nube.
     *
     * @return false si la fila no se pudo aplicar todavia, por ejemplo un
     *         movimiento cuya cuenta aun no ha bajado.
     */
    protected abstract suspend fun aplicar(
        uuid: String,
        datos: Map<String, Any?>,
        actualizadoEn: Long,
        referencias: Referencias
    ): Boolean

    protected abstract suspend fun marcarSincronizados(uuids: List<String>)

    /** Cuantas filas de esta tabla esperan subir. */
    suspend fun contarPendientes(): Int = pendientes().size

    // --- Algoritmo comun -----------------------------------------------

    /**
     * Sube lo pendiente y devuelve cuantas filas viajaron.
     *
     * Las filas marcadas como borradas suben igual: ese borrado logico es la
     * unica forma de que el otro extremo se entere de la eliminacion.
     */
    suspend fun subir(
        raiz: CollectionReference,
        firestore: FirebaseFirestore,
        referencias: Referencias
    ): Int {
        val filas = pendientes()
        if (filas.isEmpty()) return 0

        var enviadas = 0
        filas.chunked(MAX_POR_LOTE).forEach { grupo ->
            val lote = firestore.batch()
            grupo.forEach { fila ->
                lote.set(raiz.document(metadatosDe(fila).uuid), aDocumento(fila, referencias))
            }
            lote.commit().await()

            // Se marcan DESPUES de que la nube confirme. Marcarlas antes
            // perderia el cambio para siempre si la escritura fallara.
            marcarSincronizados(grupo.map { metadatosDe(it).uuid })
            enviadas += grupo.size
        }
        return enviadas
    }

    /** Baja lo que cambio desde [desde]. Devuelve cuantas se aplicaron y la marca nueva. */
    suspend fun bajar(
        raiz: CollectionReference,
        desde: Long,
        referencias: Referencias
    ): Bajada {
        // Solo lo que cambio: pedir la coleccion entera en cada pasada gastaria
        // la cuota de lectura sin ninguna necesidad.
        val documentos = raiz
            .whereGreaterThan(CAMPO_ACTUALIZADO, desde)
            .orderBy(CAMPO_ACTUALIZADO, Query.Direction.ASCENDING)
            .get()
            .await()

        var aplicadas = 0
        var marca = desde

        for (documento in documentos.documents) {
            val datos = documento.data ?: continue
            val actualizadoEn = (datos[CAMPO_ACTUALIZADO] as? Number)?.toLong() ?: 0L

            if (aplicar(documento.id, datos, actualizadoEn, referencias)) {
                aplicadas++
                // La marca solo avanza sobre lo aplicado: si la bajada se corta
                // a la mitad, la proxima pasada retoma donde quedo.
                if (actualizadoEn > marca) marca = actualizadoEn
            }
        }

        return Bajada(aplicadas, marca)
    }

    data class Bajada(val aplicadas: Int, val nuevaMarca: Long)

    /**
     * Traduccion entre identificadores locales y globales.
     *
     * El `id` de SQLite no significa nada fuera de este telefono, asi que lo
     * que viaja en el documento es el uuid de la cuenta y el de la categoria.
     * Al bajar se convierten de vuelta al id de ESTE dispositivo.
     */
    data class Referencias(
        val uuidDeCuenta: Map<Long, String> = emptyMap(),
        val uuidDeCategoria: Map<Long, String> = emptyMap(),
        val idDeCuenta: Map<String, Long> = emptyMap(),
        val idDeCategoria: Map<String, Long> = emptyMap()
    )

    protected companion object {
        const val CAMPO_ACTUALIZADO = "actualizado_en"
        const val CAMPO_BORRADO = "borrado"

        /** Firestore admite 500 operaciones por lote; se deja margen. */
        const val MAX_POR_LOTE = 400

        /** Lee un entero de un documento sin confiar en el tipo exacto. */
        fun entero(datos: Map<String, Any?>, clave: String): Long? =
            (datos[clave] as? Number)?.toLong()

        fun texto(datos: Map<String, Any?>, clave: String): String? = datos[clave] as? String

        fun booleano(datos: Map<String, Any?>, clave: String): Boolean =
            datos[clave] as? Boolean ?: false

        /** Metadatos de una fila que acaba de llegar: ya esta sincronizada. */
        fun metadatosDeLaNube(uuid: String, datos: Map<String, Any?>) =
            MetadatosDeSincronizacion(
                uuid = uuid,
                actualizadoEn = entero(datos, CAMPO_ACTUALIZADO) ?: 0L,
                borrado = booleano(datos, CAMPO_BORRADO),
                sincronizado = true
            )
    }
}
