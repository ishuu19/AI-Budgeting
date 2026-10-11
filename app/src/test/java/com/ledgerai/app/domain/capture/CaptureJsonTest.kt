package com.ledgerai.app.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CaptureJsonTest {

    @Test
    fun garbageReturnsNull() {
        assertNull(CaptureJson.parse("sorry, I can't help"))
        assertNull(CaptureJson.parse("{not json"))
    }

    @Test
    fun clothingParsesAndSurvivesCodeFences() {
        val raw = """
            ```json
            {"kind":"clothing","title":"Navy blazer","description":"A navy wool blazer.",
             "clothing":{"name":"Navy blazer","type":"jacket","colors":["navy"],"seasons":["autumn","winter"]}}
            ```
        """.trimIndent()
        val r = CaptureJson.parse(raw)!!
        assertEquals(CaptureKind.CLOTHING, r.kind)
        assertEquals("Navy blazer", r.garment?.name)
        assertEquals(listOf("navy"), r.garment?.colors)
        assertEquals(listOf("autumn", "winter"), r.garment?.seasons)
    }

    @Test
    fun unknownKindFallsBackToOther() {
        assertEquals(CaptureKind.OTHER, CaptureJson.parse("""{"kind":"spaceship","title":"x"}""")!!.kind)
    }

    @Test
    fun foodKeepsUnknownQuantityNull() {
        val r = CaptureJson.parse(
            """{"kind":"food","title":"Fridge","food":[
                {"name":"Eggs","quantity":null,"unit":"","location":"fridge","expiresOn":null},
                {"name":"Milk","quantity":1,"unit":"l","location":"fridge","expiresOn":"2026-10-20"},
                {"name":"  ","quantity":3}]}"""
        )!!
        assertEquals(2, r.food.size)
        assertNull(r.food[0].quantity)
        assertEquals(1.0, r.food[1].quantity!!, 0.0)
        assertEquals(LocalDate.of(2026, 10, 20), r.food[1].expiresOn)
    }

    @Test
    fun receiptKeepsMissingValuesNullAndBadDatesDropped() {
        val r = CaptureJson.parse(
            """{"kind":"receipt","title":"Cafe","receipt":{"merchant":"Blue Cafe","total":null,"date":"yesterday",
                "lines":[{"text":"Latte","qty":1,"price":4.5},{"text":"","qty":1}],"subscription":null}}"""
        )!!
        val receipt = r.receipt!!
        assertEquals("Blue Cafe", receipt.merchant)
        assertNull(receipt.total)
        assertNull(receipt.date)
        assertEquals(1, receipt.lines.size)
        assertNull(receipt.subscription)
    }

    @Test
    fun receiptLinesCarryACleanNameAndAFoodFlag() {
        val r = CaptureJson.parse(
            """{"kind":"receipt","receipt":{"merchant":"Mart","total":9.5,"lines":[
                {"text":"MLK 2L 0123","name":"Milk","food":true,"qty":1,"price":3.5},
                {"text":"BAG","food":false,"price":0.1}]}}"""
        )!!
        val lines = r.receipt!!.lines
        assertEquals("Milk", lines[0].name)
        assertTrue(lines[0].food)
        assertNull(lines[1].name)
        assertTrue(!lines[1].food)
    }

    @Test
    fun subscriptionNeedsAValidPeriod() {
        val good = CaptureJson.parse(
            """{"kind":"receipt","receipt":{"merchant":"Netflix","subscription":{"period":"Monthly","nextRenewalOn":"2026-11-01"}}}"""
        )!!
        assertEquals("monthly", good.receipt?.subscription?.period)
        assertEquals(LocalDate.of(2026, 11, 1), good.receipt?.subscription?.nextRenewalOn)

        val bad = CaptureJson.parse(
            """{"kind":"receipt","receipt":{"merchant":"Netflix","subscription":{"period":"sometimes"}}}"""
        )!!
        assertNull(bad.receipt?.subscription)
    }

    @Test
    fun personNameIsNullWhenAbsent() {
        val r = CaptureJson.parse("""{"kind":"person","person":{"name":null,"memory":null}}""")!!
        assertNotNull(r.person)
        assertNull(r.person?.name)
    }

    @Test
    fun personKeepsMemoryAndDate() {
        val r = CaptureJson.parse(
            """{"kind":"person","person":{"name":"Maya","when":"2026-09-14","where":"Cox's Bazar","memory":"Beach trip with Maya."}}"""
        )!!
        assertEquals("Maya", r.person?.name)
        assertEquals(LocalDate.of(2026, 9, 14), r.person?.on)
        assertEquals("Cox's Bazar", r.person?.place)
    }

    @Test
    fun promptCarriesTheNoteOnlyWhenPresent() {
        assertTrue(CapturePrompts.user("").startsWith("Describe"))
        assertTrue(CapturePrompts.user("this is Maya").contains("this is Maya"))
    }
}
