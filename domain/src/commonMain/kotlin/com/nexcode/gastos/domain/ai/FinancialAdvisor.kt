package com.nexcode.gastos.domain.ai

import com.nexcode.gastos.domain.model.Balance
import com.nexcode.gastos.domain.model.CategorySummary
import com.nexcode.gastos.domain.model.Tendencia

/**
 * Datos que se le envian al asistente financiero.
 *
 * Es un modelo de DOMINIO: no contiene JSON, ni URLs, ni cabeceras HTTP.
 * La capa de datos se encarga de traducirlo al formato que exija el proveedor
 * (Retell AI, un webhook propio, un modelo local, etc.).
 */
data class AdviceRequest(
    val periodLabel: String,
    val balance: Balance,
    val topCategories: List<CategorySummary>,
    val userQuestion: String? = null,  // null = el usuario no pregunto nada concreto
    /**
     * Analitica de los ultimos meses.
     *
     * Es opcional a proposito: el asistente sabe responder solo con el mes en
     * curso, y asi un consumidor que no calcule la serie sigue compilando.
     */
    val tendencia: Tendencia? = null
)

/** Respuesta del asistente. */
data class AdviceResponse(
    val message: String,
    val tips: List<String> = emptyList(),
    val source: Source = Source.LOCAL_SIMULATION
) {
    enum class Source { LOCAL_SIMULATION, REMOTE_AGENT }
}

/**
 * CONTRATO del asistente financiero.
 *
 * El dominio solo conoce esta interfaz. Hoy la cumple una simulacion local
 * ([com.nexcode.gastos.data.remote.ai.NexcodeAIService]); manana la puede
 * cumplir un agente de voz remoto sin tocar una sola linea del dominio.
 */
interface FinancialAdvisor {
    suspend fun requestAdvice(request: AdviceRequest): AdviceResponse
}
