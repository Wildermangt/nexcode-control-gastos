package com.nexcode.gastos.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexcode.gastos.R
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.NexcodeMint

/**
 * Contenedor con controles de deslizamiento.
 *
 *  - Deslizar hacia la IZQUIERDA descarta el elemento y ejecuta [onSwipeLeft].
 *  - Deslizar hacia la DERECHA ejecuta [onSwipeRight] y la fila vuelve a su
 *    lugar con un rebote (spring), porque la accion no elimina nada.
 *
 * El fondo cambia de color y el icono crece a medida que avanza el gesto:
 * el usuario ve lo que va a pasar antes de soltar el dedo.
 */
@Composable
fun SwipeToActionBox(
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeRight: (() -> Unit)? = null,
    leftIconRes: Int = R.drawable.ic_delete,
    rightIconRes: Int = R.drawable.ic_check,
    leftColor: Color = ExpenseRed,
    rightColor: Color = NexcodeMint,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeLeft()
                    true      // se descarta: la fila desaparece de la lista
                }

                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeRight?.invoke()
                    false     // vuelve a su sitio con animacion de resorte
                }

                SwipeToDismissBoxValue.Settled -> false
            }
        },
        // Hay que arrastrar el 35% del ancho para que la accion se dispare:
        // evita borrados accidentales al desplazar la lista.
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = onSwipeRight != null,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val progress = dismissState.progress

            val background by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> leftColor.copy(alpha = 0.22f)
                    SwipeToDismissBoxValue.StartToEnd -> rightColor.copy(alpha = 0.22f)
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "fondo-deslizamiento"
            )

            // El icono crece de 0.6x a 1.2x segun lo lejos que se arrastre.
            val iconScale by animateFloatAsState(
                targetValue = if (progress > 0.15f) 1.2f else 0.6f,
                animationSpec = spring(),
                label = "escala-icono"
            )

            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }

            val iconRes = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> rightIconRes
                else -> leftIconRes
            }

            val tint = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> rightColor
                else -> leftColor
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(iconScale)
                    )
                }
            }
        },
        content = { content() }
    )
}
