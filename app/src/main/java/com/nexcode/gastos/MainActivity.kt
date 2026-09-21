package com.nexcode.gastos

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexcode.gastos.di.NexcodeViewModelFactory
import com.nexcode.gastos.notificaciones.NotificacionesRecordatorio
import com.nexcode.gastos.notificaciones.RecordatoriosWorker
import com.nexcode.gastos.presentation.bienvenida.BienvenidaScreen
import com.nexcode.gastos.presentation.bienvenida.PreferenciasBienvenida
import com.nexcode.gastos.presentation.navigation.NexcodeNavHost
import com.nexcode.gastos.presentation.splash.SplashScreen
import com.nexcode.gastos.presentation.theme.NexcodeBackground
import com.nexcode.gastos.presentation.theme.NexcodeTheme

/** Las tres fases por las que pasa la app antes de estar operativa. */
private enum class FaseArranque { CARGA, BIENVENIDA, APLICACION }

/**
 * Unica Activity de la app: a partir de aqui todo es Compose.
 */
class MainActivity : ComponentActivity() {

    /**
     * Peticion de abrir el bolsillo, disparada por la notificacion.
     *
     * Es estado observable y no un valor fijo porque la Activity puede estar
     * viva cuando llega la notificacion: en ese caso Android no vuelve a
     * llamar a onCreate sino a onNewIntent.
     */
    private var irAlBolsillo by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        irAlBolsillo = intent.getBooleanExtra(
            NotificacionesRecordatorio.EXTRA_ABRIR_BOLSILLO,
            false
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras del sistema transparentes con iconos OSCUROS, porque el
        // fondo de la aplicacion es blanco.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        irAlBolsillo = intent?.getBooleanExtra(
            NotificacionesRecordatorio.EXTRA_ABRIR_BOLSILLO,
            false
        ) ?: false

        val preferencias = PreferenciasBienvenida(this)
        val container = (application as NexcodeApplication).container
        val factory = NexcodeViewModelFactory(container)

        setContent {
            NexcodeTheme {
                // Desde Android 13 el permiso de notificaciones se pide en
                // tiempo de ejecucion. Si el usuario lo concede, se revisa el
                // bolsillo de inmediato para que el aviso no espere un dia.
                val solicitarPermiso = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { concedido ->
                    if (concedido) {
                        RecordatoriosWorker.revisarAhora(this@MainActivity)
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !NotificacionesRecordatorio.puedeNotificar(this@MainActivity)
                    ) {
                        solicitarPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NexcodeBackground
                ) {
                    // El arranque tiene tres fases encadenadas: la pantalla de
                    // carga, la bienvenida (solo si procede) y la aplicacion.
                    // Se resuelve con estado local y no con rutas de navegacion
                    // porque ninguna de las dos primeras debe quedar en la pila:
                    // pulsar "atras" en el inicio tiene que cerrar la app, no
                    // devolver a la bienvenida.
                    var fase by rememberSaveable { mutableStateOf(FaseArranque.CARGA) }

                    AnimatedContent(
                        targetState = fase,
                        transitionSpec = {
                            fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                        },
                        label = "fase-arranque"
                    ) { faseActual ->
                        when (faseActual) {
                            FaseArranque.CARGA -> SplashScreen(
                                onTerminado = {
                                    fase = if (preferencias.completada) {
                                        FaseArranque.APLICACION
                                    } else {
                                        FaseArranque.BIENVENIDA
                                    }
                                }
                            )

                            FaseArranque.BIENVENIDA -> BienvenidaScreen(
                                onTerminar = { noVolverAMostrar ->
                                    preferencias.completada = noVolverAMostrar
                                    fase = FaseArranque.APLICACION
                                }
                            )

                            FaseArranque.APLICACION -> NexcodeNavHost(
                                factory = factory,
                                irAlBolsillo = irAlBolsillo,
                                onBolsilloAtendido = { irAlBolsillo = false },
                                onVerBienvenida = {
                                    preferencias.reiniciar()
                                    fase = FaseArranque.BIENVENIDA
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
