package com.nexcode.gastos.data.remote.ai

import com.nexcode.gastos.domain.ai.AdviceRequest
import com.nexcode.gastos.domain.ai.AdviceResponse
import com.nexcode.gastos.domain.ai.FinancialAdvisor
import com.nexcode.gastos.domain.model.Tendencia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asistente financiero de Nexcode.
 *
 * Cumple el contrato [FinancialAdvisor] del dominio de dos formas:
 *
 *  1. SIMULACION LOCAL (por defecto): un motor de reglas analiza el balance y
 *     las categorias y devuelve consejos. Funciona sin internet y sin costo.
 *  2. AGENTE REMOTO: si se configura [AiConfig.webhookUrl], envia el mismo
 *     payload JSON a un webhook (por ejemplo un agente de voz de Retell AI)
 *     y devuelve su respuesta.
 *
 * La app no cambia en ningun caso: el dominio solo ve la interfaz.
 */
class NexcodeAIService(
    private val config: AiConfig = AiConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FinancialAdvisor {

    /** Configuracion del agente. Con webhookUrl nulo se usa la simulacion local. */
    data class AiConfig(
        val webhookUrl: String? = null,
        val agentId: String = "nexcode-finance-agent",
        val apiKey: String? = null,
        val timeoutMillis: Int = 10_000
    )

    override suspend fun requestAdvice(request: AdviceRequest): AdviceResponse =
        withContext(dispatcher) {
            val endpoint = config.webhookUrl

            // Elvis + isNullOrBlank: sin endpoint configurado se responde local.
            if (endpoint.isNullOrBlank()) {
                return@withContext simulateAdvice(request)
            }

            try {
                sendToAgent(endpoint, buildPayload(request))
            } catch (e: Exception) {
                // Degradacion elegante: si el agente falla, el usuario igual
                // recibe consejos en vez de un error en pantalla.
                val local = simulateAdvice(request)
                local.copy(
                    message = "(Sin conexion con el agente) " + local.message
                )
            }
        }

    /**
     * Construye el JSON que viaja al agente conversacional.
     * Es publico a proposito: permite inspeccionarlo en pruebas y documentarlo
     * en el informe sin necesidad de tener el servicio remoto montado.
     */
    fun buildPayload(request: AdviceRequest): String {
        val categories = JSONArray()
        request.topCategories.forEach { summary ->
            categories.put(
                JSONObject()
                    .put("nombre", summary.displayName)
                    .put("total", summary.total.toMajorUnits())
                    .put("movimientos", summary.transactionCount)
                    .put("porcentaje", Math.round(summary.percentage * 100))
            )
        }

        return JSONObject()
            .put("agent_id", config.agentId)
            .put("periodo", request.periodLabel)
            .put(
                "resumen",
                JSONObject()
                    .put("ingresos", request.balance.income.toMajorUnits())
                    .put("gastos", request.balance.expense.toMajorUnits())
                    .put("saldo", request.balance.total.toMajorUnits())
                    .put("tasa_ahorro", request.balance.savingsRate ?: JSONObject.NULL)
            )
            .put("categorias", categories)
            .put("tendencia", buildTendencia(request))
            .put("pregunta_usuario", request.userQuestion ?: JSONObject.NULL)
            .toString()
    }

    /**
     * Serie mensual y sus indicadores, en el formato que espera el agente.
     *
     * Safe call: si el caso de uso no adjunto analitica se envia `null` y el
     * agente responde igual, solo que sin comparaciones historicas.
     */
    private fun buildTendencia(request: AdviceRequest): Any {
        val tendencia = request.tendencia ?: return JSONObject.NULL

        val serie = JSONArray()
        tendencia.serie.forEach { punto ->
            serie.put(
                JSONObject()
                    .put("mes", punto.mes.nombreCorto() + " " + punto.mes.anio)
                    .put("ingresos", punto.ingresos.toMajorUnits())
                    .put("gastos", punto.gastos.toMajorUnits())
                    .put("movimientos", punto.movimientos)
            )
        }

        return JSONObject()
            .put("serie", serie)
            .put("promedio_mensual", tendencia.promedioMensual.toMajorUnits())
            .put("promedio_diario", tendencia.promedioDiario.toMajorUnits())
            .put("proyeccion_cierre", tendencia.proyeccionCierre.toMajorUnits())
            .put("variacion_porcentual", tendencia.variacionEnPorcentaje ?: JSONObject.NULL)
    }

    /** POST al webhook del agente. Requiere el permiso INTERNET. */
    private fun sendToAgent(endpoint: String, payload: String): AdviceResponse {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.timeoutMillis
            readTimeout = config.timeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            config.apiKey?.let { setRequestProperty("Authorization", "Bearer " + it) }
        }

        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                throw IllegalStateException("El agente respondio " + connection.responseCode)
            }

            val json = JSONObject(body)
            val tipsJson = json.optJSONArray("tips")
            val tips = buildList {
                if (tipsJson != null) {
                    for (i in 0 until tipsJson.length()) add(tipsJson.getString(i))
                }
            }

            return AdviceResponse(
                message = json.optString("message", "Sin respuesta del agente"),
                tips = tips,
                source = AdviceResponse.Source.REMOTE_AGENT
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Motor de reglas local.
     * Recorre las categorias con funciones de orden superior para construir
     * la lista de consejos.
     */
    private fun simulateAdvice(request: AdviceRequest): AdviceResponse {
        val balance = request.balance

        if (balance.income.isZero && balance.expense.isZero) {
            return AdviceResponse(
                message = "Todavia no tengo movimientos de este mes. Registra tu primer gasto y te digo como vas.",
                tips = listOf("Toca el boton naranja para agregar un gasto en menos de cinco segundos.")
            )
        }

        val tips = buildList {
            val savings = balance.savingsRate

            when {
                savings == null ->
                    add("Aun no registras ingresos este mes: agrega tu salario para medir tu capacidad de ahorro.")

                savings < 0f ->
                    add("Estas gastando mas de lo que ingresas. Recorta primero la categoria mas alta.")

                savings < 0.10f ->
                    add("Ahorras menos del 10%. La meta sana esta entre el 15% y el 20% del ingreso.")

                savings >= 0.20f ->
                    add("Vas muy bien: estas ahorrando mas del 20% de tus ingresos este mes.")

                else ->
                    add("Tu ahorro va en camino. Sube un punto porcentual y notaras la diferencia al cierre del semestre.")
            }

            request.topCategories.firstOrNull()?.let { top ->
                if (top.percentage > 0.40f) {
                    add(
                        top.displayName + " concentra el " +
                            Math.round(top.percentage * 100) + "% de tus gastos. Ahi esta tu mayor palanca de ahorro."
                    )
                }
            }

            request.topCategories
                .filter { it.transactionCount >= 10 }
                .forEach {
                    add(
                        "Tienes " + it.transactionCount + " movimientos en " + it.displayName +
                            ": suelen ser gastos pequenos que se acumulan sin que los notes."
                    )
                }

            addAll(consejosDeTendencia(request.tendencia))
        }

        val header = "Este mes ingresaste " + balance.income.format() +
            " y gastaste " + balance.expense.format() +
            ". Tu saldo es " + balance.total.format(withSign = true) + "."

        // Elvis: si el usuario pregunto algo, se le responde primero.
        val question = request.userQuestion
        val message = if (question != null) {
            "Sobre \"" + question + "\": " + header
        } else {
            header
        }

        return AdviceResponse(
            message = message,
            tips = tips,
            source = AdviceResponse.Source.LOCAL_SIMULATION
        )
    }

    /**
     * Lectura de la analitica historica.
     *
     * Son las unicas reglas que no miran el mes en curso sino su relacion con
     * los anteriores: sin serie, un gasto de 400.000 no es alto ni bajo,
     * simplemente es 400.000.
     */
    private fun consejosDeTendencia(tendencia: Tendencia?): List<String> {
        // Sin analitica, o con un solo mes de historia, no hay nada que comparar.
        if (tendencia == null || !tendencia.hayDatos) return emptyList()

        return buildList {
            tendencia.variacionEnPorcentaje?.let { variacion ->
                when {
                    variacion >= UMBRAL_VARIACION ->
                        add(
                            "Llevas un " + variacion + "% mas de gasto que el mes pasado. " +
                                "Todavia estas a tiempo de corregir el ritmo."
                        )

                    variacion <= -UMBRAL_VARIACION ->
                        add(
                            "Vas gastando un " + (-variacion) + "% menos que el mes pasado. " +
                                "Si sostienes el ritmo, cierras el mes mejor que el anterior."
                        )
                }
            }

            if (tendencia.promedioMensual.isPositive && tendencia.superaraElPromedio) {
                add(
                    "Al ritmo de " + tendencia.promedioDiario.format() + " diarios cerrarias el mes en " +
                        tendencia.proyeccionCierre.format() + ", por encima de tu promedio de " +
                        tendencia.promedioMensual.format() + "."
                )
            }

            // El mes mas caro de la ventana: contexto que una sola foto no da.
            tendencia.serie
                .filter { !it.estaVacio }
                .maxByOrNull { it.gastos.cents }
                ?.takeIf { it.mes != tendencia.mesActual?.mes }
                ?.let { pico ->
                    add(
                        "Tu mes mas caro de los ultimos " + tendencia.serie.size + " fue " +
                            pico.mes.nombreCorto() + ", con " + pico.gastos.format() + "."
                    )
                }
        }
    }

    private companion object {
        /** Por debajo de un 10 % la variacion es ruido, no una tendencia. */
        const val UMBRAL_VARIACION = 10
    }
}
