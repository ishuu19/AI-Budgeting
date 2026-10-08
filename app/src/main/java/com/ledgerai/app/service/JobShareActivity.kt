package com.ledgerai.app.service

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val combined = listOf(text, url).filter { it.isNotBlank() }.joinToString("\n")
        val link = Regex("https?://\\S+").find(combined)?.value ?: ""
        val today = LocalDate.now()
        val app = JobApplication(
            company = combined.lines().firstOrNull()?.take(80) ?: "Company",
            title = "Role",
            url = link,
            appliedOn = today,
            followUpOn = jobRepo.defaultFollowUp(today),
            notes = combined.take(500)
        )
        CoroutineScope(Dispatchers.IO).launch {
            jobRepo.save(app)
            runOnUiThread {
                Toast.makeText(this@JobShareActivity, "Job saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
