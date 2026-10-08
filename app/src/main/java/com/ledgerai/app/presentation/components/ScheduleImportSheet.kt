package com.ledgerai.app.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleImportSheet(
    title: String,
    onDismiss: () -> Unit,
    onImportText: suspend (String, Boolean) -> Result<Int>,
    onImportCsv: suspend (Uri, Boolean) -> Result<Int>,
    onImportImage: suspend (Uri, Boolean) -> Result<Int>
) {
    var paste by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runImport(block: suspend () -> Result<Int>) {
        scope.launch {
            busy = true
            message = null
            block()
                .onSuccess { n ->
                    message = "Added $n class blocks"
                    if (n > 0) paste = ""
                }
                .onFailure { message = it.message ?: "Import failed" }
            busy = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) runImport { onImportImage(uri, replace) }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runImport { onImportCsv(uri, replace) }
    }

    LSheet(
        title = title,
        onDismiss = onDismiss,
        primary = if (busy) "Working…" else "Import text",
        onPrimary = {
            if (paste.isBlank()) {
                message = "Paste or upload a schedule first"
            } else {
                runImport { onImportText(paste, replace) }
            }
        },
        primaryEnabled = !busy && paste.isNotBlank()
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Photo, CSV, or pasted list — AI turns it into your weekly timetable.",
                style = MaterialTheme.typography.bodySmall,
                color = L.InkMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LChip("Photo", selected = false, onClick = { imagePicker.launch("image/*") })
                LChip("CSV", selected = false, onClick = { csvPicker.launch(arrayOf("text/*", "text/csv")) })
                LChip(
                    if (replace) "Replace" else "Add",
                    selected = replace,
                    onClick = { replace = !replace }
                )
            }
            LField(paste, { paste = it }, "Schedule text", singleLine = false, minLines = 6)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = L.Gold)
            }
        }
    }
}
