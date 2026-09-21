package com.nexcode.gastos.presentation.bienvenida

import android.content.Context

/**
 * Recuerda si el usuario ya vio la bienvenida.
 *
 * Vive en la capa de presentacion, y no en :data, a proposito. Lo que guarda
 * no es informacion del negocio —no es un gasto, ni una cuenta, ni un pago—
 * sino una preferencia sobre COMO se muestra la interfaz. Meterla en la capa
 * de datos obligaria a inventar un contrato en el dominio para un dato que el
 * dominio nunca va a consultar.
 *
 * Por eso usa SharedPreferences directamente: es una API de la plataforma, no
 * un motor de persistencia, y la regla de dependencias sigue intacta.
 */
class PreferenciasBienvenida(context: Context) {

    private val prefs = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    /** true cuando ya no hay que volver a mostrar la bienvenida. */
    var completada: Boolean
        get() = prefs.getBoolean(CLAVE_COMPLETADA, false)
        set(valor) = prefs.edit().putBoolean(CLAVE_COMPLETADA, valor).apply()

    /** Permite volver a verla desde el perfil sin borrar los datos de la app. */
    fun reiniciar() {
        completada = false
    }

    private companion object {
        const val ARCHIVO = "nexcode_preferencias"
        const val CLAVE_COMPLETADA = "bienvenida_completada"
    }
}
