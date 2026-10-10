package com.ledgerai.app.presentation.screens.money

import androidx.compose.runtime.Composable
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LTabPage
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.MoneySeg
import com.ledgerai.app.presentation.navigation.OpenItem
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.navigation.VoiceSeg
import com.ledgerai.app.presentation.screens.transactions.TransactionsScreen

/** Money tab: Overview, Spend, Plan, Owed. */
@Composable
fun MoneyTabScreen(
    seg: MoneySeg,
    onSeg: (MoneySeg) -> Unit,
    links: AppLinks,
    open: OpenItem? = null,
    onOpened: () -> Unit = {},
    addSpend: Boolean = false,
    onAddSpendConsumed: () -> Unit = {}
) {
    fun forSeg(vararg kinds: OpenKind): OpenItem? = open?.takeIf { it.kind in kinds }

    LTabPage(
        title = "Money",
        segments = { LSegments(MoneySeg.values().toList(), seg, { it.label }, onSeg) }
    ) {
        when (seg) {
            MoneySeg.Overview -> MoneyOverviewScreen(links)
            MoneySeg.Spend -> TransactionsScreen(
                onNavigateToVoice = { links.voice(VoiceSeg.Speak) },
                open = forSeg(OpenKind.Transaction),
                onOpened = onOpened,
                addSpend = addSpend,
                onAddSpendConsumed = onAddSpendConsumed
            )
            MoneySeg.Plan -> MoneyPlanScreen(links, forSeg(OpenKind.Budget, OpenKind.Goal), onOpened)
            MoneySeg.Owed -> MoneyOwedScreen(forSeg(OpenKind.Bill, OpenKind.Debt), onOpened)
        }
    }
}
