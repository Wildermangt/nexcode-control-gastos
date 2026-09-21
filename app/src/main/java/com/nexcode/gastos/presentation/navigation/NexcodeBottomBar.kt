package com.nexcode.gastos.presentation.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.NexcodeSurface
import com.nexcode.gastos.presentation.theme.TextSecondary

/**
 * Barra inferior con iconos drawable propios.
 *
 * El icono seleccionado crece con una animacion de resorte, y el bolsillo
 * muestra una insignia roja con la cantidad de pagos vencidos.
 */
@Composable
fun NexcodeBottomBar(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
    pendingReminders: Int = 0,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        // Barra blanca con una linea superior suave, como la del banco.
        modifier = modifier.drawBehind {
            drawLine(
                color = DividerSoft,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
        },
        containerColor = NexcodeSurface,
        tonalElevation = 0.dp
    ) {
        Destination.bottomItems.forEach { item ->
            val selected = currentRoute == item.route

            // Resorte: el icono activo rebota al 1.15 de su tamano.
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "escala-icono-nav"
            )

            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(item) },
                icon = {
                    val icon = @Composable {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = stringResource(id = item.labelRes),
                            modifier = Modifier
                                .size(22.dp)
                                .scale(scale)
                        )
                    }

                    if (item is Destination.Pocket && pendingReminders > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = ExpenseRed) {
                                    Text(
                                        text = pendingReminders.toString(),
                                        fontSize = 9.sp
                                    )
                                }
                            },
                            content = { icon() }
                        )
                    } else {
                        icon()
                    }
                },
                label = { Text(stringResource(id = item.labelRes), fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }
    }
}
