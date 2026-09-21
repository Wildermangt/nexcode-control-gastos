package com.nexcode.gastos.sincronizacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexcode.gastos.data.sync.MotorDeSincronizacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la replica, tal como lo ve el usuario.
 *
 * [hayNube] es falso cuando el proyecto se compilo sin `google-services.json`.
 * En ese caso la pantalla lo dice en vez de fingir que sincroniza.
 */
data class EstadoDeSincronizacion(
    val hayNube: Boolean = false,
    val sincronizando: Boolean = false,
    val pendientes: Int = 0,
    val ultimaPasada: Long = 0L,
    val mensaje: String? = null
) {
    val alDia: Boolean get() = hayNube && pendientes == 0 && ultimaPasada > 0L
}

/**
 * Permite ver el estado de la replica y forzarla a mano.
 *
 * El boton manual existe por una razon practica: el trabajo periodico corre
 * cada quince minutos —el minimo que admite WorkManager— y eso es demasiado
 * esperar cuando se esta enseniando la aplicacion a alguien.
 */
class SincronizacionViewModel(
    private val motor: MotorDeSincronizacion?
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDeSincronizacion(hayNube = motor != null))
    val estado: StateFlow<EstadoDeSincronizacion> = _estado.asStateFlow()

    init {
        refrescar()
    }

    /** Relee el conteo de pendientes sin tocar la red. */
    fun refrescar() {
        val activo = motor ?: return
        viewModelScope.launch {
            _estado.update {
                it.copy(
                    pendientes = activo.contarPendientes(),
                    ultimaPasada = activo.ultimaPasada()
                )
            }
        }
    }

    fun sincronizarAhora() {
        val activo = motor ?: return
        _estado.update { it.copy(sincronizando = true, mensaje = null) }

        viewModelScope.launch {
            val mensaje = when (val resultado = activo.sincronizar()) {
                is MotorDeSincronizacion.Resultado.Exito ->
                    "Listo: " + resultado.subidos + " enviados y " +
                        resultado.bajados + " recibidos."

                MotorDeSincronizacion.Resultado.SinSesion ->
                    "Sin conexion. Tus datos siguen guardados en el telefono."

                is MotorDeSincronizacion.Resultado.Fallo ->
                    "No se pudo sincronizar: " + resultado.motivo
            }

            _estado.update {
                it.copy(
                    sincronizando = false,
                    pendientes = activo.contarPendientes(),
                    ultimaPasada = activo.ultimaPasada(),
                    mensaje = mensaje
                )
            }
        }
    }
}
