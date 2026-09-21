package com.nexcode.gastos.domain.ai

import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.Tendencia
import com.nexcode.gastos.domain.model.Transaction

/**
 * CONTRATO de las herramientas que el agente puede usar sobre los datos del
 * usuario.
 *
 * Es la pieza que convierte un modelo de lenguaje en un agente: sin esto el
 * modelo solo puede hablar sobre el texto que se le envie de antemano, y con
 * esto decide por si mismo que consultar y cuando.
 *
 * El contrato vive en el dominio a proposito. Quien decide QUE puede hacer el
 * agente es la capa de negocio, no el proveedor de IA: si manana se cambia
 * Gemini por otro modelo, la lista de acciones permitidas no se mueve.
 *
 * Ninguna herramienta recibe identificadores internos. El agente trabaja con
 * los nombres que el usuario ve —"Mercado", "Transporte"— porque es lo unico
 * que va a mencionar en una conversacion.
 */
interface HerramientasFinancieras {

    /** Ingresos, gastos y reparto por categoria del mes en curso. */
    suspend fun resumenDelMes(): ResumenDelMes

    /** Serie de los ultimos meses con sus indicadores. */
    suspend fun tendenciaDelGasto(): Tendencia

    /** Pagos fijos vencidos o proximos a vencer. */
    suspend fun pagosPendientes(): List<PagoPendiente>

    /**
     * Movimientos que coinciden con el texto o la categoria indicados.
     *
     * Ambos filtros son opcionales: sin ninguno devuelve los mas recientes.
     */
    suspend fun buscarMovimientos(
        texto: String? = null,
        categoria: String? = null,
        limite: Int = LIMITE_BUSQUEDA
    ): List<Transaction>

    /**
     * Registra un gasto. Es la unica herramienta que ESCRIBE.
     *
     * Devuelve un resultado descriptivo en vez de lanzar: el agente tiene que
     * poder contarle al usuario por que no se pudo registrar.
     */
    suspend fun registrarGasto(
        monto: String?,
        titulo: String?,
        categoria: String? = null
    ): ResultadoDeRegistro

    companion object {
        const val LIMITE_BUSQUEDA = 15
    }
}

/** Foto del mes en curso, con las categorias ya ordenadas por peso. */
data class ResumenDelMes(
    val periodo: String,
    val ingresos: Money,
    val gastos: Money,
    val saldo: Money,
    val tasaDeAhorro: Float?,
    val categorias: List<CategoriaDelResumen>
)

data class CategoriaDelResumen(
    val nombre: String,
    val total: Money,
    val movimientos: Int,
    val porcentaje: Int
)

data class PagoPendiente(
    val nombre: String,
    val monto: Money,
    val diasParaVencer: Long,
    val estaVencido: Boolean
)

/**
 * Resultado de una escritura pedida por el agente.
 *
 * Jerarquia sellada: el codigo que la consume no puede olvidarse de un caso, y
 * el agente recibe siempre un mensaje que puede repetirle al usuario.
 */
sealed interface ResultadoDeRegistro {
    data class Exito(val id: Long, val descripcion: String) : ResultadoDeRegistro
    data class Rechazado(val motivo: String) : ResultadoDeRegistro
}
