package com.nexcode.gastos.domain.model

import com.nexcode.gastos.domain.exception.InvalidAmountException
import kotlin.jvm.JvmInline

/**
 * Valor monetario almacenado en CENTAVOS.
 *
 * La aplicacion opera en PESOS ENTEROS: el peso colombiano no se subdivide en
 * la practica, de modo que los importes se redondean al peso al analizarlos y
 * se muestran sin decimales. El almacenamiento sigue siendo en centavos por
 * dos razones: no obliga a migrar los datos ya guardados, y deja la puerta
 * abierta a una moneda que si use decimales.
 *
 * Nunca se usa Double para dinero: 0.1 + 0.2 == 0.30000000000000004.
 * `value class` da seguridad de tipos en compilacion sin costo de objeto en
 * tiempo de ejecucion (el compilador lo reemplaza por un Long).
 *
 * El analisis del texto se hace con aritmetica entera y no con BigDecimal,
 * porque esa clase pertenece a la biblioteca de Java y este codigo debe
 * compilar tambien para plataformas distintas de la JVM.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    val isPositive: Boolean get() = cents > 0
    val isNegative: Boolean get() = cents < 0
    val isZero: Boolean get() = cents == 0L

    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)
    operator fun times(factor: Int): Money = Money(cents * factor)
    operator fun unaryMinus(): Money = Money(-cents)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    fun abs(): Money = if (cents < 0) Money(-cents) else this

    /** Valor en pesos. Solo para mostrar o graficar, nunca para calcular. */
    fun toMajorUnits(): Double = cents / 100.0

    /** Formato colombiano en pesos enteros: 1.250.000 */
    fun format(withSign: Boolean = false): String {
        val negative = cents < 0
        val absolute = if (negative) -cents else cents
        // Redondeo al peso mas cercano: la interfaz no muestra centavos.
        val units = (absolute + 50) / 100

        val grouped = units.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()

        val sign = when {
            negative -> "-"
            withSign && cents > 0 -> "+"
            else -> ""
        }
        return sign + grouped
    }

    companion object {
        val ZERO = Money(0L)

        /** Limite superior admitido: 999.999.999,00 */
        private const val MAX_UNITS = 999_999_999L

        /**
         * El mismo limite, ya como importe.
         *
         * Se expone para que quien construya un [Money] por aritmetica —y no
         * analizando texto— pueda comprobar el tope con la misma referencia,
         * en vez de repetir la cifra y arriesgarse a que las dos se separen.
         */
        val MAXIMO = Money(MAX_UNITS * 100L)

        /** Construye un importe desde pesos, redondeando al peso mas cercano. */
        fun fromMajor(pesos: Double): Money =
            Money(kotlin.math.round(pesos).toLong() * 100L)

        /**
         * Convierte el texto que escribe el usuario en un [Money].
         *
         * Acepta "$ 12.500,50", "12500,50", "12500.50" y "25.000". Los
         * decimales que escriba el usuario se redondean al peso mas cercano.
         *
         * Reglas pensadas para el formato colombiano:
         *  - Si hay coma, la coma es el separador decimal y los puntos son miles.
         *  - Si solo hay puntos y el ultimo va seguido de EXACTAMENTE 3 digitos,
         *    se trata como separador de miles ("25.000" = veinticinco mil).
         *  - En cualquier otro caso el punto es el separador decimal ("12500.50").
         *
         * @throws InvalidAmountException si el texto esta vacio o no es numerico.
         */
        fun parse(raw: String?): Money {
            // SEGURIDAD CONTRA NULOS: orEmpty() convierte null en "" sin riesgo de NPE.
            val text = raw?.trim().orEmpty()
                .replace("$", "")
                .replace(" ", "")
                .replace(" ", "")

            if (text.isEmpty()) {
                throw InvalidAmountException("El monto no puede estar vacio")
            }

            val lastComma = text.lastIndexOf(',')
            val lastDot = text.lastIndexOf('.')
            val digitsAfterLastDot = if (lastDot >= 0) text.length - lastDot - 1 else -1

            val normalized = when {
                // Hay coma: la coma manda como decimal y los puntos son miles.
                lastComma >= 0 -> text.replace(".", "").replace(',', '.')
                // Solo puntos, el ultimo con 3 digitos detras: separador de miles.
                lastDot >= 0 && digitsAfterLastDot == 3 -> text.replace(".", "")
                // Resto de casos: el punto es el separador decimal.
                else -> text
            }

            return aCentavos(normalized, raw)
        }

        /**
         * Convierte una cadena ya normalizada (punto decimal, sin miles) a
         * centavos, con redondeo al centavo mas cercano.
         */
        private fun aCentavos(normalized: String, raw: String?): Money {
            var cuerpo = normalized
            var signo = 1L

            if (cuerpo.startsWith("-")) {
                signo = -1L
                cuerpo = cuerpo.substring(1)
            } else if (cuerpo.startsWith("+")) {
                cuerpo = cuerpo.substring(1)
            }

            val primerPunto = cuerpo.indexOf('.')
            val ultimoPunto = cuerpo.lastIndexOf('.')
            if (primerPunto != ultimoPunto) {
                throw InvalidAmountException("\"" + raw + "\" no es un monto valido")
            }

            val enteros = if (primerPunto < 0) cuerpo else cuerpo.substring(0, primerPunto)
            val decimales = if (primerPunto < 0) "" else cuerpo.substring(primerPunto + 1)

            if (enteros.isEmpty() && decimales.isEmpty()) {
                throw InvalidAmountException("\"" + raw + "\" no es un monto valido")
            }
            if (!enteros.all { it.isDigit() } || !decimales.all { it.isDigit() }) {
                throw InvalidAmountException("\"" + raw + "\" no es un monto valido")
            }

            // Se miran tres decimales para poder redondear el tercero.
            val tresDecimales = decimales.padEnd(3, '0').substring(0, 3)
            val centavosBase = tresDecimales.substring(0, 2).toLong()
            val tercerDigito = tresDecimales[2]
            val centavos = if (tercerDigito >= '5') centavosBase + 1 else centavosBase

            val unidades = if (enteros.isEmpty()) {
                0L
            } else {
                enteros.toLongOrNull()
                    ?: throw InvalidAmountException(
                        "El monto \"" + raw + "\" excede el limite permitido"
                    )
            }

            if (unidades > MAX_UNITS) {
                throw InvalidAmountException("El monto \"" + raw + "\" excede el limite permitido")
            }

            // La app trabaja en pesos enteros: se redondea al peso mas cercano.
            val totalCentavos = unidades * 100L + centavos
            val enPesos = (totalCentavos + 50L) / 100L
            return Money(signo * enPesos * 100L)
        }
    }
}
