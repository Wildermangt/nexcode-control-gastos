package com.nexcode.gastos.domain.model

import com.nexcode.gastos.domain.util.MesDelAnio

/**
 * Un mes de la serie historica.
 *
 * Existe aunque el usuario no haya registrado nada: un mes vacio es un dato,
 * no un hueco, y la serie debe poder mostrarlo.
 */
data class PuntoMensual(
    val mes: MesDelAnio,
    val ingresos: Money = Money.ZERO,
    val gastos: Money = Money.ZERO,
    val movimientos: Int = 0
) {
    val saldo: Money get() = ingresos - gastos

    val estaVacio: Boolean get() = movimientos == 0
}

/**
 * Resultado de la analitica del gasto: la serie mensual mas los indicadores
 * derivados de ella.
 *
 * Los indicadores se calculan en el dominio y no en la pantalla: la interfaz
 * solo pinta lo que este objeto ya trae resuelto. Lo construye
 * [com.nexcode.gastos.domain.usecase.analytics.AnalizarTendenciaUseCase].
 */
data class Tendencia(
    val serie: List<PuntoMensual> = emptyList(),
    /** Gasto medio de los meses anteriores ya cerrados. */
    val promedioMensual: Money = Money.ZERO,
    /** Variacion del gasto frente al mes anterior. -0,15 = un 15 % menos. */
    val variacion: Float? = null,
    val promedioDiario: Money = Money.ZERO,
    /** Gasto estimado al cierre del mes si se mantiene el ritmo actual. */
    val proyeccionCierre: Money = Money.ZERO,
    val diasTranscurridos: Int = 0,
    val diasDelMes: Int = 0
) {
    val mesActual: PuntoMensual? get() = serie.lastOrNull()

    val mesAnterior: PuntoMensual? get() = serie.getOrNull(serie.lastIndex - 1)

    /** Sin ningun movimiento en toda la ventana no hay nada que analizar. */
    val hayDatos: Boolean get() = serie.any { !it.estaVacio }

    /** Escala del grafico de barras: el mes que mas peso en la ventana. */
    val gastoMaximo: Money get() = Money(serie.maxOfOrNull { it.gastos.cents } ?: 0L)

    /** Cierto cuando el mes va camino de gastar mas que un mes promedio. */
    val superaraElPromedio: Boolean
        get() = promedioMensual.isPositive && proyeccionCierre > promedioMensual

    /** La comparacion solo tiene sentido si el mes anterior tuvo gasto. */
    val hayComparacion: Boolean get() = variacion != null

    /** Variacion en puntos porcentuales enteros, lista para mostrar. */
    val variacionEnPorcentaje: Int?
        get() = variacion?.let { kotlin.math.round(it * 100f).toInt() }
}
