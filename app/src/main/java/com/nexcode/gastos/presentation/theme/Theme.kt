package com.nexcode.gastos.presentation.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Esquema CLARO: fondo blanco con los colores del logo como acento.
 * Sobre los rellenos de color el texto va en blanco.
 */
private val NexcodeLightColors = lightColorScheme(
    primary = NexcodeBlue,
    onPrimary = Color.White,
    primaryContainer = NexcodeSurfaceElevated,
    onPrimaryContainer = NexcodeBlueDeep,
    secondary = NexcodeMintDeep,
    onSecondary = Color.White,
    tertiary = NexcodeCyan,
    onTertiary = Color.White,
    error = ExpenseRed,
    onError = Color.White,
    background = NexcodeBackground,
    onBackground = TextPrimary,
    surface = NexcodeBackground,
    onSurface = TextPrimary,
    surfaceVariant = NexcodeSurface,
    onSurfaceVariant = TextSecondary,
    outline = DividerSoft,

    // Material 3 no pinta los dialogos, menus y hojas con `surface` sino con
    // la familia `surfaceContainer`. Al no declararla, tomaba la paleta lila
    // que Material trae de fabrica y los cuadros de dialogo salian morados
    // sobre una aplicacion azul y blanca. Se fijan aqui, y no en cada
    // componente, para que cualquier dialogo futuro nazca ya con la marca.
    surfaceContainerLowest = NexcodeSurface,
    surfaceContainerLow = NexcodeSurface,
    surfaceContainer = NexcodeSurface,
    surfaceContainerHigh = NexcodeSurface,
    surfaceContainerHighest = NexcodeSurfaceSoft,

    // El tinte de elevacion tambien es morado por defecto: con el azul de la
    // marca, las sombras y superficies elevadas no desentonan.
    surfaceTint = NexcodeBlue
)

private val NexcodeTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

/**
 * Degradado del logo en movimiento continuo.
 *
 * Recorre el eje horizontal de un lado a otro, igual que la X del logo pasa
 * de azul a verde. Se usa en la tarjeta de saldo y en el boton flotante.
 */
@Composable
fun rememberAnimatedNexcodeBrush(
    durationMillis: Int = 6000,
    travel: Float = 900f
): Brush {
    val transition = rememberInfiniteTransition(label = "degradado-nexcode")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = travel,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "desplazamiento"
    )

    return Brush.linearGradient(
        colors = NexcodeGradientColors,
        start = Offset(offset - travel, 0f),
        end = Offset(offset, travel)
    )
}

/**
 * Nexcode se pinta siempre sobre blanco, igual que el logo de la marca:
 * el tema no cambia con el modo oscuro del sistema para no romper la
 * identidad visual.
 */
@Composable
fun NexcodeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NexcodeLightColors,
        typography = NexcodeTypography,
        content = content
    )
}
