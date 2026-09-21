package com.nexcode.gastos.domain

import com.nexcode.gastos.domain.ai.ResultadoDeRegistro
import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.RecurringFrequency
import com.nexcode.gastos.domain.model.RecurringPayment
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.usecase.ai.HerramientasDelAgente
import com.nexcode.gastos.domain.usecase.transaction.AddTransactionUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pruebas de las herramientas que ejecuta el agente de IA.
 *
 * Verifican la mitad del sistema que si es determinista. Lo que decida el
 * modelo —que herramienta llamar y con que argumentos— no se puede fijar en
 * una prueba unitaria; lo que la herramienta hace cuando la llaman, si.
 *
 * Son las que respaldan los casos de uso 4 y 5 de la guia: el agente consulta
 * informacion de la aplicacion, y el agente ejecuta una accion sobre ella.
 */
class HerramientasDelAgenteTest {

    private val comida = Category(id = 1L, name = "Comida y mercado")
    private val transporte = Category(id = 2L, name = "Transporte")
    private val cuenta = Account(id = 1L, name = "Efectivo", initialBalance = Money(500_000_00L))

    /** El reloj fijo de las pruebas marca el 22 de agosto de 2026. */
    private val hoy = LocalDate(2026, 8, 22)

    private fun gasto(
        categoria: Long?,
        pesos: Long,
        titulo: String,
        nota: String? = null,
        dia: Int = 10
    ) = Transaction(
        id = pesos,
        accountId = 1L,
        categoryId = categoria,
        type = TransactionType.Expense,
        amount = Money(pesos * 100L),
        dateTime = LocalDateTime(2026, 8, dia, 12, 0),
        title = titulo,
        note = nota
    )

    private fun herramientas(
        movimientos: List<Transaction> = emptyList(),
        cuentas: List<Account> = listOf(cuenta),
        pagos: List<RecurringPayment> = emptyList()
    ): Pair<HerramientasDelAgente, FakeTransactionRepository> {
        val transacciones = FakeTransactionRepository().apply { saved.addAll(movimientos) }
        val cuentasRepo = FakeAccountRepository(cuentas)
        val categorias = FakeCategoryRepository(listOf(comida, transporte))
        val recurrentes = FakeRecurringPaymentRepository(pagos)

        return HerramientasDelAgente(
            transactionRepository = transacciones,
            categoryRepository = categorias,
            accountRepository = cuentasRepo,
            recurringPaymentRepository = recurrentes,
            addTransaction = AddTransactionUseCase(
                transactionRepository = transacciones,
                accountRepository = cuentasRepo,
                categoryRepository = categorias,
                reloj = RELOJ_FIJO
            ),
            reloj = RELOJ_FIJO
        ) to transacciones
    }

    // ---------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------

    @Test
    fun el_resumen_del_mes_agrega_y_ordena_las_categorias() = runTest {
        val (agente, _) = herramientas(
            listOf(
                gasto(1L, 80_000, "Mercado"),
                gasto(1L, 20_000, "Panaderia"),
                gasto(2L, 50_000, "Uber")
            )
        )

        val resumen = agente.resumenDelMes()

        assertEquals(150_000L * 100L, resumen.gastos.cents)
        assertEquals("Comida y mercado", resumen.categorias.first().nombre)
        assertEquals(100_000L * 100L, resumen.categorias.first().total.cents)
        assertEquals(67, resumen.categorias.first().porcentaje)
    }

    @Test
    fun buscar_encuentra_por_texto_del_titulo() = runTest {
        val (agente, _) = herramientas(
            listOf(gasto(1L, 80_000, "Mercado del sabado"), gasto(2L, 50_000, "Uber"))
        )

        val encontrados = agente.buscarMovimientos(texto = "mercado")

        assertEquals(1, encontrados.size)
        assertEquals("Mercado del sabado", encontrados.first().title)
    }

    @Test
    fun buscar_resuelve_el_nombre_de_la_categoria_que_diria_el_usuario() = runTest {
        val (agente, _) = herramientas(
            listOf(gasto(1L, 80_000, "Mercado"), gasto(2L, 50_000, "Uber"))
        )

        // El usuario dice "comida"; la categoria se llama "Comida y mercado".
        val encontrados = agente.buscarMovimientos(categoria = "comida")

        assertEquals(1, encontrados.size)
        assertEquals("Mercado", encontrados.first().title)
    }

    @Test
    fun buscar_por_una_categoria_inexistente_no_devuelve_nada() = runTest {
        val (agente, _) = herramientas(listOf(gasto(1L, 80_000, "Mercado")))

        assertTrue(agente.buscarMovimientos(categoria = "criptomonedas").isEmpty())
    }

    @Test
    fun los_pagos_pendientes_excluyen_los_que_faltan_semanas() = runTest {
        val vencido = RecurringPayment(
            id = 1L,
            name = "Arriendo",
            amount = Money(900_000_00L),
            frequency = RecurringFrequency.Monthly(5),
            nextDueDate = LocalDate(2026, 8, 5),
            accountId = 1L
        )
        val lejano = RecurringPayment(
            id = 2L,
            name = "Seguro",
            amount = Money(120_000_00L),
            frequency = RecurringFrequency.Monthly(28),
            nextDueDate = LocalDate(2026, 9, 28),
            accountId = 1L
        )
        val (agente, _) = herramientas(pagos = listOf(vencido, lejano))

        val pendientes = agente.pagosPendientes()

        assertEquals(1, pendientes.size)
        assertEquals("Arriendo", pendientes.first().nombre)
        assertTrue(pendientes.first().estaVencido)
    }

    // ---------------------------------------------------------------
    // Escritura
    // ---------------------------------------------------------------

    @Test
    fun registrar_gasto_guarda_el_movimiento_y_describe_lo_guardado() = runTest {
        val (agente, repo) = herramientas()

        val resultado = agente.registrarGasto(
            monto = "25.000",
            titulo = "Almuerzo",
            categoria = "Comida y mercado"
        )

        assertTrue(resultado is ResultadoDeRegistro.Exito)
        assertEquals(1, repo.saved.size)
        assertEquals(25_000L * 100L, repo.saved.first().amount.cents)
        assertEquals("Almuerzo", repo.saved.first().title)
        assertEquals(1L, repo.saved.first().categoryId)
        assertTrue((resultado as ResultadoDeRegistro.Exito).descripcion.contains("Almuerzo"))
    }

    @Test
    fun registrar_gasto_con_un_monto_ilegible_se_rechaza_sin_guardar_nada() = runTest {
        val (agente, repo) = herramientas()

        val resultado = agente.registrarGasto(monto = "un poco", titulo = "Almuerzo")

        assertTrue(resultado is ResultadoDeRegistro.Rechazado)
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun registrar_gasto_sin_ninguna_cuenta_se_rechaza_con_un_motivo_legible() = runTest {
        val (agente, repo) = herramientas(cuentas = emptyList())

        val resultado = agente.registrarGasto(monto = "25.000", titulo = "Almuerzo")

        assertTrue(resultado is ResultadoDeRegistro.Rechazado)
        assertTrue((resultado as ResultadoDeRegistro.Rechazado).motivo.contains("cuenta"))
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun una_categoria_que_el_agente_invento_no_impide_registrar_el_gasto() = runTest {
        val (agente, repo) = herramientas()

        // El modelo puede citar una categoria que no existe. El gasto se
        // registra igual, sin categoria, en vez de perderse.
        val resultado = agente.registrarGasto(
            monto = "10.000",
            titulo = "Cafe",
            categoria = "Antojos varios"
        )

        assertTrue(resultado is ResultadoDeRegistro.Exito)
        assertEquals(1, repo.saved.size)
        assertEquals(null, repo.saved.first().categoryId)
    }

    @Test
    fun la_tendencia_que_ve_el_agente_es_la_misma_que_pinta_la_pantalla() = runTest {
        val (agente, _) = herramientas(listOf(gasto(1L, 150_000, "Mercado")))

        val tendencia = agente.tendenciaDelGasto()

        assertEquals(6, tendencia.serie.size)
        assertTrue(tendencia.hayDatos)
        assertFalse(tendencia.serie.last().estaVacio)
        assertEquals(150_000L * 100L, tendencia.serie.last().gastos.cents)
    }
}
