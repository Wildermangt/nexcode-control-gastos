package com.nexcode.gastos.data.sync

import android.content.Context

/**
 * Recuerda hasta donde bajo cada coleccion.
 *
 * Sin esta marca cada pasada tendria que descargar la coleccion entera para
 * saber que cambio, y en el nivel gratuito de Firestore eso agota la cuota de
 * lectura en pocos dias.
 *
 * Vive en preferencias y no en la base de datos a proposito: no es informacion
 * del usuario, es el estado de un proceso. Si se borra, la unica consecuencia
 * es que la proxima sincronizacion baje de mas, y como se resuelve por uuid,
 * nada se duplica.
 */
class MarcasDeSincronizacion(context: Context) {

    private val prefs = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun ultimaBajada(coleccion: String): Long = prefs.getLong(CLAVE + coleccion, 0L)

    fun guardarUltimaBajada(coleccion: String, marca: Long) {
        prefs.edit().putLong(CLAVE + coleccion, marca).apply()
    }

    /** Momento de la ultima sincronizacion completa, para mostrarlo en pantalla. */
    var ultimaPasada: Long
        get() = prefs.getLong(CLAVE_PASADA, 0L)
        set(valor) = prefs.edit().putLong(CLAVE_PASADA, valor).apply()

    /** Vuelve a empezar de cero. Util al cerrar sesion o en pruebas. */
    fun reiniciar() = prefs.edit().clear().apply()

    private companion object {
        const val ARCHIVO = "nexcode_sincronizacion"
        const val CLAVE = "ultima_bajada_"
        const val CLAVE_PASADA = "ultima_pasada"
    }
}
