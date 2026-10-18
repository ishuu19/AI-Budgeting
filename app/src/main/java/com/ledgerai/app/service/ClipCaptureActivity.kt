package com.ledgerai.app.service

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ledgerai.app.data.ai.JobPasteParser
import com.ledgerai.app.data.assistant.Executor
import com.ledgerai.app.data.clips.CaptureResult
import com.ledgerai.app.data.clips.ClipRepository
import com.ledgerai.app.data.clips.ClipTarget
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.domain.assistant.ActionPlan
import com.ledgerai.app.domain.assistant.ProposedAction
import com.ledgerai.app.domain.clips.ClipRead
import com.ledgerai.app.domain.clips.ClipRules
import com.ledgerai.app.presentation.screens.clips.ClipSheet
import com.ledgerai.app.presentation.screens.clips.ClipStrip
import com.ledgerai.app.presentation.theme.LedgerAITheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Add to Notes" from the text-selection menu, the share sheet, the tile, or the bubble.
 * Clipboard text is read only after the window has focus, and only when the user asked.
 */
@AndroidEntryPoint
class ClipCaptureActivity : ComponentActivity() {

    @Inject lateinit var clips: ClipRepository
    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var executor: Executor
    @Inject lateinit var session: UserSession

    private var clipboardMode = false
    private var readClipboard = false
    private var sourceKind = "selection"
    private var sourceApp: String? = null
    private var subject: String? = null
    private var descriptionSensitive = false

    private var ui by mutableStateOf<CaptureUi>(CaptureUi.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE)
        clipboardMode = mode == MODE_CLIPBOARD
        sourceKind = intent.getStringExtra(EXTRA_SOURCE) ?: when (intent.action) {
            Intent.ACTION_SEND -> "share"
            Intent.ACTION_PROCESS_TEXT -> "selection"
            else -> "clipboard"
        }
        sourceApp = sourcePackage()
        setContent {
            LedgerAITheme {
                when (val state = ui) {
                    CaptureUi.Idle -> Unit
                    is CaptureUi.Sheet -> ClipSheet(
                        text = state.text,
                        sourceLabel = state.label,
                        read = state.read,
                        choices = state.choices,
                        selectedId = state.selected,
                        sensitive = state.sensitive,
                        onSelect = { ui = state.copy(selected = it) },
                        onSave = { save(state, askAi = false, saveAnyway = state.sensitive) },
                        onAsk = { save(state, askAi = true, saveAnyway = false) },
                        onTrackJob = { trackJob(state) },
                        onDismiss = { finish() },
                    )
                    is CaptureUi.Strip -> ClipStrip(state.message) { undo(state.id) }
                }
            }
        }
        if (!clipboardMode) readIntent()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clipboardMode && !readClipboard) {
            readClipboard = true
            readClipboardNow()
        }
    }

    private fun readIntent() {
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            lifecycleScope.launch {
                if (!prefs.clipMenuNow()) {
                    toast("Add to Notebook is turned off")
                    finish()
                    return@launch
                }
                val raw = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                present(raw, null, false)
            }
            return
        }
        if (intent.action == Intent.ACTION_SEND) {
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            present(intent.getCharSequenceExtra(Intent.EXTRA_TEXT), subject, false)
        }
    }

    private fun readClipboardNow() {
        val manager = getSystemService(ClipboardManager::class.java)
        val description = manager?.primaryClipDescription
        descriptionSensitive = description.isSensitiveFlag()
        val item = manager?.primaryClip?.getItemAt(0)
        present(item?.coerceToText(this), null, descriptionSensitive)
    }

    private fun present(raw: CharSequence?, subject: String?, sensitiveFlag: Boolean) {
        val text = ClipRules.plain(raw ?: "")
        if (text.isBlank()) {
            toast("Nothing selected")
            finish()
            return
        }
        if (ClipRules.gate(text, sensitiveFlag) is com.ledgerai.app.domain.clips.ClipGate.Password) {
            toast("Looks like a password, not saved.")
            finish()
            return
        }
        lifecycleScope.launch {
            val fast = prefs.clipFastNow()
            val read = ClipRules.read(text)
            if (fast && ClipRules.gate(text, sensitiveFlag) is com.ledgerai.app.domain.clips.ClipGate.Ok) {
                val result = clips.capture(text, sourceKind, sourceApp, subject, ClipTarget.Default, askAi = false, saveAnyway = false, descriptionSensitive = false)
                showSaved(result, asked = false)
                return@launch
            }
            val choices = clips.choices()
            ui = CaptureUi.Sheet(
                text = text,
                label = appLabel(sourceApp),
                read = read,
                choices = choices,
                selected = choices.firstOrNull()?.id ?: "inbox",
                sensitive = ClipRules.gate(text, sensitiveFlag) is com.ledgerai.app.domain.clips.ClipGate.ConfirmSensitive,
            )
        }
    }

    private fun save(state: CaptureUi.Sheet, askAi: Boolean, saveAnyway: Boolean) {
        lifecycleScope.launch {
            val result = clips.capture(
                state.text, sourceKind, sourceApp, subject,
                clips.choiceTarget(state.selected), askAi, saveAnyway, descriptionSensitive,
            )
            showSaved(result, asked = askAi)
        }
    }

    private fun trackJob(state: CaptureUi.Sheet) {
        lifecycleScope.launch {
            val result = clips.capture(
                state.text, sourceKind, sourceApp, subject,
                clips.choiceTarget(state.selected), askAi = false, saveAnyway = false, descriptionSensitive = false,
            )
            if (result is CaptureResult.Saved) {
                val job = JobPasteParser.parse(state.text)
                val userId = session.userInfo.first().userId
                executor.execute(
                    ActionPlan(
                        inputRef = "clip:${result.id}",
                        actions = listOf(
                            ProposedAction(
                                "clip.file_job",
                                mapOf(
                                    "clipId" to result.id,
                                    "userId" to userId,
                                    "company" to job.company,
                                    "role" to job.title,
                                    "title" to job.title,
                                    "confirmed" to "true",
                                ),
                            )
                        ),
                    ),
                    approved = setOf(0),
                )
            }
            showSaved(result, asked = false)
        }
    }

    private fun showSaved(result: CaptureResult, asked: Boolean) {
        when (result) {
            CaptureResult.Empty -> {
                toast("Nothing selected")
                finish()
            }
            CaptureResult.Password -> {
                toast("Looks like a password, not saved.")
                finish()
            }
            CaptureResult.NeedsConfirm -> toast("Save anyway to keep this on the phone")
            is CaptureResult.Saved -> {
                val base = when {
                    asked -> "Saved · AI will suggest where it goes"
                    else -> "Added to ${result.destination}"
                }
                val message = buildString {
                    append(base)
                    if (result.trimmed) append(" · trimmed")
                    result.notice?.let { append(". ").append(it) }
                }
                ui = CaptureUi.Strip(result.id, message)
                lifecycleScope.launch {
                    delay(3_000)
                    if (ui is CaptureUi.Strip) finish()
                }
            }
        }
    }

    private fun undo(id: String) {
        lifecycleScope.launch {
            clips.undoCapture(id)
            finish()
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun sourcePackage(): String? {
        val fromReferrer = referrer?.takeIf { it.scheme == "android-app" }?.host
        return callingPackage ?: fromReferrer
    }

    private fun appLabel(pkg: String?): String {
        if (pkg.isNullOrBlank()) return "Unknown app"
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("Unknown app")
    }

    private sealed interface CaptureUi {
        data object Idle : CaptureUi
        data class Sheet(
            val text: String,
            val label: String,
            val read: ClipRead,
            val choices: List<com.ledgerai.app.data.clips.ClipChoice>,
            val selected: String,
            val sensitive: Boolean,
        ) : CaptureUi
        data class Strip(val id: String, val message: String) : CaptureUi
    }

    companion object {
        const val MODE_CLIPBOARD = "clipboard"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SOURCE = "source"

        fun intent(context: Context, mode: String, source: String): Intent =
            Intent(context, ClipCaptureActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_SOURCE, source)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}

private fun ClipDescription?.isSensitiveFlag(): Boolean {
    if (this == null) return false
    if (Build.VERSION.SDK_INT >= 33) {
        val extra = extras
        if (extra?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return true
    }
    return extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
}
