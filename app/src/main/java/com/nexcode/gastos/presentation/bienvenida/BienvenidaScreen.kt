package com.nexcode.gastos.presentation.bienvenida

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.R
import com.nexcode.gastos.presentation.components.GradientButton
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.NexcodeBackground
import com.nexcode.gastos.presentation.theme.NexcodeBlue
import com.nexcode.gastos.presentation.theme.NexcodeGradientColors
import com.nexcode.gastos.presentation.theme.NexcodeMintDeep
import com.nexcode.gastos.presentation.theme.NexcodeSurfaceElevated
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Una lamina de la bienvenida.
 *
 * [gesto] es la instruccion concreta: la frase que el usuario tiene que poder
 * repetir con el dedo en cuanto cierre la bienvenida. Sin ella la lamina
 * describe una funcion en vez de ensenar a usarla.
 */
private data class Lamina(
    val icono: Int,
    val titulo: String,
    val descripcion: String,
    val gesto: String,
    val tinte: Color
)

private val LAMINAS = listOf(
    Lamina(
        icono = R.drawable.ic_wallet,
        titulo = "Registra en dos toques",
        descripcion = "Toca el botón + de la barra inferior, escribe el monto y guarda. " +
            "Lo único obligatorio es el monto: la categoría y la nota puedes dejarlas en blanco.",
        gesto = "Para corregir un movimiento, tócalo. Para borrarlo, deslízalo hacia la izquierda.",
        tinte = NexcodeBlue
    ),
    Lamina(
        icono = R.drawable.ic_pocket,
        titulo = "Guarda tus pagos fijos",
        descripcion = "En el bolsillo anotas el arriendo, los servicios o las suscripciones " +
            "con su fecha. La aplicación te avisa el día que vencen, aunque la tengas cerrada.",
        gesto = "Cuando pagues uno, deslízalo hacia la derecha: se registra como gasto y salta al mes siguiente.",
        tinte = NexcodeMintDeep
    ),
    Lamina(
        icono = R.drawable.ic_chat,
        titulo = "Mira en qué se te va",
        descripcion = "El inicio te muestra dos cosas distintas: la dona reparte el gasto del " +
            "mes entre tus categorías, y las barras comparan este mes con los cinco anteriores.",
        gesto = "Fíjate en la proyección al cierre: te dice cómo vas a terminar el mes si sigues a este ritmo.",
        tinte = NexcodeBlue
    ),
    Lamina(
        icono = R.drawable.ic_mic,
        titulo = "Pregúntale al asistente",
        descripcion = "En la pestaña de mensajes puedes preguntar en tus propias palabras: " +
            "«¿cuánto llevo gastado?» o «¿en qué me estoy pasando?».",
        gesto = "Toca el micrófono para hablarle en vez de escribir: te responde también en voz alta.",
        tinte = NexcodeMintDeep
    )
)

/**
 * Bienvenida de la primera vez: como se usa la aplicacion, en cuatro laminas.
 *
 * Ofrece las dos salidas que el usuario espera de una pantalla asi:
 *  - **Saltar**, que la cierra ahora pero la deja disponible en el proximo
 *    arranque, por si la descarto sin querer;
 *  - **No volver a mostrar**, marcada por defecto, que es la que la retira
 *    para siempre.
 *
 * Separarlas importa: un usuario que toca "Saltar" por impaciencia no esta
 * pidiendo perder la explicacion, y quien desmarca la casilla esta pidiendo
 * verla otra vez de forma explicita. Desde Perfil se puede volver a abrir.
 */
@Composable
fun BienvenidaScreen(
    onTerminar: (noVolverAMostrar: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val estadoPaginas = rememberPagerState(pageCount = { LAMINAS.size })
    val alcance = rememberCoroutineScope()
    var noVolverAMostrar by remember { mutableStateOf(true) }

    val esUltima = estadoPaginas.currentPage == LAMINAS.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexcodeBackground)
            .systemBarsPadding()
    ) {
        // --- Contador de paso y salida rapida --------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // El contador cambia con un desvanecido para que se note que
            // avanzo: un numero que se sustituye de golpe pasa inadvertido.
            AnimatedContent(
                targetState = estadoPaginas.currentPage + 1,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "contador-paso"
            ) { paso ->
                Text(
                    text = "Paso " + paso + " de " + LAMINAS.size,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }

            AnimatedVisibility(
                visible = !esUltima,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Saltar",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onTerminar(noVolverAMostrar) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        HorizontalPager(
            state = estadoPaginas,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { indice ->
            LaminaBienvenida(
                lamina = LAMINAS[indice],
                indice = indice,
                estadoPaginas = estadoPaginas
            )
        }

        // --- Indicador de pagina --------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LAMINAS.indices.forEach { indice ->
                val activo = indice == estadoPaginas.currentPage
                val ancho by animateDpAsState(
                    targetValue = if (activo) 22.dp else 8.dp,
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    label = "ancho-punto"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(ancho)
                        .clip(CircleShape)
                        .background(
                            if (activo) Brush.horizontalGradient(NexcodeGradientColors)
                            else Brush.horizontalGradient(listOf(DividerSoft, DividerSoft))
                        )
                )
            }
        }

        // --- No volver a mostrar --------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { noVolverAMostrar = !noVolverAMostrar }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = noVolverAMostrar,
                onCheckedChange = { noVolverAMostrar = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = NexcodeMintDeep,
                    uncheckedColor = TextSecondary
                )
            )
            Text(
                text = "No volver a mostrar esta bienvenida",
                color = TextMedium,
                fontSize = 13.sp
            )
        }

        // --- Accion principal ------------------------------------------------
        GradientButton(
            text = if (esUltima) "Comenzar" else "Siguiente",
            onClick = {
                if (esUltima) {
                    onTerminar(noVolverAMostrar)
                } else {
                    alcance.launch {
                        estadoPaginas.animateScrollToPage(estadoPaginas.currentPage + 1)
                    }
                }
            },
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp)
        )
    }
}

/**
 * Una lamina con su animacion de entrada.
 *
 * Se anima contra [PagerState] y no contra un `LaunchedEffect(Unit)` por un
 * motivo concreto: el pager mantiene compuestas las laminas vecinas, de modo
 * que un efecto de composicion se dispararia cuando la lamina todavia no se
 * ve y el usuario llegaria a ella con la animacion ya gastada.
 *
 * Se combinan dos movimientos:
 *  - la **entrada**, que solo corre en la lamina activa y escalona icono,
 *    titulo, descripcion y gesto;
 *  - el **paralaje**, ligado al arrastre del dedo, que desplaza el icono mas
 *    que el texto y da profundidad al gesto de pasar pagina.
 */
@Composable
private fun LaminaBienvenida(
    lamina: Lamina,
    indice: Int,
    estadoPaginas: PagerState
) {
    // Distancia de esta lamina al centro de la pantalla: 0 centrada, 1 justo
    // fuera. Sigue al dedo en tiempo real.
    //
    // Se entrega como funcion y no como valor a proposito: leer el
    // desplazamiento del pager en la composicion recompone la lamina entera en
    // cada fotograma del arrastre. Leido dentro de `graphicsLayer` solo se
    // repite la fase de dibujo, que es la unica que cambia.
    val distancia: () -> Float = {
        (indice - (estadoPaginas.currentPage + estadoPaginas.currentPageOffsetFraction))
            .coerceIn(-1f, 1f)
    }

    val esActual = estadoPaginas.currentPage == indice

    val entrada = animateFloatAsState(
        targetValue = if (esActual) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "entrada-lamina"
    )

    // El icono entra con rebote: es el unico elemento que puede permitirselo
    // sin que la pantalla parezca inestable.
    val escalaIcono = animateFloatAsState(
        targetValue = if (esActual) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "escala-icono"
    )

    // Latido continuo del halo: muy leve, solo para que la pantalla no quede
    // completamente quieta mientras el usuario lee.
    val latido = rememberInfiniteTransition(label = "latido-halo")
    val escalaHalo = latido.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "escala-halo"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer {
                    // Paralaje fuerte y giro leve: el icono se queda atras
                    // respecto del dedo.
                    val d = distancia()
                    translationX = d * size.width * 0.45f
                    rotationZ = d * -8f
                    scaleX = escalaIcono.value * escalaHalo.value
                    scaleY = escalaIcono.value * escalaHalo.value
                    alpha = 1f - d.absoluteValue * 0.6f
                }
                .clip(CircleShape)
                .background(NexcodeSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(lamina.icono),
                contentDescription = null,
                tint = lamina.tinte,
                modifier = Modifier.size(58.dp)
            )
        }

        TextoEscalonado(
            entrada = { entrada.value },
            distancia = distancia,
            retraso = 0f,
            paddingSuperior = 34
        ) {
            Text(
                text = lamina.titulo,
                color = TextPrimary,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        TextoEscalonado(
            entrada = { entrada.value },
            distancia = distancia,
            retraso = 0.18f,
            paddingSuperior = 14
        ) {
            Text(
                text = lamina.descripcion,
                color = TextMedium,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center
            )
        }

        // La instruccion concreta, destacada sobre el fondo suave: es lo que
        // el usuario debe recordar de la lamina.
        TextoEscalonado(
            entrada = { entrada.value },
            distancia = distancia,
            retraso = 0.36f,
            paddingSuperior = 22
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexcodeSurfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(lamina.tinte)
                )
                Text(
                    text = lamina.gesto,
                    color = TextMedium,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

/**
 * Envuelve un bloque de texto con su parte de la animacion escalonada.
 *
 * [retraso] es la fraccion del recorrido que este bloque espera antes de
 * empezar a aparecer: 0 arranca con el icono, 0,36 llega el ultimo. El
 * escalonado se calcula sobre el mismo valor de entrada en vez de con varias
 * animaciones encadenadas, que es mas codigo y se desincroniza al pasar
 * paginas deprisa.
 */
@Composable
private fun TextoEscalonado(
    entrada: () -> Float,
    distancia: () -> Float,
    retraso: Float,
    paddingSuperior: Int,
    contenido: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(top = paddingSuperior.dp)
            .graphicsLayer {
                val d = distancia()
                val avance = ((entrada() - retraso) / (1f - retraso)).coerceIn(0f, 1f)

                // Paralaje mas suave que el del icono: el texto acompana al
                // dedo, no se adelanta.
                translationX = d * size.width * 0.22f
                translationY = (1f - avance) * 26.dp.toPx()
                alpha = avance * (1f - d.absoluteValue * 0.8f).coerceAtLeast(0f)
            },
        contentAlignment = Alignment.Center
    ) {
        contenido()
    }
}
