package com.nexcode.gastos.domain.model

/**
 * Categoria de gasto o ingreso.
 *
 * Admite jerarquia de un nivel (Transporte > Gasolina) mediante [parentId],
 * que es nullable: null significa "categoria raiz".
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val iconKey: String = "ic_category",
    val colorArgb: Long = 0xFFFF7A2FL,
    val parentId: Long? = null,
    val monthlyBudget: Money? = null,
    val appliesTo: TransactionType = TransactionType.Expense
) {
    // Encapsulamiento: las invariantes se validan al construir el objeto,
    // por lo que es imposible tener una Category invalida en memoria.
    init {
        require(name.isNotBlank()) { "El nombre de la categoria no puede estar vacio" }
        require(id == 0L || parentId != id) { "Una categoria no puede ser su propio padre" }
        require(monthlyBudget == null || monthlyBudget.isPositive) {
            "El presupuesto mensual debe ser mayor que cero"
        }
    }

    val isSubcategory: Boolean get() = parentId != null
    val hasBudget: Boolean get() = monthlyBudget != null

    /** Ruta legible. `?.let` + Elvis: safe call con valor por defecto. */
    fun displayName(parent: Category? = null): String =
        parent?.let { it.name + " > " + name } ?: name

    /** Porcentaje de presupuesto consumido, o null si la categoria no tiene presupuesto. */
    fun budgetUsage(spent: Money): Float? {
        val budget = monthlyBudget ?: return null
        if (budget.isZero) return null
        return (spent.cents.toFloat() / budget.cents.toFloat()).coerceIn(0f, 1f)
    }
}
