package com.nexcode.gastos.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nexcode.gastos.R

/**
 * Destinos de navegacion.
 *
 * Sealed class de nuevo: agregar una pantalla obliga a decidir si entra o no
 * en la barra inferior, no se puede olvidar.
 */
sealed class Destination(val route: String) {

    /** Destinos que aparecen en la barra inferior. */
    sealed class BottomDestination(
        route: String,
        @param:StringRes val labelRes: Int,
        @param:DrawableRes val iconRes: Int
    ) : Destination(route)

    data object Home : BottomDestination("home", R.string.nav_gastos, R.drawable.ic_wallet)
    data object Pocket : BottomDestination("pocket", R.string.nav_bolsillo, R.drawable.ic_pocket)
    data object Assistant : BottomDestination("assistant", R.string.nav_asistente, R.drawable.ic_chat)
    data object Profile : BottomDestination("profile", R.string.nav_perfil, R.drawable.ic_profile)

    /**
     * Pantallas completas: ocultan la barra inferior.
     *
     * AddTransaction lleva un argumento opcional: sin id crea un movimiento,
     * con id edita el que corresponda.
     */
    data object AddTransaction : Destination("add_transaction?id={id}") {
        const val ARG_ID = "id"
        const val NEW_ID = 0L

        /** Ruta concreta para navegar. */
        fun routeFor(transactionId: Long = NEW_ID): String = "add_transaction?id=" + transactionId
    }

    data object Transactions : Destination("transactions")

    /** Gestion de cuentas y de su cupo. */
    data object Accounts : Destination("accounts")

    companion object {
        val bottomItems: List<BottomDestination> = listOf(Home, Pocket, Assistant, Profile)
    }
}
