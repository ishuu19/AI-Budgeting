package com.ledgerai.app.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var paste by rememberSaveable { mutableStateOf("") }
    var replace by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runImport(block: suspend () -> Result<Int>) {
        scope.launch {
            busy = true
            message = null
            failed = false
            block()
                .onSuccess { n ->
                    failed = false
                    message = "Added $n"
                    if (n > 0) paste = ""
                }
                .onFailure {
                    failed = true
                    message = "Import failed"
                }
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
        primary = if (busy) "Working" else "Import",
        onPrimary = {
            if (paste.isBlank()) {
                failed = true
                message = "Paste a schedule"
            } else {
                runImport { onImportText(paste, replace) }
            }
        },
        primaryEnabled = !busy && paste.isNotBlank()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
            LToolButton(Icons.Filled.PhotoCamera, "Photo", { imagePicker.launch("image/*") })
            LToolButton(Icons.Filled.Description, "CSV", { csvPicker.launch(arrayOf("text/*", "text/csv")) })
        }
        LSwitchRow("Replace existing", replace, { replace = it })
        LField(paste, { paste = it }, "Schedule text", singleLine = false, minLines = 6)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = if (failed) L.Danger else L.InkMuted)
        }
    }
}
