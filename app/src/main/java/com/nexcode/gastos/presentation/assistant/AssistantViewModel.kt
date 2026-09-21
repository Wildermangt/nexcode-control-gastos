package com.nexcode.gastos.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexcode.gastos.domain.ai.AdviceResponse
import com.nexcode.gastos.domain.usecase.ai.RequestFinancialAdviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantUiState(
    val question: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val tips: List<String> = emptyList(),
    val source: AdviceResponse.Source? = null,
    /** Falso cuando no hay clave: la pantalla lo advierte en vez de fingir. */
    val agenteConfigurado: Boolean = true
) {
    /** Etiqueta de quien respondio, para que el usuario sepa que esta leyendo. */
    val etiquetaDeOrigen: String?
        get() = when (source) {
            AdviceResponse.Source.REMOTE_AGENT -> "Respondido por el agente de IA"
            AdviceResponse.Source.LOCAL_SIMULATION -> "Respondido sin conexion, con el analisis local"
            null -> null
        }
}

/** Pantalla de mensajes: conversacion con el asistente financiero de Nexcode. */
class AssistantViewModel(
    private val requestAdvice: RequestFinancialAdviceUseCase,
    agenteConfigurado: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState(agenteConfigurado = agenteConfigurado))
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        analyze()
    }

    fun onQuestionChange(value: String) = _uiState.update { it.copy(question = value) }

    fun analyze() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            // El caso de uso ya atrapa sus propias excepciones y siempre
            // devuelve una respuesta utilizable.
            val response = requestAdvice(question = _uiState.value.question)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = response.message,
                    tips = response.tips,
                    source = response.source
                )
            }
        }
    }
}
