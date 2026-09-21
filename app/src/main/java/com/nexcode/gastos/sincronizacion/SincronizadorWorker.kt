package com.nexcode.gastos.sincronizacion

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexcode.gastos.data.sync.MotorDeSincronizacion
import com.nexcode.gastos.di.AppContainer
import java.util.concurrent.TimeUnit

/**
 * Replica la base local contra la nube en segundo plano.
 *
 * Se programa con la condicion de que haya red, asi que el sistema lo despierta
 * solo cuando el telefono recupera conexion. Esa es la mitad automatica de la
 * redundancia: el usuario registra gastos en modo avion y al volver la senal
 * los cambios suben sin que tenga que hacer nada.
 *
 * Devolver `retry()` ante un fallo hace que WorkManager reintente con espera
 * creciente. No se pierde nada por esperar: los cambios siguen marcados como
 * pendientes en Room hasta que la nube los confirme.
 */
class SincronizadorWorker(
    context: Context,
    parametros: WorkerParameters
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        val motor = AppContainer(applicationContext).motorDeSincronizacion
            ?: return Result.success()   // sin nube configurada no hay nada que hacer

        return when (val resultado = motor.sincronizar()) {
            is MotorDeSincronizacion.Resultado.Exito -> {
                Log.i(
                    ETIQUETA,
                    "Sincronizado: " + resultado.subidos + " subidos, " +
                        resultado.bajados + " bajados"
                )
                Result.success()
            }

            // Sin sesion casi siempre significa sin red: no es un error, es que
            // todavia no toca. Se reintenta.
            MotorDeSincronizacion.Resultado.SinSesion -> Result.retry()

            is MotorDeSincronizacion.Resultado.Fallo -> {
                Log.w(ETIQUETA, "Fallo la sincronizacion: " + resultado.motivo)
                Result.retry()
            }
        }
    }

    companion object {
        private const val ETIQUETA = "NexcodeSync"
        private const val TRABAJO = "sincronizacion_periodica"

        /** Quince minutos es el minimo que admite WorkManager para un periodico. */
        private const val MINUTOS_ENTRE_PASADAS = 15L

        /**
         * Programa la replica periodica.
         *
         * KEEP y no REPLACE: si ya estaba programada se respeta su calendario,
         * de modo que abrir la aplicacion muchas veces no reinicie la cuenta
         * atras una y otra vez.
         */
        fun programar(context: Context) {
            val condiciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val trabajo = PeriodicWorkRequestBuilder<SincronizadorWorker>(
                MINUTOS_ENTRE_PASADAS, TimeUnit.MINUTES
            )
                .setConstraints(condiciones)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TRABAJO,
                ExistingPeriodicWorkPolicy.KEEP,
                trabajo
            )
        }
    }
}
