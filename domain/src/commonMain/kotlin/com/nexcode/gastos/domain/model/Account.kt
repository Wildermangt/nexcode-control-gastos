package com.nexcode.gastos.domain.model

/** Cuenta o bolsillo donde vive el dinero (efectivo, banco, tarjeta). */
data class Account(
    val id: Long = 0L,
    val name: String,
    val initialBalance: Money = Money.ZERO,
    val colorArgb: Long = 0xFF40E0A0L,
    val iconKey: String = "ic_wallet",
    val isArchived: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "El nombre de la cuenta no puede estar vacio" }
    }
}
