package com.ledgerai.app.data.schedule

import android.content.Context
import android.net.Uri
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.RoutineRepository
import com.ledgerai.app.data.repository.ScheduleRepository
import com.ledgerai.app.domain.model.RoutineItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ScheduleImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRepository: AiRepository,
    private val imageRecognizer: ScheduleImageRecognizer,
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository
) {

    suspend fun importText(raw: String, routineId: Long?, replace: Boolean = false): Result<Int> = runCatching {
        val text = raw.trim()
        require(text.isNotEmpty()) { "Nothing to import" }
        val rows = parseRows(text)
        require(rows.isNotEmpty()) { "Could not read a schedule from that text" }
        val routine = resolveRoutine(routineId)
        scheduleRepository.importRows(routine.id, rows, replace)
        rows.size
    }

    suspend fun importCsvUri(uri: Uri, routineId: Long?, replace: Boolean = false): Result<Int> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()?.trim()
            ?: throw IllegalArgumentException("Could not read file")
        importText(text, routineId, replace).getOrThrow()
    }

    suspend fun importImageUri(uri: Uri, routineId: Long?, replace: Boolean = false): Result<Int> = runCatching {
        val ocrRows = imageRecognizer.recognizeLines(uri)
        val rows = if (ocrRows.isNotEmpty()) {
            ocrRows
        } else {
            val raw = imageRecognizer.extractRawText(uri)
            parseRows(raw)
        }
        require(rows.isNotEmpty()) { "Could not read schedule from image — try a clearer photo or paste text" }
        val routine = resolveRoutine(routineId)
        scheduleRepository.importRows(routine.id, rows, replace)
        rows.size
    }

    private suspend fun parseRows(text: String): List<ParsedScheduleRow> {
        if (text.isBlank()) return emptyList()
        val ai = aiRepository.parseTimetable(text).getOrNull()
        if (!ai.isNullOrEmpty()) return ai
        return ScheduleCsvParser.parse(text)
    }

    private suspend fun resolveRoutine(routineId: Long?): RoutineItem {
        if (routineId != null && routineId > 0L) {
            routineRepository.findById(routineId)?.let { return it }
        }
        val active = routineRepository.observeActive().first()
        active.firstOrNull { it.title.equals("Calendar classes", ignoreCase = true) }?.let { return it }
        val id = routineRepository.insert(
            RoutineItem(
                title = "Calendar classes",
                notes = "Imported timetable",
                repeatRule = "WEEKLY",
                isActive = true
            )
        )
        return RoutineItem(
            id = id,
            title = "Calendar classes",
            notes = "Created from import",
            repeatRule = "WEEKLY",
            isActive = true
        )
    }
}
