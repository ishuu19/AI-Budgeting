package com.ledgerai.app.service

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.ledgerai.app.data.ai.JobLexiconProvider
import com.ledgerai.app.data.ai.JobPasteParser
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.domain.model.JobApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class JobShareActivity : ComponentActivity() {

    @Inject lateinit var jobRepo: JobRepository
    @Inject lateinit var lexicon: JobLexiconProvider
    @Inject lateinit var ai: AiRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val combined = listOf(subject, text).filter { it.isNotBlank() }.joinToString("\n")
        val today = LocalDate.now()
        // Rules read the share first. The cloud is asked only when neither company nor role was found.
        val parsed = JobPasteParser.parse(combined, today, lexicon.lexicon)
        CoroutineScope(Dispatchers.IO).launch {
            var company = parsed.company
            var title = parsed.title
            if (parsed.needsHelp && company.isBlank() && title.isBlank() && ai.isAiAvailable()) {
                val job = runCatching { ai.parseVoiceIntents("I applied for this job: " + combined.take(600)).getOrNull() }
                    .getOrNull()?.filterIsInstance<ParsedIntent.Job>()?.firstOrNull()
                if (job != null) {
                    company = job.company.takeUnless { it.equals("Company", true) }.orEmpty()
                    title = job.title.takeUnless { it.equals("Role", true) }.orEmpty()
                }
            }
            val applied = parsed.appliedOn ?: today
            jobRepo.save(
                JobApplication(
                    company = company.ifBlank { combined.lines().firstOrNull { it.isNotBlank() }?.take(80) ?: "Company" },
                    title = title.ifBlank { "Role" },
                    url = parsed.url,
                    source = parsed.source,
                    status = parsed.status,
                    appliedOn = applied,
                    followUpOn = parsed.followUpOn ?: jobRepo.defaultFollowUp(applied),
                    notes = parsed.notes,
                    location = parsed.location,
                    extraDates = parsed.extraDates,
                )
            )
            runOnUiThread {
                Toast.makeText(this@JobShareActivity, "Job saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
