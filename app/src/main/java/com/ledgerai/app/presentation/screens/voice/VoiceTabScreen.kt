package com.ledgerai.app.presentation.screens.voice

import androidx.compose.runtime.Composable
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LTabPage
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.OpenItem
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.navigation.VoiceSeg
import com.ledgerai.app.presentation.screens.ai.AiAssistantScreen
import com.ledgerai.app.presentation.screens.notes.NotesScreen

/** Voice tab: Speak, Notes, Ask. Notes and Ask are embedded, so they show no title or back button. */
@Composable
fun VoiceTabScreen(
    seg: VoiceSeg,
    onSeg: (VoiceSeg) -> Unit,
    links: AppLinks,
    open: OpenItem? = null,
    onOpened: () -> Unit = {},
    holdMic: Boolean = false,
    seed: String? = null,
    onSeedConsumed: () -> Unit = {}
) {
    LTabPage(
        title = "Voice",
        segments = { LSegments(VoiceSeg.values().toList(), seg, { it.label }, onSeg) }
    ) {
        when (seg) {
            VoiceSeg.Speak -> VoiceRecorderScreen(
                embedded = true,
                links = links,
                holdMic = holdMic,
                seed = seed,
                onSeedConsumed = onSeedConsumed
            )
            VoiceSeg.Notes -> NotesScreen(open = open?.takeIf { it.kind == OpenKind.Note }, onOpened = onOpened)
            VoiceSeg.Ask -> AiAssistantScreen()
        }
    }
}
