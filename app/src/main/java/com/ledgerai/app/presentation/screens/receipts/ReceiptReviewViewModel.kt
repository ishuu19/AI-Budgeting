package com.ledgerai.app.presentation.screens.receipts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.receipts.ReceiptRepository
import com.ledgerai.app.domain.receipts.Receipt
import com.ledgerai.app.domain.receipts.ReceiptProposals
import kotlinx.coroutines.launch

/**
 * Confirm writes the receipt locally and keeps the returned drafts.
 * [reviewed] supplies the lines, including a name or price corrected on screen.
 * Merchant, date, and total stay on the receipt already loaded.
 * It does not insert a transaction. There is no separate line-update store.
 */
class ReceiptReviewViewModel(
    private val repository: ReceiptRepository,
    initial: Receipt,
) : ViewModel() {
    var receipt by mutableStateOf(initial)
        private set
    var proposals by mutableStateOf<ReceiptProposals?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    fun confirm(reviewed: Receipt) {
        if (saving || receipt.locallyConfirmed) return
        val toStore = receipt.copy(lines = reviewed.lines)
        saving = true
        viewModelScope.launch {
            try {
                val result = repository.confirm(toStore)
                receipt = result.receipt
                proposals = result.proposals
            } finally {
                saving = false
            }
        }
    }
}
