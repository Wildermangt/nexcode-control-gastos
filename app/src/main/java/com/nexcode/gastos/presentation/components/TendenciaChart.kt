package com.nexcode.gastos.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.domain.model.PuntoMensual
import com.nexcode.gastos.domain.model.Tendencia
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.IncomeGreen
import com.nexcode.gastos.presentation.theme.NexcodeBlue
import com.nexcode.gastos.presentation.theme.NexcodeSurfaceElevated
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary

private val ALTURA_BARRA = 96.dp

/**
 * Analitica del gasto: barras de los ultimos meses e indicadores derivados.
 *
 * La pantalla no calcula nada. La serie, el promedio, la variacion y la
 * proyeccion llegan ya resueltos desde `AnalizarTendenciaUseCase`; aqui solo
 * se convierte cada importe en una altura y se pinta.
 */
@Composable
fun TendenciaChart(
    tendencia: Tendencia,
    modifier: Modifier = Modifier
) {
    if (!tendencia.hayDatos) return

    val maximo = tendencia.gastoMaximo.cents.coerceAtLeast(1L)
    val mesActual = tendencia.mesActual

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(tendencia) { started = true }

    val crecimiento by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "crecimiento-barras"
    )

    NexcodeCard(modifier = modifier) {
        Text(
            text = "Como vienes gastando",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Ultimos " + tendencia.serie.size + " meses",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            tendencia.serie.forEach { punto ->
                BarraDelMes(
                    punto = punto,
                    fraccion = punto.gastos.cents.toFloat() / maximo.toFloat() * crecimiento,
                    esActual = punto.mes == mesActual?.mes,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Indicadores(tendencia)
    }
}

/** Una barra con su etiqueta de mes. El mes en curso va en color de marca. */
@Composable
private fun BarraDelMes(
    punto: PuntoMensual,
    fraccion: Float,
    esActual: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTURA_BARRA),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Riel de fondo: deja ver que el mes existe aunque valga cero.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ALTURA_BARRA)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NexcodeSurfaceElevated)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Altura minima visible: una barra de cero pixeles se
                    // confunde con un mes que no se cargo.
                    .height(ALTURA_BARRA * fraccion.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (esActual) NexcodeBlue else Color(0xFFB9CBE0))
            )
        }

        Text(
            text = punto.mes.nombreCorto(),
            color = if (esActual) TextPrimary else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (esActual) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** Las tres lecturas que el dominio ya calculo. */
@Composable
private fun Indicadores(tendencia: Tendencia) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 12.dp)
            .height(1.dp)
            .background(DividerSoft)
    )

    FilaIndicador(
        etiqueta = "Promedio de los meses cerrados",
        valor = tendencia.promedioMensual.format()
    )
    FilaIndicador(
        etiqueta = "Ritmo diario de este mes",
        valor = tendencia.promedioDiario.format()
    )
    FilaIndicador(
        etiqueta = "Proyeccion al cierre del mes",
        valor = tendencia.proyeccionCierre.format(),
        color = if (tendencia.superaraElPromedio) ExpenseRed else IncomeGreen
    )

    // Safe call: sin mes anterior con gasto no hay variacion que mostrar.
    tendencia.variacionEnPorcentaje?.let { variacion ->
        val sube = variacion > 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (sube) ExpenseRed else IncomeGreen)
            )
            Text(
                text = if (variacion == 0) {
                    "Gastas igual que el mes pasado."
                } else if (sube) {
                    "Llevas un " + variacion + "% mas de gasto que el mes pasado."
                } else {
                    "Llevas un " + (-variacion) + "% menos de gasto que el mes pasado."
                },
                color = TextMedium,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FilaIndicador(
    etiqueta: String,
    valor: String,
    color: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = etiqueta, color = TextMedium, fontSize = 13.sp)
        Text(text = valor, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
