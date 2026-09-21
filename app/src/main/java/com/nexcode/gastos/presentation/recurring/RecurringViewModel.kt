package com.nexcode.gastos.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.RecurringFrequency
import com.nexcode.gastos.domain.model.RecurringPocket
import com.nexcode.gastos.domain.usecase.catalog.ObserveAccountsUseCase
import com.nexcode.gastos.domain.usecase.catalog.ObserveCategoriesUseCase
import com.nexcode.gastos.domain.usecase.recurring.AddRecurringPaymentUseCase
import com.nexcode.gastos.domain.usecase.recurring.AddRecurringResult
import com.nexcode.gastos.domain.usecase.recurring.DeleteRecurringPaymentUseCase
import com.nexcode.gastos.domain.usecase.recurring.NewRecurringInput
import com.nexcode.gastos.domain.usecase.recurring.ObserveRecurringPocketUseCase
import com.nexcode.gastos.domain.usecase.recurring.PayRecurringPaymentUseCase
import com.nexcode.gastos.domain.usecase.recurring.ToggleRecurringPaymentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del bolsillo de recordatorios y de su formulario. */
data class RecurringUiState(
    val isLoading: Boolean = true,
    val pocket: RecurringPocket = RecurringPocket(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),

    // Formulario
    val isFormVisible: Boolean = false,
    val formName: String = "",
    val formAmount: String = "",
    val formFrequencyCode: String = RecurringFrequency.CODE_MONTHLY,
    val formDay: String = "1",
    val formAccountId: Long? = null,
    val formCategoryId: Long? = null,
    val formError: String? = null,
    val isSaving: Boolean = false,
    val feedback: String? = null
) {
    /** Construye la frecuencia elegida, con valores por defecto seguros. */
    fun buildFrequency(): RecurringFrequency {
        // toIntOrNull evita una excepcion si el usuario escribe letras.
        val day = formDay.trim().toIntOrNull() ?: 1
        return when (formFrequencyCode) {
            RecurringFrequency.CODE_WEEKLY -> RecurringFrequency.Weekly(day.coerceIn(1, 7))
            RecurringFrequency.CODE_YEARLY -> RecurringFrequency.Yearly(1, day.coerceIn(1, 31))
            else -> RecurringFrequency.Monthly(day.coerceIn(1, 31))
        }
    }

    val dayLabel: String
        get() = when (formFrequencyCode) {
            RecurringFrequency.CODE_WEEKLY -> "Dia de la semana (1 = lunes)"
            RecurringFrequency.CODE_YEARLY -> "Dia de enero"
            else -> "Dia del mes"
        }
}

/**
 * Bolsillo de pagos fijos recurrentes.
 *
 * Une cuatro casos de uso: observar, crear, pagar y eliminar. La pantalla no
 * conoce ninguno de ellos, solo llama metodos de este ViewModel.
 */
class RecurringViewModel(
    private val addRecurringPayment: AddRecurringPaymentUseCase,
    private val payRecurringPayment: PayRecurringPaymentUseCase,
    private val deleteRecurringPayment: DeleteRecurringPaymentUseCase,
    private val toggleRecurringPayment: ToggleRecurringPaymentUseCase,
    observeRecurringPocket: ObserveRecurringPocketUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeAccounts: ObserveAccountsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = _uiState.asStateFlow()

    init {
        combine(
            observeRecurringPocket(),
            observeCategories(),
            observeAccounts()
        ) { pocket, categories, accounts ->
            Triple(pocket, categories, accounts)
        }
            .onEach { (pocket, categories, accounts) ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        pocket = pocket,
                        categories = categories,
                        categoriesById = categories.associateBy { it.id },
                        accounts = accounts,
                        formAccountId = state.formAccountId ?: accounts.firstOrNull()?.id
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // ------------------------- Formulario -------------------------

    fun onToggleForm() = _uiState.update {
        it.copy(isFormVisible = !it.isFormVisible, formError = null)
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(formName = value, formError = null) }
    fun onAmountChange(value: String) = _uiState.update { it.copy(formAmount = value, formError = null) }
    fun onDayChange(value: String) = _uiState.update { it.copy(formDay = value.filter { c -> c.isDigit() }) }
    fun onFrequencyChange(code: String) = _uiState.update { it.copy(formFrequencyCode = code) }
    fun onAccountChange(accountId: Long) = _uiState.update { it.copy(formAccountId = accountId) }
    fun onCategoryChange(categoryId: Long?) = _uiState.update {
        it.copy(formCategoryId = if (it.formCategoryId == categoryId) null else categoryId)
    }

    fun onSave() {
        val state = _uiState.value
        val accountId = state.formAccountId
        if (accountId == null) {
            _uiState.update { it.copy(formError = "Primero selecciona una cuenta") }
            return
        }

        _uiState.update { it.copy(isSaving = true, formError = null) }

        viewModelScope.launch {
            val result = addRecurringPayment(
                NewRecurringInput(
                    rawName = state.formName,
                    rawAmount = state.formAmount,
                    frequency = state.buildFrequency(),
                    accountId = accountId,
                    categoryId = state.formCategoryId
                )
            )

            when (result) {
                is AddRecurringResult.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        isFormVisible = false,
                        formName = "",
                        formAmount = "",
                        formDay = "1",
                        formCategoryId = null,
                        feedback = "Recordatorio creado"
                    )
                }

                is AddRecurringResult.InvalidAmount -> _uiState.update {
                    it.copy(isSaving = false, formError = result.message)
                }

                is AddRecurringResult.MissingField -> _uiState.update {
                    it.copy(isSaving = false, formError = "Falta un dato: " + result.fieldName)
                }

                is AddRecurringResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, formError = result.message)
                }
            }
        }
    }

    // ------------------------- Acciones de la lista -------------------------

    /** Deslizar a la derecha: registra el pago como gasto y adelanta la fecha. */
    fun onPay(paymentId: Long) {
        viewModelScope.launch {
            val ok = payRecurringPayment(paymentId)
            _uiState.update {
                it.copy(feedback = if (ok) "Pago registrado en tus movimientos" else "No se pudo registrar")
            }
        }
    }

    /** Deslizar a la izquierda: elimina el recordatorio. */
    fun onDelete(paymentId: Long) {
        viewModelScope.launch {
            deleteRecurringPayment(paymentId)
            _uiState.update { it.copy(feedback = "Recordatorio eliminado") }
        }
    }

    fun onTogglePayment(paymentId: Long) {
        viewModelScope.launch { toggleRecurringPayment(paymentId) }
    }

    fun onFeedbackShown() = _uiState.update { it.copy(feedback = null) }
}
