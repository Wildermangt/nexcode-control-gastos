package com.nexcode.gastos.domain.usecase.analytics

import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.PuntoMensual
import com.nexcode.gastos.domain.model.Tendencia
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.repository.TransactionRepository
import com.nexcode.gastos.domain.util.MesDelAnio
import com.nexcode.gastos.domain.util.RelojDominio
import com.nexcode.gastos.domain.util.hoy
import com.nexcode.gastos.domain.util.masMeses
import com.nexcode.gastos.domain.util.menosMeses
import com.nexcode.gastos.domain.util.primerDiaDelMes
import com.nexcode.gastos.domain.util.ultimoDiaDelMes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Analitica de datos del gasto: construye la serie mensual y sus indicadores.
 *
 * Responde a la tercera parte de la pregunta problema —analizar— alli donde
 * [GetCategorySummaryUseCase] responde a la segunda: aquel reparte un mes
 * entre categorias, y este compara un mes con los que lo precedieron.
 *
 * Es el caso de uso que documenta el pilar de estructuras y ciclos, porque
 * necesita las dos herramientas por motivos distintos: funciones de orden
 * superior para recorrer los movimientos que ya existen, y un ciclo explicito
 * para generar el eje de meses, que no existe en los datos.
 */
class AnalizarTendenciaUseCase(
    private val transactionRepository: TransactionRepository,
    private val reloj: RelojDominio
) {

    private val balanceCalculator = CalculateBalanceUseCase(transactionRepository)

    operator fun invoke(meses: Int = MESES_SERIE): Flow<Tendencia> =
        transactionRepository.observeAll().map { transactions ->
            analizar(transactions, reloj.hoy(), meses)
        }

    /** Version pura: sin base de datos, se puede probar con listas en memoria. */
    fun analizar(
        transactions: List<Transaction>,
        hoy: LocalDate,
        meses: Int = MESES_SERIE
    ): Tendencia {
        require(meses > 0) { "La serie debe cubrir al menos un mes" }

        // Recorrido de una coleccion que YA existe: funcion de orden superior.
        val porMes: Map<MesDelAnio, List<Transaction>> =
            transactions.groupBy { MesDelAnio.de(it.dateTime) }

        // ---------------------------------------------------------------
        // CICLO EXPLICITO. Aqui no se recorre una coleccion: se GENERA una.
        //
        // El eje de meses no esta en los datos. Un mes sin movimientos no
        // aparece en `porMes`, y una serie con huecos no se puede comparar ni
        // graficar: el mes de vacaciones en que no se gasto nada es
        // precisamente el que hay que ver. Por eso el ciclo va sobre el
        // calendario y consulta los datos, y no al reves.
        //
        // La cota superior es demostrable: exactamente `meses` vueltas.
        // ---------------------------------------------------------------
        val serie = ArrayList<PuntoMensual>(meses)
        var cursor: LocalDate = hoy.primerDiaDelMes().menosMeses(meses - 1)

        for (i in 0 until meses) {
            val mes = MesDelAnio.de(cursor)
            // orEmpty(): un mes sin movimientos vale una lista vacia, no null.
            val delMes = porMes[mes].orEmpty()
            val balance = balanceCalculator.calculate(delMes)

            serie.add(
                PuntoMensual(
                    mes = mes,
                    ingresos = balance.income,
                    gastos = balance.expense,
                    movimientos = delMes.size
                )
            )
            cursor = cursor.masMeses(1)
        }

        return construirIndicadores(serie, hoy)
    }

    /**
     * Deriva los indicadores de una serie ya construida.
     *
     * El promedio excluye a proposito el mes en curso: comparar un mes
     * incompleto contra una media que lo incluye da siempre una lectura
     * optimista, y el objetivo del analisis es lo contrario.
     */
    private fun construirIndicadores(serie: List<PuntoMensual>, hoy: LocalDate): Tendencia {
        val mesActual = serie.last()

        val cerrados = serie.dropLast(1).filter { !it.estaVacio }
        val promedioMensual =
            if (cerrados.isEmpty()) Money.ZERO
            else Money(cerrados.sumOf { it.gastos.cents } / cerrados.size)

        // Safe call + takeIf: sin mes anterior, o sin gasto en el, no hay
        // porcentaje que calcular. Dividir por cero seria el error clasico.
        val previo = serie.getOrNull(serie.lastIndex - 1)
        val variacion = previo
            ?.takeIf { it.gastos.isPositive }
            ?.let { anterior ->
                (mesActual.gastos.cents - anterior.gastos.cents).toFloat() /
                    anterior.gastos.cents.toFloat()
            }

        val diasTranscurridos = hoy.dayOfMonth
        val diasDelMes = ultimoDiaDelMes(hoy.year, hoy.monthNumber)
        val promedioDiario = Money(mesActual.gastos.cents / diasTranscurridos)

        return Tendencia(
            serie = serie,
            promedioMensual = promedioMensual,
            variacion = variacion,
            promedioDiario = promedioDiario,
            proyeccionCierre = promedioDiario * diasDelMes,
            diasTranscurridos = diasTranscurridos,
            diasDelMes = diasDelMes
        )
    }

    companion object {
        /** Medio ano: suficiente para ver un patron sin exigir un ano de uso. */
        const val MESES_SERIE = 6
    }
}
