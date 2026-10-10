package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.JobApplicationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JobPasteParserTest {

    private val today = LocalDate.of(2026, 10, 14)

    private fun parse(text: String) = JobPasteParser.parse(text, today)

    // text, company, role
    private val companyRoleCases: List<Triple<String, String, String>> = listOf(
        Triple("Acme - Android Engineer", "Acme", "Android Engineer"),
        Triple("Android Engineer - Acme", "Acme", "Android Engineer"),
        Triple("Acme | Backend Developer", "Acme", "Backend Developer"),
        Triple("Backend Developer | Acme", "Acme", "Backend Developer"),
        Triple("Android Engineer at Acme", "Acme", "Android Engineer"),
        Triple("Product Manager @ Globex", "Globex", "Product Manager"),
        Triple("Data Analyst at Initech Inc", "Initech Inc", "Data Analyst"),
        Triple("Acme is hiring a Software Engineer", "Acme", "Software Engineer"),
        Triple("Globex is hiring Data Scientist", "Globex", "Data Scientist"),
        Triple("Applied to Acme for Android Engineer", "Acme", "Android Engineer"),
        Triple("Applied for Android Engineer at Acme", "Acme", "Android Engineer"),
        Triple("Applied to Hooli as UX Designer", "Hooli", "UX Designer"),
        Triple("Company: Acme\nRole: Android Engineer", "Acme", "Android Engineer"),
        Triple("Company: Acme\nPosition: QA Tester", "Acme", "QA Tester"),
        Triple("Employer: Globex\nJob title: Project Manager", "Globex", "Project Manager"),
        Triple("Company: Rolls-Royce\nRole: Mechanical Engineer", "Rolls-Royce", "Mechanical Engineer"),
        Triple("Check out this job at Acme: Android Engineer", "Acme", "Android Engineer"),
        Triple("I thought you might be interested in Android Engineer at Acme", "Acme", "Android Engineer"),
        Triple("Acme – Marketing Manager", "Acme", "Marketing Manager"),
        Triple("Acme — Sales Executive", "Acme", "Sales Executive"),
        Triple("Rolls-Royce - Mechanical Engineer", "Rolls-Royce", "Mechanical Engineer"),
        Triple("Senior iOS Developer at Pied Piper", "Pied Piper", "Senior iOS Developer"),
        Triple("Hiring: Frontend Developer at Stark Industries", "Stark Industries", "Hiring: Frontend Developer"),
        Triple("Intern - Wayne Enterprises", "Wayne Enterprises", "Intern"),
        Triple("Wayne Enterprises - Finance Intern", "Wayne Enterprises", "Finance Intern"),
        Triple("Umbrella | DevOps Engineer", "Umbrella", "DevOps Engineer"),
        Triple("Cyberdyne - Research Scientist", "Cyberdyne", "Research Scientist"),
        Triple("Teaching Assistant at Oscorp", "Oscorp", "Teaching Assistant"),
    )

    @Test
    fun companyAndRoleCasesAreEnough() = assertTrue(companyRoleCases.size >= 25)

    @Test
    fun companyAndRoleAreFound() {
        val bad = companyRoleCases.filter { (text, company, role) ->
            val r = parse(text)
            r.company != company || r.title != role
        }.map { (t, c, r) -> "'$t' expected [$c | $r] got [${parse(t).company} | ${parse(t).title}]" }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    // text, expected source
    private val sourceCases: List<Pair<String, String>> = listOf(
        "Acme - Android Engineer https://www.linkedin.com/jobs/view/123" to "LinkedIn",
        "Dev at X https://in.indeed.com/viewjob?jk=1" to "Indeed",
        "Dev at X https://www.glassdoor.com/job/1" to "Glassdoor",
        "Dev at X https://jobs.bdjobs.com/details/1" to "BDJobs",
        "Dev at X https://www.ziprecruiter.com/c/x" to "ZipRecruiter",
        "Dev at X https://wellfound.com/jobs/1" to "Wellfound",
        "Dev at X https://boards.greenhouse.io/x/jobs/1" to "Greenhouse",
        "Dev at X https://jobs.lever.co/x/1" to "Lever",
        "Dev at X https://apply.workable.com/x/j/1" to "Workable",
        "Dev at X https://careers.acme.com/jobs/1" to "Acme",
        "Dev at X https://www.naukri.com/job-1" to "Naukri",
        "Dev at X via LinkedIn" to "LinkedIn",
        "Dev at X seen on Indeed" to "Indeed",
        "Dev at X\nSource: Referral" to "Referral",
        "Dev at X\nvia: A friend" to "A friend",
        "Dev at X https://remoteok.com/remote-jobs/1" to "RemoteOK",
        "Dev at X" to "",
    )

    @Test
    fun sourceIsFound() {
        val bad = sourceCases.filter { (text, source) -> parse(text).source != source }
            .map { (t, s) -> "'$t' expected '$s' got '${parse(t).source}'" }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    // text, expected status
    private val statusCases: List<Pair<String, JobApplicationStatus>> = listOf(
        "Acme - Android Engineer" to JobApplicationStatus.APPLIED,
        "Applied to Acme for Android Engineer" to JobApplicationStatus.APPLIED,
        "Acme - Android Engineer\nInterview on 20 Oct 2026" to JobApplicationStatus.INTERVIEW,
        "Acme - Android Engineer, phone screen tomorrow" to JobApplicationStatus.SCREENING,
        "Acme - Android Engineer, recruiter call booked" to JobApplicationStatus.SCREENING,
        "Acme - Android Engineer\nWe are pleased to extend an offer" to JobApplicationStatus.OFFER,
        "Acme - Android Engineer\nUnfortunately we are not moving forward" to JobApplicationStatus.REJECTED,
        "Acme - Android Engineer, not selected" to JobApplicationStatus.REJECTED,
        "Acme - Android Engineer\nI withdrew my application" to JobApplicationStatus.WITHDRAWN,
        "Acme - Android Engineer\nStatus: Offer" to JobApplicationStatus.OFFER,
        "Acme - Android Engineer\nStatus: rejected" to JobApplicationStatus.REJECTED,
        "Acme - Android Engineer\nStatus: screening" to JobApplicationStatus.SCREENING,
        "Interview with Acme for Android Engineer" to JobApplicationStatus.INTERVIEW,
        "Congratulations! Acme - Android Engineer" to JobApplicationStatus.OFFER,
        "Unfortunately Acme interview ended, offer rescinded" to JobApplicationStatus.REJECTED,
    )

    @Test
    fun statusIsFound() {
        val bad = statusCases.filter { (text, status) -> parse(text).status != status }
            .map { (t, s) -> "'$t' expected $s got ${parse(t).status}" }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun urlIsExtractedWithoutTrailingPunctuation() {
        assertEquals("https://jobs.example.com/a/1", parse("Apply: https://jobs.example.com/a/1.").url)
        assertEquals("https://jobs.example.com/a/1", parse("(see https://jobs.example.com/a/1)").url)
        assertEquals("", parse("no link here").url)
    }

    @Test
    fun urlIsNotPartOfCompanyOrRole() {
        val r = parse("Acme - Android Engineer https://www.linkedin.com/jobs/view/123")
        assertEquals("Acme", r.company)
        assertEquals("Android Engineer", r.title)
    }

    @Test
    fun locationLabel() = assertEquals("Dhaka", parse("Acme - Android Engineer\nLocation: Dhaka").location)

    @Test
    fun interviewDateGoesToExtraDates() {
        val r = parse("Acme - Android Engineer\nInterview on 20 Oct 2026")
        assertEquals("Interview · 20 Oct 2026", r.extraDates)
    }

    @Test
    fun deadlineDateGoesToExtraDates() {
        val r = parse("Acme - Android Engineer\nApply by Nov 5, 2026")
        assertEquals("Deadline · 5 Nov 2026", r.extraDates)
    }

    @Test
    fun isoDateWorks() {
        assertEquals("Interview · 12 Oct 2026", parse("Acme - Dev Engineer\nInterview 2026-10-12").extraDates)
    }

    @Test
    fun slashDateWorks() {
        assertEquals("Interview · 12 Oct 2026", parse("Acme - Dev Engineer\nInterview 12/10/2026").extraDates)
    }

    @Test
    fun appliedDateIsRead() {
        assertEquals(LocalDate.of(2026, 10, 1), parse("Acme - Dev Engineer\nApplied on 1 Oct 2026").appliedOn)
    }

    @Test
    fun followUpDateIsRead() {
        assertEquals(LocalDate.of(2026, 10, 25), parse("Acme - Dev Engineer\nFollow up on 25 Oct 2026").followUpOn)
    }

    @Test
    fun noDatesMeansNoExtras() {
        val r = parse("Acme - Dev Engineer")
        assertEquals("", r.extraDates)
        assertEquals(null, r.appliedOn)
        assertEquals(null, r.followUpOn)
    }

    @Test
    fun explicitDateForms() {
        val cases = listOf(
            "12 Oct 2026" to LocalDate.of(2026, 10, 12), "12th October 2026" to LocalDate.of(2026, 10, 12),
            "Oct 12, 2026" to LocalDate.of(2026, 10, 12), "October 12 2026" to LocalDate.of(2026, 10, 12),
            "2026-12-31" to LocalDate.of(2026, 12, 31), "31/12/2026" to LocalDate.of(2026, 12, 31),
            "20 Oct" to LocalDate.of(2026, 10, 20), "Nov 5" to LocalDate.of(2026, 11, 5),
            "5 Jan" to LocalDate.of(2027, 1, 5), "no date" to null,
        )
        cases.forEach { (text, expected) -> assertEquals(text, expected, JobPasteParser.explicitDate(text, today)) }
    }

    // ─── needsHelp ───────────────────────────────────────────────────────────

    @Test
    fun completeParseNeedsNoHelp() = assertFalse(parse("Acme - Android Engineer").needsHelp)

    @Test
    fun missingCompanyNeedsHelp() = assertTrue(parse("Android Engineer").needsHelp)

    @Test
    fun gibberishNeedsHelp() {
        val r = parse("asdf qwer zxcv")
        assertTrue(r.needsHelp)
        assertEquals("", r.company)
    }

    @Test
    fun emptyTextIsEmpty() {
        val r = parse("   ")
        assertEquals("", r.company)
        assertTrue(r.needsHelp)
    }

    // ─── parseMany ───────────────────────────────────────────────────────────

    @Test
    fun oneJobPerLine() {
        val list = JobPasteParser.parseMany("Acme - Android Engineer\nGlobex - Data Analyst\nHooli | Product Manager", today)
        assertEquals(listOf("Acme", "Globex", "Hooli"), list.map { it.company })
        assertEquals(listOf("Android Engineer", "Data Analyst", "Product Manager"), list.map { it.title })
    }

    @Test
    fun labelledBlockIsOneJob() {
        val list = JobPasteParser.parseMany("Company: Acme\nRole: Android Engineer\nLocation: Dhaka", today)
        assertEquals(1, list.size)
        assertEquals("Acme", list[0].company)
    }

    @Test
    fun singleLineIsOneJob() = assertEquals(1, JobPasteParser.parseMany("Acme - Android Engineer", today).size)

    @Test
    fun shareWithLinkIsOneJob() {
        val list = JobPasteParser.parseMany("Android Engineer at Acme\nhttps://www.linkedin.com/jobs/view/1", today)
        assertEquals(1, list.size)
        assertEquals("LinkedIn", list[0].source)
    }

    @Test
    fun linesWithLinksStaySeparateJobs() {
        val list = JobPasteParser.parseMany(
            "Acme - Android Engineer https://a.example.com/1\nGlobex - Data Analyst https://b.example.com/2", today
        )
        assertEquals(2, list.size)
        assertEquals("https://b.example.com/2", list[1].url)
    }

    // ─── lexicon ─────────────────────────────────────────────────────────────

    @Test
    fun tsvAddsRoleWords() {
        val lex = JobLexicon.fromTsv("# term\tkind\nbarista\tROLE\n")
        val r = JobPasteParser.parse("Barista - Blue Bottle", today, lex)
        assertEquals("Blue Bottle", r.company)
        assertEquals("Barista", r.title)
    }

    @Test
    fun tsvAddsSources() {
        val lex = JobLexicon.fromTsv("kormo\tSOURCE\tKormo Jobs\n")
        assertEquals("Kormo Jobs", JobPasteParser.parse("Dev Engineer at X via kormo", today, lex).source)
    }

    @Test
    fun tsvAddsStatusPhrases() {
        val lex = JobLexicon.fromTsv("shortlisted\tSTATUS\tSCREENING\n")
        assertEquals(JobApplicationStatus.SCREENING, JobPasteParser.parse("Acme - Dev Engineer, shortlisted", today, lex).status)
    }

    @Test
    fun tsvBadRowsAreSkipped() {
        val lex = JobLexicon.fromTsv("x\nfoo\tUNKNOWNKIND\nbar\tSTATUS\tNOPE\n")
        assertTrue("engineer" in lex.roleWords)
    }

    @Test
    fun notesKeepTheOriginalText() {
        assertTrue(parse("Acme - Android Engineer").notes.contains("Acme"))
    }
}
