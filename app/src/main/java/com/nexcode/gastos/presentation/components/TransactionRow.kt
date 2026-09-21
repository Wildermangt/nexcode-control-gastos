package com.nexcode.gastos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.domain.model.Category
import com.nexcode.gastos.domain.model.Transaction
import com.nexcode.gastos.domain.model.TransactionType
import com.nexcode.gastos.presentation.theme.DividerSoft
import com.nexcode.gastos.presentation.theme.ExpenseRed
import com.nexcode.gastos.presentation.theme.IncomeGreen
import com.nexcode.gastos.presentation.theme.NexcodeSurface
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary
import java.time.format.DateTimeFormatter
import com.nexcode.gastos.presentation.util.formatear

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Fila de un movimiento, al estilo de Ivy Wallet. */
@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Elvis en cadena: si no hay categoria se usa el color del tipo.
    val accent = Color(
        (category?.colorArgb ?: defaultColorFor(transaction.type)).toInt()
    )
    val amountColor = when (transaction.type) {
        is TransactionType.Income -> IncomeGreen
        is TransactionType.Expense -> ExpenseRed
        is TransactionType.Transfer -> TextSecondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexcodeSurface)
            .border(1.dp, DividerSoft, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                // Safe call + Elvis: inicial de la categoria o del titulo.
                text = (category?.name ?: transaction.title).take(1).uppercase(),
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = (category?.name ?: "Sin categoria") +
                    "  ·  " + transaction.dateTime.formatear(HOUR_FORMAT),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (transaction.hasNote) {
                Text(
                    text = transaction.displayNote,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = signPrefix(transaction.type) + "$ " + transaction.amount.format(),
            color = amountColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun defaultColorFor(type: TransactionType): Long = when (type) {
    is TransactionType.Income -> 0xFF40E0A0L
    is TransactionType.Expense -> 0xFFFF7A2FL
    is TransactionType.Transfer -> 0xFF9A9AA5L
}

private fun signPrefix(type: TransactionType): String = when (type) {
    is TransactionType.Income -> "+ "
    is TransactionType.Expense -> "- "
    is TransactionType.Transfer -> ""
}
