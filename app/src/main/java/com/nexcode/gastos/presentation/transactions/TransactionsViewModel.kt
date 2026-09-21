package com.nexcode.gastos.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.usecase.catalog.ObserveCategoriesUseCase
import com.nexcode.gastos.domain.usecase.transaction.DeleteTransactionUseCase
import com.nexcode.gastos.domain.usecase.transaction.BuscarTransaccionesUseCase
import com.nexcode.gastos.domain.usecase.transaction.GetTransactionsUseCase
import com.nexcode.gastos.exportacion.ValidadorEntrada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val grouped: Map<LocalDate, List<Transaction>> = emptyMap(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val consulta: String = "",
    val buscando: Boolean = false,
    val resultados: Map<LocalDate, List<Transaction>> = emptyMap()
) {
    /** Lo que se pinta: los resultados si hay busqueda activa, si no el historico. */
    val visibles: Map<LocalDate, List<Transaction>> get() = if (buscando) resultados else grouped

    val sinResultados: Boolean get() = buscando && resultados.isEmpty()
}

/** Historico completo de movimientos. */
class TransactionsViewModel(
    getTransactions: GetTransactionsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val buscarTransacciones: BuscarTransaccionesUseCase,
    private val deleteTransaction: DeleteTransactionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        combine(getTransactions(), observeCategories()) { grouped, categories ->
            TransactionsUiState(
                isLoading = false,
                grouped = grouped,
                categoriesById = categories.associateBy { it.id }
            )
        }
            // copy: la busqueda en curso no se pierde cuando llega una
            // emision nueva del historico.
            .onEach { nuevo ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        grouped = nuevo.grouped,
                        categoriesById = nuevo.categoriesById
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Cada pulsacion relanza la busqueda.
     *
     * El texto pasa antes por el validador escrito en Java, que recorta,
     * colapsa los espacios y descarta lo que sea demasiado corto para que la
     * consulta signifique algo. Si no queda consulta valida se vuelve al
     * historico completo en vez de mostrar una lista vacia.
     */
    fun onConsultaChange(texto: String) {
        _uiState.update { it.copy(consulta = texto) }

        val consulta = ValidadorEntrada.consulta(texto)
        if (consulta == null) {
            _uiState.update { it.copy(buscando = false, resultados = emptyMap()) }
            return
        }

        viewModelScope.launch {
            val encontrados = buscarTransacciones(consulta)
            // Se comprueba que el texto no haya cambiado mientras se
            // consultaba: sin esto, una respuesta lenta pisa a una posterior.
            if (_uiState.value.consulta == texto) {
                _uiState.update { it.copy(buscando = true, resultados = encontrados) }
            }
        }
    }

    fun onLimpiarBusqueda() {
        _uiState.update { it.copy(consulta = "", buscando = false, resultados = emptyMap()) }
    }

    fun onDelete(transactionId: Long) {
        viewModelScope.launch { deleteTransaction(transactionId) }
    }
}
