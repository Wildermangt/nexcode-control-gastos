package com.nexcode.gastos.domain.usecase.ai

import com.nexcode.gastos.domain.ai.AdviceRequest
import com.nexcode.gastos.domain.ai.AdviceResponse
import com.nexcode.gastos.domain.ai.FinancialAdvisor
import com.nexcode.gastos.domain.repository.CategoryRepository
import com.nexcode.gastos.domain.repository.TransactionRepository
import com.nexcode.gastos.domain.usecase.analytics.AnalizarTendenciaUseCase
import com.nexcode.gastos.domain.usecase.analytics.CalculateBalanceUseCase
import com.nexcode.gastos.domain.usecase.analytics.GetCategorySummaryUseCase
import com.nexcode.gastos.domain.util.RelojDominio
import com.nexcode.gastos.domain.util.hoy
import com.nexcode.gastos.domain.util.menosMeses
import com.nexcode.gastos.domain.util.primerDiaDelMes
import kotlinx.datetime.LocalDate

/**
 * Arma la foto financiera del mes y se la entrega al asistente.
 *
 * El caso de uso no sabe si detras hay una simulacion local o un agente de voz
 * remoto: solo conoce la interfaz [FinancialAdvisor].
 */
class RequestFinancialAdviceUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val advisor: FinancialAdvisor,
    private val reloj: RelojDominio
) {

    suspend operator fun invoke(
        question: String? = null,
        today: LocalDate? = null
    ): AdviceResponse {
        return try {
            val dia = today ?: reloj.hoy()
            val firstDay = dia.primerDiaDelMes()
            val transactions = transactionRepository.findByRange(firstDay, dia)

            // Ventana historica de la analitica: se consulta acotada al rango
            // que la serie necesita, no la tabla entera.
            val desde = firstDay.menosMeses(AnalizarTendenciaUseCase.MESES_SERIE - 1)
            val historico = transactionRepository.findByRange(desde, dia)

            val balance = CalculateBalanceUseCase(transactionRepository).calculate(transactions)
            val categoriesById = categoryRepository.getAll().associateBy { it.id }
            val summaries = GetCategorySummaryUseCase(transactionRepository, categoryRepository)
                .summarize(transactions, categoriesById)
                .take(TOP_CATEGORIES)

            val request = AdviceRequest(
                periodLabel = firstDay.toString() + " a " + dia.toString(),
                balance = balance,
                topCategories = summaries,
                // Elvis: una pregunta en blanco se envia como null, no como "".
                userQuestion = question?.trim()?.takeIf { it.isNotBlank() },
                // La serie de los ultimos meses: sin ella el asistente solo
                // puede describir el mes, no compararlo con nada.
                tendencia = AnalizarTendenciaUseCase(transactionRepository, reloj)
                    .analizar(historico, dia)
            )

            advisor.requestAdvice(request)
        } catch (e: Exception) {
            AdviceResponse(
                message = "No pude analizar tus finanzas en este momento: " +
                    (e.message ?: "error desconocido"),
                tips = emptyList()
            )
        }
    }

    private companion object {
        const val TOP_CATEGORIES = 5
    }
}
