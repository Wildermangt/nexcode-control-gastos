package com.nexcode.gastos.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexcode.gastos.domain.model.Balance
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.CategorySummary
import com.nexcode.gastos.domain.model.Tendencia
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.usecase.analytics.AnalizarTendenciaUseCase
import com.nexcode.gastos.domain.usecase.analytics.ObserveMonthlyReportUseCase
import com.nexcode.gastos.domain.usecase.catalog.ObserveCategoriesUseCase
import com.nexcode.gastos.domain.usecase.transaction.DeleteTransactionUseCase
import com.nexcode.gastos.domain.usecase.transaction.GetTransactionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Estado INMUTABLE de la pantalla principal.
 * La UI solo lee este objeto: no puede modificar el estado por su cuenta.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val balance: Balance = Balance(),
    val grouped: Map<LocalDate, List<Transaction>> = emptyMap(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val categorySummaries: List<CategorySummary> = emptyList(),
    val tendencia: Tendencia = Tendencia(),
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && grouped.isEmpty()

    /** La dona solo tiene sentido si hubo gastos en el mes. */
    val hasChart: Boolean get() = categorySummaries.isNotEmpty()

    /** La serie solo se muestra si hay algo que comparar. */
    val hasTendencia: Boolean get() = tendencia.hayDatos
}

class HomeViewModel(
    private val getTransactions: GetTransactionsUseCase,
    private val observeMonthlyReport: ObserveMonthlyReportUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val analizarTendencia: AnalizarTendenciaUseCase,
    private val deleteTransaction: DeleteTransactionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    /** Expuesto como StateFlow de solo lectura: estado seguro para la UI. */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // combine: cuatro flujos independientes que producen un unico estado.
        // El saldo y la dona salen del MISMO reporte mensual, asi nunca se
        // contradicen entre si; la tendencia observa la misma tabla, de modo
        // que registrar un gasto actualiza las tres vistas a la vez.
        combine(
            getTransactions(limit = RECENT_LIMIT),
            observeMonthlyReport(),
            observeCategories(),
            analizarTendencia()
        ) { grouped, report, categories, tendencia ->
            HomeUiState(
                isLoading = false,
                balance = report.balance,
                categorySummaries = report.categories,
                tendencia = tendencia,
                grouped = grouped,
                categoriesById = categories.associateBy { it.id }
            )
        }
            .catch { error ->
                emit(
                    HomeUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "No se pudieron cargar los movimientos"
                    )
                )
            }
            .onEach { state -> _uiState.value = state }
            .launchIn(viewModelScope)
    }

    fun onDeleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            deleteTransaction(transactionId)
        }
    }

    private companion object {
        const val RECENT_LIMIT = 30
    }
}
