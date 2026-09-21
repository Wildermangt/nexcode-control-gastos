package com.nexcode.gastos.data.auth

import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * La identidad con la que la aplicacion habla con la nube.
 *
 * ### El problema que resuelve
 *
 * La sesion anonima le da dueno a los datos sin pedirle nada al usuario, y por
 * eso se eligio. Pero ese dueno **vive dentro de la instalacion**: Firebase le
 * asigna un uid distinto a cada instalacion y no hay forma de volver a
 * autenticarse como el una vez que se borran los datos de la aplicacion.
 *
 * Con solo sesion anonima, entonces, la replica en la nube no es una copia de
 * seguridad: si el telefono se pierde, la copia queda huerfana. Y dos
 * dispositivos nunca ven los mismos datos, porque cada uno replica su propio
 * subarbol.
 *
 * [enlazar] cierra ese hueco. `linkWithCredential` **conserva el uid** y le
 * anade un correo y una contrasena, asi que todo lo que ya estaba subido sigue
 * siendo del mismo dueno; lo unico que cambia es que ahora ese dueno se puede
 * demostrar desde otro telefono con [entrar].
 */
class CuentaDeUsuario(
    private val auth: FirebaseAuth,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Como esta identificado el usuario ahora mismo.
     *
     * Se mira el correo y no el metodo de acceso: una cuenta enlazada es
     * exactamente la que tiene uno.
     */
    sealed interface Estado {
        /** Hay dueno, pero solo existe en este telefono. */
        data object Anonima : Estado

        /** La copia se puede recuperar desde otro dispositivo. */
        data class Enlazada(val correo: String) : Estado
    }

    sealed interface Resultado {
        data object Exito : Resultado
        data class Fallo(val motivo: String) : Resultado
    }

    fun estado(): Estado {
        val correo = auth.currentUser?.email
        return if (correo.isNullOrBlank()) Estado.Anonima else Estado.Enlazada(correo)
    }

    /**
     * Le pone correo y contrasena a la sesion actual, sin cambiar de dueno.
     *
     * Si todavia no hay sesion se abre una anonima primero: da igual, porque el
     * enlace se hace sobre ella y conserva su uid. Lo que NO se puede hacer es
     * crear la cuenta por separado y luego "mover" los datos, que era la otra
     * alternativa: eso implicaria reescribir los cuarenta documentos bajo un uid
     * nuevo y perder cualquier cambio que llegara a mitad.
     */
    suspend fun enlazar(correo: String, clave: String): Resultado = withContext(dispatcher) {
        try {
            val usuario = auth.currentUser
                ?: auth.signInAnonymously().await().user
                ?: return@withContext Resultado.Fallo(
                    "No se pudo abrir la sesion. Revisa tu conexion."
                )

            usuario.linkWithCredential(EmailAuthProvider.getCredential(correo, clave)).await()
            Resultado.Exito
        } catch (e: Exception) {
            Resultado.Fallo(motivoDe(e, "enlazando la cuenta " + correo))
        }
    }

    /**
     * Entra en una cuenta ya creada, normalmente desde otro telefono.
     *
     * La sesion anonima que hubiera se queda atras. No se pierde nada
     * importante: lo unico que contenia era la siembra de demostracion de esta
     * instalacion, y quien llama a esto lo hace justo para traerse los datos de
     * verdad.
     */
    suspend fun entrar(correo: String, clave: String): Resultado = withContext(dispatcher) {
        try {
            auth.signInWithEmailAndPassword(correo, clave).await()
            Resultado.Exito
        } catch (e: Exception) {
            Resultado.Fallo(motivoDe(e, "entrando como " + correo))
        }
    }

    /**
     * Traduce el fallo a algo que se pueda leer en pantalla, dejando SIEMPRE el
     * error original en el registro.
     *
     * Sin esa linea de registro, "no se pudo" es indistinguible de un proveedor
     * sin habilitar en la consola, y diagnosticarlo cuesta horas. Ya paso dos
     * veces en este proyecto.
     */
    private fun motivoDe(e: Exception, contexto: String): String {
        Log.w(ETIQUETA, contexto + ": " + e::class.java.simpleName + ": " + e.message)

        // El motivo viaja en el CODIGO, no en el texto: el mensaje que trae la
        // excepcion esta redactado en ingles y para un desarrollador. Mirar solo
        // el texto dejaba este caso cayendo en el "no se pudo" generico.
        val codigo = (e as? FirebaseAuthException)?.errorCode.orEmpty()
        return when {
            e is FirebaseAuthWeakPasswordException ->
                "La contrasena es muy corta. Usa al menos seis caracteres."

            e is FirebaseAuthUserCollisionException ->
                "Ese correo ya tiene una cuenta. Usa \"Entrar en mi cuenta\"."

            e is FirebaseAuthInvalidUserException ->
                "No existe una cuenta con ese correo."

            e is FirebaseAuthInvalidCredentialsException ->
                "Correo o contrasena incorrectos."

            // El caso que aparece cuando el proveedor sigue apagado en la
            // consola de Firebase. Se nombra explicitamente porque el mensaje
            // crudo no le dice nada a nadie.
            codigo.contains("OPERATION_NOT_ALLOWED") ->
                "Falta habilitar el acceso por correo en la consola de Firebase."

            codigo.contains("NETWORK") ->
                "Sin conexion. Intentalo cuando vuelvas a tener red."

            else -> "No se pudo completar. Revisa tu conexion e intenta de nuevo."
        }
    }

    private companion object {
        const val ETIQUETA = "NexcodeCuenta"
    }
}
