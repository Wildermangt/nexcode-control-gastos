package com.nexcode.gastos.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nexcode.gastos.BuildConfig
import com.nexcode.gastos.data.di.DataProvider
import com.nexcode.gastos.data.remote.ai.GeminiAdvisor
import com.nexcode.gastos.domain.usecase.ai.HerramientasDelAgente
import com.nexcode.gastos.domain.usecase.ai.RequestFinancialAdviceUseCase
import com.nexcode.gastos.domain.usecase.analytics.AnalizarTendenciaUseCase
import com.nexcode.gastos.domain.usecase.analytics.CalculateBalanceUseCase
import com.nexcode.gastos.domain.usecase.analytics.GetCategorySummaryUseCase
import com.nexcode.gastos.domain.usecase.analytics.ObserveMonthlyReportUseCase
import com.nexcode.gastos.domain.usecase.catalog.ObserveAccountsSummaryUseCase
import com.nexcode.gastos.domain.usecase.catalog.ObserveAccountsUseCase
import com.nexcode.gastos.domain.usecase.catalog.UpdateAccountBalanceUseCase
import com.nexcode.gastos.domain.usecase.catalog.ObserveCategoriesUseCase
import com.nexcode.gastos.domain.usecase.recurring.AddRecurringPaymentUseCase
import com.nexcode.gastos.domain.usecase.recurring.DeleteRecurringPaymentUseCase
import com.nexcode.gastos.domain.usecase.recurring.GetPagosPorAvisarUseCase
import com.nexcode.gastos.domain.usecase.recurring.ObserveRecurringPocketUseCase
import com.nexcode.gastos.domain.usecase.recurring.PayRecurringPaymentUseCase
import com.nexcode.gastos.domain.usecase.recurring.ToggleRecurringPaymentUseCase
import com.nexcode.gastos.domain.usecase.transaction.AddTransactionUseCase
import com.nexcode.gastos.domain.usecase.transaction.BuscarTransaccionesUseCase
import com.nexcode.gastos.domain.usecase.transaction.DeleteTransactionUseCase
import com.nexcode.gastos.domain.usecase.transaction.GetTransactionByIdUseCase
import com.nexcode.gastos.domain.usecase.transaction.GetTransactionsUseCase
import com.nexcode.gastos.domain.usecase.transaction.UpdateTransactionUseCase
import com.nexcode.gastos.presentation.addtransaction.AddTransactionViewModel
import com.nexcode.gastos.presentation.accounts.AccountsViewModel
import com.nexcode.gastos.presentation.assistant.AssistantViewModel
import com.nexcode.gastos.presentation.home.HomeViewModel
import com.nexcode.gastos.presentation.recurring.RecurringViewModel
import com.nexcode.gastos.cuenta.CuentaViewModel
import com.nexcode.gastos.sincronizacion.SincronizacionViewModel
import com.nexcode.gastos.presentation.transactions.TransactionsViewModel

/**
 * Contenedor de dependencias (localizador de servicios).
 *
 * Une las tres capas: pide a :data las implementaciones concretas y con ellas
 * construye los casos de uso del dominio que consumen los ViewModel.
 * Ninguna pantalla ni ViewModel construye un repositorio por su cuenta.
 *
 * Se prefirio un contenedor explicito sobre Hilt para que la trazabilidad de
 * las dependencias sea visible de un vistazo en el informe.
 */
class AppContainer(context: Context) {

    private val data = DataProvider(context)

    /** Reloj del dispositivo que cumple el contrato del dominio. */
    private val reloj = RelojSistema()

    // --- Casos de uso (capa de dominio) ---
    val addTransaction = AddTransactionUseCase(
        transactionRepository = data.transactionRepository,
        accountRepository = data.accountRepository,
        categoryRepository = data.categoryRepository,
        reloj = reloj
    )
    val updateTransaction = UpdateTransactionUseCase(
        transactionRepository = data.transactionRepository,
        accountRepository = data.accountRepository,
        categoryRepository = data.categoryRepository
    )
    val getTransactionById = GetTransactionByIdUseCase(data.transactionRepository)
    val getTransactions = GetTransactionsUseCase(data.transactionRepository)
    val buscarTransacciones = BuscarTransaccionesUseCase(data.transactionRepository)
    val deleteTransaction = DeleteTransactionUseCase(data.transactionRepository)
    val calculateBalance = CalculateBalanceUseCase(data.transactionRepository)
    val getCategorySummary = GetCategorySummaryUseCase(
        data.transactionRepository, data.categoryRepository
    )
    val observeMonthlyReport = ObserveMonthlyReportUseCase(
        data.transactionRepository, data.categoryRepository, reloj
    )
    val analizarTendencia = AnalizarTendenciaUseCase(data.transactionRepository, reloj)
    val observeCategories = ObserveCategoriesUseCase(data.categoryRepository)
    val observeAccounts = ObserveAccountsUseCase(data.accountRepository)
    val observeAccountsSummary = ObserveAccountsSummaryUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository
    )
    val updateAccountBalance = UpdateAccountBalanceUseCase(data.accountRepository)
    // --- Agente de IA -------------------------------------------------
    //
    // Las herramientas se arman con los mismos casos de uso que alimentan la
    // interfaz: el agente y las pantallas no pueden dar cifras distintas
    // porque pasan por el mismo calculo.
    private val herramientasDelAgente = HerramientasDelAgente(
        transactionRepository = data.transactionRepository,
        categoryRepository = data.categoryRepository,
        accountRepository = data.accountRepository,
        recurringPaymentRepository = data.recurringPaymentRepository,
        addTransaction = addTransaction,
        reloj = reloj
    )

    /**
     * Con clave configurada responde el agente de Gemini; sin ella, o si la
     * red falla, responde el motor local de reglas que expone :data.
     */
    private val agente = GeminiAdvisor(
        herramientas = herramientasDelAgente,
        respaldo = data.financialAdvisor,
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    /** Para que la interfaz pueda avisar de que el agente no esta configurado. */
    val agenteConfigurado: Boolean get() = agente.estaConfigurado

    val requestAdvice = RequestFinancialAdviceUseCase(
        data.transactionRepository, data.categoryRepository, agente, reloj
    )

    // --- Bolsillo de recordatorios ---
    val observeRecurringPocket = ObserveRecurringPocketUseCase(data.recurringPaymentRepository, reloj)
    val addRecurringPayment = AddRecurringPaymentUseCase(
        data.recurringPaymentRepository, data.accountRepository, reloj
    )
    val payRecurringPayment = PayRecurringPaymentUseCase(
        data.recurringPaymentRepository, data.transactionRepository, reloj
    )
    val toggleRecurringPayment = ToggleRecurringPaymentUseCase(data.recurringPaymentRepository)
    val deleteRecurringPayment = DeleteRecurringPaymentUseCase(data.recurringPaymentRepository)

    /** Consulta puntual que alimenta la notificacion del sistema. */
    private val pagosPorAvisar = GetPagosPorAvisarUseCase(data.recurringPaymentRepository, reloj)

    suspend fun getPagosPorAvisar(hoy: kotlinx.datetime.LocalDate) = pagosPorAvisar(hoy)

    /** Motor de replica, o null si la nube todavia no esta configurada. */
    val motorDeSincronizacion = data.motorDeSincronizacion

    /** Identidad frente a la nube: anonima o enlazada a un correo. */
    val cuentaDeUsuario = data.cuentaDeUsuario

    /** Deja el telefono con los datos de la cuenta recien abierta. */
    suspend fun reemplazarDatosLocalesPorLaNube() = data.reemplazarDatosLocalesPorLaNube()

    /** Marcas de la ultima pasada, para poder mostrarlas en pantalla. */
    val marcasDeSincronizacion = data.marcasDeSincronizacion

    suspend fun seedIfEmpty() = data.seedIfEmpty()
}

/**
 * Fabrica de ViewModels: les entrega casos de uso, nunca repositorios.
 */
class NexcodeViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AccountsViewModel::class.java) -> AccountsViewModel(
            observeAccountsSummary = container.observeAccountsSummary,
            updateAccountBalance = container.updateAccountBalance
        ) as T

        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            getTransactions = container.getTransactions,
            observeMonthlyReport = container.observeMonthlyReport,
            observeCategories = container.observeCategories,
            analizarTendencia = container.analizarTendencia,
            deleteTransaction = container.deleteTransaction
        ) as T

        modelClass.isAssignableFrom(AddTransactionViewModel::class.java) -> AddTransactionViewModel(
            addTransaction = container.addTransaction,
            updateTransaction = container.updateTransaction,
            getTransactionById = container.getTransactionById,
            observeCategories = container.observeCategories,
            observeAccounts = container.observeAccounts
        ) as T

        modelClass.isAssignableFrom(TransactionsViewModel::class.java) -> TransactionsViewModel(
            getTransactions = container.getTransactions,
            observeCategories = container.observeCategories,
            buscarTransacciones = container.buscarTransacciones,
            deleteTransaction = container.deleteTransaction
        ) as T

        modelClass.isAssignableFrom(AssistantViewModel::class.java) -> AssistantViewModel(
            requestAdvice = container.requestAdvice,
            agenteConfigurado = container.agenteConfigurado
        ) as T

        modelClass.isAssignableFrom(RecurringViewModel::class.java) -> RecurringViewModel(
            addRecurringPayment = container.addRecurringPayment,
            payRecurringPayment = container.payRecurringPayment,
            deleteRecurringPayment = container.deleteRecurringPayment,
            toggleRecurringPayment = container.toggleRecurringPayment,
            observeRecurringPocket = container.observeRecurringPocket,
            observeCategories = container.observeCategories,
            observeAccounts = container.observeAccounts
        ) as T

        modelClass.isAssignableFrom(SincronizacionViewModel::class.java) ->
            SincronizacionViewModel(motor = container.motorDeSincronizacion) as T

        modelClass.isAssignableFrom(CuentaViewModel::class.java) -> CuentaViewModel(
            cuenta = container.cuentaDeUsuario,
            reemplazarPorLaNube = { container.reemplazarDatosLocalesPorLaNube() }
        ) as T

        else -> throw IllegalArgumentException(
            "ViewModel no registrado en la fabrica: " + modelClass.name
        )
    }
}
