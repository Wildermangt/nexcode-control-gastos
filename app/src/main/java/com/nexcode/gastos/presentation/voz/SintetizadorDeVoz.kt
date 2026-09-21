package com.nexcode.gastos.presentation.voz

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Sintesis de voz con el motor del sistema operativo.
 *
 * Igual que el reconocedor, no usa ningun servicio externo: Android incluye
 * su propio sintetizador, que funciona sin conexion una vez instalada la voz
 * del idioma.
 */
class SintetizadorDeVoz(context: Context) {

    private var motor: TextToSpeech? = null
    private var listo = false

    init {
        motor = TextToSpeech(context) { estado ->
            if (estado == TextToSpeech.SUCCESS) {
                // Se intenta el espanol de Colombia y se cae al de Espana si
                // el dispositivo no lo tiene instalado.
                val resultado = motor?.setLanguage(Locale("es", "CO"))
                if (resultado == TextToSpeech.LANG_MISSING_DATA ||
                    resultado == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    motor?.setLanguage(Locale("es", "ES"))
                }
                motor?.setSpeechRate(RITMO)
                listo = true
            }
        }
    }

    /**
     * Lee el texto en voz alta.
     *
     * QUEUE_FLUSH y no QUEUE_ADD: si llega un analisis nuevo mientras se lee
     * el anterior, interesa el nuevo. Encolarlos obligaria al usuario a oir
     * una respuesta que ya no le sirve.
     */
    fun hablar(texto: String) {
        if (!listo || texto.isBlank()) return
        motor?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, ID_LOCUCION)
    }

    /** Corta la lectura en curso. */
    fun detener() {
        motor?.stop()
    }

    /** Libera el motor. Debe llamarse al salir de la pantalla. */
    fun liberar() {
        motor?.stop()
        motor?.shutdown()
        motor = null
        listo = false
    }

    private companion object {
        const val ID_LOCUCION = "nexcode-asistente"

        /** Algo mas pausado que el valor por defecto: son cifras. */
        const val RITMO = 0.95f
    }
}
