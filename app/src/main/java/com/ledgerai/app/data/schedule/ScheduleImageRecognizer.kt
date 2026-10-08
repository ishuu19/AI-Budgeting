package com.ledgerai.app.data.schedule

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ledgerai.app.data.repository.AiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class ScheduleImageRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRepository: AiRepository
) {
    suspend fun extractRawText(uri: Uri): String {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return ""
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    cont.resume(result.text.orEmpty())
                }
                .addOnFailureListener { e ->
                    recognizer.close()
                    cont.resumeWithException(e)
                }
        }
    }

    /** Gemini vision first, then OCR + text AI + line parser. */
    suspend fun recognizeLines(uri: Uri): List<ParsedScheduleRow> {
        val vision = aiRepository.parseTimetableFromImage(context, uri).getOrNull()
        if (!vision.isNullOrEmpty()) return vision

        val rawText = extractRawText(uri)
        if (rawText.isBlank()) return emptyList()
        val aiText = aiRepository.parseTimetable(rawText).getOrNull()
        if (!aiText.isNullOrEmpty()) return aiText
        return ScheduleCsvParser.parse(rawText)
    }

    fun lastParseJson(rows: List<ParsedScheduleRow>): String =
        ScheduleTimetableJson.toJson(rows)
}
