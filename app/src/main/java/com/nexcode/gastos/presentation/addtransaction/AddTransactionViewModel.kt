package com.nexcode.gastos.presentation.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.usecase.catalog.ObserveAccountsUseCase
import com.nexcode.gastos.domain.usecase.catalog.ObserveCategoriesUseCase
import com.nexcode.gastos.domain.usecase.transaction.AddTransactionResult
import com.nexcode.gastos.domain.usecase.transaction.AddTransactionUseCase
import com.nexcode.gastos.domain.usecase.transaction.GetTransactionByIdUseCase
import com.nexcode.gastos.domain.usecase.transaction.NewTransactionInput
import com.nexcode.gastos.automatizacion.AvisoDeMovimiento
import com.nexcode.gastos.automatizacion.MovimientoParaAviso
import com.nexcode.gastos.exportacion.ValidadorEntrada
import com.nexcode.gastos.presentation.util.paraBitacora
import com.nexcode.gastos.domain.usecase.transaction.UpdateTransactionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del formulario. Todo lo que el usuario escribe es texto. */
data class AddTransactionUiState(
    val amountText: String = "",
    val title: String = "",
    val note: String = "",
    val type: TransactionType = TransactionType.Expense,
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    /** null = alta nueva; con valor = se esta editando ese movimiento. */
    val editingId: Long? = null
) {
    /** Categorias que aplican al tipo elegido. Filtrado con orden superior. */
    val visibleCategories: List<Category>
        get() = categories.filter { it.appliesTo.code == type.code }

    val isEditing: Boolean get() = editingId != null

    val screenTitle: String get() = if (isEditing) "Editar movimiento" else "Nuevo movimiento"

    val saveLabel: String get() = if (isEditing) "Guardar cambios" else "Guardar"
}

/**
 * Formulario de alta Y de edicion.
 *
 * El mismo estado sirve para los dos casos: si [AddTransactionUiState.editingId]
 * tiene valor se llama al caso de uso de actualizar, si es null al de crear.
 * Ambos devuelven el mismo tipo de resultado, asi que el `when` de abajo no se
 * duplica.
 */
class AddTransactionViewModel(
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val getTransactionById: GetTransactionByIdUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeAccounts: ObserveAccountsUseCase,
    /**
     * Aviso a la automatizacion de Make. Se inyecta como funcion y no como
     * objeto para que las pruebas del formulario no tengan que tocar la red:
     * por omision no hace nada.
     */
    private val avisarMovimiento: suspend (MovimientoParaAviso) -> Unit = {},
    /** Correo al que llega la confirmacion. Lo resuelve quien construye. */
    private val correoDestino: () -> String = { AvisoDeMovimiento.CORREO_POR_DEFECTO }
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        combine(observeCategories(), observeAccounts()) { categories, accounts ->
            categories to accounts
        }
            .onEach { (categories, accounts) ->
                _uiState.update { state ->
                    state.copy(
                        categories = categories,
                        accounts = accounts,
                        // Elvis: si aun no hay cuenta elegida se toma la primera.
                        selectedAccountId = state.selectedAccountId ?: accounts.firstOrNull()?.id
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Carga un movimiento existente en el formulario.
     *
     * Se llama una sola vez desde la pantalla: si el id es null o ya se cargo,
     * no hace nada. El monto vuelve a texto con el formato colombiano para que
     * el usuario vea lo mismo que escribio.
     */
    fun startEditing(transactionId: Long?) {
        if (transactionId == null || transactionId <= 0L) return
        if (_uiState.value.editingId == transactionId) return

        viewModelScope.launch {
            // Safe call: si el movimiento se borro mientras tanto, no se rompe nada.
            val transaction = getTransactionById(transactionId) ?: return@launch

            _uiState.update { state ->
                state.copy(
                    editingId = transaction.id,
                    amountText = transaction.amount.format(),
                    title = transaction.title,
                    // Elvis: la nota es opcional en el dominio, aqui el campo es texto.
                    note = transaction.note ?: "",
                    type = transaction.type,
                    selectedCategoryId = transaction.categoryId,
                    selectedAccountId = transaction.accountId,
                    errorMessage = null
                )
            }
        }
    }

    fun onAmountChange(value: String) = _uiState.update {
        it.copy(amountText = value, errorMessage = null)
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value) }

    fun onNoteChange(value: String) = _uiState.update { it.copy(note = value) }

    fun onTypeChange(type: TransactionType) = _uiState.update {
        it.copy(type = type, selectedCategoryId = null, errorMessage = null)
    }

    fun onCategorySelected(categoryId: Long?) = _uiState.update {
        it.copy(selectedCategoryId = if (it.selectedCategoryId == categoryId) null else categoryId)
    }

    fun onAccountSelected(accountId: Long) = _uiState.update {
        it.copy(selectedAccountId = accountId)
    }

    /**
     * Guarda la transaccion.
     * El caso de uso devuelve un resultado, no lanza excepciones: aqui solo se
     * traduce ese resultado a un mensaje para el usuario.
     */
    fun onSave() {
        val state = _uiState.value
        val accountId = state.selectedAccountId
        if (accountId == null) {
            _uiState.update { it.copy(errorMessage = "Primero crea o selecciona una cuenta") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val input = NewTransactionInput(
                rawAmount = state.amountText,
                // El titulo y la nota pasan por el saneador escrito en Java:
                // recorta, colapsa espacios, quita caracteres de control y
                // limita la longitud. El dominio comprueba que el titulo no
                // este vacio, pero no que quepa en una fila de la lista.
                rawTitle = ValidadorEntrada.titulo(state.title),
                rawNote = ValidadorEntrada.nota(state.note),
                accountId = accountId,
                categoryId = state.selectedCategoryId,
                type = state.type
            )

            // Mismo formulario, dos casos de uso distintos.
            val editingId = state.editingId
            val result = if (editingId != null) {
                updateTransaction(editingId, input)
            } else {
                addTransaction(input)
            }

            when (result) {
                is AddTransactionResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                    // Solo las altas se avisan: una edicion no es un movimiento
                    // nuevo y duplicaria la fila en la bitacora de la nube.
                    if (editingId == null) avisarDeLaAlta(result, state)
                }

                is AddTransactionResult.InvalidAmount ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }

                is AddTransactionResult.MissingField ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Falta un dato: " + result.fieldName
                        )
                    }

                is AddTransactionResult.NotFound ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }

                is AddTransactionResult.Failure ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
            }
        }
    }

    /**
     * Manda el movimiento recien creado a la automatizacion.
     *
     * Se hace DESPUES de marcar `saved = true`: la pantalla ya se cerro y el
     * usuario no espera por la red. Si el envio falla no se le dice nada,
     * porque su gasto si quedo guardado; el fallo solo va al registro.
     */
    private suspend fun avisarDeLaAlta(
        exito: AddTransactionResult.Success,
        state: AddTransactionUiState
    ) {
        val movimiento = exito.transaction
        val cuenta = state.accounts.firstOrNull { it.id == movimiento.accountId }
        val categoria = state.categories.firstOrNull { it.id == movimiento.categoryId }

        avisarMovimiento(
            MovimientoParaAviso(
                fechaHora = movimiento.dateTime.paraBitacora(),
                tipo = if (movimiento.type.sign >= 0) "Ingreso" else "Gasto",
                titulo = movimiento.title,
                // `format()` devuelve solo los digitos agrupados ("85.000"):
                // el simbolo lo pone la interfaz. Como este texto va a un
                // correo, aqui se antepone.
                monto = "$ " + movimiento.amount.format(),
                // Pesos enteros: la hoja necesita un numero, no texto con puntos.
                montoNumero = movimiento.amount.cents / 100,
                categoria = categoria?.name ?: "Sin categoria",
                cuenta = cuenta?.name ?: "Sin cuenta",
                nota = movimiento.note ?: "",
                // Es el CUPO que el usuario fijo para la cuenta, no el saldo
                // disponible: el disponible exige combinar los movimientos y
                // eso vive en otro caso de uso. Se manda por completitud, pero
                // el correo no lo muestra para no dar una cifra confusa.
                saldoCuenta = cuenta?.initialBalance?.format() ?: "",
                correo = correoDestino()
            )
        )
    }
}
