package com.nexcode.gastos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexcode.gastos.data.local.converter.RoomConverters
import com.nexcode.gastos.data.local.dao.AccountDao
import com.nexcode.gastos.data.local.dao.CategoryDao
import com.nexcode.gastos.data.local.dao.RecurringPaymentDao
import com.nexcode.gastos.data.local.dao.TransactionDao
import com.nexcode.gastos.data.local.entity.AccountEntity
import com.nexcode.gastos.data.local.entity.CategoryEntity
import com.nexcode.gastos.data.local.entity.RecurringPaymentEntity
import com.nexcode.gastos.data.local.entity.TransactionEntity

/** Base de datos local (SQLite gestionada por Room). */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringPaymentEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class NexcodeDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao

    companion object {
        private const val DB_NAME = "nexcode.db"

        /**
         * Migracion 1 -> 2: se agrega el bolsillo de recordatorios.
         *
         * Se escribe la migracion en lugar de borrar la base para no perder los
         * movimientos ya registrados por el usuario al actualizar la app.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_payments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`amount_cents` INTEGER NOT NULL, " +
                        "`frequency_code` TEXT NOT NULL, " +
                        "`frequency_value1` INTEGER, " +
                        "`frequency_value2` INTEGER, " +
                        "`next_due_date` TEXT NOT NULL, " +
                        "`account_id` INTEGER NOT NULL, " +
                        "`category_id` INTEGER, " +
                        "`note` TEXT, " +
                        "`last_paid_date` TEXT, " +
                        "`is_active` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_payments_account_id` " +
                        "ON `recurring_payments` (`account_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_payments_category_id` " +
                        "ON `recurring_payments` (`category_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_payments_next_due_date` " +
                        "ON `recurring_payments` (`next_due_date`)"
                )
            }
        }

        /**
         * Migracion 2 -> 3: la base se prepara para replicarse en la nube.
         *
         * Cada tabla gana los cuatro campos de [MetadatosDeSincronizacion]. La
         * parte delicada es rellenar el `uuid` de las filas que ya existen: sin
         * eso quedarian todas con la cadena vacia, el indice unico fallaria y
         * la actualizacion tumbaria la aplicacion con los datos del usuario
         * dentro.
         *
         * El uuid se genera en SQL con `randomblob(16)`, que son los mismos 128
         * bits de azar que un UUID version 4. No se usa el generador de Java
         * porque habria que traer cada fila a memoria, y una migracion tiene
         * que poder correr sobre una tabla grande sin cargarla entera.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {

            private val tablas = listOf(
                "accounts", "categories", "transactions", "recurring_payments"
            )

            override fun migrate(db: SupportSQLiteDatabase) {
                val ahora = System.currentTimeMillis()

                tablas.forEach { tabla ->
                    db.execSQL(
                        "ALTER TABLE `" + tabla + "` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''"
                    )
                    db.execSQL(
                        "ALTER TABLE `" + tabla + "` ADD COLUMN `actualizado_en` " +
                            "INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE `" + tabla + "` ADD COLUMN `borrado` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE `" + tabla + "` ADD COLUMN `sincronizado` " +
                            "INTEGER NOT NULL DEFAULT 0"
                    )

                    // Identidad global para lo que ya estaba guardado.
                    db.execSQL(
                        "UPDATE `" + tabla + "` SET `uuid` = lower(hex(randomblob(16))), " +
                            "`actualizado_en` = " + ahora
                    )

                    // El indice se crea DESPUES de rellenar: sobre la columna
                    // recien creada todas las filas valdrian '' y un indice
                    // unico sobre valores repetidos no se puede construir.
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_" + tabla + "_uuid` " +
                            "ON `" + tabla + "` (`uuid`)"
                    )
                }
            }
        }

        @Volatile
        private var instance: NexcodeDatabase? = null

        /** Singleton con doble verificacion: una sola conexion para toda la app. */
        fun getInstance(context: Context): NexcodeDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): NexcodeDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                NexcodeDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
