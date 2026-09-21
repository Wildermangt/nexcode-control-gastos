package com.nexcode.gastos.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.presentation.components.BalanceHeader
import com.nexcode.gastos.presentation.components.AlineacionDeTexto
import com.nexcode.gastos.presentation.components.MargenLateral
import com.nexcode.gastos.presentation.components.CategoryDonutChart
import com.nexcode.gastos.presentation.components.DateHeader
import com.nexcode.gastos.presentation.components.SwipeToActionBox
import com.nexcode.gastos.presentation.components.TendenciaChart
import com.nexcode.gastos.presentation.components.TransactionRow
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.nexcode.gastos.presentation.util.formatear
import com.nexcode.gastos.presentation.util.hoy

private val MONTH_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "CO"))

// El margen lateral vive junto a las tarjetas: de ahi sale tambien la
// alineacion del texto del encabezado.
private val SIDE_PADDING = MargenLateral

/**
 * Pantalla principal, con la misma estructura que el inicio de NEXCODE Banca
 * digital: encabezado azul de ancho completo con el saldo, y debajo las
 * tarjetas blancas de los movimientos agrupados por fecha.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSeeAll: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            // El panel superior ya no llega al borde: la lista entera empieza
            // por debajo de la barra de estado.
            top = contentPadding.calculateTopPadding() + 10.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // El encabezado ocupa todo el ancho: sin margen lateral.
        item {
            BalanceHeader(
                balance = state.balance,
                periodLabel = hoy().formatear(MONTH_FORMAT),
                greeting = "Hola, Jeferson"
                // Sin desplazamiento superior: el encabezado llega hasta el
                // borde y aparta su contenido por dentro.
            )
        }

        // Grafico de dona: reparto del gasto del mes por categoria.
        if (state.hasChart) {
            item {
                CategoryDonutChart(
                    summaries = state.categorySummaries,
                    modifier = Modifier.padding(start = SIDE_PADDING, end = SIDE_PADDING, top = 14.dp)
                )
            }
        }

        // Analitica: como se compara este mes con los anteriores.
        if (state.hasTendencia) {
            item {
                TendenciaChart(
                    tendencia = state.tendencia,
                    modifier = Modifier.padding(start = SIDE_PADDING, end = SIDE_PADDING, top = 14.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Los titulos de seccion van fuera de tarjeta, asi que
                    // llevan la alineacion completa para caer en el mismo eje
                    // que el texto que si esta dentro de una.
                    .padding(start = AlineacionDeTexto, end = AlineacionDeTexto, top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Movimientos recientes",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onSeeAll) {
                    Text("Ver todos", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (state.isEmpty) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Todavia no hay movimientos",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Toca el boton de abajo para registrar tu primer gasto.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // Ciclo sobre el Map agrupado: un encabezado por dia y sus movimientos.
        state.grouped.forEach { (date, transactions) ->
            item(key = "header-" + date) {
                DateHeader(
                    date = date,
                    dayTotal = netTotal(transactions),
                    modifier = Modifier.padding(horizontal = AlineacionDeTexto)
                )
            }

            items(items = transactions, key = { it.id }) { transaction ->
                // Deslizar a la izquierda elimina el movimiento.
                SwipeToActionBox(
                    onSwipeLeft = { viewModel.onDeleteTransaction(transaction.id) },
                    modifier = Modifier
                        .padding(horizontal = SIDE_PADDING)
                        .animateItem()
                ) {
                    TransactionRow(
                        transaction = transaction,
                        // Safe call: la categoria puede no existir.
                        category = transaction.categoryId?.let { state.categoriesById[it] },
                        // Tocar la fila abre el mismo formulario en modo edicion.
                        onClick = { onEditTransaction(transaction.id) }
                    )
                }
            }
        }
    }
}

/** Suma neta del dia usando fold sobre la lista. */
private fun netTotal(transactions: List<Transaction>): Money =
    transactions.fold(Money.ZERO) { acc, transaction ->
        when (transaction.type) {
            is TransactionType.Transfer -> acc
            else -> acc + transaction.signedAmount
        }
    }
