package com.ledgerai.app.presentation.screens.bills

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.presentation.components.CategoryChip
import com.ledgerai.app.presentation.components.EmptyStateCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(private val billRepo: BillRepository) : ViewModel() {

    val bills: StateFlow<List<Bill>> = billRepo.getActiveBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _monthlyTotal = MutableStateFlow(0.0)
    val monthlyTotal: StateFlow<Double> = _monthlyTotal.asStateFlow()

    init {
        viewModelScope.launch {
            _monthlyTotal.value = billRepo.getTotalMonthlyBills()
        }
    }

    fun addBill(name: String, amount: Double, frequency: BillFrequency, nextDueDate: LocalDate, category: TransactionCategory) {
        viewModelScope.launch {
            billRepo.insert(Bill(name = name, amount = amount, frequency = frequency,
                nextDueDate = nextDueDate, category = category))
            _monthlyTotal.value = billRepo.getTotalMonthlyBills()
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            billRepo.delete(bill)
            _monthlyTotal.value = billRepo.getTotalMonthlyBills()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(viewModel: BillsViewModel = hiltViewModel()) {
    val bills by viewModel.bills.collectAsState()
    val monthlyTotal by viewModel.monthlyTotal.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bills & Subscriptions") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add bill")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Monthly recurring bills", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("\$${"%.2f".format(monthlyTotal)}", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            if (bills.isEmpty()) {
                item {
                    EmptyStateCard(emoji = "🔄", title = "No bills tracked",
                        subtitle = "Add recurring bills to never miss a payment")
                }
            } else {
                items(bills, key = { it.id }) { bill ->
                    BillCard(bill = bill, onDelete = { viewModel.deleteBill(bill) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddBillDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, amount, freq, date, cat ->
                viewModel.addBill(name, amount, freq, date, cat)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BillCard(bill: Bill, onDelete: () -> Unit) {
    val daysUntilDue = LocalDate.now().until(bill.nextDueDate).days

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bill.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryChip(bill.category)
                    Text(bill.frequency.displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = when {
                        daysUntilDue < 0 -> "Overdue!"
                        daysUntilDue == 0 -> "Due today"
                        daysUntilDue <= 7 -> "Due in $daysUntilDue days"
                        else -> "Due ${bill.nextDueDate}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (daysUntilDue <= 3) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("\$${"%.2f".format(bill.amount)}", fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBillDialog(onDismiss: () -> Unit, onConfirm: (String, Double, BillFrequency, LocalDate, TransactionCategory) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedFrequency by remember { mutableStateOf(BillFrequency.MONTHLY) }
    var dueDateText by remember { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var selectedCategory by remember { mutableStateOf(TransactionCategory.SUBSCRIPTIONS) }
    var freqExpanded by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bill / Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bill name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                ExposedDropdownMenuBox(expanded = freqExpanded, onExpandedChange = { freqExpanded = it }) {
                    OutlinedTextField(value = selectedFrequency.displayName, onValueChange = {}, readOnly = true,
                        label = { Text("Frequency") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(freqExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                        BillFrequency.entries.forEach { freq ->
                            DropdownMenuItem(text = { Text(freq.displayName) }, onClick = { selectedFrequency = freq; freqExpanded = false })
                        }
                    }
                }

                OutlinedTextField(value = dueDateText, onValueChange = { dueDateText = it },
                    label = { Text("Next due date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@TextButton
                    val date = try { LocalDate.parse(dueDateText) } catch (_: Exception) { return@TextButton }
                    onConfirm(name, amount, selectedFrequency, date, selectedCategory)
                },
                enabled = name.isNotBlank() && amountText.toDoubleOrNull() != null
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
