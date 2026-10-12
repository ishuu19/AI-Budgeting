package com.ledgerai.app.domain.clips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipJsonTest {

    @Test
    fun garbageReturnsNull() {
        assertNull(ClipJson.parse("sorry, I can't help"))
        assertNull(ClipJson.parse("{not json"))
    }

    @Test
    fun unknownKindBecomesOther() {
        assertEquals("other", ClipJson.parse("""{"kind":"spaceship","title":"x"}""")!!.kind)
    }

    @Test
    fun badDateAndBlankNameStayNull() {
        val parsed = ClipJson.parse(
            """{"kind":"event","title":"Dentist","fields":{"date":"tomorrow","time":"3pm","place":"","person":" "}}"""
        )!!
        assertNull(parsed.date)
        assertNull(parsed.time)
        assertNull(parsed.place)
        assertNull(parsed.person)
    }

    @Test
    fun autoModeDoesNotFileABill() {
        assertFalse(ClipFiling.autoApplies("bill", ClipFiling.AUTO))
        assertFalse(ClipFiling.autoApplies("transaction", ClipFiling.AUTO))
        assertFalse(ClipFiling.autoApplies("debt", ClipFiling.AUTO))
        assertFalse(ClipFiling.autoApplies("task", ClipFiling.AUTO))
        assertFalse(ClipFiling.autoApplies("shopping", ClipFiling.SUGGEST))
        assertTrue(ClipFiling.autoApplies("shopping", ClipFiling.AUTO))
        assertTrue(ClipFiling.autoApplies("note", ClipFiling.AUTO))
        assertEquals(com.ledgerai.app.domain.assistant.Risk.HIGH, ClipFiling.risk("bill"))
    }

    @Test
    fun goldenSetKeepsOnlyWrittenFields() {
        GOLD.forEach { gold ->
            val parsed = ClipJson.parse(gold.json)
            assertTrue("no json for: ${gold.source}", parsed != null)
            assertEquals(gold.kind, parsed!!.kind)
            assertTrue("invented field in: ${gold.source}", ClipJson.grounded(gold.source, parsed))
        }
        assertEquals(30, GOLD.size)
    }

    private data class Gold(val source: String, val json: String, val kind: String)

    private val GOLD = listOf(
        Gold("Shinjuku Gyoen opens 9:00, closed Mondays. Entry ¥500.", obj("place", "Shinjuku Gyoen", time = "09:00", place = "Shinjuku Gyoen", amount = "500", currency = "JPY"), "place"),
        Gold("Dinner at 15:00 on 12 Nov", obj("event", "Dinner", date = "2026-11-12", time = "15:00"), "event"),
        Gold("Software Engineer at Acme", obj("job", "Software Engineer", company = "Acme", role = "Software Engineer"), "job"),
        Gold("Buy milk and bread", obj("shopping", "Milk", item = "milk"), "shopping"),
        Gold("Todo: email the landlord", obj("task", "Email the landlord"), "task"),
        Gold("Sam's birthday is 3 May", obj("memory", "Sam birthday", date = "2026-05-03", person = "Sam"), "memory"),
        Gold("Electric bill 40 dollars due 1 Nov", obj("bill", "Electric bill", date = "2026-11-01", amount = "40", currency = "USD"), "bill"),
        Gold("Recipe: chop the onions", obj("note", "Onions", item = "onions"), "note"),
        Gold("Address: 14 King Street", obj("place", "King Street", place = "King Street"), "place"),
        Gold("A quote: stay curious", obj("note", "Stay curious"), "note"),
        Gold("https://example.com/maps", obj("note", "Link"), "note"),
        Gold("Call Maya on 555-0100", obj("note", "Call Maya", person = "Maya"), "note"),
        Gold("Write to ada@example.com", obj("note", "Email Ada"), "note"),
        Gold("Spent 12 at Blue Cafe", obj("transaction", "Blue Cafe", amount = "12", place = "Blue Cafe"), "transaction"),
        Gold("I owe Mia 20 dollars", obj("debt", "Mia", amount = "20", currency = "USD", person = "Mia"), "debt"),
        Gold("Milk\nEggs", obj("shopping", "Groceries", split = listOf("Milk", "Eggs")), "shopping"),
        Gold("Just a thought about the trip", obj("other", "Trip"), "other"),
        Gold("Booking ref QX8-22K, check-in 15:00 12 Nov", obj("event", "Check-in", date = "2026-11-12", time = "15:00"), "event"),
        Gold("Flight BA249 to London", obj("event", "Flight BA249", place = "London"), "event"),
        Gold("Interview at Northwind for Designer", obj("job", "Designer", company = "Northwind", role = "Designer"), "job"),
        Gold("Add eggs to the list", obj("shopping", "Eggs", item = "eggs"), "shopping"),
        Gold("Dentist at 15:30", obj("event", "Dentist", time = "15:30"), "event"),
        Gold("Confirmation number AB12", obj("note", "Confirmation AB12"), "note"),
        Gold("Park near the river", obj("place", "River", place = "river"), "place"),
        Gold("Remember to renew the passport", obj("task", "Renew the passport"), "task"),
        Gold("Recipe step: soak the rice", obj("note", "Soak the rice"), "note"),
        Gold("Meet Priya at the library", obj("event", "Library", place = "library", person = "Priya"), "event"),
        Gold("Invoice 80 from Orbit", obj("bill", "Orbit", amount = "80", company = "Orbit"), "bill"),
        Gold("Role: Analyst Company: Helix", obj("job", "Analyst", company = "Helix", role = "Analyst"), "job"),
        Gold("Buy onions", obj("shopping", "Onions", item = "onions"), "shopping"),
    )

    private fun obj(
        kind: String,
        title: String,
        date: String? = null,
        time: String? = null,
        place: String? = null,
        amount: String? = null,
        currency: String? = null,
        company: String? = null,
        role: String? = null,
        person: String? = null,
        item: String? = null,
        split: List<String> = emptyList(),
    ): String {
        fun q(v: String?) = if (v == null) "null" else "\"$v\""
        val pieces = split.joinToString(",") { "\"$it\"" }
        return """{"kind":"$kind","title":"$title","tags":[],"split":[$pieces],"fields":{"date":${q(date)},"time":${q(time)},"place":${q(place)},"amount":${amount ?: "null"},"currency":${q(currency)},"company":${q(company)},"role":${q(role)},"person":${q(person)},"item":${q(item)}},"confidence":"high"}"""
    }
}
