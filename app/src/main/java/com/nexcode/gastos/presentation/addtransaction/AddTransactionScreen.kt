package com.nexcode.gastos.presentation.addtransaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.presentation.components.GradientButton
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.IncomeGreen
import com.nexcode.gastos.presentation.theme.NexcodeSurface
import com.nexcode.gastos.presentation.theme.NexcodeSurfaceElevated
import com.nexcode.gastos.presentation.theme.TextMedium
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary

/**
 * Formulario de registro.
 *
 * Ninguna validacion vive aqui: la pantalla envia el texto crudo al ViewModel,
 * el ViewModel al caso de uso, y solo pinta el mensaje que reciba de vuelta.
 */
@Composable
fun AddTransactionScreen(
    viewModel: AddTransactionViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    transactionId: Long? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Si llega un id, el formulario se llena con el movimiento existente.
    LaunchedEffect(transactionId) {
        viewModel.startEditing(transactionId)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = state.screenTitle,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // --- Tipo de transaccion ---
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TypeChip(
                label = "Gasto",
                selected = state.type is TransactionType.Expense,
                accent = ExpenseRed,
                onClick = { viewModel.onTypeChange(TransactionType.Expense) }
            )
            TypeChip(
                label = "Ingreso",
                selected = state.type is TransactionType.Income,
                accent = IncomeGreen,
                onClick = { viewModel.onTypeChange(TransactionType.Income) }
            )
        }

        // --- Monto ---
        OutlinedTextField(
            value = state.amountText,
            onValueChange = viewModel::onAmountChange,
            label = { Text("Monto") },
            placeholder = { Text("25.000") },
            prefix = { Text("$ ") },
            singleLine = true,
            isError = state.errorMessage != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = nexcodeFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        // --- Titulo ---
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            label = { Text("Titulo (opcional)") },
            placeholder = { Text("Almuerzo en la universidad") },
            singleLine = true,
            colors = nexcodeFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        // --- Categorias ---
        Text(
            text = "Categoria",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        // Al editar, la lista se desplaza sola hasta la categoria guardada.
        val categoryListState = rememberLazyListState()
        LaunchedEffect(state.selectedCategoryId, state.visibleCategories.size) {
            val index = state.visibleCategories.indexOfFirst { it.id == state.selectedCategoryId }
            if (index >= 0) categoryListState.animateScrollToItem(index)
        }

        LazyRow(
            state = categoryListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = state.visibleCategories, key = { it.id }) { category ->
                FilterChip(
                    selected = state.selectedCategoryId == category.id,
                    onClick = { viewModel.onCategorySelected(category.id) },
                    label = { Text(category.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NexcodeSurfaceElevated,
                        labelColor = TextMedium,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // --- Cuenta ---
        Text(
            text = "Cuenta",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        val accountListState = rememberLazyListState()
        LaunchedEffect(state.selectedAccountId, state.accounts.size) {
            val index = state.accounts.indexOfFirst { it.id == state.selectedAccountId }
            if (index >= 0) accountListState.animateScrollToItem(index)
        }

        LazyRow(
            state = accountListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = state.accounts, key = { it.id }) { account ->
                FilterChip(
                    selected = state.selectedAccountId == account.id,
                    onClick = { viewModel.onAccountSelected(account.id) },
                    label = { Text(account.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NexcodeSurfaceElevated,
                        labelColor = TextMedium,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // --- Nota opcional ---
        OutlinedTextField(
            value = state.note,
            onValueChange = viewModel::onNoteChange,
            label = { Text("Nota (opcional)") },
            colors = nexcodeFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        )

        // --- Error traducido desde el caso de uso, con entrada animada ---
        AnimatedVisibility(
            visible = state.errorMessage != null,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            Text(
                text = state.errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        GradientButton(
            text = if (state.isSaving) "Guardando..." else state.saveLabel,
            onClick = viewModel::onSave,
            enabled = !state.isSaving
        )

        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NexcodeSurfaceElevated,
                contentColor = TextMedium
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Cancelar", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Medium) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = NexcodeSurfaceElevated,
            labelColor = TextMedium,
            selectedContainerColor = accent.copy(alpha = 0.20f),
            selectedLabelColor = accent
        )
    )
}

@Composable
private fun nexcodeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = MaterialTheme.colorScheme.primary
)
