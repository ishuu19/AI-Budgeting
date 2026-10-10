package com.ledgerai.app.presentation.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.subscriptions.SubscriptionRepository
import com.ledgerai.app.domain.subscriptions.NewSubscription
import com.ledgerai.app.domain.subscriptions.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Not a Hilt view model. The integrator constructs this after SubscriptionDao
 * is provided. [userId] is the signed-in user, or null for rows not yet scoped.
 */
class SubscriptionsViewModel(
    private val repository: SubscriptionRepository,
    private val userId: String?,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    val subscriptions: StateFlow<List<Subscription>> = repository.observeActive(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun currentDay(): LocalDate = today()

    fun add(draft: NewSubscription) {
        viewModelScope.launch {
            repository.add(userId, draft)
            _notice.value = "Subscription added"
        }
    }

    fun markCancelled(subscription: Subscription) {
        viewModelScope.launch {
            val result = repository.markCancelled(subscription.id, userId)
            if (!result.updated) return@launch
            check(!result.merchantContacted) { "Subscription cancel must not contact a merchant" }
            _notice.value = "Marked cancelled in LedgerAI. ${subscription.merchant} was not contacted."
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }
}
