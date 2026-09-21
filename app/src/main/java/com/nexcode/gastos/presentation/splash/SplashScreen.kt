package com.nexcode.gastos.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.nexcode.gastos.R
import com.nexcode.gastos.presentation.theme.NexcodeBackground
import com.nexcode.gastos.presentation.theme.NexcodeBlue
import com.nexcode.gastos.presentation.theme.NexcodeGradientColors
import com.nexcode.gastos.presentation.theme.NexcodeMint
import com.nexcode.gastos.presentation.theme.TextSecondary
import kotlinx.coroutines.delay

/** Lo que dura la pantalla como minimo, aunque la carga termine antes. */
private const val DURACION_MINIMA_MS = 2000L

/**
 * Pantalla de carga inicial.
 *
 * El simbolo entra creciendo con un rebote corto, el nombre aparece despues y
 * bajo ambos gira un arco de progreso. La animacion no es decorativa: cubre el
 * tiempo real en que la base de datos se abre y siembra sus datos, que en un
 * telefono lento no es instantaneo.
 *
 * Se mantiene un minimo de [DURACION_MINIMA_MS] para que el arranque no
 * parpadee cuando la carga es inmediata: un destello de medio segundo se
 * percibe como un fallo, no como rapidez.
 */
@Composable
fun SplashScreen(
    onTerminado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val version = remember {
        runCatching {
            contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    // Entrada del simbolo: de 0,6 a 1 con un rebote breve.
    val escala = remember { Animatable(0.6f) }
    val opacidadSimbolo = remember { Animatable(0f) }
    val opacidadTexto = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        opacidadSimbolo.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        escala.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    LaunchedEffect(Unit) {
        delay(280)
        opacidadTexto.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(Unit) {
        delay(DURACION_MINIMA_MS)
        onTerminado()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexcodeBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(contentAlignment = Alignment.Center) {
                ArcoDeCarga(modifier = Modifier.size(148.dp))

                Image(
                    painter = painterResource(R.drawable.simbolo_nexcode),
                    contentDescription = null,
                    modifier = Modifier
                        .size(92.dp)
                        .scale(escala.value)
                        .alpha(opacidadSimbolo.value)
                )
            }

            Text(
                text = "NEXCODE",
                style = TextStyle(
                    brush = Brush.horizontalGradient(NexcodeGradientColors),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp
                ),
                modifier = Modifier
                    .padding(top = 30.dp)
                    .alpha(opacidadTexto.value)
            )

            Text(
                text = "Control de gastos personales",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .alpha(opacidadTexto.value)
            )
        }

        Text(
            text = "Version $version",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .alpha(opacidadTexto.value)
        )
    }
}

/**
 * Arco que gira alrededor del simbolo mientras la app carga.
 *
 * Se dibuja con [Canvas] en vez de usar un indicador de Material para que
 * lleve el degradado de la marca y no el color por defecto del tema.
 */
@Composable
private fun ArcoDeCarga(modifier: Modifier = Modifier) {
    val transicion = rememberInfiniteTransition(label = "arco-carga")
    val giro by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "giro-arco"
    )

    Canvas(modifier = modifier) {
        val grosor = 3.dp.toPx()
        rotate(degrees = giro) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, NexcodeMint, NexcodeBlue)
                ),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = grosor, cap = StrokeCap.Round)
            )
        }
    }
}
