package com.nexcode.gastos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.presentation.theme.IncomeGreen
import com.nexcode.gastos.presentation.theme.NexcodeBlue
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary
import com.nexcode.gastos.presentation.theme.WarningAmber
import com.nexcode.gastos.sincronizacion.EstadoDeSincronizacion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FORMATO_HORA = SimpleDateFormat("d MMM, HH:mm", Locale("es", "CO"))

/**
 * Estado de la copia en la nube.
 *
 * Sin esta tarjeta la replica es invisible: el usuario no sabe si sus datos
 * estan a salvo o solo en el telefono, que es justo lo que la redundancia
 * pretende resolver.
 *
 * El boton manual existe porque el trabajo automatico corre cada quince
 * minutos —el minimo que admite el sistema— y eso es demasiado esperar cuando
 * alguien esta mirando.
 */
@Composable
fun TarjetaDeSincronizacion(
    estado: EstadoDeSincronizacion,
    onSincronizar: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexcodeCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colorDelEstado(estado))
            )
            Text(
                text = "Copia en la nube",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            text = descripcion(estado),
            color = TextMedium,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (estado.ultimaPasada > 0L) {
            Text(
                text = "Ultima vez: " + FORMATO_HORA.format(Date(estado.ultimaPasada)),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        estado.mensaje?.let { mensaje ->
            Text(
                text = mensaje,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (estado.hayNube) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (estado.sincronizando) {
                    CircularProgressIndicator(
                        color = NexcodeBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    TextButton(onClick = onSincronizar) {
                        Text("Sincronizar ahora", color = NexcodeBlue, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun colorDelEstado(estado: EstadoDeSincronizacion): Color = when {
    !estado.hayNube -> TextSecondary
    estado.pendientes > 0 -> WarningAmber
    estado.alDia -> IncomeGreen
    else -> TextSecondary
}

private fun descripcion(estado: EstadoDeSincronizacion): String = when {
    !estado.hayNube ->
        "Sin nube configurada. Tus datos viven solo en este telefono."

    estado.pendientes == 1 ->
        "1 cambio pendiente de subir. Se enviara solo en cuanto haya conexion."

    estado.pendientes > 1 ->
        estado.pendientes.toString() + " cambios pendientes de subir. " +
            "Se enviaran solos en cuanto haya conexion."

    estado.alDia ->
        "Al dia. Tus datos estan en el telefono y en la nube."

    else ->
        "Todo enviado. Falta la primera sincronizacion completa."
}
