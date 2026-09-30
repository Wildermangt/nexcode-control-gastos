package com.nexcode.gastos.automatizacion

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Datos que viajan a la automatizacion cuando se registra un movimiento.
 *
 * Hay dos campos para el importe a proposito:
 *  - [monto] es el texto ya formateado ("$ 85.000") y es el que se lee en el
 *    correo;
 *  - [montoNumero] son los pesos como entero (85000) y es el que se escribe en
 *    la hoja de calculo.
 *
 * Si se mandara solo el texto, Google Sheets interpretaria el punto de los
 * miles como separador decimal y guardaria 85 en vez de 85000: la columna
 * dejaria de poder sumarse.
 */
data class MovimientoParaAviso(
    val fechaHora: String,
    val tipo: String,
    val titulo: String,
    val monto: String,
    val montoNumero: Long,
    val categoria: String,
    val cuenta: String,
    val nota: String,
    val saldoCuenta: String,
    val correo: String,
    val dispositivo: String = Build.MODEL + " - Android " + Build.VERSION.RELEASE
)

/**
 * Avisa a Make cada vez que se registra un movimiento.
 *
 * Se usa `HttpURLConnection` del propio JDK y no una libreria de red: es una
 * sola peticion POST, no justifica sumar Retrofit ni OkHttp al APK.
 *
 * **El aviso nunca puede tumbar el registro del gasto.** Por eso [enviar]
 * devuelve un booleano en vez de lanzar: si el telefono esta sin datos, el
 * movimiento ya quedo guardado en la base local y lo unico que se pierde es la
 * copia en la nube. Quien llama decide si le importa, y hoy no le importa.
 */
object AvisoDeMovimiento {

    /**
     * Disparador del escenario `NEXCODE Gastos - Registro de movimiento`.
     * Es un webhook publico de Make: no lleva secretos, solo recibe.
     */
    const val URL_WEBHOOK = "https://hook.us2.make.com/o3hnnopg1muwhx5fgazow0v6da3onnew"

    /** A donde llega el correo cuando la sesion no esta enlazada a una cuenta. */
    const val CORREO_POR_DEFECTO = "wildermangt@gmail.com"

    private const val ETIQUETA = "AvisoDeMovimiento"
    private const val ESPERA_MS = 8_000

    suspend fun enviar(movimiento: MovimientoParaAviso): Boolean = withContext(Dispatchers.IO) {
        try {
            val conexion = (URL(URL_WEBHOOK).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = ESPERA_MS
                readTimeout = ESPERA_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            OutputStreamWriter(conexion.outputStream, Charsets.UTF_8).use { salida ->
                salida.write(aJson(movimiento))
            }

            val codigo = conexion.responseCode
            conexion.disconnect()

            val correcto = codigo in 200..299
            if (correcto) {
                Log.i(ETIQUETA, "Movimiento enviado a Make: " + movimiento.titulo)
            } else {
                Log.w(ETIQUETA, "Make respondio " + codigo)
            }
            correcto
        } catch (e: Exception) {
            // Sin red o con el escenario apagado. El gasto ya esta guardado.
            Log.w(ETIQUETA, "No se pudo avisar a Make: " + e.message)
            false
        }
    }

    /**
     * Arma el JSON a mano.
     *
     * Son diez campos de texto: traer un serializador para esto seria mas
     * codigo del que ahorra. Lo unico delicado es escapar las comillas y los
     * saltos de linea que el usuario pueda haber escrito en la nota.
     */
    private fun aJson(m: MovimientoParaAviso): String = buildString {
        append('{')
        campo("fechaHora", m.fechaHora); append(',')
        campo("tipo", m.tipo); append(',')
        campo("titulo", m.titulo); append(',')
        campo("monto", m.monto); append(',')
        append("\"montoNumero\":").append(m.montoNumero); append(',')
        campo("categoria", m.categoria); append(',')
        campo("cuenta", m.cuenta); append(',')
        campo("nota", m.nota); append(',')
        campo("saldoCuenta", m.saldoCuenta); append(',')
        campo("correo", m.correo); append(',')
        campo("dispositivo", m.dispositivo)
        append('}')
    }

    private fun StringBuilder.campo(clave: String, valor: String) {
        append('"').append(clave).append("\":\"").append(escapar(valor)).append('"')
    }

    private fun escapar(texto: String): String = buildString(texto.length) {
        for (c in texto) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append(' ') else append(c)
            }
        }
    }
}
