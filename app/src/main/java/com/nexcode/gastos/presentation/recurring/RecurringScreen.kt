package com.nexcode.gastos.presentation.recurring

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.RecurringFrequency
import com.nexcode.gastos.domain.model.RecurringPayment
import com.nexcode.gastos.presentation.components.AnimatedMoneyText
import com.nexcode.gastos.presentation.components.GradientButton
import com.nexcode.gastos.presentation.components.SwipeToActionBox
import com.nexcode.gastos.presentation.theme.BalanceGradientColors
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.NexcodeBlue
import com.nexcode.gastos.presentation.theme.NexcodeBlueDeep
import com.nexcode.gastos.presentation.theme.NexcodeMintDeep
import com.nexcode.gastos.presentation.theme.NexcodeSurface
import com.nexcode.gastos.presentation.theme.NexcodeSurfaceElevated
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextOnBrand
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary
import com.nexcode.gastos.presentation.theme.WarningAmber
import com.nexcode.gastos.presentation.theme.WhiteAlpha15
import com.nexcode.gastos.presentation.theme.WhiteAlpha70
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.nexcode.gastos.presentation.util.formatear
import com.nexcode.gastos.presentation.util.hoy
import kotlinx.datetime.LocalDate

private val DUE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "CO"))

/**
 * BOLSILLO DE RECORDATORIOS: los pagos fijos que se repiten todos los meses.
 *
 * Controles de deslizamiento en cada fila:
 *   deslizar a la DERECHA  -> registrar el pago (crea el gasto real)
 *   deslizar a la IZQUIERDA -> eliminar el recordatorio
 */
@Composable
fun RecurringScreen(
    viewModel: RecurringViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = hoy()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Bolsillo de recordatorios",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item { PocketSummaryCard(state = state) }

        item {
            if (state.isFormVisible) {
                Button(
                    onClick = viewModel::onToggleForm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexcodeSurfaceElevated,
                        contentColor = TextMedium
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(text = "Cancelar", fontWeight = FontWeight.SemiBold)
                }
            } else {
                GradientButton(
                    text = "+  Nuevo pago fijo",
                    onClick = viewModel::onToggleForm,
                    height = 50
                )
            }
        }

        // El formulario se despliega con animacion de altura y desvanecido.
        item {
            AnimatedVisibility(
                visible = state.isFormVisible,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(tween(150))
            ) {
                RecurringForm(viewModel = viewModel, state = state)
            }
        }

        if (!state.isLoading && state.pocket.isEmpty) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tu bolsillo esta vacio",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Agrega el arriendo, el internet o la cuota de la universidad.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        items(items = state.pocket.payments, key = { it.id }) { payment ->
            SwipeToActionBox(
                onSwipeLeft = { viewModel.onDelete(payment.id) },
                onSwipeRight = { viewModel.onPay(payment.id) },
                modifier = Modifier.animateItem()
            ) {
                RecurringRow(
                    payment = payment,
                    category = payment.categoryId?.let { state.categoriesById[it] },
                    today = today,
                    onClick = { viewModel.onTogglePayment(payment.id) }
                )
            }
        }

        if (!state.pocket.isEmpty) {
            item {
                Text(
                    text = "Desliza a la derecha para registrar el pago, a la izquierda para eliminarlo.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Resumen del bolsillo con el degradado de marca, como la tarjeta de saldo del banco. */
@Composable
private fun PocketSummaryCard(state: RecurringUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = NexcodeBlueDeep,
                spotColor = NexcodeBlueDeep
            )
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(BalanceGradientColors))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "COMPROMISO MENSUAL FIJO",
                color = WhiteAlpha70,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium
            )

            AnimatedMoneyText(
                money = state.pocket.monthlyCommitment,
                fontSize = 30.sp,
                color = TextOnBrand,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CounterPill(
                    value = state.pocket.payments.count { it.isActive },
                    label = "activos",
                    color = TextOnBrand
                )
                CounterPill(
                    value = state.pocket.dueTodayCount,
                    label = "hoy",
                    color = WarningAmber
                )
                CounterPill(
                    value = state.pocket.overdueCount,
                    label = "vencidos",
                    color = Color(0xFFFF9AA2)
                )
            }

            // Safe call: puede no haber proximo pago.
            state.pocket.nextPayment?.let { next ->
                val vencido = next.status(hoy()) == RecurringPayment.Status.OVERDUE
                Text(
                    text = (if (vencido) "Mas urgente: " else "Proximo: ") +
                        next.name + " el " + next.nextDueDate.formatear(DUE_FORMAT),
                    color = if (vencido) Color(0xFFFF9AA2) else WhiteAlpha70,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun CounterPill(value: Int, label: String, color: Color) {
    // Sobre el degradado azul las pastillas van en blanco translucido.
    // El numero crece con un pequeno rebote cuando cambia.
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "escala-contador"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WhiteAlpha15)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = value.toString(),
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(scale)
        )
        Text(text = label, color = WhiteAlpha70, fontSize = 11.sp)
    }
}

/** Fila de un pago fijo con su estado de vencimiento. */
@Composable
private fun RecurringRow(
    payment: RecurringPayment,
    category: Category?,
    today: LocalDate,
    onClick: () -> Unit
) {
    val status = payment.status(today)
    val statusColor = when (status) {
        RecurringPayment.Status.OVERDUE -> ExpenseRed
        RecurringPayment.Status.TODAY -> WarningAmber
        RecurringPayment.Status.SOON -> NexcodeBlue
        RecurringPayment.Status.SCHEDULED -> NexcodeMintDeep
        RecurringPayment.Status.PAUSED -> TextSecondary
    }
    val statusLabel = when (status) {
        RecurringPayment.Status.OVERDUE -> "Vencido"
        RecurringPayment.Status.TODAY -> "Vence hoy"
        RecurringPayment.Status.SOON -> "En " + payment.daysUntilDue(today) + " dias"
        RecurringPayment.Status.SCHEDULED -> payment.nextDueDate.formatear(DUE_FORMAT)
        RecurringPayment.Status.PAUSED -> "Pausado"
    }

    // Los vencidos laten para llamar la atencion.
    val pulse = rememberInfiniteTransition(label = "latido")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alfa-latido"
    )
    val dotAlpha = if (status == RecurringPayment.Status.OVERDUE) pulseAlpha else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexcodeSurface)
            .border(1.dp, DividerSoft, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(statusColor.copy(alpha = dotAlpha), CircleShape)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payment.name,
                color = if (payment.isActive) TextPrimary else TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // Elvis: la categoria puede no existir.
                text = (category?.name ?: "Sin categoria") + "  ·  " + payment.frequency.label(),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$ " + payment.amount.format(),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = statusLabel,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Formulario para crear un pago fijo. */
@Composable
private fun RecurringForm(
    viewModel: RecurringViewModel,
    state: RecurringUiState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NexcodeSurface)
            .border(1.dp, DividerSoft, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.formName,
            onValueChange = viewModel::onNameChange,
            label = { Text("Nombre del pago") },
            placeholder = { Text("Arriendo") },
            singleLine = true,
            colors = formColors(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.formAmount,
            onValueChange = viewModel::onAmountChange,
            label = { Text("Monto") },
            placeholder = { Text("850.000") },
            prefix = { Text("$ ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = formColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Periodicidad", color = TextSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrequencyChip(state, viewModel, RecurringFrequency.CODE_MONTHLY, "Mensual")
            FrequencyChip(state, viewModel, RecurringFrequency.CODE_WEEKLY, "Semanal")
            FrequencyChip(state, viewModel, RecurringFrequency.CODE_YEARLY, "Anual")
        }

        OutlinedTextField(
            value = state.formDay,
            onValueChange = viewModel::onDayChange,
            label = { Text(state.dayLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = formColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Categoria", color = TextSecondary, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = state.categories, key = { it.id }) { category ->
                FilterChip(
                    selected = state.formCategoryId == category.id,
                    onClick = { viewModel.onCategoryChange(category.id) },
                    label = { Text(category.name) },
                    colors = chipColors()
                )
            }
        }

        Text(text = "Cuenta", color = TextSecondary, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = state.accounts, key = { it.id }) { account ->
                FilterChip(
                    selected = state.formAccountId == account.id,
                    onClick = { viewModel.onAccountChange(account.id) },
                    label = { Text(account.name) },
                    colors = chipColors()
                )
            }
        }

        // El error entra y sale con desvanecido.
        AnimatedVisibility(
            visible = state.formError != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = state.formError ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        GradientButton(
            text = "Guardar recordatorio",
            onClick = viewModel::onSave,
            enabled = !state.isSaving,
            height = 50
        )
    }
}

@Composable
private fun FrequencyChip(
    state: RecurringUiState,
    viewModel: RecurringViewModel,
    code: String,
    label: String
) {
    FilterChip(
        selected = state.formFrequencyCode == code,
        onClick = { viewModel.onFrequencyChange(code) },
        label = { Text(label) },
        colors = chipColors()
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = NexcodeSurfaceElevated,
    labelColor = TextMedium,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)

@Composable
private fun formColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = MaterialTheme.colorScheme.primary
)
