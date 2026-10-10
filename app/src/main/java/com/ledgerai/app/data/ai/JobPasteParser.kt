package com.ledgerai.app.data.ai

import android.content.Context
import com.ledgerai.app.domain.model.JobApplicationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Words used to tell a role from a company, and status and source phrases. Merged from assets/rules/job_terms.tsv. */
class JobLexicon(
    val roleWords: Set<String>,
    val statusPhrases: List<Pair<String, JobApplicationStatus>>,
    val sources: List<Pair<String, String>>,
) {
    companion object {
        private val ROLE_WORDS = setOf(
            "engineer", "developer", "manager", "designer", "analyst", "intern", "internship", "scientist", "architect",
            "consultant", "specialist", "coordinator", "administrator", "assistant", "associate", "director", "lead",
            "officer", "executive", "technician", "teacher", "lecturer", "researcher", "tester", "qa", "devops", "sre",
            "programmer", "accountant", "writer", "editor", "recruiter", "representative", "supervisor", "trainee",
            "swe", "pm", "ux", "ui", "frontend", "backend", "fullstack", "android", "ios", "mobile", "data", "cloud",
        )
        private val STATUS = listOf(
            "unfortunately" to JobApplicationStatus.REJECTED, "not moving forward" to JobApplicationStatus.REJECTED,
            "regret to inform" to JobApplicationStatus.REJECTED, "rejected" to JobApplicationStatus.REJECTED,
            "not selected" to JobApplicationStatus.REJECTED, "offer" to JobApplicationStatus.OFFER,
            "congratulations" to JobApplicationStatus.OFFER, "interview" to JobApplicationStatus.INTERVIEW,
            "phone screen" to JobApplicationStatus.SCREENING, "screening" to JobApplicationStatus.SCREENING,
            "recruiter call" to JobApplicationStatus.SCREENING, "online assessment" to JobApplicationStatus.SCREENING,
            "withdrew" to JobApplicationStatus.WITHDRAWN, "withdrawn" to JobApplicationStatus.WITHDRAWN,
            "applied" to JobApplicationStatus.APPLIED, "application received" to JobApplicationStatus.APPLIED,
            "submitted" to JobApplicationStatus.APPLIED,
        )
        private val SOURCES = listOf(
            "linkedin" to "LinkedIn", "indeed" to "Indeed", "glassdoor" to "Glassdoor", "bdjobs" to "BDJobs",
            "ziprecruiter" to "ZipRecruiter", "wellfound" to "Wellfound", "angel.co" to "Wellfound", "monster" to "Monster",
            "naukri" to "Naukri", "joinhandshake" to "Handshake", "handshake" to "Handshake", "greenhouse" to "Greenhouse",
            "lever.co" to "Lever", "workable" to "Workable", "ashbyhq" to "Ashby", "smartrecruiters" to "SmartRecruiters",
            "remoteok" to "RemoteOK", "weworkremotely" to "WeWorkRemotely", "simplyhired" to "SimplyHired",
            "jobstreet" to "JobStreet", "seek.com" to "Seek", "facebook" to "Facebook", "telegram" to "Telegram",
            "whatsapp" to "WhatsApp", "mail.google" to "Gmail", "gmail" to "Gmail",
        )

        val DEFAULT = JobLexicon(ROLE_WORDS, STATUS, SOURCES)

        /**
         * Rows are `term<TAB>kind[<TAB>value]`. kind ROLE adds a role word, STATUS maps a phrase to a status name
         * (value), SOURCE maps a url or text fragment to a display name (value). Unknown rows are skipped.
         */
        fun fromTsv(tsv: String): JobLexicon {
            val roles = ROLE_WORDS.toMutableSet()
            val status = STATUS.toMutableList()
            val sources = SOURCES.toMutableList()
            for (raw in tsv.lines()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val c = line.split('\t').map { it.trim() }
                if (c.size < 2) continue
                val term = c[0].lowercase(Locale.ENGLISH)
                when (c[1].uppercase(Locale.ENGLISH)) {
                    "ROLE" -> roles += term
                    "STATUS" -> JobApplicationStatus.entries.firstOrNull { it.name.equals(c.getOrNull(2), true) }?.let { status += term to it }
                    "SOURCE" -> sources += term to (c.getOrNull(2)?.takeIf { it.isNotBlank() } ?: c[0])
                }
            }
            return JobLexicon(roles, status, sources)
        }
    }
}

data class ParsedJobPaste(
    val company: String = "",
    val title: String = "",
    val url: String = "",
    val source: String = "",
    val status: JobApplicationStatus = JobApplicationStatus.APPLIED,
    val location: String = "",
    val appliedOn: LocalDate? = null,
    val followUpOn: LocalDate? = null,
    /** One per line: "Interview · 12 Oct 2026". */
    val extraDates: String = "",
    val notes: String = "",
    /** True when a labelled field (Company:, Role:) was present. */
    val labelled: Boolean = false,
) {
    /** The cloud is only worth asking when the rules found neither a company nor a role. */
    val needsHelp: Boolean get() = company.isBlank() || title.isBlank()
}

/** Rule-based reader for pasted or shared job text (LinkedIn shares, email lines, notes). */
object JobPasteParser {

    private val URL = Regex("https?://[^\\s<>\"]+")
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val OUT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private val NOISE = Regex(
        "^(?:check out(?: this)?(?: job)?(?: posting)?(?: at| for)?|i thought you might be interested in|i found this job|new job(?: alert)?|job alert|apply(?: now)?|share(?:d)?(?: job)?)\\s*[:,-]?\\s*",
        RegexOption.IGNORE_CASE
    )
    private val SEP = Regex("\\s+[|\\u2013\\u2014\\u2022]\\s+|\\s+-\\s+|\\s*\\|\\s*|:\\s+")

    private fun isRole(s: String, lex: JobLexicon): Boolean =
        s.lowercase(Locale.ENGLISH).split(Regex("[^a-z0-9+#]+")).any { it in lex.roleWords }

    private fun clean(s: String): String =
        s.replace(Regex("[\\p{So}\\p{Cs}]"), "").trim().trim(',', ':', '-', '.', '|', '"', '\'', ' ').replace(Regex("\\s+"), " ")

    private fun labelled(text: String, vararg names: String): String? {
        val re = Regex("(?im)^\\s*(?:${names.joinToString("|")})\\s*[:=]\\s*(.+)$")
        return re.find(text)?.groupValues?.get(1)?.let(::clean)?.takeIf { it.isNotEmpty() }
    }

    fun sourceFor(url: String, text: String, lex: JobLexicon): String {
        val hay = (url + " " + text).lowercase(Locale.ENGLISH)
        lex.sources.firstOrNull { hay.contains(it.first) }?.let { return it.second }
        if (url.isNotBlank()) {
            val host = Regex("https?://(?:www\\.)?([^/:?#]+)").find(url)?.groupValues?.get(1).orEmpty()
            val parts = host.split('.')
            val name = if (parts.size >= 2) parts[parts.size - 2] else host
            if (name.isNotBlank()) return name.replaceFirstChar { it.uppercase() }
        }
        return ""
    }

    private fun statusFor(text: String, lex: JobLexicon): JobApplicationStatus {
        val lower = text.lowercase(Locale.ENGLISH)
        // Earlier entries in a priority order win: a rejection beats the word "applied" in the same text.
        val order = listOf(
            JobApplicationStatus.REJECTED, JobApplicationStatus.OFFER, JobApplicationStatus.INTERVIEW,
            JobApplicationStatus.SCREENING, JobApplicationStatus.WITHDRAWN, JobApplicationStatus.APPLIED,
        )
        for (st in order) {
            if (lex.statusPhrases.any { it.second == st && Regex("(?<![a-z])" + Regex.escape(it.first) + "(?![a-z])").containsMatchIn(lower) }) return st
        }
        return JobApplicationStatus.APPLIED
    }

    /** First explicit date in [s], or null. Reads 2026-10-12, 12 Oct 2026, Oct 12 2026, 12/10/2026, 12 Oct. */
    internal fun explicitDate(s: String, today: LocalDate): LocalDate? {
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(s)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()?.let { return it }
        }
        val mon = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*"
        Regex("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+$mon\\.?,?\\s*(\\d{4})?").find(s)?.let { m ->
            return build(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3], today)
        }
        Regex("(?i)\\b$mon\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s*(\\d{4}))?").find(s)?.let { m ->
            return build(m.groupValues[2].toInt(), m.groupValues[1], m.groupValues[3], today)
        }
        Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b").find(s)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }

    private fun build(day: Int, monthText: String, yearText: String, today: LocalDate): LocalDate? {
        val month = MONTHS.indexOf(monthText.lowercase(Locale.ENGLISH).take(3)) + 1
        if (month == 0) return null
        val year = yearText.toIntOrNull()
        return runCatching {
            if (year != null) LocalDate.of(year, month, day)
            else LocalDate.of(today.year, month, day).let { if (it.isBefore(today.minusDays(60))) it.plusYears(1) else it }
        }.getOrNull()
    }

    private val DATE_LABELS = listOf(
        "interview" to Regex("(?i)\\binterview"),
        "Deadline" to Regex("(?i)\\b(?:deadline|apply by|closes?|closing|last date|due)\\b"),
        "Follow-up" to Regex("(?i)\\bfollow[ -]?up\\b"),
        "Test" to Regex("(?i)\\b(?:assessment|test|exam|coding challenge)\\b"),
        "Start" to Regex("(?i)\\b(?:start date|starts?|joining)\\b"),
        "Offer" to Regex("(?i)\\boffer\\b"),
    )

    /** Splits a block into one or more candidate lines of "company/role". */
    private fun candidateLines(text: String, url: String): List<String> =
        text.lines().map { it.replace(URL, "").trim() }
            .map { NOISE.replace(it, "").trim() }
            .filter { it.isNotEmpty() && !Regex("(?i)^(?:company|role|position|title|job title|location|status|source|link|url|date)\\s*[:=]").containsMatchIn(it) }
            .filter { it != url }

    private fun splitCompanyRole(line: String, lex: JobLexicon): Pair<String, String>? {
        val l = clean(line)
        if (l.isEmpty()) return null
        Regex("(?i)^(?:applied|apply|applying)\\s+(?:to|at|with)\\s+(.+?)\\s+(?:for|as)\\s+(?:(?:a|an|the)\\s+)?(.+)$").find(l)?.let {
            return clean(it.groupValues[1]) to clean(it.groupValues[2])
        }
        Regex("(?i)^(?:applied|apply|applying)\\s+(?:for|as)\\s+(?:(?:a|an|the)\\s+)?(.+?)\\s+(?:at|with|to|@)\\s+(.+)$").find(l)?.let {
            return clean(it.groupValues[2]) to clean(it.groupValues[1])
        }
        Regex("(?i)^(.+?)\\s+(?:is hiring|are hiring|hiring|is looking for|looking for)\\s+(?:(?:a|an|the)\\s+)?(.+)$").find(l)?.let {
            return clean(it.groupValues[1]) to clean(it.groupValues[2])
        }
        Regex("(?i)^(.+?)\\s+(?:at|@)\\s+(.+)$").find(l)?.let { m ->
            val left = clean(m.groupValues[1])
            val right = clean(m.groupValues[2].split(SEP).first())
            if (isRole(left, lex)) return right to left
        }
        val parts = l.split(SEP).map { clean(it) }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val a = parts[0]
            val b = parts[1]
            val ar = isRole(a, lex)
            val br = isRole(b, lex)
            return when {
                ar && !br -> b to a
                br && !ar -> a to b
                else -> a to b
            }
        }
        if (isRole(l, lex)) return "" to l
        return null
    }

    fun parse(text: String, today: LocalDate = LocalDate.now(), lex: JobLexicon = JobLexicon.DEFAULT): ParsedJobPaste {
        val raw = text.trim()
        if (raw.isEmpty()) return ParsedJobPaste()
        val url = URL.find(raw)?.value?.trimEnd('.', ',', ')', ';').orEmpty()
        var company = labelled(raw, "company", "employer", "organi[sz]ation", "firm") ?: ""
        var title = labelled(raw, "role", "position", "title", "job title", "job", "post", "vacancy") ?: ""
        val location = labelled(raw, "location", "place", "city", "where") ?: ""
        val isLabelled = company.isNotEmpty() || title.isNotEmpty()
        if (company.isEmpty() || title.isEmpty()) {
            for (line in candidateLines(raw, url)) {
                val pair = splitCompanyRole(line, lex) ?: continue
                if (company.isEmpty()) company = pair.first
                if (title.isEmpty()) title = pair.second
                if (company.isNotEmpty() && title.isNotEmpty()) break
            }
        }
        company = company.take(80)
        title = title.take(80)
        val explicitStatus = labelled(raw, "status")
        val status = explicitStatus?.let { s -> JobApplicationStatus.entries.firstOrNull { it.name.equals(s, true) } }
            ?: statusFor(raw, lex)
        val source = labelled(raw, "source", "via", "platform") ?: sourceFor(url, raw, lex)

        var applied: LocalDate? = null
        var follow: LocalDate? = null
        val extras = mutableListOf<String>()
        for (line in raw.lines()) {
            val d = explicitDate(line.replace(URL, ""), today) ?: continue
            val lower = line.lowercase(Locale.ENGLISH)
            when {
                Regex("\\bapplied\\b").containsMatchIn(lower) && applied == null -> applied = d
                Regex("follow[ -]?up").containsMatchIn(lower) && follow == null -> follow = d
                else -> {
                    val label = DATE_LABELS.firstOrNull { it.second.containsMatchIn(line) }?.first
                    if (label != null) extras += "${label.replaceFirstChar { it.uppercase() }} · ${d.format(OUT)}"
                }
            }
        }
        return ParsedJobPaste(
            company = company, title = title, url = url, source = source, status = status,
            location = location, appliedOn = applied, followUpOn = follow,
            extraDates = extras.distinct().joinToString("\n"), notes = raw.take(500), labelled = isLabelled,
        )
    }

    /**
     * A paste with several one-line jobs ("Acme - Android dev", one per line) becomes several results.
     * Anything else (a share with a link, labelled fields, a single job over many lines) is one job.
     */
    fun parseMany(text: String, today: LocalDate = LocalDate.now(), lex: JobLexicon = JobLexicon.DEFAULT): List<ParsedJobPaste> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 2 && lines.count { URL.containsMatchIn(it) } <= lines.size) {
            val each = lines.map { parse(it, today, lex) }
            val noLabels = lines.none { Regex("(?i)^\\s*[a-z ]{3,14}\\s*[:=]").containsMatchIn(it) }
            if (noLabels && each.all { it.company.isNotBlank() && it.title.isNotBlank() }) return each
        }
        return listOf(parse(text, today, lex))
    }
}

/** Loads assets/rules/job_terms.tsv once over the embedded defaults. */
@Singleton
class JobLexiconProvider @Inject constructor(@ApplicationContext private val context: Context) {
    val lexicon: JobLexicon by lazy {
        RuleAssets.read(context, "rules/job_terms.tsv")?.let { JobLexicon.fromTsv(it) } ?: JobLexicon.DEFAULT
    }
}
