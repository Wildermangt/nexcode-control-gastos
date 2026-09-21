package com.nexcode.gastos.notificaciones

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexcode.gastos.NexcodeApplication
import com.nexcode.gastos.presentation.util.hoy
import java.util.concurrent.TimeUnit

/**
 * Trabajo en segundo plano que revisa el bolsillo y publica el recordatorio.
 *
 * Se ejecuta aunque la aplicacion este cerrada: es lo que convierte el estado
 * visual del bolsillo en un aviso real. Consulta el dominio a traves del
 * contenedor de dependencias, sin conocer Room ni la base de datos.
 */
class RecordatoriosWorker(
    context: Context,
    parametros: WorkerParameters
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? NexcodeApplication
                ?: return Result.success()   // contexto inesperado: no se reintenta

            val hoy = hoy()
            val pagos = app.container.getPagosPorAvisar(hoy)

            NotificacionesRecordatorio.mostrar(applicationContext, pagos, hoy)
            Result.success()
        } catch (e: Exception) {
            // Un fallo puntual (base ocupada, por ejemplo) se reintenta luego.
            Result.retry()
        }
    }

    companion object {
        private const val TRABAJO_DIARIO = "revision_diaria_pagos_fijos"
        private const val TRABAJO_INMEDIATO = "revision_inmediata_pagos_fijos"

        /**
         * Programa la revision diaria.
         *
         * WorkManager no garantiza una hora exacta, y es lo correcto: el
         * sistema agrupa los trabajos para no gastar bateria. Para un
         * recordatorio de pagos, avisar dentro de la ventana del dia es
         * suficiente; exigir un despertador exacto seria abusivo.
         */
        fun programarRevisionDiaria(context: Context) {
            val trabajo = PeriodicWorkRequestBuilder<RecordatoriosWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TRABAJO_DIARIO,
                ExistingPeriodicWorkPolicy.KEEP,   // no reprogramar en cada arranque
                trabajo
            )
        }

        /** Revision inmediata: se lanza al abrir la app para no esperar al ciclo. */
        fun revisarAhora(context: Context) {
            val trabajo = OneTimeWorkRequestBuilder<RecordatoriosWorker>().build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                TRABAJO_INMEDIATO,
                ExistingWorkPolicy.REPLACE,
                trabajo
            )
        }
    }
}
