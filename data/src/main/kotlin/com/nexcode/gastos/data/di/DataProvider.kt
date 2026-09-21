package com.nexcode.gastos.data.di

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nexcode.gastos.data.auth.CuentaDeUsuario
import com.nexcode.gastos.data.local.NexcodeDatabase
import com.nexcode.gastos.data.local.seed.DefaultData
import com.nexcode.gastos.data.remote.ai.NexcodeAIService
import com.nexcode.gastos.data.sync.AdaptadorDeCategorias
import com.nexcode.gastos.data.sync.AdaptadorDeCuentas
import com.nexcode.gastos.data.sync.AdaptadorDeMovimientos
import com.nexcode.gastos.data.sync.AdaptadorDePagosFijos
import com.nexcode.gastos.data.sync.MarcasDeSincronizacion
import com.nexcode.gastos.data.sync.MotorDeSincronizacion
import com.nexcode.gastos.data.repository.AccountRepositoryImpl
import com.nexcode.gastos.data.repository.CategoryRepositoryImpl
import com.nexcode.gastos.data.repository.RecurringPaymentRepositoryImpl
import com.nexcode.gastos.data.repository.TransactionRepositoryImpl
import com.nexcode.gastos.domain.ai.FinancialAdvisor
import com.nexcode.gastos.domain.repository.AccountRepository
import com.nexcode.gastos.domain.repository.CategoryRepository
import com.nexcode.gastos.domain.repository.RecurringPaymentRepository
import com.nexcode.gastos.domain.repository.TransactionRepository

/**
 * Fabrica publica de la CAPA DE DATOS.
 *
 * Es lo unico que la capa de presentacion puede ver de este modulo, y solo
 * devuelve CONTRATOS del dominio: Room, los DAO y las entidades quedan
 * encapsulados aqui dentro. Si manana se cambia Room por otra base de datos,
 * la app ni se entera.
 */
class DataProvider(
    context: Context,
    aiConfig: NexcodeAIService.AiConfig = NexcodeAIService.AiConfig()
) {

    private val contexto = context.applicationContext

    private val database = NexcodeDatabase.getInstance(context)

    val transactionRepository: TransactionRepository =
        TransactionRepositoryImpl(database.transactionDao())

    val accountRepository: AccountRepository =
        AccountRepositoryImpl(database.accountDao())

    val categoryRepository: CategoryRepository =
        CategoryRepositoryImpl(database.categoryDao())

    val recurringPaymentRepository: RecurringPaymentRepository =
        RecurringPaymentRepositoryImpl(database.recurringPaymentDao())

    /** Sin webhookUrl configurada responde la simulacion local. */
    val financialAdvisor: FinancialAdvisor = NexcodeAIService(aiConfig)

    /** Marcas de la ultima bajada. Visible para poder mostrarlas en pantalla. */
    val marcasDeSincronizacion = MarcasDeSincronizacion(contexto)

    /**
     * Motor de replica, o null si la nube no esta configurada.
     *
     * Se decide preguntandole a Firebase si llego a inicializarse, y no con una
     * bandera propia: sin `google-services.json` el plugin no corre, Firebase
     * no arranca y cualquier llamada lanzaria. Devolver null aqui deja a la
     * aplicacion operando solo contra Room, que es justo el comportamiento
     * esperado cuando no hay nube.
     */
    val motorDeSincronizacion: MotorDeSincronizacion? by lazy {
        try {
            FirebaseApp.getInstance()
            MotorDeSincronizacion(
                cuentas = AdaptadorDeCuentas(database.accountDao()),
                categorias = AdaptadorDeCategorias(database.categoryDao()),
                movimientos = AdaptadorDeMovimientos(database.transactionDao()),
                pagosFijos = AdaptadorDePagosFijos(database.recurringPaymentDao()),
                firestore = FirebaseFirestore.getInstance(),
                auth = FirebaseAuth.getInstance(),
                marcas = marcasDeSincronizacion
            )
        } catch (e: Exception) {
            // Sin nube configurada es lo normal, pero se deja constancia: si un
            // dia deja de sincronizar por otro motivo, el registro es lo unico
            // que distingue "no hay google-services.json" de un fallo real.
            Log.i(
                ETIQUETA,
                "Sin motor de replica: " + e::class.java.simpleName + ": " + e.message
            )
            null
        }
    }

    /**
     * Identidad del usuario frente a la nube, o null si no hay nube.
     *
     * Va aparte del motor de replica a proposito: el motor solo necesita *un*
     * uid con el que escribir, y le da igual como se consiguio. Quien decide si
     * ese uid es anonimo o esta enlazado a un correo es esta clase.
     */
    val cuentaDeUsuario: CuentaDeUsuario? by lazy {
        try {
            FirebaseApp.getInstance()
            CuentaDeUsuario(FirebaseAuth.getInstance())
        } catch (e: Exception) {
            Log.i(
                ETIQUETA,
                "Sin cuenta en la nube: " + e::class.java.simpleName + ": " + e.message
            )
            null
        }
    }

    /**
     * Deja este telefono con los datos de la cuenta en la que se acaba de
     * entrar, descartando lo que hubiera aqui.
     *
     * Es la segunda mitad de "recuperar la copia en otro telefono". Sin esto,
     * al entrar en la cuenta el telefono conservaria su propia siembra de
     * demostracion y la subiria a la cuenta del usuario mezclada con sus datos
     * reales.
     *
     * El borrado es **fisico**. Marcar las filas como borradas propagaria esos
     * borrados a la nube en la siguiente pasada y destruiria los datos buenos
     * en todos los dispositivos: el borrado logico existe precisamente para
     * viajar, y aqui no debe viajar nada.
     *
     * El orden respeta las claves foraneas: primero lo que depende del
     * catalogo, despues el catalogo.
     */
    suspend fun reemplazarDatosLocalesPorLaNube(): Boolean {
        val motor = motorDeSincronizacion ?: return false

        database.transactionDao().borrarTodo()
        database.recurringPaymentDao().borrarTodo()
        database.accountDao().borrarTodo()
        database.categoryDao().borrarTodo()

        // Las marcas dicen hasta donde se bajo cada coleccion. Si no se
        // reinician, la pasada creeria que ya tiene todo y no bajaria nada
        // sobre una base que acaba de quedar vacia.
        marcasDeSincronizacion.reiniciar()

        return motor.sincronizar() is MotorDeSincronizacion.Resultado.Exito
    }

    /**
     * Siembra las cuatro secciones la primera vez que abre la app:
     * cuentas, categorias, movimientos y pagos fijos.
     *
     * Antes de sembrar se le pregunta a la nube si ya tiene datos de este
     * usuario. Sembrar sin preguntar era lo que duplicaba todo en el segundo
     * dispositivo: sembraba sus diez cuentas y ademas se bajaba las diez de la
     * nube. Si la nube responde que si, no se siembra nada y la primera pasada
     * de sincronizacion trae los datos de verdad.
     *
     * Si no se puede preguntar —sin red, o sin nube configurada— se siembra
     * igual: la aplicacion tiene que abrir con algo. Esa siembra a ciegas ya no
     * duplica nada porque los uuid de la demostracion son fijos (ver
     * [DefaultData]); a lo sumo la bajada actualiza esas mismas filas.
     */
    suspend fun seedIfEmpty() {
        if (motorDeSincronizacion?.laNubeTieneDatos() == true) return

        DefaultData.seedIfEmpty(
            accountDao = database.accountDao(),
            categoryDao = database.categoryDao(),
            transactionDao = database.transactionDao(),
            recurringPaymentDao = database.recurringPaymentDao()
        )
    }

    private companion object {
        const val ETIQUETA = "NexcodeDatos"
    }
}
