package com.ledgerai.app.domain.assistant

import com.ledgerai.app.domain.model.DebtDirection
import java.time.LocalDate

/** A stored merchant name the selector may copy into a prompt. */
data class KnownMerchant(
    val name: String,
)

/** An open debt the selector may copy into a prompt. Amount and due date stay null when unknown. */
data class OpenDebtFact(
    val person: String,
    val direction: DebtDirection,
    val amount: Double?,
    val dueDate: LocalDate?,
)

/** Merchants and debts chosen for one utterance. */
data class ContextBundle(
    val merchants: List<KnownMerchant>,
    val debts: List<OpenDebtFact>,
)
