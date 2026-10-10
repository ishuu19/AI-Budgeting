package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantLookupTest {

    private val lookup = TsvMerchantLookup("")

    // merchant, note, expected category (or null)
    private val cases: List<Triple<String, String, TransactionCategory?>> = listOf(
        Triple("Starbucks", "", TransactionCategory.FOOD),
        Triple("starbucks coffee", "", TransactionCategory.FOOD),
        Triple("McDonalds", "", TransactionCategory.FOOD),
        Triple("KFC", "", TransactionCategory.FOOD),
        Triple("Pizza Hut", "", TransactionCategory.FOOD),
        Triple("Dominos", "", TransactionCategory.FOOD),
        Triple("subway", "", TransactionCategory.FOOD),
        Triple("Foodpanda", "", TransactionCategory.FOOD),
        Triple("Shwapno", "", TransactionCategory.FOOD),
        Triple("Agora", "", TransactionCategory.FOOD),
        Triple("Walmart", "", TransactionCategory.FOOD),
        Triple("Costco", "", TransactionCategory.FOOD),
        Triple("Uber", "", TransactionCategory.TRANSPORT),
        Triple("UBER trip", "", TransactionCategory.TRANSPORT),
        Triple("Lyft", "", TransactionCategory.TRANSPORT),
        Triple("Pathao", "", TransactionCategory.TRANSPORT),
        Triple("Obhai", "", TransactionCategory.TRANSPORT),
        Triple("Shell", "", TransactionCategory.TRANSPORT),
        Triple("Metro", "", TransactionCategory.TRANSPORT),
        Triple("Netflix", "", TransactionCategory.SUBSCRIPTIONS),
        Triple("Spotify", "", TransactionCategory.SUBSCRIPTIONS),
        Triple("YouTube Premium", "", TransactionCategory.SUBSCRIPTIONS),
        Triple("iCloud", "", TransactionCategory.SUBSCRIPTIONS),
        Triple("ChatGPT", "", TransactionCategory.SUBSCRIPTIONS),
        Triple("Disney", "", TransactionCategory.SUBSCRIPTIONS),
        Triple("Amazon", "", TransactionCategory.SHOPPING),
        Triple("Daraz", "", TransactionCategory.SHOPPING),
        Triple("IKEA", "", TransactionCategory.SHOPPING),
        Triple("Zara", "", TransactionCategory.SHOPPING),
        Triple("Aarong", "", TransactionCategory.SHOPPING),
        Triple("eBay", "", TransactionCategory.SHOPPING),
        Triple("Cinema", "", TransactionCategory.ENTERTAINMENT),
        Triple("Steam", "", TransactionCategory.ENTERTAINMENT),
        Triple("PlayStation", "", TransactionCategory.ENTERTAINMENT),
        Triple("Star Cineplex", "", TransactionCategory.ENTERTAINMENT),
        Triple("Pharmacy", "", TransactionCategory.HEALTH),
        Triple("Square Pharma", "", TransactionCategory.HEALTH),
        Triple("Lazz Pharma", "", TransactionCategory.HEALTH),
        Triple("City Hospital", "", TransactionCategory.HEALTH),
        Triple("Dental Clinic", "", TransactionCategory.HEALTH),
        Triple("Gym", "", TransactionCategory.HEALTH),
        Triple("Grameenphone", "", TransactionCategory.UTILITIES),
        Triple("Robi", "", TransactionCategory.UTILITIES),
        Triple("Banglalink", "", TransactionCategory.UTILITIES),
        Triple("DPDC", "", TransactionCategory.UTILITIES),
        Triple("DESCO", "", TransactionCategory.UTILITIES),
        Triple("Titas Gas", "", TransactionCategory.UTILITIES),
        Triple("WASA", "", TransactionCategory.UTILITIES),
        Triple("Electricity", "", TransactionCategory.UTILITIES),
        Triple("Landlord", "", TransactionCategory.RENT),
        Triple("House rent", "", TransactionCategory.RENT),
        Triple("Udemy", "", TransactionCategory.EDUCATION),
        Triple("Coursera", "", TransactionCategory.EDUCATION),
        Triple("Tuition", "", TransactionCategory.EDUCATION),
        Triple("Bookshop", "", TransactionCategory.EDUCATION),
        Triple("Upwork", "", TransactionCategory.FREELANCE),
        Triple("Fiverr", "", TransactionCategory.FREELANCE),
        Triple("Payroll", "", TransactionCategory.SALARY),
        Triple("Salary", "", TransactionCategory.SALARY),
        // note only
        Triple("", "coffee at starbucks", TransactionCategory.FOOD),
        Triple("", "uber to office", TransactionCategory.TRANSPORT),
        Triple("", "netflix monthly", TransactionCategory.SUBSCRIPTIONS),
        Triple("", "bought from amazon", TransactionCategory.SHOPPING),
        Triple("", "paid the landlord", TransactionCategory.RENT),
        // merchant wins over note
        Triple("Uber", "netflix", TransactionCategory.TRANSPORT),
        Triple("Amazon", "uber", TransactionCategory.SHOPPING),
        // punctuation and case
        Triple("STARBUCKS!!!", "", TransactionCategory.FOOD),
        Triple("  Netflix  ", "", TransactionCategory.SUBSCRIPTIONS),
        Triple("McDonald's", "", TransactionCategory.FOOD),
        // unknown
        Triple("Corner Shop 24", "", null),
        Triple("Zyx", "", null),
        Triple("", "", null),
        Triple("Rubber", "", null),
        Triple("Suberb", "", null),
    )

    @Test
    fun hasEnoughCases() = assertTrue("have ${cases.size}", cases.size >= 70)

    @Test
    fun everyMerchantMapsToItsCategory() {
        val bad = cases.filter { (m, n, expected) -> lookup.lookup(m, n)?.category != expected }
            .map { (m, n, e) -> "'$m' / '$n' expected $e got ${lookup.lookup(m, n)?.category}" }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun canonicalNameFixesSpelling() {
        assertEquals("Starbucks", lookup.lookup("starbucks", "")?.merchant)
        assertEquals("Domino's", lookup.lookup("dominos", "")?.merchant)
        assertEquals("YouTube Premium", lookup.lookup("youtube premium", "")?.merchant)
    }

    @Test
    fun exactCanonicalNameNeedsNoFix() {
        assertNull(lookup.lookup("Starbucks", "")?.merchant)
    }

    @Test
    fun noCanonicalSuggestionForNoteOnlyMatch() {
        assertNull(lookup.lookup("", "coffee at starbucks")?.merchant)
    }

    @Test
    fun tsvRowsExtendTheEmbeddedList() {
        val l = TsvMerchantLookup("# alias\tcanonical\tcategory\nchaldal\tChaldal\tFOOD\nbkash fee\tbKash\tUTILITIES\n")
        assertEquals(TransactionCategory.FOOD, l.lookup("chaldal order", "")?.category)
        assertEquals("Chaldal", l.lookup("chaldal", "")?.merchant)
        assertEquals(TransactionCategory.UTILITIES, l.lookup("bkash fee", "")?.category)
    }

    @Test
    fun tsvColumnOrderDoesNotMatter() {
        val l = TsvMerchantLookup("FOOD\tkacchi\tKacchi Bhai\n")
        assertEquals(TransactionCategory.FOOD, l.lookup("kacchi", "")?.category)
    }

    @Test
    fun tsvTwoColumnRows() {
        val l = TsvMerchantLookup("pottery barn\tSHOPPING\n")
        assertEquals(TransactionCategory.SHOPPING, l.lookup("Pottery Barn", "")?.category)
    }

    @Test
    fun tsvDisplayNameCategory() {
        val l = TsvMerchantLookup("tutor\tTutor\tEducation\n")
        assertEquals(TransactionCategory.EDUCATION, l.lookup("tutor", "")?.category)
    }

    @Test
    fun badTsvRowsAreSkipped() {
        val l = TsvMerchantLookup("onlyonecolumn\nfoo\tbar\tnotacategory\n\n")
        assertNull(l.lookup("foo", ""))
    }

    @Test
    fun longerAliasWinsOverShorterOne() {
        val l = TsvMerchantLookup("pathao food\tPathao Food\tFOOD\n")
        assertEquals(TransactionCategory.FOOD, l.lookup("pathao food", "")?.category)
        assertEquals(TransactionCategory.TRANSPORT, l.lookup("pathao ride", "")?.category)
    }

    // ─── corrections ─────────────────────────────────────────────────────────

    @Test
    fun correctionBeatsLexicon() {
        val store = MemoryCorrectionStore()
        store.put("starbucks", TransactionCategory.ENTERTAINMENT)
        val l = TsvMerchantLookup("", store)
        val s = l.lookup("Starbucks", "")!!
        assertEquals(TransactionCategory.ENTERTAINMENT, s.category)
        assertEquals("Remembered", s.reason)
    }

    @Test
    fun correctionForUnknownMerchant() {
        val store = MemoryCorrectionStore()
        store.put("corner shop 24", TransactionCategory.FOOD)
        assertEquals(TransactionCategory.FOOD, TsvMerchantLookup("", store).lookup("Corner Shop 24!", "")?.category)
    }

    @Test
    fun otherMerchantsAreNotAffectedByCorrections() {
        val store = MemoryCorrectionStore()
        store.put("starbucks", TransactionCategory.ENTERTAINMENT)
        assertEquals(TransactionCategory.TRANSPORT, TsvMerchantLookup("", store).lookup("Uber", "")?.category)
    }

    @Test
    fun lexiconReasonIsRules() {
        assertEquals(SOURCE_RULES, lookup.lookup("Uber", "")?.reason)
    }

    @Test
    fun memoryStoreRoundTrip() {
        val store = MemoryCorrectionStore()
        assertNull(store.get("x"))
        store.put("x", TransactionCategory.HEALTH)
        assertEquals(TransactionCategory.HEALTH, store.get("x"))
    }

    @Test
    fun normStripsSymbols() {
        assertEquals("mcdonalds", TsvMerchantLookup.norm("McDonald's"))
        assertEquals("a b", TsvMerchantLookup.norm("  A---B  "))
    }
}
