package com.nexcode.gastos.data.sync

import com.nexcode.gastos.data.local.dao.AccountDao
import com.nexcode.gastos.data.local.dao.CategoryDao
import com.nexcode.gastos.data.local.dao.RecurringPaymentDao
import com.nexcode.gastos.data.local.dao.TransactionDao
import com.nexcode.gastos.data.local.entity.AccountEntity
import com.nexcode.gastos.data.local.entity.CategoryEntity
import com.nexcode.gastos.data.local.entity.MetadatosDeSincronizacion
import com.nexcode.gastos.data.local.entity.RecurringPaymentEntity
import com.nexcode.gastos.data.local.entity.TransactionEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Los cuatro adaptadores de tabla.
 *
 * Cada uno solo traduce: de entidad a documento y de documento a entidad. El
 * algoritmo de replica esta en [AdaptadorDeTabla] y no se repite.
 *
 * Las fechas viajan como texto ISO en lugar de como numero. Cuesta unos bytes
 * mas, pero al abrir la consola de Firebase se lee "2026-09-07T12:30" en vez de
 * un entero de trece cifras, y esa consola es la herramienta con la que se
 * administra la aplicacion.
 */

// ------------------------------ Cuentas ------------------------------

class AdaptadorDeCuentas(private val dao: AccountDao) : AdaptadorDeTabla<AccountEntity>() {

    override val coleccion = "cuentas"

    override suspend fun pendientes() = dao.pendientes()
    override suspend fun todas() = dao.todasParaSincronizar()
    override fun metadatosDe(fila: AccountEntity) = fila.sync
    override fun idDe(fila: AccountEntity) = fila.id
    override suspend fun marcarSincronizados(uuids: List<String>) = dao.marcarSincronizados(uuids)

    override fun aDocumento(fila: AccountEntity, referencias: Referencias) = mapOf(
        "nombre" to fila.name,
        "saldo_inicial_centavos" to fila.initialBalanceCents,
        "color" to fila.colorArgb,
        "icono" to fila.iconKey,
        "archivada" to fila.isArchived,
        CAMPO_ACTUALIZADO to fila.sync.actualizadoEn,
        CAMPO_BORRADO to fila.sync.borrado
    )

    override suspend fun aplicar(
        uuid: String,
        datos: Map<String, Any?>,
        actualizadoEn: Long,
        referencias: Referencias
    ): Boolean {
        val existente = dao.findByUuid(uuid)
        if (existente != null && existente.sync.actualizadoEn >= actualizadoEn) return false

        val entidad = AccountEntity(
            id = existente?.id ?: 0L,
            name = texto(datos, "nombre") ?: return false,
            initialBalanceCents = entero(datos, "saldo_inicial_centavos") ?: 0L,
            colorArgb = entero(datos, "color") ?: 0xFF40E0A0L,
            iconKey = texto(datos, "icono") ?: "ic_wallet",
            isArchived = booleano(datos, "archivada"),
            sync = metadatosDeLaNube(uuid, datos)
        )

        if (existente == null) dao.insert(entidad) else dao.update(entidad)
        return true
    }
}

// ----------------------------- Categorias -----------------------------

class AdaptadorDeCategorias(private val dao: CategoryDao) : AdaptadorDeTabla<CategoryEntity>() {

    override val coleccion = "categorias"

    override suspend fun pendientes() = dao.pendientes()
    override suspend fun todas() = dao.todasParaSincronizar()
    override fun metadatosDe(fila: CategoryEntity) = fila.sync
    override fun idDe(fila: CategoryEntity) = fila.id
    override suspend fun marcarSincronizados(uuids: List<String>) = dao.marcarSincronizados(uuids)

    override fun aDocumento(fila: CategoryEntity, referencias: Referencias) = mapOf(
        "nombre" to fila.name,
        "icono" to fila.iconKey,
        "color" to fila.colorArgb,
        "presupuesto_centavos" to fila.monthlyBudgetCents,
        "aplica_a" to fila.appliesTo,
        CAMPO_ACTUALIZADO to fila.sync.actualizadoEn,
        CAMPO_BORRADO to fila.sync.borrado
    )

    override suspend fun aplicar(
        uuid: String,
        datos: Map<String, Any?>,
        actualizadoEn: Long,
        referencias: Referencias
    ): Boolean {
        val existente = dao.findByUuid(uuid)
        if (existente != null && existente.sync.actualizadoEn >= actualizadoEn) return false

        val entidad = CategoryEntity(
            id = existente?.id ?: 0L,
            name = texto(datos, "nombre") ?: return false,
            iconKey = texto(datos, "icono") ?: "ic_category",
            colorArgb = entero(datos, "color") ?: 0xFFFF7A2FL,
            // La jerarquia de categorias no se replica todavia: es de un solo
            // nivel y la aplicacion no deja crearla desde la interfaz.
            parentId = existente?.parentId,
            monthlyBudgetCents = entero(datos, "presupuesto_centavos"),
            appliesTo = texto(datos, "aplica_a") ?: "EXPENSE",
            sync = metadatosDeLaNube(uuid, datos)
        )

        if (existente == null) dao.insert(entidad) else dao.update(entidad)
        return true
    }
}

// ---------------------------- Movimientos ----------------------------

class AdaptadorDeMovimientos(private val dao: TransactionDao) : AdaptadorDeTabla<TransactionEntity>() {

    override val coleccion = "movimientos"

    override suspend fun pendientes() = dao.pendientes()
    override suspend fun todas() = dao.todasParaSincronizar()
    override fun metadatosDe(fila: TransactionEntity) = fila.sync
    override fun idDe(fila: TransactionEntity) = fila.id
    override suspend fun marcarSincronizados(uuids: List<String>) = dao.marcarSincronizados(uuids)

    override fun aDocumento(fila: TransactionEntity, referencias: Referencias) = mapOf(
        // Viajan los uuid, no los id: el id local no significa nada en la nube.
        "cuenta_uuid" to referencias.uuidDeCuenta[fila.accountId],
        "categoria_uuid" to fila.categoryId?.let { referencias.uuidDeCategoria[it] },
        "tipo" to fila.typeCode,
        "monto_centavos" to fila.amountCents,
        "fecha_hora" to fila.dateTime.toString(),
        "titulo" to fila.title,
        "nota" to fila.note,
        CAMPO_ACTUALIZADO to fila.sync.actualizadoEn,
        CAMPO_BORRADO to fila.sync.borrado
    )

    override suspend fun aplicar(
        uuid: String,
        datos: Map<String, Any?>,
        actualizadoEn: Long,
        referencias: Referencias
    ): Boolean {
        val existente = dao.findByUuid(uuid)
        if (existente != null && existente.sync.actualizadoEn >= actualizadoEn) return false

        // Si la cuenta todavia no ha bajado, este movimiento no se puede
        // aplicar: la clave foranea lo rechazaria. Se deja para la proxima
        // pasada, cuando su cuenta ya exista.
        val cuentaUuid = texto(datos, "cuenta_uuid") ?: return false
        val cuentaId = referencias.idDeCuenta[cuentaUuid] ?: return false

        val fechaHora = texto(datos, "fecha_hora")?.let {
            runCatching { LocalDateTime.parse(it) }.getOrNull()
        } ?: return false

        val entidad = TransactionEntity(
            id = existente?.id ?: 0L,
            accountId = cuentaId,
            categoryId = texto(datos, "categoria_uuid")?.let { referencias.idDeCategoria[it] },
            typeCode = texto(datos, "tipo") ?: "EXPENSE",
            targetAccountId = existente?.targetAccountId,
            amountCents = entero(datos, "monto_centavos") ?: 0L,
            dateTime = fechaHora,
            title = texto(datos, "titulo") ?: "Movimiento",
            note = texto(datos, "nota"),
            sync = metadatosDeLaNube(uuid, datos)
        )

        if (existente == null) dao.insert(entidad) else dao.update(entidad)
        return true
    }
}

// ---------------------------- Pagos fijos ----------------------------

class AdaptadorDePagosFijos(
    private val dao: RecurringPaymentDao
) : AdaptadorDeTabla<RecurringPaymentEntity>() {

    override val coleccion = "pagos_fijos"

    override suspend fun pendientes() = dao.pendientes()
    override suspend fun todas() = dao.todasParaSincronizar()
    override fun metadatosDe(fila: RecurringPaymentEntity) = fila.sync
    override fun idDe(fila: RecurringPaymentEntity) = fila.id
    override suspend fun marcarSincronizados(uuids: List<String>) = dao.marcarSincronizados(uuids)

    override fun aDocumento(fila: RecurringPaymentEntity, referencias: Referencias) = mapOf(
        "nombre" to fila.name,
        "monto_centavos" to fila.amountCents,
        "frecuencia" to fila.frequencyCode,
        "frecuencia_valor1" to fila.frequencyValue1,
        "frecuencia_valor2" to fila.frequencyValue2,
        "proximo_vencimiento" to fila.nextDueDate.toString(),
        "cuenta_uuid" to referencias.uuidDeCuenta[fila.accountId],
        "categoria_uuid" to fila.categoryId?.let { referencias.uuidDeCategoria[it] },
        "nota" to fila.note,
        "ultimo_pago" to fila.lastPaidDate?.toString(),
        "activo" to fila.isActive,
        CAMPO_ACTUALIZADO to fila.sync.actualizadoEn,
        CAMPO_BORRADO to fila.sync.borrado
    )

    override suspend fun aplicar(
        uuid: String,
        datos: Map<String, Any?>,
        actualizadoEn: Long,
        referencias: Referencias
    ): Boolean {
        val existente = dao.findByUuid(uuid)
        if (existente != null && existente.sync.actualizadoEn >= actualizadoEn) return false

        val cuentaUuid = texto(datos, "cuenta_uuid") ?: return false
        val cuentaId = referencias.idDeCuenta[cuentaUuid] ?: return false

        val vencimiento = texto(datos, "proximo_vencimiento")?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return false

        val entidad = RecurringPaymentEntity(
            id = existente?.id ?: 0L,
            name = texto(datos, "nombre") ?: return false,
            amountCents = entero(datos, "monto_centavos") ?: 0L,
            frequencyCode = texto(datos, "frecuencia") ?: "MONTHLY",
            frequencyValue1 = entero(datos, "frecuencia_valor1")?.toInt(),
            frequencyValue2 = entero(datos, "frecuencia_valor2")?.toInt(),
            nextDueDate = vencimiento,
            accountId = cuentaId,
            categoryId = texto(datos, "categoria_uuid")?.let { referencias.idDeCategoria[it] },
            note = texto(datos, "nota"),
            lastPaidDate = texto(datos, "ultimo_pago")?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            },
            isActive = datos["activo"] as? Boolean ?: true,
            sync = metadatosDeLaNube(uuid, datos)
        )

        if (existente == null) dao.insert(entidad) else dao.update(entidad)
        return true
    }
}
