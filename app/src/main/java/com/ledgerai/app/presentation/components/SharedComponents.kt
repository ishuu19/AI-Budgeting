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
import com.ledgerai.app.presentation.theme.*

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            letterSpacing = (-0.2).sp
        )
        action?.invoke()
    }
}

@Composable
fun PrimaryKpi(
    label: String,
    formattedValue: String,
    takeaway: String,
    modifier: Modifier = Modifier,
    valueColor: Color = RoyalWhite
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = formattedValue,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                lineHeight = 40.sp,
                letterSpacing = (-0.8).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (takeaway.isNotBlank()) {
                Text(
                    text = takeaway,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandGoldLight,
                    fontWeight = FontWeight.Medium
                )
            }
        }
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
    val color = if (isExpense) Color(0xFFFFC9C2) else BrandGoldLight
    val sign = if (isExpense) "-" else "+"
    Text(
        text = "$prefix$sign${"%.2f".format(amount)}",
        color = color,
        style = style,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        modifier = modifier
    )
}

@Composable
fun CategoryChip(
    category: TransactionCategory,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = BrandGold.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandGold.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = category.displayName,
            color = BrandGoldLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
        usagePercent >= 100 -> Color(0xFFFFC9C2)
        usagePercent >= 85 -> BrandGold
        else -> BrandGoldLight
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(BrandEmeraldDark.copy(alpha = 0.45f))
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandGold.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(emoji, fontSize = 36.sp)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun LoadingCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandGold.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandGold, strokeWidth = 2.dp)
        }
    }
}

@Composable
fun InfoChip(
    label: String,
    value: String,
    color: Color = BrandGoldLight,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BrandGold.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandGold.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.85f)
            )
        }
    }
}
