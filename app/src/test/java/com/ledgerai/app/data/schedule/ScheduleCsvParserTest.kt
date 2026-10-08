package com.ledgerai.app.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ScheduleCsvParserTest {

    @Test
    fun parsesHeaderRow() {
        val text = """
            Day,Start,End,Title,Course,Location
            Mon,09:00,10:30,Calculus,MATH101,Room 204
        """.trimIndent()
        val rows = ScheduleCsvParser.parse(text)
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].dayOfWeek)
        assertEquals(LocalTime.of(9, 0), rows[0].startTime)
        assertEquals("Calculus", rows[0].title)
        assertEquals("MATH101", rows[0].courseCode)
    }

    @Test
    fun parsesWithoutHeader() {
        val text = "Wed,14:00,15:00,Lab,CS201,B12"
        val rows = ScheduleCsvParser.parse(text)
        assertTrue(rows.isNotEmpty())
        assertEquals(3, rows[0].dayOfWeek)
    }
}
