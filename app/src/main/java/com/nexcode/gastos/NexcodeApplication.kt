package com.nexcode.gastos

import android.app.Application
import com.nexcode.gastos.di.AppContainer
import com.nexcode.gastos.notificaciones.NotificacionesRecordatorio
import com.nexcode.gastos.notificaciones.RecordatoriosWorker
import com.nexcode.gastos.sincronizacion.SincronizadorWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de arranque. Construye el contenedor de dependencias una sola vez y
 * siembra los datos iniciales si la base esta vacia.
 */
class NexcodeApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // El canal debe existir antes de publicar cualquier notificacion.
        NotificacionesRecordatorio.crearCanal(this)

        applicationScope.launch {
            try {
                container.seedIfEmpty()
            } catch (e: Exception) {
                // Si la siembra falla la app debe abrir igual, solo que vacia.
                e.printStackTrace()
            }

            // Revision diaria de pagos fijos, y una inmediata para no
            // esperar al primer ciclo.
            RecordatoriosWorker.programarRevisionDiaria(this@NexcodeApplication)

            // Replica periodica. Se programa siempre: sin nube configurada el
            // trabajo termina de inmediato sin hacer nada.
            SincronizadorWorker.programar(this@NexcodeApplication)
            RecordatoriosWorker.revisarAhora(this@NexcodeApplication)
        }
    }
}
