package com.nexcode.gastos.presentation.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.exportacion.ExportadorCsv
import java.io.File

/**
 * Escribe el CSV en la cache y lo ofrece a otra aplicacion.
 *
 * El formato lo produce [ExportadorCsv], que esta en Java; aqui solo se
 * resuelve la parte que si depende de Android: donde se deja el archivo y como
 * se entrega el permiso de lectura a quien lo reciba.
 *
 * @return true si se pudo lanzar el selector de aplicaciones.
 */
fun compartirMovimientosComoCsv(
    context: Context,
    movimientos: List<Transaction>,
    categorias: Map<Long, Category>,
    fecha: String
): Boolean = try {
    val csv = ExportadorCsv.exportar(movimientos, categorias)

    // Subcarpeta propia: es la unica ruta que el proveedor publica.
    val carpeta = File(context.cacheDir, "exportaciones").apply { mkdirs() }
    val archivo = File(carpeta, ExportadorCsv.nombreDeArchivo(fecha))
    archivo.writeText(csv, Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".archivos",
        archivo
    )

    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Movimientos de Nexcode")
        // El permiso viaja con el intent y caduca solo: la otra aplicacion no
        // conserva acceso a la carpeta despues de leer el archivo.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(envio, "Exportar movimientos").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
    true
} catch (e: Exception) {
    // Un dispositivo sin ninguna aplicacion capaz de recibir el archivo, o un
    // fallo de escritura, no deben tumbar la pantalla.
    false
}
