package com.nexcode.gastos.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.presentation.theme.TextSecondary
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.nexcode.gastos.domain.util.menosDias
import com.nexcode.gastos.presentation.util.formatear
import com.nexcode.gastos.presentation.util.hoy
import kotlinx.datetime.LocalDate

private val DAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "CO"))

/** Encabezado de dia con el total neto de esa fecha. */
@Composable
fun DateHeader(
    date: LocalDate,
    dayTotal: Money,
    modifier: Modifier = Modifier
) {
    val label = when (date) {
        hoy() -> "Hoy"
        hoy().menosDias(1) -> "Ayer"
        else -> date.formatear(DAY_FORMAT).replaceFirstChar { it.uppercase() }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "$ " + dayTotal.format(withSign = true),
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
