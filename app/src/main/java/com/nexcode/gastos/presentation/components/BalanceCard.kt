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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.domain.model.Balance
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.presentation.theme.BalanceGradientColors
import com.nexcode.gastos.presentation.theme.HeaderGradientColors
import com.nexcode.gastos.presentation.theme.NexcodeBlueDeep
import com.nexcode.gastos.presentation.theme.NexcodeMint
import com.nexcode.gastos.presentation.theme.TextOnBrand
import com.nexcode.gastos.presentation.theme.WhiteAlpha20
import com.nexcode.gastos.presentation.theme.WhiteAlpha15
import com.nexcode.gastos.presentation.theme.WhiteAlpha70

/**
 * Encabezado de la pantalla principal, con la misma estructura que el inicio
 * de NEXCODE Banca digital: bloque con degradado azul, esquinas inferiores
 * redondeadas a 34 dp y el saldo en blanco encima.
 */
@Composable
fun BalanceHeader(
    balance: Balance,
    periodLabel: String,
    greeting: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // El panel flota con el mismo margen lateral que las tarjetas de
            // abajo y con su mismo radio, de modo que los tres bloques de la
            // pantalla comparten ancho y esquinas.
            .padding(horizontal = MargenLateral)
            .clip(RoundedCornerShape(RadioTarjeta))
            .background(Brush.linearGradient(HeaderGradientColors))
    ) {
        Column(
            // El relleno interior es el de una tarjeta: asi el texto cae en el
            // mismo eje vertical que el de «En que se te fue el mes».
            modifier = Modifier.padding(
                start = RellenoTarjeta,
                end = RellenoTarjeta,
                top = 16.dp,
                bottom = 20.dp
            )
        ) {
            // Saludo y periodo comparten linea: ninguno de los dos es el dato
            // principal de la pantalla, y juntos liberan el espacio que ahora
            // ocupa el saldo.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    color = TextOnBrand,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = periodLabel.uppercase(),
                    color = TextOnBrand,
                    fontSize = 10.5.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(WhiteAlpha15)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Text(
                text = "Saldo del mes",
                color = WhiteAlpha70,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 22.dp)
            )
            AnimatedMoneyText(
                money = balance.total,
                fontSize = 38.sp,
                color = TextOnBrand,
                modifier = Modifier.padding(top = 2.dp)
            )

            ProportionBar(
                income = balance.income.cents,
                expense = balance.expense.cents,
                modifier = Modifier.padding(top = 20.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmountPill(
                    label = "Ingresos",
                    money = balance.income,
                    dotColor = NexcodeMint,
                    modifier = Modifier.weight(1f)
                )
                AmountPill(
                    label = "Gastos",
                    money = balance.expense,
                    dotColor = Color(0xFFFF9AA2),
                    modifier = Modifier.weight(1f)
                )
            }

            balance.savingsRate?.let { rate ->
                Text(
                    text = "Tasa de ahorro " + (rate * 100).toInt() + "%",
                    color = WhiteAlpha70,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

/**
 * Tarjeta de saldo con el degradado de los tres tonos del logotipo, el mismo
 * recurso que usa NEXCODE Banca digital (fondo_tarjeta_saldo: 26 dp de radio
 * y sombra de 10 dp).
 *
 * Animaciones:
 *  1. el contador del saldo recorre el camino hasta el valor nuevo,
 *  2. la barra de proporcion ingresos/gastos crece al cambiar los datos.
 */
@Composable
fun BalanceCard(
    balance: Balance,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = NexcodeBlueDeep,
                spotColor = NexcodeBlueDeep
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = BalanceGradientColors,
                    start = androidx.compose.ui.geometry.Offset(0f, 600f),
                    end = androidx.compose.ui.geometry.Offset(900f, 0f)
                )
            )
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = periodLabel.uppercase(),
                color = WhiteAlpha70,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "Saldo del mes",
                color = TextOnBrand,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp)
            )

            AnimatedMoneyText(
                money = balance.total,
                fontSize = 36.sp,
                color = TextOnBrand,
                modifier = Modifier.padding(top = 2.dp)
            )

            ProportionBar(
                income = balance.income.cents,
                expense = balance.expense.cents,
                modifier = Modifier.padding(top = 18.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmountPill(
                    label = "Ingresos",
                    money = balance.income,
                    dotColor = NexcodeMint,
                    modifier = Modifier.weight(1f)
                )
                AmountPill(
                    label = "Gastos",
                    money = balance.expense,
                    dotColor = Color(0xFFFF9AA2),
                    modifier = Modifier.weight(1f)
                )
            }

            // Tasa de ahorro: solo aparece si hubo ingresos (null safety).
            balance.savingsRate?.let { rate ->
                Text(
                    text = "Tasa de ahorro: " + (rate * 100).toInt() + "%",
                    color = WhiteAlpha70,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/** Barra que muestra la proporcion entre ingresos y gastos, animada. */
@Composable
private fun ProportionBar(
    income: Long,
    expense: Long,
    modifier: Modifier = Modifier
) {
    val total = (income + expense).coerceAtLeast(1L)
    val targetIncome = income.toFloat() / total.toFloat()

    val incomeWeight by animateFloatAsState(
        targetValue = targetIncome.coerceIn(0.001f, 0.999f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "proporcion-ingresos"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(WhiteAlpha20)
    ) {
        Box(
            modifier = Modifier
                .weight(incomeWeight)
                .fillMaxWidth()
                .height(6.dp)
                .background(NexcodeMint)
        )
        Box(
            modifier = Modifier
                .weight(1f - incomeWeight)
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0xFFFF9AA2))
        )
    }
}

@Composable
private fun AmountPill(
    label: String,
    money: Money,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    // El punto va en la MISMA linea que la etiqueta, no centrado contra el
    // bloque de dos lineas: asi queda a la altura de la palabra que nombra
    // y no flotando entre el rotulo y la cifra.
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Text(text = label, color = WhiteAlpha70, fontSize = 12.sp)
        }
        AnimatedMoneyText(
            money = money,
            fontSize = 16.sp,
            color = TextOnBrand,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
