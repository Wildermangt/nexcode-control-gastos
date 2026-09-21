package com.nexcode.gastos.presentation.voz

import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Reconocimiento de voz con las API del sistema operativo.
 *
 * No se usa ningun servicio externo: Android trae su propio motor de
 * reconocimiento, de modo que el asistente no cuesta dinero ni exige cuenta,
 * y en los dispositivos que lo admiten puede funcionar sin conexion.
 *
 * Esta clase vive en la capa de presentacion porque el microfono es una
 * capacidad del dispositivo. El dominio no se entera de que existe.
 */
class ReconocedorDeVoz(private val context: Context) {

    private var reconocedor: SpeechRecognizer? = null

    /** Falso en dispositivos sin motor de reconocimiento instalado. */
    val disponible: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Empieza a escuchar.
     *
     * @param onParcial se llama mientras el usuario habla, con lo entendido
     *        hasta el momento; sirve para dar respuesta visual inmediata.
     * @param onResultado se llama una sola vez con la frase definitiva.
     * @param onError se llama con un mensaje ya redactado para el usuario.
     */
    fun escuchar(
        onParcial: (String) -> Unit,
        onResultado: (String) -> Unit,
        onError: (String) -> Unit
    ) = escuchar(onParcial, onResultado, onError, preferirSinConexion = true)

    /**
     * @param preferirSinConexion primero se intenta el motor local, que es mas
     *        rapido y no gasta datos. Si el dispositivo no tiene instalado el
     *        paquete de idioma sin conexion, se reintenta una sola vez con el
     *        motor en linea en lugar de fallar.
     */
    private fun escuchar(
        onParcial: (String) -> Unit,
        onResultado: (String) -> Unit,
        onError: (String) -> Unit,
        preferirSinConexion: Boolean
    ) {
        if (!disponible) {
            onError("Este dispositivo no tiene reconocimiento de voz")
            return
        }

        liberar()

        val nuevo = SpeechRecognizer.createSpeechRecognizer(context)
        reconocedor = nuevo

        nuevo.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val texto = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                // Elvis + isNullOrBlank: si el motor devuelve vacio se trata
                // como error y no como una pregunta en blanco.
                if (texto.isNullOrBlank()) {
                    onError("No entendi lo que dijiste")
                } else {
                    onResultado(texto)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(onParcial)
            }

            override fun onError(error: Int) {
                Log.w(ETIQUETA, "Error de reconocimiento: codigo " + error)

                // Un fallo del motor local no es definitivo: puede ser que
                // falte el paquete de idioma sin conexion. Se reintenta una
                // sola vez en linea, salvo cuando el error significa que el
                // usuario simplemente no dijo nada o falta el permiso: en
                // esos casos reintentar solo haria esperar de mas.
                val vuelveAFallar = error in ERRORES_SIN_REINTENTO

                if (preferirSinConexion && !vuelveAFallar) {
                    escuchar(onParcial, onResultado, onError, preferirSinConexion = false)
                } else {
                    onError(mensajeDeError(error))
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intencion = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferirSinConexion)
        }

        nuevo.startListening(intencion)
    }

    /** Deja de escuchar y procesa lo dicho hasta ahora. */
    fun detener() {
        reconocedor?.stopListening()
    }

    /** Libera el motor. Debe llamarse al salir de la pantalla. */
    fun liberar() {
        reconocedor?.destroy()
        reconocedor = null
    }

    /** Traduce los codigos de error del sistema a algo que el usuario entienda. */
    private fun mensajeDeError(codigo: Int): String = when (codigo) {
        SpeechRecognizer.ERROR_NO_MATCH -> "No entendi lo que dijiste"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuche nada. Intenta de nuevo"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso del microfono"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El reconocimiento necesita conexion en este dispositivo"
        SpeechRecognizer.ERROR_AUDIO -> "Hubo un problema con el microfono"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocimiento esta ocupado. Espera un momento"
        ERROR_IDIOMA_NO_SOPORTADO,
        ERROR_IDIOMA_NO_DISPONIBLE ->
            "Falta el paquete de voz en espanol. Instalalo desde los ajustes del sistema"
        else -> "No se pudo reconocer la voz"
    }

    private companion object {
        const val ETIQUETA = "ReconocedorDeVoz"

        // Constantes numericas y no las del SDK: existen desde Android 13 y
        // este proyecto admite desde Android 8.
        const val ERROR_IDIOMA_NO_SOPORTADO = 12
        const val ERROR_IDIOMA_NO_DISPONIBLE = 13

        /** Errores en los que reintentar no aporta nada. */
        val ERRORES_SIN_REINTENTO = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        )
    }
}
