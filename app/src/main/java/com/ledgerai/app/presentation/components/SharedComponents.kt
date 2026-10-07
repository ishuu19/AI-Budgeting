package com.ledgerai.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.presentation.theme.CategoryColors
import com.ledgerai.app.presentation.theme.ExpenseRed
import com.ledgerai.app.presentation.theme.IncomeGreen

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        action?.invoke()
    }
}

@Composable
fun AmountText(
    amount: Double,
    isExpense: Boolean = true,
    prefix: String = "",
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    val color = if (isExpense) ExpenseRed else IncomeGreen
    val sign = if (isExpense) "-" else "+"
    Text(
        text = "$prefix$sign${"%.2f".format(amount)}",
        color = color,
        style = style,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
fun CategoryChip(
    category: TransactionCategory,
    modifier: Modifier = Modifier
) {
    val colorIndex = category.ordinal % CategoryColors.size
    val color = CategoryColors[colorIndex]
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Text(
            text = category.displayName,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CategoryDot(
    category: TransactionCategory,
    size: Dp = 10.dp
) {
    val colorIndex = category.ordinal % CategoryColors.size
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CategoryColors[colorIndex])
    )
}

@Composable
fun BudgetProgressBar(
    usagePercent: Int,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val color = when {
        usagePercent >= 100 -> ExpenseRed
        usagePercent >= 80 -> MaterialTheme.colorScheme.error
        usagePercent >= 60 -> Color(0xFFF59E0B)
        else -> IncomeGreen
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((usagePercent / 100f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
fun EmptyStateCard(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(emoji, fontSize = 40.sp)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LoadingCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun InfoChip(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}
