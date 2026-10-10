package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.TransactionCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class VoiceRoutingTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val now = LocalDateTime.of(today, LocalTime.of(10, 0))

    private fun route(text: String, cloudEnabled: Boolean = true, cloud: suspend () -> List<ParsedIntent>): RoutedIntents =
        runBlocking { VoiceIntentRouter.route(text, cloudEnabled, today, now, cloud) }

    @Test
    fun onDeviceAnswer_readsTheKindAfterTheArrow() {
        val parsed = LocalParseAnswer.read("SPEND", "spent 12 on lunch", today, now)
        val tx = parsed as ParsedIntent.Transaction
        assertEquals(12.0, tx.amount!!, 0.001)
    }

    @Test
    fun onDeviceModelIsFirstAndSkipsCloud() {
        var cloudCalls = 0
        val local = ParsedIntent.Note(title = "Idea", body = "ship the widget", rawTranscript = "ship the widget")
        val r = runBlocking {
            VoiceIntentRouter.route("ship the widget", true, today, now, cloud = { cloudCalls++; emptyList() }) {
                listOf(local)
            }
        }
        assertEquals(0, cloudCalls)
        assertEquals(IntentSource.ON_DEVICE, r.source)
        assertTrue(r.items.single() is ParsedIntent.Note)
    }

    @Test
    fun confidentRulesNeverCallCloud() {
        var calls = 0
        val texts = listOf(
            "spent 12 on lunch", "Coffee 4.50 at Starbucks", "set an alarm for 7 am", "remind me to call mom tomorrow at 9 am",
            "I owe Alex 40", "save 500 for vacation", "set a food budget of 300", "note buy milk", "delete the gym alarm",
        )
        for (t in texts) {
            val r = route(t) { calls++; emptyList() }
            assertEquals(t, IntentSource.RULES, r.source)
            assertTrue(t, !r.cloudCalled)
        }
        assertEquals(0, calls)
    }

    @Test
    fun lowConfidenceCallsCloudOnce() {
        var calls = 0
        val cloud = ParsedIntent.Transaction(
            amount = 30.0, category = TransactionCategory.HEALTH, merchant = "Pharmacy", date = today, note = "", rawTranscript = "paid 30",
        )
        val r = route("paid 30") { calls++; listOf(cloud) }
        assertEquals(1, calls)
        assertEquals(IntentSource.AI, r.source)
        val tx = r.items.single() as ParsedIntent.Transaction
        assertEquals(30.0, tx.amount!!, 0.001)
        assertEquals(TransactionCategory.HEALTH, tx.category)
        assertEquals("Pharmacy", tx.merchant)
    }

    @Test
    fun ruleFieldsWithEvidenceSurviveTheMerge() {
        val cloud = ParsedIntent.Transaction(
            amount = 99.0, category = TransactionCategory.SHOPPING, merchant = "Other", date = today, note = "", rawTranscript = "x",
        )
        val r = route("paid 30 for the thing") { listOf(cloud) }
        val tx = r.items.single() as ParsedIntent.Transaction
        assertEquals(30.0, tx.amount!!, 0.001)
    }

    @Test
    fun unmatchedCallsCloudAndCloudWins() {
        val note = ParsedIntent.Note(title = "Thought", body = "hello there how are you", rawTranscript = "hello there how are you")
        val r = route("hello there how are you") { listOf(note) }
        assertEquals(IntentSource.AI, r.source)
        assertTrue(r.items.single() is ParsedIntent.Note)
    }

    @Test
    fun cloudFailureFallsBackToRules() {
        val r = route("paid 30") { throw IllegalStateException("offline") }
        assertEquals(IntentSource.RULES, r.source)
        assertTrue(r.cloudCalled)
        val tx = r.items.single() as ParsedIntent.Transaction
        assertEquals(30.0, tx.amount!!, 0.001)
    }

    @Test
    fun emptyCloudAnswerKeepsRules() {
        val r = route("paid 30") { emptyList() }
        assertEquals(IntentSource.RULES, r.source)
        assertTrue(r.items.single() is ParsedIntent.Transaction)
    }

    @Test
    fun cloudFallbackOffNeverCallsCloud() {
        var calls = 0
        val r = route("hello there how are you", cloudEnabled = false) { calls++; emptyList() }
        assertEquals(0, calls)
        assertEquals(IntentSource.RULES, r.source)
        assertTrue(r.items.single() is ParsedIntent.Unmatched)
    }

    @Test
    fun confidenceReflectsEvidence() {
        assertTrue(RuleEngine.evaluate("spent 12 on lunch", today, now).confident)
        assertTrue(!RuleEngine.evaluate("paid 30", today, now).confident)
        assertTrue(!RuleEngine.evaluate("blah blah", today, now).confident)
        assertTrue(RuleEngine.evaluate("set an alarm for 7 am", today, now).items.single().points >= 70)
        assertTrue(RuleEngine.evaluate("set an alarm", today, now).items.single().points < 70)
        assertEquals(CalendarEventKind.ALARM, (RuleEngine.evaluate("set an alarm for 7 am", today, now).intents.single() as ParsedIntent.Event).kind)
    }

    @Test
    fun lexiconMerchantsAndKeywordsAreUsed() {
        val saved = QuickParse.lexicon
        try {
            QuickParse.lexicon = RuleLexicon(
                mapOf(
                    RuleLexicon.MERCHANTS to listOf(listOf("blue bottle", "FOOD", "Blue Bottle Coffee"), listOf("khaas food", "FOOD", "Khaas Food")),
                    RuleLexicon.KEYWORDS to listOf(listOf("kacchi", "FOOD"), listOf("vet", "HEALTH")),
                )
            )
            val tx = QuickParse.parseVoiceIntent("spent 450 at khaas food", today, now) as ParsedIntent.Transaction
            assertEquals("Khaas Food", tx.merchant)
            assertEquals(TransactionCategory.FOOD, tx.category)
            val vet = QuickParse.parseVoiceIntent("paid 80 for vet", today, now) as ParsedIntent.Transaction
            assertEquals(TransactionCategory.HEALTH, vet.category)
            assertTrue(RuleEngine.evaluate("spent 450 at khaas food", today, now).confident)
        } finally {
            QuickParse.lexicon = saved
        }
    }

    @Test
    fun lexiconToleratesMissingAndEmptyFiles() {
        val empty = RuleLexicon.fromStreams { null }
        assertTrue(empty.isEmpty)
        assertEquals(null, empty.merchant("starbucks"))
        val parsed = RuleLexicon.parse("# comment\n\nuber\tTRANSPORT\tUber\n")
        assertEquals(1, parsed.size)
    }

    @Test
    fun largeLexiconLooksUpFast() {
        val rows = (0 until 20000).map { listOf("shop$it market", "SHOPPING", "Shop $it") }
        val lex = RuleLexicon(mapOf(RuleLexicon.MERCHANTS to rows))
        lex.merchant("warm up")
        val start = System.nanoTime()
        repeat(200) { lex.merchant("spent 20 at shop19999 market today") }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 1000)
        assertEquals("Shop 19999", lex.merchant("spent 20 at shop19999 market today")?.name)
    }
}
