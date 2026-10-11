package com.ledgerai.app.domain.assistant

fun renderContext(bundle: ContextBundle): String {
    val lines = ArrayList<String>(2)
    if (bundle.merchants.isNotEmpty()) {
        lines += "Known merchants: " + bundle.merchants.joinToString(", ") { it.name }
    }
    if (bundle.debts.isNotEmpty()) {
        lines += "Open debts: " + bundle.debts.joinToString("; ") { formatDebt(it) }
    }
    return lines.joinToString("\n")
}

private fun formatDebt(debt: OpenDebtFact): String {
    val parts = ArrayList<String>(4)
    parts += debt.person
    parts += debt.direction.name
    val amount = debt.amount
    if (amount != null) {
        parts += formatAmount(amount)
    }
    val dueDate = debt.dueDate
    if (dueDate != null) {
        parts += "due $dueDate"
    }
    return parts.joinToString(" ")
}

private fun formatAmount(amount: Double): String {
    val raw = amount.toString()
    return if (raw.endsWith(".0")) raw.dropLast(2) else raw
}
