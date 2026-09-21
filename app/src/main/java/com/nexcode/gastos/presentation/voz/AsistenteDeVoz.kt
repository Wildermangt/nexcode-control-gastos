package com.nexcode.gastos.presentation.voz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Estado de la conversacion por voz, tal como lo pinta la pantalla. */
data class EstadoDeVoz(
    val escuchando: Boolean = false,
    val textoParcial: String = "",
    val error: String? = null,
    val disponible: Boolean = true
)

/**
 * Une el reconocedor y el sintetizador en un solo objeto con estado,
 * y se encarga de liberarlos cuando la pantalla desaparece.
 *
 * Los motores de voz retienen recursos del sistema: no liberarlos deja el
 * microfono ocupado y el sintetizador vivo despues de salir de la pantalla.
 */
class AsistenteDeVoz(
    private val reconocedor: ReconocedorDeVoz,
    private val sintetizador: SintetizadorDeVoz,
    private val alCambiarEstado: (EstadoDeVoz) -> Unit
) {
    private var estado = EstadoDeVoz(disponible = reconocedor.disponible)

    private fun actualizar(nuevo: EstadoDeVoz) {
        estado = nuevo
        alCambiarEstado(nuevo)
    }

    /** Empieza a escuchar; [alTerminar] recibe la frase reconocida. */
    fun escuchar(alTerminar: (String) -> Unit) {
        sintetizador.detener()   // no escuchar mientras la app habla
        actualizar(estado.copy(escuchando = true, textoParcial = "", error = null))

        reconocedor.escuchar(
            onParcial = { parcial ->
                actualizar(estado.copy(textoParcial = parcial))
            },
            onResultado = { texto ->
                actualizar(estado.copy(escuchando = false, textoParcial = texto))
                alTerminar(texto)
            },
            onError = { mensaje ->
                actualizar(estado.copy(escuchando = false, error = mensaje))
            }
        )
    }

    fun detenerEscucha() {
        reconocedor.detener()
        actualizar(estado.copy(escuchando = false))
    }

    fun hablar(texto: String) = sintetizador.hablar(texto)

    fun callar() = sintetizador.detener()

    fun liberar() {
        reconocedor.liberar()
        sintetizador.liberar()
    }
}

/**
 * Crea el asistente de voz asociado al ciclo de vida de la composicion.
 *
 * Devuelve el asistente y su estado observable. El `DisposableEffect`
 * garantiza que los motores se liberen al salir de la pantalla.
 */
@Composable
fun rememberAsistenteDeVoz(): Pair<AsistenteDeVoz, EstadoDeVoz> {
    val context = LocalContext.current
    var estado by remember { mutableStateOf(EstadoDeVoz()) }

    val asistente = remember {
        AsistenteDeVoz(
            reconocedor = ReconocedorDeVoz(context),
            sintetizador = SintetizadorDeVoz(context),
            alCambiarEstado = { estado = it }
        )
    }

    DisposableEffect(Unit) {
        onDispose { asistente.liberar() }
    }

    return asistente to estado
}
