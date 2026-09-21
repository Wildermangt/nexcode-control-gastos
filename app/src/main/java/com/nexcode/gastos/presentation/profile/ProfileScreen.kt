package com.nexcode.gastos.presentation.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.nexcode.gastos.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import com.nexcode.gastos.presentation.components.NexcodeCard
import com.nexcode.gastos.presentation.components.TarjetaDeCuenta
import com.nexcode.gastos.presentation.components.TarjetaDeSincronizacion
import com.nexcode.gastos.sincronizacion.EstadoDeSincronizacion
import com.nexcode.gastos.cuenta.CuentaViewModel
import com.nexcode.gastos.cuenta.EstadoDeCuenta
import com.nexcode.gastos.sincronizacion.SincronizacionViewModel
import com.nexcode.gastos.presentation.theme.NexcodeBlue
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary

/** Perfil del usuario e informacion tecnica de la aplicacion. */
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    sincronizacion: SincronizacionViewModel? = null,
    cuenta: CuentaViewModel? = null,
    onVerBienvenida: () -> Unit = {},
    onVerCuentas: () -> Unit = {}
) {
    val estadoSync by (sincronizacion?.estado ?: MutableStateFlow(EstadoDeSincronizacion()))
        .collectAsStateWithLifecycle()

    val estadoCuenta by (cuenta?.estado ?: MutableStateFlow(EstadoDeCuenta()))
        .collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Logo real de la marca: el simbolo gira suavemente al entrar y el
        // logotipo con texto aparece debajo con un desvanecido.
        val entered = remember { MutableTransitionState(false).apply { targetState = true } }
        val symbolRotation by animateFloatAsState(
            targetValue = if (entered.currentState) 0f else -12f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "giro-logo"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.simbolo_nexcode),
                contentDescription = stringResource(id = R.string.cd_logo),
                modifier = Modifier
                    .size(64.dp)
                    .rotate(symbolRotation)
            )
            Column {
                Text(
                    text = "NEXCODE CORPORATION",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Control de gastos personales",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        AnimatedVisibility(
            visibleState = entered,
            enter = fadeIn(tween(900))
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_nexcode_texto),
                contentDescription = stringResource(id = R.string.cd_logo),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }

        // Estado de la replica. Va lo primero porque es lo unico de esta
        // pantalla sobre lo que el usuario puede actuar.
        TarjetaDeSincronizacion(
            estado = estadoSync,
            onSincronizar = { sincronizacion?.sincronizarAhora() }
        )

        // Justo debajo de la replica: sin cuenta, esa copia no se puede
        // recuperar, y las dos tarjetas juntas cuentan la historia entera.
        TarjetaDeCuenta(
            estado = estadoCuenta,
            onProteger = { correo, clave -> cuenta?.proteger(correo, clave) },
            onEntrar = { correo, clave -> cuenta?.entrar(correo, clave) },
            onMensajeVisto = { cuenta?.descartarMensaje() }
        )

        InfoCard(
            title = "Equipo de desarrollo",
            lines = listOf(
                "Jeferson Wilderman Gonzalez Tenjo",
                "Jose Leandro Ocampo Camacho",
                "Ingenieria de Sistemas - Universidad de San Buenaventura",
                "Optativa II: Desarrollo Movil"
            )
        )

        InfoCard(
            title = "Arquitectura",
            lines = listOf(
                "Capa de presentacion: Jetpack Compose + ViewModel (MVVM)",
                "Capa de dominio: Kotlin puro, casos de uso y contratos",
                "Capa de datos: Room / SQLite y servicio de IA",
                "Regla: app -> domain <- data"
            )
        )

        InfoCard(
            title = "Version",
            lines = listOf("Nexcode 1.0", "minSdk 26 - compileSdk 35")
        )

        NexcodeCard(padding = PaddingValues(18.dp)) {
            Text(
                text = "CUENTAS",
                color = NexcodeBlue,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Administrar cupos",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onVerCuentas() }
                    .padding(vertical = 8.dp)
            )
            Text(
                text = "Sube o baja el dinero disponible en cada cuenta.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        // Deja volver a ver la bienvenida sin tener que borrar los datos de
        // la aplicacion, que es lo unico que quedaba para revisarla.
        NexcodeCard(padding = PaddingValues(18.dp)) {
            Text(
                text = "AYUDA",
                color = NexcodeBlue,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ver la bienvenida otra vez",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onVerBienvenida() }
                    .padding(vertical = 8.dp)
            )
            Text(
                text = "Repasa en tres pantallas qué hace la aplicación.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>) {
    NexcodeCard(padding = PaddingValues(18.dp)) {
        Text(
            text = title.uppercase(),
            color = NexcodeBlue,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold
        )
        // Ciclo sobre la lista de lineas.
        lines.forEach { line ->
            Text(
                text = line,
                color = TextMedium,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
