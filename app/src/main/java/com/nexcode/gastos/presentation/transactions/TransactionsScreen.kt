package com.nexcode.gastos.presentation.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.nexcode.gastos.presentation.util.compartirMovimientosComoCsv
import com.nexcode.gastos.presentation.util.hoy
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import com.nexcode.gastos.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexcode.gastos.domain.model.Money
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.presentation.components.DateHeader
import com.nexcode.gastos.presentation.components.RellenoTarjeta
import com.nexcode.gastos.presentation.components.SwipeToActionBox
import com.nexcode.gastos.presentation.components.TransactionRow
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.NexcodeBlue
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onBack: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("< Volver", color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RellenoTarjeta),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Todos los movimientos",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                // Exporta lo que se esta viendo: si hay una busqueda activa,
                // salen solo esos movimientos y no el historico entero.
                TextButton(
                    onClick = {
                        compartirMovimientosComoCsv(
                            context = contexto,
                            movimientos = state.visibles.values.flatten(),
                            categorias = state.categoriesById,
                            fecha = hoy().toString()
                        )
                    }
                ) {
                    Text("Exportar CSV", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }

        // Buscador. Filtra por titulo y por nota contra la base de datos, no
        // sobre la lista ya cargada: asi encuentra tambien lo que no cabe en
        // la pantalla.
        item {
            OutlinedTextField(
                value = state.consulta,
                onValueChange = viewModel::onConsultaChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RellenoTarjeta, vertical = 4.dp),
                singleLine = true,
                label = { Text("Buscar por titulo o nota") },
                placeholder = { Text("mercado, uber, arriendo...") },
                trailingIcon = {
                    if (state.consulta.isNotEmpty()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "Limpiar la busqueda",
                            tint = TextSecondary,
                            modifier = Modifier.clickable { viewModel.onLimpiarBusqueda() }
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexcodeBlue,
                    unfocusedBorderColor = DividerSoft
                )
            )
        }

        if (state.buscando) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = RellenoTarjeta),
                    text = state.resultados.values.sumOf { it.size }.toString() +
                        " movimientos encontrados",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        if (state.sinResultados) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = RellenoTarjeta, vertical = 24.dp),
                    text = "Ningun movimiento coincide con \"" + state.consulta + "\".",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        if (!state.isLoading && !state.buscando && state.grouped.isEmpty()) {
            item {
                Text(
                    text = "Aun no has registrado movimientos.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        state.visibles.forEach { (date, transactions) ->
            item(key = "h-" + date) {
                DateHeader(
                    date = date,
                    dayTotal = netTotal(transactions),
                    // La lista ya lleva 16 dp; estos 16 mas dejan el rotulo
                    // sobre el mismo eje que el texto de las filas.
                    modifier = Modifier.padding(horizontal = RellenoTarjeta)
                )
            }
            items(items = transactions, key = { it.id }) { transaction ->
                SwipeToActionBox(
                    onSwipeLeft = { viewModel.onDelete(transaction.id) },
                    modifier = Modifier.animateItem()
                ) {
                    TransactionRow(
                        transaction = transaction,
                        category = transaction.categoryId?.let { state.categoriesById[it] },
                        onClick = { onEditTransaction(transaction.id) }
                    )
                }
            }
        }
    }
}

private fun netTotal(transactions: List<Transaction>): Money =
    transactions.fold(Money.ZERO) { acc, transaction ->
        when (transaction.type) {
            is TransactionType.Transfer -> acc
            else -> acc + transaction.signedAmount
        }
    }
