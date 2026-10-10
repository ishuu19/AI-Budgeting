package com.ledgerai.app.data.schedule

import android.content.Context
import android.net.Uri
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.CalendarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRepository: AiRepository,
    private val imageRecognizer: ScheduleImageRecognizer,
    private val calendarRepository: CalendarRepository
) {

    suspend fun importText(raw: String, replace: Boolean = false): Result<Int> = runCatching {
        val text = raw.trim()
        require(text.isNotEmpty()) { "Nothing to import" }
        val rows = parseRows(text)
        require(rows.isNotEmpty()) { "Could not read a schedule from that text" }
        calendarRepository.importClasses(rows, replace)
    }

    suspend fun importCsvUri(uri: Uri, replace: Boolean = false): Result<Int> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()?.trim()
            ?: throw IllegalArgumentException("Could not read file")
        importText(text, replace).getOrThrow()
    }

    suspend fun importImageUri(uri: Uri, replace: Boolean = false): Result<Int> = runCatching {
        val ocrRows = imageRecognizer.recognizeLines(uri)
        val rows = if (ocrRows.isNotEmpty()) {
            ocrRows
        } else {
            val raw = imageRecognizer.extractRawText(uri)
            parseRows(raw)
        }
        require(rows.isNotEmpty()) { "Could not read schedule from image — try a clearer photo or paste text" }
        calendarRepository.importClasses(rows, replace)
    }

    private suspend fun parseRows(text: String): List<ParsedScheduleRow> {
        if (text.isBlank()) return emptyList()
        // Rules first: a JSON timetable, then the CSV and free-text line parser. The cloud only runs when both find nothing.
        TimetableRules.parse(text).takeIf { it.isNotEmpty() }?.let { return it }
        return aiRepository.parseTimetable(text).getOrNull().orEmpty()
    }
}
