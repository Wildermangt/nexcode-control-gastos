package com.nexcode.gastos.cuenta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexcode.gastos.data.auth.CuentaDeUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo que la pantalla necesita saber sobre la identidad del usuario.
 *
 * [correo] nulo significa sesion anonima: hay copia en la nube, pero atada a
 * esta instalacion.
 */
data class EstadoDeCuenta(
    val hayNube: Boolean = false,
    val correo: String? = null,
    val procesando: Boolean = false,
    val mensaje: String? = null
) {
    val enlazada: Boolean get() = correo != null
}

/**
 * Protege la copia en la nube y permite recuperarla en otro telefono.
 *
 * Son las dos caras de lo mismo: [proteger] le pone correo y contrasena a la
 * sesion anonima de este telefono, y [entrar] usa esas credenciales desde otro
 * para traerse los datos.
 */
class CuentaViewModel(
    private val cuenta: CuentaDeUsuario?,
    private val reemplazarPorLaNube: suspend () -> Boolean
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDeCuenta(hayNube = cuenta != null))
    val estado: StateFlow<EstadoDeCuenta> = _estado.asStateFlow()

    init {
        refrescar()
    }

    fun refrescar() {
        val activa = cuenta ?: return
        val correo = (activa.estado() as? CuentaDeUsuario.Estado.Enlazada)?.correo
        _estado.update { it.copy(correo = correo) }
    }

    fun descartarMensaje() = _estado.update { it.copy(mensaje = null) }

    /** Enlaza la sesion de este telefono a un correo, conservando los datos. */
    fun proteger(correo: String, clave: String) {
        val activa = cuenta ?: return
        _estado.update { it.copy(procesando = true, mensaje = null) }

        viewModelScope.launch {
            val mensaje = when (val resultado = activa.enlazar(correo.trim(), clave)) {
                CuentaDeUsuario.Resultado.Exito ->
                    "Listo. Tu copia ya se puede recuperar desde otro telefono."

                is CuentaDeUsuario.Resultado.Fallo -> resultado.motivo
            }
            _estado.update { it.copy(procesando = false, mensaje = mensaje) }
            refrescar()
        }
    }

    /**
     * Entra en una cuenta existente y deja este telefono con SUS datos.
     *
     * El reemplazo va despues de entrar y solo si entrar funciono: borrar
     * primero dejaria el telefono vacio si la contrasena estuviera mal.
     */
    fun entrar(correo: String, clave: String) {
        val activa = cuenta ?: return
        _estado.update { it.copy(procesando = true, mensaje = null) }

        viewModelScope.launch {
            val mensaje = when (val resultado = activa.entrar(correo.trim(), clave)) {
                CuentaDeUsuario.Resultado.Exito ->
                    if (reemplazarPorLaNube()) {
                        "Listo. Los datos de tu cuenta ya estan en este telefono."
                    } else {
                        "Entraste, pero no se pudieron traer los datos. " +
                            "Pulsa \"Sincronizar ahora\"."
                    }

                is CuentaDeUsuario.Resultado.Fallo -> resultado.motivo
            }
            _estado.update { it.copy(procesando = false, mensaje = mensaje) }
            refrescar()
        }
    }
}
