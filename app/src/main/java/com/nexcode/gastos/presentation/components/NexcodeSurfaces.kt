package com.nexcode.gastos.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.HeaderGradientColors
import com.nexcode.gastos.presentation.theme.NexcodeGradientColors
import com.nexcode.gastos.presentation.theme.NexcodeSurface
import com.nexcode.gastos.presentation.theme.TextOnBrand
import com.nexcode.gastos.presentation.theme.WhiteAlpha70

/**
 * Piezas visuales tomadas de NEXCODE Banca digital para que las dos apps se
 * vean de la misma familia: encabezado con degradado y esquinas inferiores
 * redondeadas, tarjetas blancas con borde suave y botones con degradado.
 */

/** Separacion de las tarjetas respecto al borde de la pantalla. */
val MargenLateral = 16.dp

/** Relleno interior por defecto de [NexcodeCard]. */
val RellenoTarjeta = 16.dp

/** Radio de las esquinas, comun al encabezado y a las tarjetas. */
val RadioTarjeta = 20.dp

/**
 * Distancia entre el borde de la pantalla y el texto que va dentro de una
 * tarjeta. El encabezado aplica esta misma medida a su contenido para que sus
 * lineas queden a plomo con las de las tarjetas de abajo.
 *
 * Se declara derivada y no como un 32 dp suelto: si manana cambia el margen o
 * el relleno, la alineacion se mantiene sola en vez de romperse en silencio.
 */
val AlineacionDeTexto = MargenLateral + RellenoTarjeta

/** Encabezado azul con las esquinas de abajo redondeadas (34 dp, como el banco). */
@Composable
fun NexcodeHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .background(Brush.linearGradient(HeaderGradientColors))
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 22.dp)
        ) {
            Text(
                text = title,
                color = TextOnBrand,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            // Elvis implicito: el subtitulo es opcional.
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = WhiteAlpha70,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            content()
        }
    }
}

/** Tarjeta blanca con borde suave, el patron de tarjeta del banco. */
@Composable
fun NexcodeCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    elevation: Int = 2,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(containerColor = NexcodeSurface),
        border = BorderStroke(1.dp, DividerSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}

/**
 * Boton con el degradado de marca (18 dp de radio, igual que boton_degradado
 * del banco). Al presionarlo se encoge un poco con una animacion de resorte.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Int = 52,
    colors: List<Color> = NexcodeGradientColors,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "escala-boton"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) Brush.horizontalGradient(colors)
                else Brush.horizontalGradient(listOf(DividerSoft, DividerSoft))
            )
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = TextOnBrand,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = WhiteAlpha70
            ),
            elevation = null,
            modifier = Modifier.fillMaxWidth().height(height.dp)
        ) {
            if (content != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = content)
            } else {
                Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
