package com.nexcode.gastos.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.domain.model.CategorySummary
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary

private const val MAX_SLICES = 6
private const val GAP_DEGREES = 3f

/**
 * Grafico de dona con el reparto del gasto por categoria.
 *
 * Se dibuja con [Canvas] y `drawArc`: cada categoria es un arco cuyo angulo
 * sale del porcentaje que ya calculo el dominio en `GetCategorySummaryUseCase`.
 * La pantalla no hace cuentas, solo pinta.
 *
 * La animacion barre los arcos de 0 a su angulo final en 900 ms.
 */
@Composable
fun CategoryDonutChart(
    summaries: List<CategorySummary>,
    modifier: Modifier = Modifier
) {
    if (summaries.isEmpty()) return

    // Las categorias pequenas se agrupan en "Otras" para que la dona se lea.
    val slices = remember(summaries) { buildSlices(summaries) }
    val total = remember(summaries) { Money(summaries.sumOf { it.total.cents }) }

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(summaries) { started = true }

    val sweepProgress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "barrido-dona"
    )

    NexcodeCard(modifier = modifier) {
        Text(
            text = "En que se te fue el mes",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(132.dp)) {
                    val stroke = 22.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)

                    // Aro de fondo: se ve incluso mientras la animacion avanza.
                    drawArc(
                        color = DividerSoft,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )

                    // Ciclo sobre las porciones: cada una arranca donde termino la anterior.
                    var startAngle = -90f
                    slices.forEach { slice ->
                        val fullSweep = slice.fraction * 360f
                        val sweep = (fullSweep - GAP_DEGREES).coerceAtLeast(0f) * sweepProgress
                        if (sweep > 0f) {
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke)
                            )
                        }
                        startAngle += fullSweep
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Gastado", color = TextSecondary, fontSize = 10.sp)
                    AnimatedMoneyText(
                        money = total,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        prefix = "$ "
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slices.forEach { slice ->
                    LegendRow(slice = slice)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(slice: DonutSlice) {
    val percent = (slice.fraction * 100).toInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(slice.color, CircleShape)
        )
        Text(
            text = slice.label,
            color = TextMedium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = percent.toString() + "%",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Una porcion de la dona ya lista para pintar. */
private data class DonutSlice(
    val label: String,
    val fraction: Float,
    val color: Color
)

/**
 * Convierte los resumenes del dominio en porciones.
 * Toma las cinco categorias mas grandes y suma el resto en "Otras".
 */
private fun buildSlices(summaries: List<CategorySummary>): List<DonutSlice> {
    val visible = summaries.take(MAX_SLICES - 1).map {
        DonutSlice(
            label = it.displayName,
            fraction = it.percentage,
            color = Color(it.colorArgb.toInt())
        )
    }

    val restFraction = summaries.drop(MAX_SLICES - 1).sumOf { it.percentage.toDouble() }.toFloat()

    return if (restFraction > 0.001f) {
        visible + DonutSlice(
            label = "Otras",
            fraction = restFraction,
            color = Color(0xFF8494A8)
        )
    } else {
        visible
    }
}
