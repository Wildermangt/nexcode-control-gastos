package com.nexcode.gastos.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.usecase.catalog.AccountSummary
import com.nexcode.gastos.domain.usecase.catalog.ObserveAccountsSummaryUseCase
import com.nexcode.gastos.domain.usecase.catalog.UpdateAccountBalanceUseCase
import com.nexcode.gastos.domain.usecase.catalog.UpdateAccountResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de cuentas y de su cuadro de edicion. */
data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountSummary> = emptyList(),

    // Cuadro de edicion: null cuando esta cerrado.
    val editingId: Long? = null,
    val editingName: String = "",
    val editingAmount: String = "",
    val editingError: String? = null,
    val isSaving: Boolean = false,
    val feedback: String? = null
) {
    val totalCupo: Money get() = Money(accounts.sumOf { it.cupo.cents })
    val totalDisponible: Money get() = Money(accounts.sumOf { it.disponible.cents })
}

/** Ajustes rapidos del cupo, en pesos. */
private val PASOS = listOf(10_000L, 50_000L, 100_000L)

/**
 * Gestiona las cuentas y el cupo de cada una.
 *
 * La pantalla no calcula ni valida nada: pide el ajuste al caso de uso y
 * traduce el resultado sellado a estado visible. Por eso no hay un solo
 * bloque `try` aqui dentro.
 */
class AccountsViewModel(
    observeAccountsSummary: ObserveAccountsSummaryUseCase,
    private val updateAccountBalance: UpdateAccountBalanceUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    val pasos: List<Long> = PASOS

    init {
        observeAccountsSummary()
            .onEach { lista ->
                _state.update { it.copy(isLoading = false, accounts = lista) }
            }
            .launchIn(viewModelScope)
    }

    // --- cuadro de edicion --------------------------------------------------

    fun abrirEdicion(resumen: AccountSummary) {
        _state.update {
            it.copy(
                editingId = resumen.account.id,
                editingName = resumen.account.name,
                // Se ofrece el valor actual ya escrito: cambiarlo es mas
                // comodo que teclearlo entero desde cero.
                editingAmount = resumen.cupo.format(),
                editingError = null
            )
        }
    }

    fun cerrarEdicion() {
        _state.update { it.copy(editingId = null, editingError = null) }
    }

    fun onAmountChange(texto: String) {
        _state.update { it.copy(editingAmount = texto, editingError = null) }
    }

    fun guardarCupo() {
        val id = _state.value.editingId ?: return
        val texto = _state.value.editingAmount
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            traducir(updateAccountBalance(accountId = id, rawAmount = texto)) {
                "Cupo de " + it + " actualizado"
            }
        }
    }

    // --- ajustes rapidos ----------------------------------------------------

    fun aumentar(accountId: Long, pesos: Long) = ajustar(accountId, pesos)

    fun disminuir(accountId: Long, pesos: Long) = ajustar(accountId, -pesos)

    private fun ajustar(accountId: Long, pesos: Long) {
        viewModelScope.launch {
            val delta = Money.fromMajor(pesos.toDouble())
            traducir(updateAccountBalance.ajustar(accountId, delta)) { nombre ->
                val signo = if (pesos >= 0) "+" else "-"
                nombre + ": " + signo + " " + Money.fromMajor(
                    kotlin.math.abs(pesos).toDouble()
                ).format()
            }
        }
    }

    // --- traduccion del resultado sellado -----------------------------------

    /**
     * Convierte el resultado del dominio en estado de pantalla.
     *
     * El `when` es exhaustivo por ser el tipo sellado: si manana se agrega una
     * variante de error, el compilador obliga a contemplarla aqui.
     */
    private fun traducir(
        resultado: UpdateAccountResult,
        mensajeExito: (nombre: String) -> String
    ) {
        _state.update { actual ->
            when (resultado) {
                is UpdateAccountResult.Success -> actual.copy(
                    isSaving = false,
                    editingId = null,
                    editingError = null,
                    feedback = mensajeExito(resultado.account.name)
                )

                is UpdateAccountResult.InvalidAmount -> actual.copy(
                    isSaving = false,
                    editingError = resultado.message
                )

                is UpdateAccountResult.NotFound -> actual.copy(
                    isSaving = false,
                    editingId = null,
                    feedback = resultado.message
                )

                is UpdateAccountResult.Failure -> actual.copy(
                    isSaving = false,
                    editingError = resultado.message
                )
            }
        }
    }

    fun consumirFeedback() {
        _state.update { it.copy(feedback = null) }
    }
}
