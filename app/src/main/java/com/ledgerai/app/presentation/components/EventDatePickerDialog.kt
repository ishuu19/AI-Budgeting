package com.ledgerai.app.presentation.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { ms ->
                    onConfirm(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("Done", color = L.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = L.InkMuted) }
        },
        colors = DatePickerDefaults.colors(containerColor = L.Page)
    ) {
        DatePicker(
            state = pickerState,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = L.Page,
                selectedDayContainerColor = L.Box,
                selectedDayContentColor = L.OnBox,
                todayDateBorderColor = L.Gold,
                todayContentColor = L.Primary
            )
        )
    }
}
