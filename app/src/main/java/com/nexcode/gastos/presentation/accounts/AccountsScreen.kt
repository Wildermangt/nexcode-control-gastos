package com.nexcode.gastos.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexcode.gastos.domain.usecase.catalog.AccountSummary
import com.nexcode.gastos.presentation.components.AnimatedMoneyText
import com.nexcode.gastos.presentation.components.MargenLateral
import com.nexcode.gastos.presentation.components.NexcodeCard
import com.nexcode.gastos.presentation.components.RellenoTarjeta
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.HeaderGradientColors
import com.nexcode.gastos.presentation.theme.IncomeGreen
import com.nexcode.gastos.presentation.theme.NexcodeSurfaceElevated
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextOnBrand
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary
import com.nexcode.gastos.presentation.theme.WhiteAlpha70

/**
 * Gestion de cuentas: muestra el cupo de cada una y permite subirlo o bajarlo.
 *
 * Distingue en pantalla dos cifras que conviene no confundir: el **cupo** que
 * el usuario fija y el **disponible** que queda tras los movimientos ya
 * registrados. Si solo se mostrara el cupo, la pantalla afirmaria que hay un
 * dinero que quiza ya se gasto.
 */
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 10.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Encabezado(state, onBack) }

        items(items = state.accounts, key = { it.account.id }) { resumen ->
            TarjetaDeCuenta(
                resumen = resumen,
                pasos = viewModel.pasos,
                onEditar = { viewModel.abrirEdicion(resumen) },
                onAumentar = { viewModel.aumentar(resumen.account.id, it) },
                onDisminuir = { viewModel.disminuir(resumen.account.id, it) },
                modifier = Modifier.padding(horizontal = MargenLateral)
            )
        }

        state.feedback?.let { texto ->
            item {
                Text(
                    text = texto,
                    color = IncomeGreen,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = MargenLateral + RellenoTarjeta)
                )
            }
        }
    }

    if (state.editingId != null) {
        DialogoDeCupo(
            nombre = state.editingName,
            monto = state.editingAmount,
            error = state.editingError,
            guardando = state.isSaving,
            onMontoChange = viewModel::onAmountChange,
            onGuardar = viewModel::guardarCupo,
            onCerrar = viewModel::cerrarEdicion
        )
    }
}

@Composable
private fun Encabezado(state: AccountsUiState, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MargenLateral)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(HeaderGradientColors))
    ) {
        Column(modifier = Modifier.padding(RellenoTarjeta)) {
            Text(
                text = "< Volver",
                color = WhiteAlpha70,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
            Text(
                text = "Mis cuentas",
                color = TextOnBrand,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = "Cupo total",
                color = WhiteAlpha70,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
            AnimatedMoneyText(
                money = state.totalCupo,
                fontSize = 30.sp,
                color = TextOnBrand
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Disponible tras movimientos:", color = WhiteAlpha70, fontSize = 12.sp)
                Spacer(Modifier.size(6.dp))
                AnimatedMoneyText(
                    money = state.totalDisponible,
                    fontSize = 14.sp,
                    color = TextOnBrand,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TarjetaDeCuenta(
    resumen: AccountSummary,
    pasos: List<Long>,
    onEditar: () -> Unit,
    onAumentar: (Long) -> Unit,
    onDisminuir: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    NexcodeCard(modifier = modifier, padding = PaddingValues(RellenoTarjeta)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(resumen.account.colorArgb.toInt()))
            )
            Text(
                text = resumen.account.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            )
            Text(
                text = "Editar",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEditar() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Cupo", color = TextSecondary, fontSize = 11.sp)
                AnimatedMoneyText(
                    money = resumen.cupo,
                    fontSize = 19.sp,
                    color = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Disponible", color = TextSecondary, fontSize = 11.sp)
                AnimatedMoneyText(
                    money = resumen.disponible,
                    fontSize = 19.sp,
                    color = if (resumen.disponible.cents < 0) ExpenseRed else IncomeGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (resumen.movimientos.cents != 0L) {
            Text(
                text = "Movimientos registrados: " + resumen.movimientos.format(withSign = true),
                color = TextMedium,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // Ciclo sobre los pasos: un par de botones por cada cantidad.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pasos.forEach { pesos ->
                BotonDePaso(
                    texto = "- " + etiquetaCorta(pesos),
                    modifier = Modifier.weight(1f),
                    onClick = { onDisminuir(pesos) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pasos.forEach { pesos ->
                BotonDePaso(
                    texto = "+ " + etiquetaCorta(pesos),
                    modifier = Modifier.weight(1f),
                    resaltado = true,
                    onClick = { onAumentar(pesos) }
                )
            }
        }
    }
}

@Composable
private fun BotonDePaso(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    resaltado: Boolean = false
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (resaltado) NexcodeSurfaceElevated else DividerSoft.copy(alpha = 0.4f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = if (resaltado) MaterialTheme.colorScheme.primary else TextMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 50 000 -> "50 mil", 1 000 000 -> "1 M": etiquetas que caben en el boton. */
private fun etiquetaCorta(pesos: Long): String = when {
    pesos >= 1_000_000 -> (pesos / 1_000_000).toString() + " M"
    pesos >= 1_000 -> (pesos / 1_000).toString() + " mil"
    else -> pesos.toString()
}

@Composable
private fun DialogoDeCupo(
    nombre: String,
    monto: String,
    error: String?,
    guardando: Boolean,
    onMontoChange: (String) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(text = "Cupo de " + nombre, fontSize = 18.sp) },
        text = {
            Column {
                Text(
                    text = "Escribe la cantidad que quieres dejar en esta cuenta.",
                    color = TextMedium,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = monto,
                    onValueChange = onMontoChange,
                    singleLine = true,
                    isError = error != null,
                    prefix = { Text("$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = ExpenseRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onGuardar, enabled = !guardando) {
                Text(if (guardando) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text("Cancelar") }
        }
    )
}
