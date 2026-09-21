package com.nexcode.gastos.data.local.seed

import com.nexcode.gastos.data.local.dao.AccountDao
import com.nexcode.gastos.data.local.dao.CategoryDao
import com.nexcode.gastos.data.local.dao.RecurringPaymentDao
import com.nexcode.gastos.data.local.dao.TransactionDao
import com.nexcode.gastos.data.local.entity.MetadatosDeSincronizacion
import com.nexcode.gastos.data.mapper.toEntity
import com.nexcode.gastos.domain.model.Account
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.RecurringFrequency
import com.nexcode.gastos.domain.model.RecurringPayment
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.domain.util.menosDias
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate

/**
 * Datos de demostracion con los que arranca la app la primera vez:
 * 10 cuentas, 10 categorias, 10 movimientos y 10 pagos fijos.
 *
 * Sirven para sustentar el proyecto sin tener que digitar todo a mano.
 *
 * ### Por que la siembra escribe por DAO y no por repositorio
 *
 * Cada fila sembrada necesita unos [MetadatosDeSincronizacion] hechos a medida,
 * y esos metadatos no cruzan al dominio a proposito: los repositorios reciben
 * modelos que no tienen donde llevarlos. Como esta clase ya vive en la capa de
 * datos, escribe directamente contra los DAO y el dominio sigue sin enterarse
 * de que la aplicacion replica.
 *
 * ### Por que los uuid son fijos y no aleatorios
 *
 * Un uuid aleatorio por dispositivo duplicaba los datos de demostracion: el
 * telefono nuevo sembraba sus diez cuentas *y* se bajaba las diez de la nube,
 * que para el eran otras diez cuentas distintas. Con un uuid derivado del
 * nombre, las dos copias son la misma fila y la bajada la actualiza en vez de
 * anadirla.
 */
object DefaultData {

    /**
     * Fecha de los metadatos de la semilla, fija y en el pasado.
     *
     * Es lo que hace que el usuario siempre gane: cualquier edicion suya lleva
     * la hora real, que es mayor, y los conflictos se resuelven por la fecha
     * mas reciente. Si la semilla se marcara con `System.currentTimeMillis()`,
     * una siembra tardia pisaria cambios anteriores del usuario solo por haber
     * ocurrido despues.
     */
    const val EPOCA_SEMILLA = 1_700_000_000_000L

    // ------------------------- 10 cuentas -------------------------
    val accounts: List<Account> = listOf(
        Account(name = "Efectivo", colorArgb = 0xFF40E0A0L),
        Account(name = "Bancolombia", colorArgb = 0xFF1080D0L),
        Account(name = "Nequi", colorArgb = 0xFF9B6BFFL),
        Account(name = "Daviplata", colorArgb = 0xFFFF5C7AL),
        Account(name = "Ahorro a la mano", colorArgb = 0xFF70E0F0L),
        Account(name = "Tarjeta debito", colorArgb = 0xFF104080L),
        Account(name = "Tarjeta credito", colorArgb = 0xFFFFC24BL),
        Account(name = "PayPal", colorArgb = 0xFF4BD1FFL),
        Account(name = "Fondo de emergencia", colorArgb = 0xFF40E0A0L),
        Account(name = "Ahorro universidad", colorArgb = 0xFF1080D0L)
    )

    // ------------------------- 10 categorias -------------------------
    val categories: List<Category> = listOf(
        Category(name = "Comida", iconKey = "ic_food", colorArgb = 0xFFFF7A2FL),
        Category(name = "Transporte", iconKey = "ic_transport", colorArgb = 0xFF1080D0L),
        Category(name = "Universidad", iconKey = "ic_study", colorArgb = 0xFF9B6BFFL),
        Category(name = "Mercado", iconKey = "ic_market", colorArgb = 0xFF40E0A0L),
        Category(name = "Ocio", iconKey = "ic_fun", colorArgb = 0xFFFF5C7AL),
        Category(name = "Servicios", iconKey = "ic_bill", colorArgb = 0xFFFFC24BL),
        Category(name = "Salud", iconKey = "ic_health", colorArgb = 0xFF4BD1FFL),
        Category(name = "Ropa", iconKey = "ic_clothes", colorArgb = 0xFF70E0F0L),
        Category(name = "Suscripciones", iconKey = "ic_subs", colorArgb = 0xFF104080L),
        Category(
            name = "Salario",
            iconKey = "ic_salary",
            colorArgb = 0xFF40E0A0L,
            appliesTo = TransactionType.Income
        )
    )

    /**
     * Siembra las cuatro secciones si la base acaba de nacer.
     *
     * El guardia mira el conteo **fisico** de cada tabla, no el visible: un
     * usuario que borrara todas sus cuentas dejaria el conteo visible en cero,
     * y volver a sembrar chocaria contra el indice unico de `uuid` con la
     * aplicacion ya en manos del usuario.
     */
    suspend fun seedIfEmpty(
        accountDao: AccountDao,
        categoryDao: CategoryDao,
        transactionDao: TransactionDao,
        recurringPaymentDao: RecurringPaymentDao
    ) {
        // El catalogo se siembra entero o no se siembra: los movimientos y los
        // pagos fijos apuntan a sus cuentas y categorias por posicion.
        if (accountDao.contarTodas() > 0 || categoryDao.contarTodas() > 0) return

        val accountIds = accounts.map {
            accountDao.insert(it.toEntity(sync = semilla(CUENTA, it.name)))
        }
        val categoryIds = categories.map {
            categoryDao.insert(it.toEntity(sync = semilla(CATEGORIA, it.name)))
        }

        val today = java.time.LocalDate.now().toKotlinLocalDate()

        // Indices legibles para no perderse entre numeros.
        val efectivo = accountIds[0]
        val bancolombia = accountIds[1]
        val nequi = accountIds[2]
        val daviplata = accountIds[3]
        val tarjetaDebito = accountIds[5]
        val tarjetaCredito = accountIds[6]

        val comida = categoryIds[0]
        val transporte = categoryIds[1]
        val universidad = categoryIds[2]
        val mercado = categoryIds[3]
        val ocio = categoryIds[4]
        val servicios = categoryIds[5]
        val salud = categoryIds[6]
        val ropa = categoryIds[7]
        val suscripciones = categoryIds[8]
        val salario = categoryIds[9]

        // ------------------------- 10 movimientos -------------------------
        if (transactionDao.contarTodas() == 0) {
            val demo = listOf(
                transaction(salario, bancolombia, "Quincena", 1_400_000, TransactionType.Income, today.menosDias(6), 9, 15, "Pago de la quincena"),
                transaction(comida, efectivo, "Almuerzo en la U", 12_000, TransactionType.Expense, today, 12, 30, null),
                transaction(transporte, nequi, "Transmilenio", 5_800, TransactionType.Expense, today, 7, 10, "Ida y vuelta"),
                transaction(mercado, bancolombia, "Mercado D1", 87_500, TransactionType.Expense, today.menosDias(1), 18, 40, null),
                transaction(suscripciones, tarjetaCredito, "Netflix", 26_900, TransactionType.Expense, today.menosDias(2), 20, 5, "Plan compartido"),
                transaction(universidad, efectivo, "Fotocopias del taller", 3_500, TransactionType.Expense, today.menosDias(2), 10, 20, null),
                transaction(ocio, daviplata, "Cine con Leandro", 32_000, TransactionType.Expense, today.menosDias(3), 19, 0, "Funcion de las 7"),
                transaction(servicios, nequi, "Recarga de celular", 20_000, TransactionType.Expense, today.menosDias(4), 15, 45, null),
                transaction(salud, efectivo, "Drogueria", 18_400, TransactionType.Expense, today.menosDias(5), 11, 25, "Pastillas para la gripa"),
                transaction(ropa, tarjetaDebito, "Camiseta", 55_000, TransactionType.Expense, today.menosDias(7), 16, 10, null)
            )
            demo.forEach {
                transactionDao.insert(it.toEntity(sync = semilla(MOVIMIENTO, it.title)))
            }
        }

        // ------------------------- 10 pagos fijos -------------------------
        if (recurringPaymentDao.contarTodas() == 0) {
            val pagos = listOf(
                // Vencido a proposito: se ve la fila roja latiendo.
                recurring("Arriendo", 850_000, RecurringFrequency.Monthly(5), bancolombia, servicios, today.menosDias(3), "Apartamento con Leandro"),
                // Vence hoy.
                recurring("Energia", 120_000, RecurringFrequency.Monthly(today.dayOfMonth), bancolombia, servicios, today, null),
                recurring("Internet fibra", 89_900, RecurringFrequency.Monthly(10), bancolombia, servicios, null, null),
                recurring("Plan celular", 45_000, RecurringFrequency.Monthly(15), nequi, servicios, null, null),
                recurring("Netflix", 26_900, RecurringFrequency.Monthly(20), tarjetaCredito, suscripciones, null, "Plan compartido"),
                recurring("Spotify", 16_900, RecurringFrequency.Monthly(8), tarjetaCredito, suscripciones, null, null),
                recurring("Gimnasio", 79_000, RecurringFrequency.Monthly(3), bancolombia, salud, null, null),
                recurring("Agua", 68_000, RecurringFrequency.Monthly(25), bancolombia, servicios, null, null),
                recurring("Pasajes de la semana", 35_000, RecurringFrequency.Weekly(1), efectivo, transporte, null, "Recarga de la tarjeta"),
                recurring("Matricula", 4_200_000, RecurringFrequency.Yearly(1, 15), bancolombia, universidad, null, "Semestre")
            )
            pagos.forEach {
                recurringPaymentDao.insert(it.toEntity(sync = semilla(PAGO_FIJO, it.name)))
            }
        }
    }

    // ------------------------- Identidad de la semilla -------------------------

    /**
     * Metadatos de una fila sembrada: uuid deducible y fecha fija.
     *
     * Quedan pendientes de subir, como cualquier fila nueva. Solo siembra el
     * dispositivo que encuentra la nube vacia —eso lo decide `DataProvider`—,
     * asi que estas filas suben una vez y las demas copias las reciben.
     */
    private fun semilla(tabla: String, nombre: String) = MetadatosDeSincronizacion(
        uuid = uuidDemo(tabla, nombre),
        actualizadoEn = EPOCA_SEMILLA,
        borrado = false,
        sincronizado = false
    )

    /**
     * Identificador estable de una fila de demostracion.
     *
     * Se deriva del nombre, que esta fijo en el codigo, para que dos telefonos
     * generen exactamente el mismo. El prefijo `demo-` sirve ademas para
     * reconocer estas filas de un vistazo en la consola de Firebase.
     */
    internal fun uuidDemo(tabla: String, nombre: String): String {
        val ranura = nombre.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
        return "demo-" + tabla + "-" + ranura
    }

    private const val CUENTA = "cuenta"
    private const val CATEGORIA = "categoria"
    private const val MOVIMIENTO = "movimiento"
    private const val PAGO_FIJO = "pago"

    // ------------------------- Constructores auxiliares -------------------------

    private fun transaction(
        categoryId: Long,
        accountId: Long,
        title: String,
        pesos: Long,
        type: TransactionType,
        date: LocalDate,
        hour: Int,
        minute: Int,
        note: String?
    ): Transaction = Transaction(
        accountId = accountId,
        categoryId = categoryId,
        type = type,
        amount = Money(pesos * 100L),
        dateTime = LocalDateTime(date, LocalTime(hour, minute)),
        title = title,
        note = note
    )

    private fun recurring(
        name: String,
        pesos: Long,
        frequency: RecurringFrequency,
        accountId: Long,
        categoryId: Long,
        forcedDueDate: LocalDate?,
        note: String?
    ): RecurringPayment = RecurringPayment(
        name = name,
        amount = Money(pesos * 100L),
        frequency = frequency,
        // Elvis: si no se fuerza una fecha, se calcula la proxima ocurrencia.
        nextDueDate = forcedDueDate ?: frequency.nextDateAfter(
            java.time.LocalDate.now().toKotlinLocalDate().menosDias(1)
        ),
        accountId = accountId,
        categoryId = categoryId,
        note = note
    )
}
