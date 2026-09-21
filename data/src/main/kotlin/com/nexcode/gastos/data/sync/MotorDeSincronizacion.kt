package com.nexcode.gastos.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Mantiene sincronizadas la base del dispositivo y la replica en la nube.
 *
 * Las dos son autonomas y esa es la premisa del diseno: sin red la aplicacion
 * escribe en Room y funciona entera; si el telefono se pierde, los datos siguen
 * en la nube. Esta clase solo se ocupa de que vuelvan a coincidir cuando ambas
 * estan disponibles.
 *
 * El orden de la pasada no es arbitrario:
 *
 *  1. Se sube todo lo pendiente, borrados incluidos.
 *  2. Se bajan cuentas y categorias.
 *  3. Se **reconstruye** la traduccion de identificadores, porque el paso
 *     anterior pudo crear cuentas nuevas en esta base.
 *  4. Se bajan movimientos y pagos fijos, que dependen de las anteriores.
 *
 * Invertir 2 y 4 haria que un movimiento llegara antes que su cuenta y la clave
 * foranea lo rechazaria.
 */
class MotorDeSincronizacion(
    private val cuentas: AdaptadorDeCuentas,
    private val categorias: AdaptadorDeCategorias,
    private val movimientos: AdaptadorDeMovimientos,
    private val pagosFijos: AdaptadorDePagosFijos,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val marcas: MarcasDeSincronizacion,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Resultado de una pasada.
     *
     * Jerarquia sellada en vez de un booleano: la interfaz tiene que poder
     * distinguir "no habia nada que hacer" de "no se pudo", y decirlo.
     */
    sealed interface Resultado {
        data class Exito(val subidos: Int, val bajados: Int) : Resultado
        data object SinSesion : Resultado
        data class Fallo(val motivo: String) : Resultado
    }

    /** El catalogo va primero; los movimientos dependen de el. */
    private val catalogo get() = listOf(cuentas, categorias)
    private val dependientes get() = listOf(movimientos, pagosFijos)

    suspend fun sincronizar(): Resultado = withContext(dispatcher) {
        try {
            val uid = sesion() ?: return@withContext Resultado.SinSesion
            val raiz = firestore.collection(USUARIOS).document(uid)

            var subidos = 0
            var bajados = 0

            var referencias = construirReferencias()

            // 1. Subida completa.
            (catalogo + dependientes).forEach { adaptador ->
                subidos += adaptador.subir(
                    raiz.collection(adaptador.coleccion), firestore, referencias
                )
            }

            // 2. Bajada del catalogo.
            catalogo.forEach { bajados += bajar(it, raiz, referencias) }

            // 3. La traduccion de identificadores caduco: pudieron entrar
            //    cuentas o categorias nuevas en el paso anterior.
            referencias = construirReferencias()

            // 4. Bajada de lo que depende del catalogo.
            dependientes.forEach { bajados += bajar(it, raiz, referencias) }

            marcas.ultimaPasada = System.currentTimeMillis()
            Resultado.Exito(subidos, bajados)
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Fallo la pasada: " + e::class.java.simpleName + ": " + e.message)
            Resultado.Fallo(e.message ?: "fallo de sincronizacion")
        }
    }

    /** Cambios locales que la nube todavia no conoce. */
    suspend fun contarPendientes(): Int = withContext(dispatcher) {
        (catalogo + dependientes).sumOf { it.contarPendientes() }
    }

    /**
     * Si este usuario ya tiene datos en la nube. `null` cuando no se pudo
     * averiguar: sin red, sin sesion, o porque la consulta tardo demasiado.
     *
     * Existe por la siembra: un telefono nuevo se encuentra la base local
     * vacia y llena las cuatro tablas con los datos de demostracion, pero si
     * la nube ya tiene los de otro dispositivo, la primera pasada los baja
     * encima. Preguntar antes de sembrar es lo que evita esa duplicacion.
     *
     * Se lee **solo del servidor** a proposito. Con la fuente por defecto,
     * Firestore responderia desde su cache local —vacia en una instalacion
     * recien hecha— y la respuesta seria un "la nube no tiene nada" que no
     * consulto nada. Sin red, `Source.SERVER` lanza, y eso es justo lo que se
     * quiere: no se sabe, y quien pregunta decide.
     *
     * Basta con mirar las cuentas: sin cuentas no hay movimientos ni pagos
     * fijos que puedan bajar, y un documento es suficiente para responder.
     */
    suspend fun laNubeTieneDatos(): Boolean? = withContext(dispatcher) {
        withTimeoutOrNull(ESPERA_MAXIMA_MS) {
            try {
                val uid = sesion() ?: return@withTimeoutOrNull null
                val documentos = firestore.collection(USUARIOS).document(uid)
                    .collection(cuentas.coleccion)
                    .limit(1)
                    .get(Source.SERVER)
                    .await()
                !documentos.isEmpty
            } catch (e: Exception) {
                Log.w(
                    ETIQUETA,
                    "No se pudo mirar la nube antes de sembrar: " +
                        e::class.java.simpleName + ": " + e.message
                )
                null
            }
        }
    }

    /** Momento de la ultima pasada completa, o 0 si nunca hubo una. */
    fun ultimaPasada(): Long = marcas.ultimaPasada

    private suspend fun bajar(
        adaptador: AdaptadorDeTabla<*>,
        raiz: com.google.firebase.firestore.DocumentReference,
        referencias: AdaptadorDeTabla.Referencias
    ): Int {
        val bajada = adaptador.bajar(
            raiz.collection(adaptador.coleccion),
            marcas.ultimaBajada(adaptador.coleccion),
            referencias
        )
        marcas.guardarUltimaBajada(adaptador.coleccion, bajada.nuevaMarca)
        return bajada.aplicadas
    }

    /**
     * Sesion anonima.
     *
     * El usuario nunca escribe un correo ni una contrasena: la aplicacion se
     * comporta igual que antes de tener nube. Pero sus datos tienen dueno, y
     * las reglas de seguridad impiden que otro los lea.
     */
    private suspend fun sesion(): String? = try {
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
    } catch (e: Exception) {
        // Sin red, o sin proyecto configurado: se sigue operando en local.
        //
        // Se registra el motivo porque en pantalla solo cabe "sin conexion", y
        // sin esto no habria forma de distinguir una red caida de un proveedor
        // anonimo que quedo sin habilitar en la consola.
        Log.w(ETIQUETA, "No hubo sesion: " + e::class.java.simpleName + ": " + e.message)
        null
    }

    /** Tabla de equivalencias entre el id de este telefono y el uuid global. */
    private suspend fun construirReferencias(): AdaptadorDeTabla.Referencias {
        val todasLasCuentas = cuentas.todas()
        val todasLasCategorias = categorias.todas()

        return AdaptadorDeTabla.Referencias(
            uuidDeCuenta = todasLasCuentas.associate { it.id to it.sync.uuid },
            uuidDeCategoria = todasLasCategorias.associate { it.id to it.sync.uuid },
            idDeCuenta = todasLasCuentas.associate { it.sync.uuid to it.id },
            idDeCategoria = todasLasCategorias.associate { it.sync.uuid to it.id }
        )
    }

    private companion object {
        const val USUARIOS = "usuarios"
        const val ETIQUETA = "NexcodeSync"

        /**
         * Tope de la consulta previa a la siembra.
         *
         * La app no puede quedarse en blanco esperando a la nube: pasado este
         * tiempo se responde "no se sabe" y se siembra igual, que es el
         * comportamiento sin nube de toda la vida.
         */
        const val ESPERA_MAXIMA_MS = 12_000L
    }
}
