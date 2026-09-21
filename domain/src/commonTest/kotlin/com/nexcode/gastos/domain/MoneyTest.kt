package com.nexcode.gastos.domain

import com.nexcode.gastos.domain.exception.InvalidAmountException
import com.nexcode.gastos.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pruebas del value class Money.
 *
 * Viven en `commonTest`, asi que se ejecutan tanto en la JVM como en Node.js:
 * la misma prueba valida las dos plataformas.
 */
class MoneyTest {

    @Test
    fun parse_acepta_formato_colombiano_con_separador_de_miles() {
        // 12.500,50 se redondea a 12.501 pesos: la app no maneja centavos.
        assertEquals(1_250_100L, Money.parse("12.500,50").cents)
    }

    @Test
    fun parse_acepta_punto_como_separador_decimal() {
        assertEquals(1_250_100L, Money.parse("12500.50").cents)
    }

    @Test
    fun parse_trata_el_punto_con_tres_digitos_como_separador_de_miles() {
        assertEquals(2_500_000L, Money.parse("25.000").cents)
    }

    @Test
    fun parse_ignora_simbolo_de_peso_y_espacios() {
        assertEquals(500_000L, Money.parse(" $ 5000 ").cents)
    }

    @Test
    fun parse_de_null_lanza_InvalidAmountException() {
        assertFailsWith<InvalidAmountException> { Money.parse(null) }
    }

    @Test
    fun parse_de_texto_no_numerico_lanza_InvalidAmountException() {
        assertFailsWith<InvalidAmountException> { Money.parse("mil pesos") }
    }

    @Test
    fun parse_redondea_hacia_arriba_al_peso_mas_cercano() {
        assertEquals(1_300L, Money.parse("12,60").cents)
    }

    @Test
    fun parse_redondea_hacia_abajo_al_peso_mas_cercano() {
        assertEquals(1_200L, Money.parse("12,40").cents)
    }

    @Test
    fun parse_rechaza_montos_por_encima_del_limite() {
        assertFailsWith<InvalidAmountException> { Money.parse("1000000000") }
    }

    @Test
    fun la_suma_de_importes_es_exacta() {
        val total = Money.parse("10.000") + Money.parse("20.000") + Money.parse("5.500")
        assertEquals(Money.parse("35.500").cents, total.cents)
    }

    @Test
    fun format_agrupa_miles_con_punto_y_no_muestra_decimales() {
        assertEquals("1.250.000", Money(125_000_000L).format())
    }

    @Test
    fun format_redondea_los_centavos_heredados() {
        // Un dato guardado antes del cambio a pesos enteros se muestra redondeado.
        assertEquals("1.250.001", Money(125_000_050L).format())
    }
}
