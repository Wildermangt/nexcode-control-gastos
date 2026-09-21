package com.nexcode.gastos.data.remote.ai

import android.util.Log
import com.nexcode.gastos.domain.ai.AdviceRequest
import com.nexcode.gastos.domain.ai.AdviceResponse
import com.nexcode.gastos.domain.ai.FinancialAdvisor
import com.nexcode.gastos.domain.ai.HerramientasFinancieras
import com.nexcode.gastos.domain.ai.ResultadoDeRegistro
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Agente financiero de Nexcode sobre la API REST de Gemini.
 *
 * Es un agente y no una simple llamada a un modelo: no se le manda el resumen
 * del mes ya cocinado esperando un parrafo. Se le declaran cinco funciones
 * sobre los datos del usuario y el modelo decide cuales llamar, en que orden y
 * cuantas veces, hasta que puede responder.
 *
 * Se habla con la API por HTTP y JSON en vez de usar el SDK oficial de Android,
 * y la razon es concreta. El SDK esta descontinuado y se quedo atras respecto
 * de la API: rechaza el rol con el que hoy se devuelve el resultado de una
 * funcion, y sobre todo descarta el `thoughtSignature` que los modelos Gemini 3
 * adjuntan a cada llamada y exigen de vuelta. Sin ese campo la conversacion se
 * corta con un 400 en el segundo turno, que es justo donde un agente empieza a
 * servir para algo. Aqui el JSON se construye a mano y la firma viaja intacta.
 *
 * Degradacion: sin clave configurada, o si la red falla, responde el [respaldo]
 * local de reglas. La aplicacion nunca se queda sin asistente.
 */
class GeminiAdvisor(
    private val herramientas: HerramientasFinancieras,
    private val respaldo: FinancialAdvisor,
    private val apiKey: String?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FinancialAdvisor {

    /** Cierto cuando hay clave y el agente puede operar. */
    val estaConfigurado: Boolean get() = !apiKey.isNullOrBlank()

    /** Error de la API con su codigo, para no tener que adivinarlo del texto. */
    private class ErrorDeApi(val codigo: Int, mensaje: String) : Exception(mensaje)

    /**
     * Intenta la conversacion recorriendo los modelos por orden.
     *
     * El nivel gratuito devuelve 503 "high demand" de forma aleatoria y por
     * modelo: en una misma sesion uno responde y el siguiente no. Se comprobo
     * lanzando la misma peticion contra seis modelos seguidos, y los que
     * fallaban cambiaban entre una ejecucion y otra.
     *
     * Por eso ante saturacion se cambia de modelo en vez de insistir sobre el
     * mismo, que es lo que haria un reintento normal y no arregla nada. Ante
     * cualquier otro error se corta: una clave invalida o una cuota agotada
     * fallan igual en todos los modelos, y probarlos solo alarga la espera.
     */
    override suspend fun requestAdvice(request: AdviceRequest): AdviceResponse =
        withContext(dispatcher) {
            if (!estaConfigurado) return@withContext respaldo.requestAdvice(request)

            var ultimo: Exception? = null

            for (modelo in MODELOS) {
                try {
                    return@withContext conversar(modelo, request)
                } catch (e: Exception) {
                    // Se registra porque en la pantalla solo cabe el motivo
                    // resumido, y sin el error original no hay forma de
                    // distinguir una clave mal pegada de un corte de red.
                    Log.w(ETIQUETA, "Fallo con " + modelo + ": " + e.message?.take(200))
                    ultimo = e
                    if (!esSaturacion(e)) break
                }
            }

            degradar(request, ultimo?.let { motivoDe(it) } ?: "El agente no respondio")
        }

    /**
     * Bucle del agente: preguntar, ejecutar las funciones que pida, repetir.
     *
     * Termina cuando el modelo deja de pedir funciones, o al agotar
     * [MAX_VUELTAS]. La cota no es decorativa: sin ella, un modelo que se
     * empecine en llamar funciones dejaria la pantalla cargando para siempre.
     */
    private suspend fun conversar(modelo: String, request: AdviceRequest): AdviceResponse {
        // Historial completo de la conversacion. Se reenvia entero en cada
        // vuelta porque la API no guarda estado entre peticiones.
        val contenidos = JSONArray().put(turno(ROL_USUARIO, mensajeInicial(request)))

        repeat(MAX_VUELTAS) {
            val respuesta = pedir(modelo, contenidos)
            val contenido = contenidoDe(respuesta)
                ?: return AdviceResponse(
                    message = SIN_TEXTO,
                    source = AdviceResponse.Source.REMOTE_AGENT
                )

            val llamadas = llamadasAFunciones(contenido)

            if (llamadas.isEmpty()) {
                return AdviceResponse(
                    message = textoDe(contenido).ifBlank { SIN_TEXTO },
                    source = AdviceResponse.Source.REMOTE_AGENT
                )
            }

            // El turno del modelo se devuelve TAL CUAL vino. Es lo que conserva
            // el thoughtSignature de cada llamada: reconstruirlo campo a campo
            // lo perderia y la API rechazaria el turno siguiente.
            contenidos.put(contenido)

            // Todas las respuestas viajan en un unico turno. Partirlas en
            // varios hace que el modelo deje de pedir funciones en paralelo.
            val respuestas = JSONArray()
            llamadas.forEach { respuestas.put(ejecutar(it)) }
            contenidos.put(
                JSONObject().put("role", ROL_USUARIO).put("parts", respuestas)
            )
        }

        return degradar(request, "El agente tardo demasiado en concluir")
    }

    // -----------------------------------------------------------------
    // Transporte
    // -----------------------------------------------------------------

    /**
     * Una peticion a la API.
     *
     * La clave viaja en la cabecera y no en la URL: asi no queda escrita en
     * registros de servidores intermedios ni en trazas de red.
     */
    private fun pedir(modelo: String, contenidos: JSONArray): JSONObject {
        val cuerpo = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", INSTRUCCIONES)))
            )
            .put("contents", contenidos)
            .put(
                "tools",
                JSONArray().put(JSONObject().put("function_declarations", CATALOGO_DE_FUNCIONES))
            )
            .toString()

        val conexion = (URL(BASE + modelo + ":generateContent").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MILIS
                readTimeout = TIMEOUT_MILIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey.orEmpty())
            }

        try {
            conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }

            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (codigo !in 200..299) {
                throw ErrorDeApi(codigo, mensajeDeError(texto, codigo))
            }
            return JSONObject(texto)
        } finally {
            conexion.disconnect()
        }
    }

    /** Extrae el mensaje del cuerpo de error, o describe el codigo si no lo trae. */
    private fun mensajeDeError(cuerpo: String, codigo: Int): String = try {
        JSONObject(cuerpo).getJSONObject("error").optString("message", "HTTP " + codigo)
    } catch (e: Exception) {
        "HTTP " + codigo
    }

    // -----------------------------------------------------------------
    // Lectura de la respuesta
    // -----------------------------------------------------------------

    /** Contenido del primer candidato, o null si la respuesta viene vacia. */
    private fun contenidoDe(respuesta: JSONObject): JSONObject? =
        respuesta.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")

    private fun llamadasAFunciones(contenido: JSONObject): List<JSONObject> {
        val partes = contenido.optJSONArray("parts") ?: return emptyList()
        val llamadas = mutableListOf<JSONObject>()

        // Ciclo explicito con indice: JSONArray es una clase de la plataforma y
        // no ofrece las funciones de orden superior de las colecciones Kotlin.
        for (i in 0 until partes.length()) {
            partes.optJSONObject(i)?.optJSONObject("functionCall")?.let { llamadas.add(it) }
        }
        return llamadas
    }

    private fun textoDe(contenido: JSONObject): String {
        val partes = contenido.optJSONArray("parts") ?: return ""
        val texto = StringBuilder()

        for (i in 0 until partes.length()) {
            val fragmento = partes.optJSONObject(i)?.optString("text").orEmpty()
            if (fragmento.isNotBlank()) {
                if (texto.isNotEmpty()) texto.append('\n')
                texto.append(fragmento)
            }
        }
        return texto.toString().trim()
    }

    // -----------------------------------------------------------------
    // Ejecucion de las funciones
    // -----------------------------------------------------------------

    /**
     * Ejecuta una funcion y la empaqueta como respuesta.
     *
     * Un fallo se devuelve dentro del resultado, nunca como una excepcion que
     * corte el bucle: el modelo tiene que poder enterarse de que la consulta
     * fallo y decirselo al usuario en vez de inventarse la cifra.
     */
    private suspend fun ejecutar(llamada: JSONObject): JSONObject {
        val nombre = llamada.optString("name")
        val argumentos = llamada.optJSONObject("args") ?: JSONObject()

        val salida = try {
            when (nombre) {
                RESUMEN -> jsonResumen()
                TENDENCIA -> jsonTendencia()
                PAGOS -> jsonPagos()
                BUSCAR -> jsonBusqueda(argumentos)
                REGISTRAR -> jsonRegistro(argumentos)
                else -> JSONObject().put("error", "La funcion " + nombre + " no existe")
            }
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "fallo desconocido")
        }

        return JSONObject().put(
            "functionResponse",
            JSONObject().put("name", nombre).put("response", salida)
        )
    }

    /** Lee un argumento de texto, descartando los vacios y el literal "null". */
    private fun texto(argumentos: JSONObject, clave: String): String? =
        argumentos.optString(clave).trim().takeIf { it.isNotBlank() && it != "null" }

    // -----------------------------------------------------------------
    // Resultados de las funciones
    //
    // Van en JSON y con las cifras YA formateadas en pesos. Si le llegaran los
    // centavos en crudo, el agente los leeria como pesos y multiplicaria por
    // cien todo lo que dijera.
    // -----------------------------------------------------------------

    private suspend fun jsonResumen(): JSONObject {
        val resumen = herramientas.resumenDelMes()
        val categorias = JSONArray()
        resumen.categorias.forEach {
            categorias.put(
                JSONObject()
                    .put("nombre", it.nombre)
                    .put("total", it.total.format())
                    .put("movimientos", it.movimientos)
                    .put("porcentaje", it.porcentaje)
            )
        }
        return JSONObject()
            .put("periodo", resumen.periodo)
            .put("ingresos", resumen.ingresos.format())
            .put("gastos", resumen.gastos.format())
            .put("saldo", resumen.saldo.format())
            .put(
                "tasa_de_ahorro",
                resumen.tasaDeAhorro?.let { Math.round(it * 100).toString() + "%" }
                    ?: JSONObject.NULL
            )
            .put("categorias", categorias)
    }

    private suspend fun jsonTendencia(): JSONObject {
        val tendencia = herramientas.tendenciaDelGasto()
        val serie = JSONArray()
        tendencia.serie.forEach {
            serie.put(
                JSONObject()
                    .put("mes", it.mes.nombreCorto() + " " + it.mes.anio)
                    .put("gastos", it.gastos.format())
                    .put("ingresos", it.ingresos.format())
                    .put("movimientos", it.movimientos)
            )
        }
        return JSONObject()
            .put("serie", serie)
            .put("promedio_meses_cerrados", tendencia.promedioMensual.format())
            .put("ritmo_diario", tendencia.promedioDiario.format())
            .put("proyeccion_al_cierre", tendencia.proyeccionCierre.format())
            .put("dias_transcurridos", tendencia.diasTranscurridos)
            .put("dias_del_mes", tendencia.diasDelMes)
            .put(
                "variacion_vs_mes_anterior",
                tendencia.variacionEnPorcentaje?.let { it.toString() + "%" } ?: JSONObject.NULL
            )
    }

    private suspend fun jsonPagos(): JSONObject {
        val pagos = JSONArray()
        herramientas.pagosPendientes().forEach {
            pagos.put(
                JSONObject()
                    .put("nombre", it.nombre)
                    .put("monto", it.monto.format())
                    .put("dias_para_vencer", it.diasParaVencer)
                    .put("vencido", it.estaVencido)
            )
        }
        return JSONObject().put("pagos", pagos)
    }

    private suspend fun jsonBusqueda(argumentos: JSONObject): JSONObject {
        val limite = texto(argumentos, "limite")?.toIntOrNull()
            ?: HerramientasFinancieras.LIMITE_BUSQUEDA

        val encontrados = herramientas.buscarMovimientos(
            texto = texto(argumentos, "texto"),
            categoria = texto(argumentos, "categoria"),
            limite = limite
        )

        val lista = JSONArray()
        encontrados.forEach {
            lista.put(
                JSONObject()
                    .put("titulo", it.title)
                    .put("monto", it.amount.format())
                    .put("tipo", it.type.code)
                    .put("fecha", it.day.toString())
                    .put("nota", it.note ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("encontrados", encontrados.size)
            .put("movimientos", lista)
    }

    private suspend fun jsonRegistro(argumentos: JSONObject): JSONObject {
        val resultado = herramientas.registrarGasto(
            monto = texto(argumentos, "monto"),
            titulo = texto(argumentos, "titulo"),
            categoria = texto(argumentos, "categoria")
        )
        return when (resultado) {
            is ResultadoDeRegistro.Exito -> JSONObject()
                .put("registrado", true)
                .put("id", resultado.id)
                .put("detalle", resultado.descripcion)

            is ResultadoDeRegistro.Rechazado -> JSONObject()
                .put("registrado", false)
                .put("motivo", resultado.motivo)
        }
    }

    // -----------------------------------------------------------------

    private fun turno(rol: String, texto: String): JSONObject =
        JSONObject()
            .put("role", rol)
            .put("parts", JSONArray().put(JSONObject().put("text", texto)))

    /**
     * Primer mensaje.
     *
     * Solo lleva la pregunta y el periodo. El estado financiero NO se adjunta a
     * proposito: si se le entregara masticado, el modelo no tendria motivo para
     * llamar a las funciones y dejaria de ser un agente.
     */
    private fun mensajeInicial(request: AdviceRequest): String {
        val pregunta = request.userQuestion
        return if (pregunta != null) {
            "Periodo actual: " + request.periodLabel + ".\n\nPregunta del usuario: " + pregunta
        } else {
            "Periodo actual: " + request.periodLabel +
                ".\n\nEl usuario abrio el asistente sin preguntar nada concreto. " +
                "Consulta su situacion y dale una lectura breve de como va el mes."
        }
    }

    /** Solo la saturacion justifica probar otro modelo. */
    private fun esSaturacion(e: Exception): Boolean =
        e is ErrorDeApi && (e.codigo == 503 || e.codigo == 500)

    /** Traduce el fallo a algo que el usuario pueda entender y accionar. */
    private fun motivoDe(e: Exception): String = when {
        e !is ErrorDeApi -> "Sin conexion con el agente"
        e.codigo == 400 -> "El agente rechazo la consulta"
        e.codigo == 401 || e.codigo == 403 -> "La clave de la API no es valida"
        e.codigo == 429 -> "Se agoto la cuota del agente"
        e.codigo == 503 || e.codigo == 500 -> "El servicio esta saturado, intenta en un momento"
        else -> "El agente no pudo responder"
    }

    /** Respuesta del motor local, avisando de que el agente no contesto. */
    private suspend fun degradar(request: AdviceRequest, motivo: String): AdviceResponse {
        val local = respaldo.requestAdvice(request)
        return local.copy(message = "(" + motivo + ") " + local.message)
    }

    private companion object {
        const val ETIQUETA = "NexcodeAgente"

        const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

        /** La API vigente solo admite USER y ASSISTANT; el rol "function" da 400. */
        const val ROL_USUARIO = "user"

        const val TIMEOUT_MILIS = 60_000

        /**
         * Modelos por orden de preferencia.
         *
         * Flash y no Pro: las respuestas son cortas, el usuario espera en una
         * pantalla del telefono y el nivel gratuito da cuota suficiente para
         * usar la aplicacion a diario.
         *
         * El primero es un alias y no una version fijada. Google retira los
         * modelos antiguos y a partir de ese dia la peticion devuelve 404: es
         * exactamente lo que le paso a gemini-2.0-flash mientras se escribia
         * esto. El alias apunta siempre al Flash vigente; los dos siguientes
         * son la red de seguridad cuando ese esta saturado.
         */
        val MODELOS = listOf(
            "gemini-flash-latest",
            "gemini-3.7-flash",
            "gemini-flash-lite-latest"
        )

        /**
         * Tope de vueltas. Cuatro alcanzan de sobra: la pregunta mas exigente
         * necesita el resumen, la tendencia y una busqueda, y sobra una vuelta
         * para concluir.
         */
        const val MAX_VUELTAS = 4

        const val SIN_TEXTO = "No consegui formular una respuesta. Intenta preguntarlo de otra forma."

        const val RESUMEN = "consultar_resumen_del_mes"
        const val TENDENCIA = "consultar_tendencia"
        const val PAGOS = "consultar_pagos_pendientes"
        const val BUSCAR = "buscar_movimientos"
        const val REGISTRAR = "registrar_gasto"

        /**
         * Contexto y limites del agente.
         *
         * Las tres ultimas reglas son las que lo convierten en un componente de
         * la aplicacion y no en un asistente generico: sin ellas responderia
         * sobre cualquier tema y rellenaria con estimaciones los huecos que no
         * pueda consultar.
         */
        val INSTRUCCIONES = """
            Eres el asistente financiero de Nexcode, una aplicacion de control de
            gastos personales para estudiantes universitarios colombianos. Hablas
            en espanol de Colombia, de tu, y con frases cortas.

            Los importes estan en pesos colombianos y te llegan ya formateados
            (por ejemplo 1.250.000). Repitelos tal cual: no los conviertas, no les
            pongas decimales y no los redondees.

            Como trabajas:
            - Nunca supongas cifras. Si necesitas un dato, llama a una funcion.
            - Puedes llamar a varias funciones antes de responder si hace falta.
            - Si el usuario pide registrar un gasto, usa registrar_gasto y luego
              confirmale exactamente lo que quedo guardado.

            Limites que debes respetar:
            - Solo hablas de las finanzas personales de este usuario y del uso de
              la aplicacion. Si te preguntan por cualquier otro tema —politica,
              deportes, programacion, consejos de inversion en bolsa, noticias—
              dilo con naturalidad, en una frase, y ofrece ayudarle con sus
              gastos. No intentes responder igualmente.
            - Si una funcion devuelve vacio o falla, dilo. No rellenes el hueco
              con una estimacion.
            - No das recomendaciones de inversion ni asesoria legal o tributaria.
        """.trimIndent()

        /** Declaracion de un parametro de texto. */
        private fun parametro(descripcion: String): JSONObject =
            JSONObject().put("type", "string").put("description", descripcion)

        private fun funcion(
            nombre: String,
            descripcion: String,
            parametros: Map<String, String> = emptyMap(),
            obligatorios: List<String> = emptyList()
        ): JSONObject {
            val propiedades = JSONObject()
            parametros.forEach { (clave, texto) -> propiedades.put(clave, parametro(texto)) }

            val esquema = JSONObject()
                .put("type", "object")
                .put("properties", propiedades)
            if (obligatorios.isNotEmpty()) {
                esquema.put("required", JSONArray(obligatorios))
            }

            return JSONObject()
                .put("name", nombre)
                .put("description", descripcion)
                .put("parameters", esquema)
        }

        /** Catalogo de funciones tal como lo ve el modelo. */
        val CATALOGO_DE_FUNCIONES: JSONArray = JSONArray()
            .put(
                funcion(
                    RESUMEN,
                    "Ingresos, gastos, saldo y reparto por categoria del mes en curso. " +
                        "Usala para cualquier pregunta sobre como va el mes."
                )
            )
            .put(
                funcion(
                    TENDENCIA,
                    "Serie de gasto de los ultimos seis meses con el promedio, el ritmo diario " +
                        "y la proyeccion al cierre. Usala para comparar con meses anteriores o " +
                        "para responder si el usuario va a terminar bien el mes."
                )
            )
            .put(
                funcion(
                    PAGOS,
                    "Pagos fijos vencidos o proximos a vencer, con los dias que faltan."
                )
            )
            .put(
                funcion(
                    BUSCAR,
                    "Busca movimientos por texto del titulo o de la nota, por categoria, o los " +
                        "mas recientes si no se indica ningun filtro.",
                    mapOf(
                        "texto" to "Texto a buscar en el titulo o la nota",
                        "categoria" to "Nombre de la categoria, tal como lo diria el usuario",
                        "limite" to "Cuantos movimientos devolver como maximo, hasta 15"
                    )
                )
            )
            .put(
                funcion(
                    REGISTRAR,
                    "Registra un gasto nuevo en la cuenta del usuario. Es la unica funcion que " +
                        "modifica datos: usala solo cuando el usuario lo pida.",
                    mapOf(
                        "monto" to "Monto en pesos, como lo dijo el usuario: 25.000, 25000 o 12.500,50",
                        "titulo" to "Descripcion corta del gasto, por ejemplo Almuerzo",
                        "categoria" to "Nombre de la categoria, si el usuario la menciono"
                    ),
                    listOf("monto")
                )
            )
    }
}
