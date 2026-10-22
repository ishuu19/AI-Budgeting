package com.ledgerai.app.data.local.room

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/** Version 22 adds the rebuild's tables and columns without touching any existing row. */
class Migration21To22Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, deletedAt INTEGER)")
        exec("INSERT INTO notes (title) VALUES ('Passport number notes')")
        exec("INSERT INTO notes (title, deletedAt) VALUES ('Old deleted', 5)")
        exec("CREATE TABLE calendar_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL)")
        exec("INSERT INTO calendar_events (title, kind) VALUES ('Dentist', 'EVENT')")
        exec("CREATE TABLE media_assets (id TEXT PRIMARY KEY NOT NULL, localPath TEXT NOT NULL)")
        exec("INSERT INTO media_assets (id, localPath) VALUES ('m1', '/a.jpg')")
        Migration21To22Sql.statements.forEach { exec(it) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun existingNotesKeepTheirRowsAndLandInTheNotesFolder() {
        assertEquals(1L, scalar("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL"))
        // A null folderId is the Notes folder, so no note had to be copied or moved.
        assertEquals(1L, scalar("SELECT COUNT(*) FROM notes WHERE folderId IS NULL AND deletedAt IS NULL"))
    }

    @Test
    fun existingCalendarRowsGainNullAppointmentColumns() {
        db.createStatement().use { st ->
            st.executeQuery("SELECT title, kind, personId, jobId, status FROM calendar_events").use { rs ->
                assertTrue(rs.next())
                assertEquals("Dentist", rs.getString("title"))
                assertEquals("EVENT", rs.getString("kind"))
                assertNull(rs.getString("personId"))
                assertNull(rs.getString("status"))
            }
        }
    }

    @Test
    fun existingMediaIsNotLockedByDefault() {
        assertEquals(0L, scalar("SELECT locked FROM media_assets WHERE id = 'm1'"))
    }

    @Test
    fun newTablesExistAndAcceptRows() {
        exec("INSERT INTO birthdays (id, personId, month, day, celebrateMinute, note, r1mo, r2w, r1w, r3d, r1d, r5h, r30m, r10m, rAt, updatedAt) VALUES ('b1', 'p1', 11, 14, 0, '', 1,1,1,1,1,1,1,1,1, 1)")
        exec("INSERT INTO trips (id, name, place, startDate, endDate, notes, r1w, r1d, visibility, updatedAt) VALUES ('t1', 'Dhaka', 'Dhaka', '2026-11-20', '2026-11-24', '', 1, 1, 'private', 1)")
        exec("INSERT INTO app_usage_days (day, packageName, label, foregroundMs, launches) VALUES ('2026-10-22', 'com.x', 'X', 1000, 1)")
        assertEquals(1L, scalar("SELECT COUNT(*) FROM birthdays"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM trips"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM app_usage_days"))
        for (table in listOf("folders", "achievements", "wishes", "passwords", "reminders")) {
            assertEquals(0L, scalar("SELECT COUNT(*) FROM $table"))
        }
    }

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun scalar(sql: String): Long =
        db.createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) } }
}
