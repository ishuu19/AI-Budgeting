package com.ledgerai.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NoteRulesTest {

    private val now = LocalDateTime.of(2026, 10, 14, 12, 0)

    // ─── tags ────────────────────────────────────────────────────────────────

    private val tagCases: List<Triple<String, String, String>> = listOf(
        // title, body, a tag that must be suggested
        Triple("Groceries", "Buy milk and eggs from the store", "shopping"),
        Triple("Rent", "Pay the rent and the electricity bill", "finance"),
        Triple("Budget", "Cut the expense on food and save money", "finance"),
        Triple("Standup", "Project meeting with the client about the report", "work"),
        Triple("Interview prep", "Resume and cv updates for the job interview", "work"),
        Triple("App idea", "What if we build a startup around this concept", "ideas"),
        Triple("Brainstorm", "Ideas for the weekend", "ideas"),
        Triple("2027 goals", "My goals and resolution: build a habit and achieve the milestone", "goals"),
        Triple("Mom birthday", "Gift for mom and family dinner", "personal"),
        Triple("Exam week", "Revision for the physics exam and the lecture notes", "study"),
        Triple("Homework", "Assignment due and the quiz chapter", "study"),
        Triple("Dentist", "Doctor appointment and medicine refill", "health"),
        Triple("Gym plan", "Workout and diet, sleep eight hours", "health"),
        Triple("Trip", "Flight ticket and hotel booking, passport check", "travel"),
        Triple("Dinner", "Recipe for pasta, ingredients to cook", "food"),
        Triple("Loan", "The bank loan and tax invoice", "finance"),
        Triple("Order", "Order the shoes from the cart", "shopping"),
        Triple("Thesis", "Thesis chapter and lab tutorial", "study"),
        Triple("Visa", "Visa and airport luggage", "travel"),
        Triple("Sprint", "Sprint planning, standup and deadline", "work"),
        Triple("Bazar", "Bazar korte hobe", "shopping"),
        Triple("Porashona", "Porashona plan for exam", "study"),
        Triple("Wedding", "Wedding anniversary gift", "personal"),
        Triple("Hospital", "Hospital visit, vitamin and exercise", "health"),
        Triple("Salary", "Salary negotiation next month", "finance"),
    )

    @Test
    fun tagTableHasEnoughCases() = assertTrue(tagCases.size >= 25)

    @Test
    fun tagsSuggestedForEveryCase() {
        val bad = tagCases.filter { (t, b, tag) -> tag !in NoteRules.suggestTags(t, b) }
            .map { (t, b, tag) -> "'$t / $b' missing $tag, got ${NoteRules.suggestTags(t, b)}" }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun hashtagsBecomeTags() {
        val tags = NoteRules.suggestTags("Note", "call later #garden #nudge")
        assertTrue("garden" in tags)
        assertFalse("nudge" in tags)
    }

    @Test
    fun noTagsForNeutralText() = assertTrue(NoteRules.suggestTags("Hello", "The quick brown fox").isEmpty())

    @Test
    fun tagLimitIsRespected() {
        val tags = NoteRules.suggestTags("a", "buy rent meeting idea goal family exam doctor flight recipe", max = 3)
        assertEquals(3, tags.size)
    }

    @Test
    fun titleHitsOutweighBodyHits() {
        val tags = NoteRules.suggestTags("Gym", "buy groceries and a bag", max = 1)
        assertEquals(listOf("health"), tags)
    }

    @Test
    fun tsvLexiconExtendsDefaults() {
        val extra = NoteRules.parseTagTsv("# comment\nkubernetes\twork\npottery\thobby\nbadline\n")
        val merged = NoteRules.mergeTags(extra)
        assertTrue("hobby" in NoteRules.suggestTags("Pottery class", "wheel and clay", merged))
        assertEquals(setOf("work", "hobby"), extra.keys)
    }

    @Test
    fun triggerTsvParses() {
        assertEquals(listOf("remind me", "ping"), NoteRules.parseTriggerTsv("# x\nremind me\t1\n\nping"))
    }

    @Test
    fun mergedTriggersAreDistinct() {
        val merged = NoteRules.mergeTriggers(listOf("remind me", "ping"))
        assertEquals(1, merged.count { it == "remind me" })
        assertTrue("ping" in merged)
    }

    // ─── summary ─────────────────────────────────────────────────────────────

    @Test
    fun shortNoteSummaryIsTheNote() {
        val r = NoteRules.summarize("T", "Call the bank. Pay the rent.")
        assertEquals(listOf("Call the bank.", "Pay the rent."), r.bullets)
        assertEquals("Call the bank. Pay the rent.", r.summary)
    }

    @Test
    fun emptyBodyFallsBackToTitle() {
        val r = NoteRules.summarize("Only a title", "")
        assertEquals("Only a title", r.summary)
        assertEquals(listOf("Only a title"), r.bullets)
    }

    @Test
    fun emptyEverythingIsEmpty() {
        val r = NoteRules.summarize("", "")
        assertEquals("", r.summary)
        assertTrue(r.bullets.isEmpty())
    }

    private val long = """
        The product launch is planned for November. The launch needs a final budget review.
        Lunch was nice today. The weather was warm.
        Marketing must submit the launch plan by Friday. We talked about music.
        The launch budget is 5000 dollars. Someone brought cookies.
    """.trimIndent()

    @Test
    fun longNoteGivesThreeBullets() {
        val r = NoteRules.summarize("Launch", long)
        assertEquals(3, r.bullets.size)
    }

    @Test
    fun summaryPrefersTopicSentences() {
        val r = NoteRules.summarize("Launch", long)
        assertTrue(r.bullets.joinToString(" "), r.bullets.count { it.contains("launch", true) } >= 2)
        assertFalse(r.bullets.any { it.contains("cookies") })
    }

    @Test
    fun summaryKeepsOriginalOrder() {
        val r = NoteRules.summarize("Launch", long)
        val positions = r.bullets.map { long.indexOf(it) }
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun summaryIsCapped() {
        val big = (1..40).joinToString(" ") { "Sentence number $it talks about launch details and budget." }
        assertTrue(NoteRules.summarize("x", big).summary.length <= 220)
    }

    @Test
    fun bulletMarkersAreStripped() {
        val r = NoteRules.summarize("List", "- milk\n- eggs\n* bread")
        assertEquals(listOf("milk", "eggs", "bread"), r.bullets)
    }

    // ─── action items ────────────────────────────────────────────────────────

    private val todoCases: List<Pair<String, String>> = listOf(
        "Buy milk" to "milk",
        "todo: renew passport" to "passport",
        "- [ ] email the landlord" to "landlord",
        "Call mom tomorrow" to "mom",
        "I need to submit the report" to "report",
        "Remind me to pay rent" to "rent",
        "Don't forget to book the dentist" to "dentist",
        "We must finish the slides" to "slides",
        "Pay electricity bill" to "electricity",
        "Send invoice to the client" to "invoice",
        "Book flight tickets" to "flight",
        "Prepare the presentation" to "presentation",
        "bazar korte hobe" to "bazar",
        "I have to return the book" to "book",
        "Schedule dentist visit" to "dentist",
        "Bring laptop charger" to "charger",
        "Order new shoes" to "shoes",
        "Cancel the subscription" to "subscription",
        "Renew the gym membership" to "gym",
        "Print the tickets" to "tickets",
    )

    @Test
    fun todoLinesBecomeTasks() {
        val bad = todoCases.filter { (line, word) ->
            NoteRules.actionItems(line, now).none { it.kind == "TASK" && it.title.contains(word, true) }
        }.map { it.first }
        assertTrue("Not recognised as tasks:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun plainStatementsAreNotTasks() {
        listOf("The weather is nice today.", "Lunch was good.", "I like this song.", "It rained.").forEach {
            assertTrue(it, NoteRules.actionItems(it, now).isEmpty())
        }
    }

    @Test
    fun ideasAreKeptApartFromTasks() {
        val items = NoteRules.actionItems("Idea: a budget app for students\nMaybe learn piano\nWhat if we sell it", now)
        assertEquals(3, items.size)
        assertTrue(items.all { it.kind == "IDEA" })
    }

    @Test
    fun spokenDateBecomesDue() {
        val item = NoteRules.actionItems("Call mom tomorrow at 5pm", now).single()
        assertEquals(LocalDateTime.of(2026, 10, 15, 17, 0), item.due)
    }

    @Test
    fun undatedTaskHasNoDue() = assertNull(NoteRules.actionItems("Buy milk", now).single().due)

    @Test
    fun duplicateTasksAreMerged() {
        assertEquals(1, NoteRules.actionItems("Buy milk\nbuy milk\nBuy milk.", now).size)
    }

    @Test
    fun mixedNoteSplitsTasksAndIdeas() {
        val items = NoteRules.actionItems("Meeting notes.\nTodo: send the deck.\nIdea: new logo.\nWe had tea.", now)
        assertEquals(listOf("TASK", "IDEA"), items.map { it.kind })
    }

    // ─── nudges ──────────────────────────────────────────────────────────────

    private val nudgeCases: List<Pair<String, Boolean>> = listOf(
        "[nudge] remind me to call the bank tomorrow" to true,
        "#nudge Don't forget to pay rent on Friday" to true,
        "[nudge] Submit the assignment by tomorrow" to true,
        "[nudge] follow up with Sarah next week" to true,
        "[nudge] Dentist appointment on Monday at 10am" to true,
        "[nudge] renew passport" to true,
        "[nudge] Mom's birthday is coming" to true,
        "[nudge] I must send the invoice" to true,
        "[nudge] I need to book tickets" to true,
        "[nudge] bring the charger" to true,
        "[nudge] korte hobe bazar" to true,
        "[nudge] kinte hobe dim" to true,
        "[nudge] the sky is blue and the grass is green" to false,
        "[nudge] I like tea." to false,
        "[nudge] nothing to see here" to false,
        "[nudge] what a day" to false,
    )

    @Test
    fun nudgeTriggersFireOnlyOnTriggerLines() {
        val bad = nudgeCases.filter { (note, expected) -> NoteRules.nudges(note, now).isNotEmpty() != expected }.map { it.first }
        assertTrue("Wrong nudge decision:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun nudgeUsesSpokenDate() {
        val n = NoteRules.nudges("[nudge] remind me to call the bank tomorrow at 3pm", now).single()
        assertEquals(LocalDateTime.of(2026, 10, 15, 15, 0), n.at)
        assertTrue(n.message.startsWith("Call the bank"))
        assertTrue(n.reason.startsWith("Rules"))
    }

    @Test
    fun nudgeWithoutDateDefaultsToNineAm() {
        val n = NoteRules.nudges("[nudge] renew passport", now).single()
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 0), n.at)
    }

    @Test
    fun nudgeBeforeNineStaysToday() {
        val early = LocalDateTime.of(2026, 10, 14, 7, 0)
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), NoteRules.nudges("[nudge] renew passport", early).single().at)
    }

    @Test
    fun nudgeMarkerIsNotPartOfMessage() {
        val n = NoteRules.nudges("[nudge] pay the electricity bill", now).single()
        assertFalse(n.message.contains("nudge", true))
    }

    @Test
    fun nudgesAreDeduplicatedByMessage() {
        val note = "[nudge] pay rent\nPay rent\npay rent."
        assertEquals(1, NoteRules.nudges(note, now).size)
    }

    @Test
    fun nudgesAreCappedAtThree() {
        val note = (1..8).joinToString("\n") { "[nudge] pay bill number $it" }
        assertEquals(3, NoteRules.nudges(note, now).size)
    }

    @Test
    fun customTriggersWork() {
        assertTrue(NoteRules.nudges("ping the team", now, listOf("ping")).isNotEmpty())
        assertTrue(NoteRules.nudges("ping the team", now, listOf("other")).isEmpty())
    }

    @Test
    fun triggersMatchWholeWordsOnly() {
        assertTrue(NoteRules.nudges("The payment page was blue", now, listOf("pay")).isEmpty())
    }

    @Test
    fun optedInDetection() {
        assertTrue(NoteRules.optedIn("hello [nudge]"))
        assertTrue(NoteRules.optedIn("hello #NUDGE"))
        assertFalse(NoteRules.optedIn("hello nudge"))
        assertNotNull(NoteRules.nudges("#nudge call dad", now).firstOrNull())
    }
}
