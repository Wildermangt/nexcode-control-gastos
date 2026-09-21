package com.nexcode.gastos.domain.usecase.ai

import com.nexcode.gastos.domain.ai.CategoriaDelResumen
import com.nexcode.gastos.domain.ai.HerramientasFinancieras
import com.nexcode.gastos.domain.ai.PagoPendiente
import com.nexcode.gastos.domain.ai.ResultadoDeRegistro
import com.nexcode.gastos.domain.ai.ResumenDelMes
import com.nexcode.gastos.domain.model.RecurringPayment
import com.nexcode.gastos.domain.model.Tendencia
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.repository.AccountRepository
import com.nexcode.gastos.domain.repository.CategoryRepository
import com.nexcode.gastos.domain.repository.RecurringPaymentRepository
import com.nexcode.gastos.domain.repository.TransactionRepository
import com.nexcode.gastos.domain.usecase.analytics.AnalizarTendenciaUseCase
import com.nexcode.gastos.domain.usecase.analytics.CalculateBalanceUseCase
import com.nexcode.gastos.domain.usecase.analytics.GetCategorySummaryUseCase
import com.nexcode.gastos.domain.usecase.transaction.AddTransactionUseCase
import com.nexcode.gastos.domain.usecase.transaction.AddTransactionResult
import com.nexcode.gastos.domain.usecase.transaction.NewTransactionInput
import com.nexcode.gastos.domain.util.RelojDominio
import com.nexcode.gastos.domain.util.hoy
import com.nexcode.gastos.domain.util.menosMeses
import com.nexcode.gastos.domain.util.primerDiaDelMes
import kotlinx.coroutines.flow.first
import kotlin.math.round

/**
 * Implementacion de las herramientas del agente.
 *
 * No inventa logica: compone los casos de uso que ya existen. Eso es
 * deliberado —el agente y la interfaz tienen que dar exactamente la misma
 * cifra, y la unica forma de garantizarlo es que las dos pasen por el mismo
 * calculo—.
 *
 * Su segunda responsabilidad es traducir entre el vocabulario del usuario y el
 * del sistema: el agente dice "Mercado" y aqui se resuelve a un identificador
 * de categoria, sin que el modelo llegue a ver un solo id.
 */
class HerramientasDelAgente(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val addTransaction: AddTransactionUseCase,
    private val reloj: RelojDominio
) : HerramientasFinancieras {

    override suspend fun resumenDelMes(): ResumenDelMes {
        val dia = reloj.hoy()
        val primero = dia.primerDiaDelMes()
        val delMes = transactionRepository.findByRange(primero, dia)

        val balance = CalculateBalanceUseCase(transactionRepository).calculate(delMes)
        val categoriasPorId = categoryRepository.getAll().associateBy { it.id }
        val resumenes = GetCategorySummaryUseCase(transactionRepository, categoryRepository)
            .summarize(delMes, categoriasPorId)

        return ResumenDelMes(
            periodo = primero.toString() + " a " + dia.toString(),
            ingresos = balance.income,
            gastos = balance.expense,
            saldo = balance.total,
            tasaDeAhorro = balance.savingsRate,
            categorias = resumenes.map {
                CategoriaDelResumen(
                    nombre = it.displayName,
                    total = it.total,
                    movimientos = it.transactionCount,
                    porcentaje = round(it.percentage * 100f).toInt()
                )
            }
        )
    }

    override suspend fun tendenciaDelGasto(): Tendencia {
        val dia = reloj.hoy()
        val desde = dia.primerDiaDelMes().menosMeses(AnalizarTendenciaUseCase.MESES_SERIE - 1)
        val historico = transactionRepository.findByRange(desde, dia)
        return AnalizarTendenciaUseCase(transactionRepository, reloj).analizar(historico, dia)
    }

    override suspend fun pagosPendientes(): List<PagoPendiente> {
        val dia = reloj.hoy()
        return recurringPaymentRepository.observeAll().first()
            .filter { it.isActive }
            .filter { it.status(dia) != RecurringPayment.Status.SCHEDULED }
            .sortedBy { it.nextDueDate }
            .map {
                PagoPendiente(
                    nombre = it.name,
                    monto = it.amount,
                    diasParaVencer = it.daysUntilDue(dia),
                    estaVencido = it.status(dia) == RecurringPayment.Status.OVERDUE
                )
            }
    }

    override suspend fun buscarMovimientos(
        texto: String?,
        categoria: String?,
        limite: Int
    ): List<Transaction> {
        val tope = limite.coerceIn(1, HerramientasFinancieras.LIMITE_BUSQUEDA)
        val consulta = texto?.trim()?.takeIf { it.isNotBlank() }
        val nombreCategoria = categoria?.trim()?.takeIf { it.isNotBlank() }

        // Sin ningun filtro, los mas recientes: es lo que espera quien
        // pregunta "en que he gastado ultimamente".
        if (consulta == null && nombreCategoria == null) {
            return transactionRepository.observeRecent(tope).first()
        }

        val base = if (consulta != null) {
            transactionRepository.buscar(consulta, tope * FACTOR_HOLGURA)
        } else {
            transactionRepository.observeRecent(tope * FACTOR_HOLGURA).first()
        }

        // El filtro por categoria se aplica despues porque la consulta guarda
        // el id, no el nombre, y el agente solo conoce el nombre.
        val filtrados = nombreCategoria?.let { nombre ->
            val id = resolverCategoria(nombre)?.id
            if (id == null) emptyList() else base.filter { it.categoryId == id }
        } ?: base

        return filtrados.take(tope)
    }

    override suspend fun registrarGasto(
        monto: String?,
        titulo: String?,
        categoria: String?
    ): ResultadoDeRegistro {
        // La cuenta no la elige el agente: se usa la primera del usuario. Dejar
        // que el modelo escoja cuenta seria darle una decision que no le
        // corresponde y que el usuario no ha pedido.
        val cuenta = accountRepository.getAll().firstOrNull()
            ?: return ResultadoDeRegistro.Rechazado(
                "El usuario todavia no tiene ninguna cuenta creada."
            )

        val idCategoria = categoria?.trim()?.takeIf { it.isNotBlank() }
            ?.let { resolverCategoria(it)?.id }

        val resultado = addTransaction(
            NewTransactionInput(
                rawAmount = monto,
                rawTitle = titulo,
                type = TransactionType.Expense,
                accountId = cuenta.id,
                categoryId = idCategoria
            )
        )

        // El caso de uso ya devuelve un resultado sellado: aqui solo se
        // traduce a un mensaje que el agente pueda repetirle al usuario.
        return when (resultado) {
            is AddTransactionResult.Success -> ResultadoDeRegistro.Exito(
                id = resultado.transactionId,
                descripcion = "Gasto de " + resultado.transaction.amount.format() +
                    " registrado como \"" + resultado.transaction.title +
                    "\" en la cuenta " + cuenta.name + "."
            )

            is AddTransactionResult.InvalidAmount ->
                ResultadoDeRegistro.Rechazado(resultado.message)

            is AddTransactionResult.MissingField ->
                ResultadoDeRegistro.Rechazado("Falta un dato: " + resultado.fieldName)

            is AddTransactionResult.NotFound ->
                ResultadoDeRegistro.Rechazado(resultado.message)

            is AddTransactionResult.Failure ->
                ResultadoDeRegistro.Rechazado(resultado.message)
        }
    }

    /**
     * Busca una categoria por su nombre tal como lo escribiria una persona.
     *
     * Primero exacto sin distinguir mayusculas, y si no, por contencion: el
     * agente puede decir "comida" donde la categoria se llama "Comida y
     * mercado".
     */
    private suspend fun resolverCategoria(nombre: String) =
        categoryRepository.getAll().let { todas ->
            todas.firstOrNull { it.name.equals(nombre, ignoreCase = true) }
                ?: todas.firstOrNull { it.name.contains(nombre, ignoreCase = true) }
        }

    private companion object {
        /**
         * Se piden mas filas de las necesarias porque el filtro por categoria
         * se aplica despues de la consulta y descarta parte del resultado.
         */
        const val FACTOR_HOLGURA = 4
    }
}
