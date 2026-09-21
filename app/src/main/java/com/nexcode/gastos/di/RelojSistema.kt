package com.nexcode.gastos.di

import com.nexcode.gastos.domain.util.RelojDominio
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

/**
 * Implementacion del reloj del dominio para Android.
 *
 * El dominio declara la interfaz [RelojDominio] porque conocer la hora actual
 * es una dependencia del entorno, no una regla de negocio. Aqui se cumple ese
 * contrato con el reloj del dispositivo; en las pruebas se sustituye por una
 * fecha fija.
 */
class RelojSistema : RelojDominio {
    override fun ahora(): LocalDateTime =
        java.time.LocalDateTime.now().toKotlinLocalDateTime()
}
