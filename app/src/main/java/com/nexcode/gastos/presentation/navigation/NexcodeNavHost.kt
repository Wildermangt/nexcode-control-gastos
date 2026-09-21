package com.nexcode.gastos.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexcode.gastos.R
import com.nexcode.gastos.di.NexcodeViewModelFactory
import com.nexcode.gastos.presentation.addtransaction.AddTransactionScreen
import com.nexcode.gastos.presentation.addtransaction.AddTransactionViewModel
import com.nexcode.gastos.presentation.assistant.AssistantScreen
import com.nexcode.gastos.presentation.assistant.AssistantViewModel
import com.nexcode.gastos.presentation.home.HomeScreen
import com.nexcode.gastos.presentation.home.HomeViewModel
import com.nexcode.gastos.presentation.accounts.AccountsScreen
import com.nexcode.gastos.presentation.accounts.AccountsViewModel
import com.nexcode.gastos.presentation.profile.ProfileScreen
import com.nexcode.gastos.presentation.recurring.RecurringScreen
import com.nexcode.gastos.presentation.recurring.RecurringViewModel
import com.nexcode.gastos.cuenta.CuentaViewModel
import com.nexcode.gastos.sincronizacion.SincronizacionViewModel
import com.nexcode.gastos.presentation.theme.rememberAnimatedNexcodeBrush
import com.nexcode.gastos.presentation.transactions.TransactionsScreen
import com.nexcode.gastos.presentation.transactions.TransactionsViewModel

private const val TRANSITION_MS = 320

/**
 * Esqueleto de navegacion.
 *
 * Cada destino tiene su propia animacion de entrada y salida:
 *  - las pestanas de la barra inferior se funden con un desplazamiento corto,
 *  - las pantallas completas entran deslizandose desde abajo o desde la derecha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexcodeNavHost(
    factory: NexcodeViewModelFactory,
    modifier: Modifier = Modifier,
    /** true cuando el usuario llego tocando la notificacion de un pago fijo. */
    irAlBolsillo: Boolean = false,
    onBolsilloAtendido: () -> Unit = {},
    onVerBienvenida: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // El bolsillo se comparte con la barra inferior para pintar la insignia.
    val pocketViewModel: RecurringViewModel = viewModel(factory = factory)
    val pocketState by pocketViewModel.uiState.collectAsStateWithLifecycle()
    val pendingReminders = pocketState.pocket.overdueCount + pocketState.pocket.dueTodayCount

    // La notificacion pide abrir el bolsillo: se atiende como evento para que
    // funcione tanto en arranque en frio como con la app ya abierta.
    LaunchedEffect(irAlBolsillo) {
        if (irAlBolsillo) {
            navController.navigate(Destination.Pocket.route) {
                popUpTo(Destination.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onBolsilloAtendido()
        }
    }

    val showBottomBar = Destination.bottomItems.any { it.route == currentRoute }
    val showFab = currentRoute == Destination.Home.route
    val fabBrush = rememberAnimatedNexcodeBrush(durationMillis = 4000, travel = 220f)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                NexcodeBottomBar(
                    currentRoute = currentRoute,
                    pendingReminders = pendingReminders,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(Destination.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            // El boton flotante aparece con un rebote y lleva el degradado del logo.
            AnimatedVisibility(
                visible = showFab,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { navController.navigate(Destination.AddTransaction.routeFor()) },
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(fabBrush)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = stringResource(id = R.string.cd_agregar)
                    )
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            // Transicion por defecto de las pestanas: fundido con deslizamiento corto.
            enterTransition = {
                fadeIn(tween(TRANSITION_MS)) +
                    slideInHorizontally(tween(TRANSITION_MS)) { it / 12 }
            },
            exitTransition = {
                fadeOut(tween(TRANSITION_MS / 2)) +
                    slideOutHorizontally(tween(TRANSITION_MS)) { -it / 12 }
            }
        ) {
            composable(Destination.Home.route) {
                val viewModel: HomeViewModel = viewModel(factory = factory)
                HomeScreen(
                    viewModel = viewModel,
                    onSeeAll = { navController.navigate(Destination.Transactions.route) },
                    onEditTransaction = { id -> navController.navigate(Destination.AddTransaction.routeFor(id)) },
                    contentPadding = innerPadding
                )
            }

            composable(Destination.Pocket.route) {
                RecurringScreen(
                    viewModel = pocketViewModel,
                    contentPadding = innerPadding
                )
            }

            composable(Destination.Assistant.route) {
                val viewModel: AssistantViewModel = viewModel(factory = factory)
                AssistantScreen(viewModel = viewModel, contentPadding = innerPadding)
            }

            composable(Destination.Profile.route) {
                val sincronizacion: SincronizacionViewModel = viewModel(factory = factory)
                val cuenta: CuentaViewModel = viewModel(factory = factory)
                ProfileScreen(
                    contentPadding = innerPadding,
                    sincronizacion = sincronizacion,
                    cuenta = cuenta,
                    onVerBienvenida = onVerBienvenida,
                    onVerCuentas = { navController.navigate(Destination.Accounts.route) }
                )
            }

            // Pantalla completa: entra desde abajo, como una hoja modal.
            // El argumento id decide si crea o edita.
            composable(
                route = Destination.AddTransaction.route,
                arguments = listOf(
                    navArgument(Destination.AddTransaction.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = Destination.AddTransaction.NEW_ID
                    }
                ),
                enterTransition = {
                    slideInVertically(tween(TRANSITION_MS)) { it } + fadeIn(tween(TRANSITION_MS))
                },
                exitTransition = {
                    slideOutVertically(tween(TRANSITION_MS)) { it } + fadeOut(tween(TRANSITION_MS))
                }
            ) { entry ->
                val viewModel: AddTransactionViewModel = viewModel(factory = factory)
                val editingId = entry.arguments?.getLong(Destination.AddTransaction.ARG_ID)
                    ?: Destination.AddTransaction.NEW_ID

                AddTransactionScreen(
                    viewModel = viewModel,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                    transactionId = editingId.takeIf { it > 0L },
                    modifier = Modifier.padding(
                        PaddingValues(top = innerPadding.calculateTopPadding())
                    )
                )
            }

            // Historico: entra desde la derecha.
            composable(
                route = Destination.Transactions.route,
                enterTransition = {
                    slideInHorizontally(tween(TRANSITION_MS)) { it } + fadeIn(tween(TRANSITION_MS))
                },
                exitTransition = {
                    slideOutHorizontally(tween(TRANSITION_MS)) { it } + fadeOut(tween(TRANSITION_MS))
                }
            ) {
                val viewModel: TransactionsViewModel = viewModel(factory = factory)
                TransactionsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onEditTransaction = { id ->
                        navController.navigate(Destination.AddTransaction.routeFor(id))
                    }
                )
            }

            // Cuentas: entra desde la derecha, como el historico.
            composable(
                route = Destination.Accounts.route,
                enterTransition = {
                    slideInHorizontally(tween(TRANSITION_MS)) { it } + fadeIn(tween(TRANSITION_MS))
                },
                exitTransition = {
                    slideOutHorizontally(tween(TRANSITION_MS)) { it } + fadeOut(tween(TRANSITION_MS))
                }
            ) {
                val viewModel: AccountsViewModel = viewModel(factory = factory)
                AccountsScreen(
                    viewModel = viewModel,
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
