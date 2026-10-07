package com.ledgerai.app.presentation.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    prefilled: ParsedTransaction? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double, TransactionType, TransactionCategory, String, String, LocalDate) -> Unit
) {
    var amountText by remember { mutableStateOf(prefilled?.amount?.toString() ?: "") }
    var merchant by remember { mutableStateOf(prefilled?.merchant ?: "") }
    var note by remember { mutableStateOf(prefilled?.note ?: "") }
    var selectedType by remember { mutableStateOf(prefilled?.type ?: TransactionType.EXPENSE) }
    var selectedCategory by remember { mutableStateOf(prefilled?.category ?: TransactionCategory.OTHER) }
    var date by remember { mutableStateOf(prefilled?.date ?: LocalDate.now()) }
    var categoryExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Add Transaction", style = MaterialTheme.typography.titleLarge)

            // Type Toggle
            Row(modifier = Modifier.fillMaxWidth()) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                }
            }

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Merchant
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Source") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    TransactionCategory.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.displayName) },
                            onClick = {
                                selectedCategory = cat
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // Date — simple display; in a real app would use a DatePicker
            OutlinedTextField(
                value = date.toString(),
                onValueChange = {
                    try { date = LocalDate.parse(it) } catch (_: Exception) {}
                },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: return@Button
                        onConfirm(amount, selectedType, selectedCategory, merchant, note, date)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = amountText.toDoubleOrNull() != null
                ) {
                    Text("Add")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
