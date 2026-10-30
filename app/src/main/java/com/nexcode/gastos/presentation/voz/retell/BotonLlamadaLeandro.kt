package com.nexcode.gastos.presentation.voz.retell

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexcode.gastos.BuildConfig
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.TextSecondary
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Botón "Hablar con Leandro": abre una llamada de voz con el agente de Retell.
 * Pide el micrófono la primera vez y cuelga al salir de la pantalla.
 */
@Composable
fun BotonLlamadaLeandro(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val llamada = remember { LlamadaConLeandro(contexto.applicationContext, BuildConfig.LEANDRO_URL, app = "gastos") }
    val estado by llamada.estado.collectAsState()

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) alcance.launch { llamada.iniciar() }
    }

    // Al salir de la pantalla se cuelga. Se usa un alcance global porque el de
    // la pantalla ya se está cancelando en ese momento.
    @Suppress("OPT_IN_USAGE")
    DisposableEffect(Unit) { onDispose { GlobalScope.launch { llamada.colgar() } } }

    Column(modifier = modifier) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = estado !is LlamadaConLeandro.Estado.Conectando,
            colors = if (estado is LlamadaConLeandro.Estado.EnCurso) {
                ButtonDefaults.buttonColors(containerColor = ExpenseRed)
            } else {
                ButtonDefaults.buttonColors()
            },
            onClick = {
                when {
                    estado is LlamadaConLeandro.Estado.EnCurso -> alcance.launch { llamada.colgar() }
                    ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> alcance.launch { llamada.iniciar() }
                    else -> permiso.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        ) {
            Text(
                when (estado) {
                    LlamadaConLeandro.Estado.EnCurso -> "Colgar"
                    LlamadaConLeandro.Estado.Conectando -> "Conectando con Leandro…"
                    else -> "Hablar con Leandro"
                }
            )
        }
        (estado as? LlamadaConLeandro.Estado.Fallo)?.let {
            Text(text = it.motivo, color = TextSecondary, fontSize = 12.sp)
        }
    }
}
