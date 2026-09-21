package com.nexcode.gastos.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.nexcode.gastos.domain.model.Money

/**
 * Saldo con contador animado.
 *
 * Cuando el monto cambia, el numero no salta: recorre el camino hasta el valor
 * nuevo en 700 ms. Se anima sobre los CENTAVOS y se vuelve a construir un
 * [Money] en cada cuadro, asi el formato colombiano nunca se rompe.
 */
@Composable
fun AnimatedMoneyText(
    money: Money,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    withSign: Boolean = false,
    prefix: String = "$ ",
    brush: Brush? = null,
    fontWeight: FontWeight = FontWeight.Bold,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    val animatedCents by animateFloatAsState(
        targetValue = money.cents.toFloat(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "contador-saldo"
    )

    val text = prefix + Money(animatedCents.toLong()).format(withSign = withSign)

    if (brush != null) {
        // El degradado del logo pintado sobre el propio texto.
        Text(
            text = text,
            style = TextStyle(brush = brush, fontSize = fontSize, fontWeight = fontWeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    } else {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}
