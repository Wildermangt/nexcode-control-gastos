package com.nexcode.gastos.domain

import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.usecase.analytics.AnalizarTendenciaUseCase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas de la analitica del gasto.
 *
 * La funcion `analizar` es pura: recibe la lista de movimientos y la fecha de
 * hoy, y no consulta la base de datos. Eso permite fijar el calendario y
 * comprobar los indicadores con cifras exactas.
 */
class AnalizarTendenciaUseCaseTest {

    private val useCase = AnalizarTendenciaUseCase(
        transactionRepository = FakeTransactionRepository(),
        reloj = RELOJ_FIJO
    )

    /** 15 de agosto de 2026: mes de 31 dias, con 16 ya transcurridos. */
    private val hoy = LocalDate(2026, 8, 15)

    private fun gasto(mes: Int, pesos: Long, dia: Int = 10) = Transaction(
        accountId = 1L,
        type = TransactionType.Expense,
        amount = Money(pesos * 100L),
        dateTime = LocalDateTime(2026, mes, dia, 10, 0),
        title = "Gasto"
    )

    private fun ingreso(mes: Int, pesos: Long) = Transaction(
        accountId = 1L,
        type = TransactionType.Income,
        amount = Money(pesos * 100L),
        dateTime = LocalDateTime(2026, mes, 1, 10, 0),
        title = "Ingreso"
    )

    @Test
    fun la_serie_cubre_la_ventana_completa_y_termina_en_el_mes_actual() {
        val tendencia = useCase.analizar(listOf(gasto(8, 100_000)), hoy, meses = 6)

        assertEquals(6, tendencia.serie.size)
        assertEquals(3, tendencia.serie.first().mes.mes)   // marzo
        assertEquals(8, tendencia.serie.last().mes.mes)    // agosto
    }

    @Test
    fun los_meses_sin_movimientos_aparecen_en_cero_y_no_como_huecos() {
        // Solo hay gasto en junio y en agosto: la serie debe traer los seis.
        val tendencia = useCase.analizar(
            listOf(gasto(6, 50_000), gasto(8, 80_000)),
            hoy,
            meses = 6
        )

        assertEquals(6, tendencia.serie.size)
        val julio = tendencia.serie.first { it.mes.mes == 7 }
        assertTrue(julio.estaVacio)
        assertEquals(0L, julio.gastos.cents)
    }

    @Test
    fun separa_ingresos_de_gastos_en_cada_mes() {
        val tendencia = useCase.analizar(
            listOf(gasto(8, 30_000), ingreso(8, 200_000)),
            hoy,
            meses = 6
        )

        val agosto = tendencia.serie.last()
        assertEquals(200_000L * 100L, agosto.ingresos.cents)
        assertEquals(30_000L * 100L, agosto.gastos.cents)
        assertEquals(170_000L * 100L, agosto.saldo.cents)
        assertEquals(2, agosto.movimientos)
    }

    @Test
    fun el_promedio_excluye_el_mes_en_curso_y_los_meses_vacios() {
        // Junio 100.000, julio 200.000, agosto (en curso) 900.000.
        val tendencia = useCase.analizar(
            listOf(gasto(6, 100_000), gasto(7, 200_000), gasto(8, 900_000)),
            hoy,
            meses = 6
        )

        // Promedio de los DOS meses cerrados con movimientos, no de los cinco.
        assertEquals(150_000L * 100L, tendencia.promedioMensual.cents)
    }

    @Test
    fun calcula_la_variacion_frente_al_mes_anterior() {
        // Julio 200.000 -> agosto 250.000 es un 25 % mas.
        val tendencia = useCase.analizar(
            listOf(gasto(7, 200_000), gasto(8, 250_000)),
            hoy,
            meses = 6
        )

        assertTrue(abs(tendencia.variacion!! - 0.25f) < 0.001f)
        assertEquals(25, tendencia.variacionEnPorcentaje)
    }

    @Test
    fun sin_gasto_el_mes_anterior_no_hay_variacion_en_vez_de_division_por_cero() {
        val tendencia = useCase.analizar(listOf(gasto(8, 250_000)), hoy, meses = 6)

        assertNull(tendencia.variacion)
        assertFalse(tendencia.hayComparacion)
    }

    @Test
    fun proyecta_el_cierre_del_mes_con_el_ritmo_diario() {
        // 150.000 en 15 dias = 10.000 diarios; a 31 dias, 310.000 al cierre.
        val tendencia = useCase.analizar(listOf(gasto(8, 150_000)), hoy, meses = 6)

        assertEquals(15, tendencia.diasTranscurridos)
        assertEquals(31, tendencia.diasDelMes)
        assertEquals(10_000L * 100L, tendencia.promedioDiario.cents)
        assertEquals(310_000L * 100L, tendencia.proyeccionCierre.cents)
    }

    @Test
    fun avisa_cuando_la_proyeccion_supera_el_promedio_historico() {
        // Julio cerro en 100.000; agosto lleva 300.000 a mitad de mes.
        val tendencia = useCase.analizar(
            listOf(gasto(7, 100_000), gasto(8, 300_000)),
            hoy,
            meses = 6
        )

        assertTrue(tendencia.superaraElPromedio)
    }

    @Test
    fun sin_movimientos_la_serie_existe_pero_no_hay_datos_que_analizar() {
        val tendencia = useCase.analizar(emptyList(), hoy, meses = 6)

        assertEquals(6, tendencia.serie.size)
        assertFalse(tendencia.hayDatos)
        assertEquals(0L, tendencia.promedioMensual.cents)
        assertFalse(tendencia.superaraElPromedio)
    }
}
