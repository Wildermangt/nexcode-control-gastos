package com.nexcode.gastos.presentation.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.nexcode.gastos.R
import com.nexcode.gastos.domain.ai.AdviceResponse
import com.nexcode.gastos.presentation.components.GradientButton
import com.nexcode.gastos.presentation.components.NexcodeCard
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.IncomeGreen
import com.nexcode.gastos.presentation.theme.NexcodeGradientColors
import com.nexcode.gastos.presentation.theme.NexcodeSurfaceElevated
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextOnBrand
import com.nexcode.gastos.presentation.voz.rememberAsistenteDeVoz
import com.nexcode.gastos.presentation.theme.NexcodeSurface
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary

/** Asistente financiero: analiza el mes y responde preguntas del usuario. */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Reconocedor y sintetizador del sistema, atados al ciclo de vida.
    val (asistenteDeVoz, estadoVoz) = rememberAsistenteDeVoz()

    // Solo se lee la respuesta en voz alta si la pregunta llego por voz.
    var esperandoRespuestaHablada by remember { mutableStateOf(false) }

    val escuchar = {
        esperandoRespuestaHablada = true
        asistenteDeVoz.escuchar { reconocido ->
            viewModel.onQuestionChange(reconocido)
            viewModel.analyze()
        }
    }

    val permisoMicrofono = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido -> if (concedido) escuchar() }

    // Cuando llega la respuesta de una pregunta hablada, se lee en voz alta.
    LaunchedEffect(state.message, state.isLoading) {
        val mensaje = state.message
        if (esperandoRespuestaHablada && !state.isLoading && mensaje != null) {
            val consejos = state.tips.joinToString(separator = " ")
            asistenteDeVoz.hablar(mensaje + " " + consejos)
            esperandoRespuestaHablada = false
        }
    }

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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Asistente Nexcode",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        // Quien respondio. Se muestra siempre: el usuario tiene derecho a saber
        // si esta leyendo al agente o al analisis local de respaldo.
        state.etiquetaDeOrigen?.let { etiqueta ->
            Text(
                text = etiqueta,
                color = if (state.source == AdviceResponse.Source.REMOTE_AGENT) IncomeGreen else TextSecondary,
                fontSize = 12.sp
            )
        }

        if (!state.agenteConfigurado) {
            NexcodeCard(padding = PaddingValues(14.dp)) {
                Text(
                    text = "El agente de IA no esta configurado",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Falta la clave de la API en local.properties. Mientras tanto " +
                        "respondo con el analisis local, que funciona sin conexion pero no " +
                        "entiende preguntas escritas.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Elvis implicito con let: la tarjeta solo aparece si hay mensaje.
        state.message?.let { message ->
            NexcodeCard(padding = PaddingValues(18.dp)) {
                Text(
                    text = message,
                    color = TextPrimary,
                    fontSize = 15.sp
                )
            }
        }

        // Ciclo sobre los consejos devueltos por el asistente.
        state.tips.forEachIndexed { index, tip ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = (index + 1).toString() + ".",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(text = tip, color = TextSecondary, fontSize = 14.sp)
            }
        }

        // Lo que se va entendiendo mientras el usuario habla.
        AnimatedVisibility(
            visible = estadoVoz.escuchando || estadoVoz.error != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            NexcodeCard(padding = PaddingValues(16.dp)) {
                if (estadoVoz.error != null) {
                    Text(
                        text = estadoVoz.error ?: "",
                        color = ExpenseRed,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = "Escuchando...",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = estadoVoz.textoParcial.ifBlank { "Habla ahora" },
                        color = TextMedium,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.question,
            onValueChange = viewModel::onQuestionChange,
            label = { Text("Pregunta algo sobre tus finanzas") },
            placeholder = { Text("En que estoy gastando de mas?") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = TextSecondary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BotonMicrofono(
                escuchando = estadoVoz.escuchando,
                habilitado = estadoVoz.disponible,
                onClick = {
                    when {
                        estadoVoz.escuchando -> asistenteDeVoz.detenerEscucha()

                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED -> escuchar()

                        else -> permisoMicrofono.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )

            GradientButton(
                text = "Analizar mis finanzas",
                onClick = {
                    asistenteDeVoz.callar()
                    esperandoRespuestaHablada = false
                    viewModel.analyze()
                },
                enabled = !state.isLoading,
                height = 50,
                modifier = Modifier.weight(1f)
            )
        }

        if (!estadoVoz.disponible) {
            Text(
                text = "Este dispositivo no tiene reconocimiento de voz instalado.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Boton de microfono.
 *
 * Mientras escucha late con una animacion continua: es la forma de indicar
 * que el microfono esta abierto sin ocupar espacio con texto.
 */
@Composable
private fun BotonMicrofono(
    escuchando: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit
) {
    val latido = rememberInfiniteTransition(label = "latido-microfono")
    val escala by latido.animateFloat(
        initialValue = 1f,
        targetValue = if (escuchando) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "escala-microfono"
    )

    IconButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier
            .size(50.dp)
            .scale(escala)
            .clip(CircleShape)
            .background(
                if (escuchando) {
                    Brush.linearGradient(listOf(ExpenseRed, ExpenseRed))
                } else if (habilitado) {
                    Brush.linearGradient(NexcodeGradientColors)
                } else {
                    Brush.linearGradient(listOf(NexcodeSurfaceElevated, NexcodeSurfaceElevated))
                }
            )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_mic),
            contentDescription = if (escuchando) "Detener la escucha" else "Preguntar por voz",
            tint = if (habilitado) TextOnBrand else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}
