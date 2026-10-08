package com.ledgerai.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class SyncTimeTest {

    @Test
    fun millisIso_roundTrip() {
        val ms = 1_728_374_400_000L // 2024-10-08T12:00:00Z
        val iso = SyncTime.millisToIso(ms)
        assertEquals(ms, SyncTime.isoToMillis(iso))
    }

    @Test
    fun isoToMillis_acceptsOffsetDateTime() {
        val ms = SyncTime.isoToMillis("2024-10-08T12:00:00+00:00")
        assertEquals(1_728_374_400_000L, ms)
    }

    @Test
    fun isoToMillis_blankOrInvalid_returnsZero() {
        assertEquals(0L, SyncTime.isoToMillis(null))
        assertEquals(0L, SyncTime.isoToMillis(""))
        assertEquals(0L, SyncTime.isoToMillis("not-a-date"))
    }

    @Test
    fun date_roundTrip() {
        val date = LocalDate.of(2026, 10, 8)
        assertEquals(date, SyncTime.stringToDate(SyncTime.dateToString(date)))
    }

    @Test
    fun dateTime_roundTrip_utc() {
        val dt = LocalDateTime.of(2026, 10, 8, 9, 30, 0)
        val iso = SyncTime.dateTimeToIso(dt)
        assertEquals(dt, SyncTime.isoToDateTime(iso))
    }

    @Test
    fun time_roundTrip() {
        val time = LocalTime.of(7, 5, 9)
        assertEquals(time, SyncTime.stringToTime(SyncTime.timeToString(time)))
    }

    @Test
    fun postgrestGtFilter_prefixesGt() {
        val filter = SyncTime.postgrestGtFilter(0L)
        assertTrue(filter.startsWith("gt."))
        assertTrue(filter.contains("1970"))
    }

    @Test
    fun remoteWins_lww_compare() {
        val older = 1_000L
        val newer = 2_000L
        assertTrue(SyncTime.remoteWins(newer, older))
        assertTrue(SyncTime.remoteWins(newer, newer)) // tie → remote
        assertFalse(SyncTime.remoteWins(older, newer))
    }

    @Test
    fun remoteWins_fromIso() {
        val localMs = InstantEpoch.ms("2024-10-08T12:00:00Z")
        val remoteIso = SyncTime.millisToIso(localMs + 5_000)
        assertTrue(SyncTime.remoteWins(remoteIso, localMs))
        assertFalse(SyncTime.remoteWins(SyncTime.millisToIso(localMs - 1), localMs))
    }

    private object InstantEpoch {
        fun ms(iso: String): Long =
            java.time.Instant.parse(iso).toEpochMilli()
    }
}
