package com.nexcode.gastos.notificaciones

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexcode.gastos.MainActivity
import com.nexcode.gastos.R
import com.nexcode.gastos.domain.model.RecurringPayment
import kotlinx.datetime.LocalDate

/**
 * Publica en la barra de notificaciones el aviso de los pagos fijos que
 * vencen hoy o que ya estan vencidos.
 *
 * Es el complemento que le faltaba al bolsillo de recordatorios: hasta ahora
 * el estado solo se veia al abrir la aplicacion, de modo que un recordatorio
 * no recordaba nada.
 */
object NotificacionesRecordatorio {

    const val CANAL_ID = "recordatorios_pagos_fijos"
    private const val NOTIFICACION_ID = 1001

    /**
     * Crea el canal de notificaciones. Desde Android 8 es obligatorio: sin
     * canal, el sistema descarta la notificacion en silencio.
     */
    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val canal = NotificationChannel(
            CANAL_ID,
            "Recordatorios de pagos fijos",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisa el dia que vence el arriendo, los servicios o una suscripcion."
            enableVibration(true)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(canal)
    }

    /** true si la app puede publicar notificaciones en este dispositivo. */
    fun puedeNotificar(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Muestra el recordatorio. Si no hay pagos por avisar retira la
     * notificacion anterior, para que no quede un aviso obsoleto en la barra.
     */
    fun mostrar(context: Context, pagos: List<RecurringPayment>, hoy: LocalDate) {
        val manager = NotificationManagerCompat.from(context)

        if (pagos.isEmpty()) {
            manager.cancel(NOTIFICACION_ID)
            return
        }
        if (!puedeNotificar(context)) return

        crearCanal(context)

        val vencidos = pagos.count { it.status(hoy) == RecurringPayment.Status.OVERDUE }
        val titulo = construirTitulo(pagos, vencidos)
        val total = pagos.fold(0L) { acumulado, pago -> acumulado + pago.amount.cents }

        // Estilo de bandeja: una linea por pago, hasta cinco.
        val detalle = NotificationCompat.InboxStyle()
        pagos.take(5).forEach { pago ->
            detalle.addLine(linea(pago, hoy))
        }
        if (pagos.size > 5) {
            detalle.setSummaryText("y " + (pagos.size - 5) + " mas")
        }

        val abrirApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ABRIR_BOLSILLO, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(context, CANAL_ID)
            .setSmallIcon(R.drawable.ic_pocket)
            .setContentTitle(titulo)
            .setContentText(
                "Suman $ " + com.nexcode.gastos.domain.model.Money(total).format()
            )
            .setStyle(detalle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(NOTIFICACION_ID, notificacion)
        } catch (e: SecurityException) {
            // El usuario revoco el permiso entre la comprobacion y el envio.
        }
    }

    private fun construirTitulo(pagos: List<RecurringPayment>, vencidos: Int): String = when {
        pagos.size == 1 && vencidos == 1 -> pagos.first().name + " esta vencido"
        pagos.size == 1 -> pagos.first().name + " vence hoy"
        vencidos > 0 -> pagos.size.toString() + " pagos fijos por atender"
        else -> pagos.size.toString() + " pagos fijos vencen hoy"
    }

    private fun linea(pago: RecurringPayment, hoy: LocalDate): String {
        val estado = when (pago.status(hoy)) {
            RecurringPayment.Status.OVERDUE -> {
                val dias = -pago.daysUntilDue(hoy)
                if (dias == 1L) "vencido ayer" else "vencido hace " + dias + " dias"
            }
            else -> "vence hoy"
        }
        return pago.name + " · $ " + pago.amount.format() + " · " + estado
    }

    /** Bandera que lleva la notificacion para abrir directamente el bolsillo. */
    const val EXTRA_ABRIR_BOLSILLO = "abrir_bolsillo"
}
