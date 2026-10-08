package com.ledgerai.app.data.local.room

import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Runs the real migration SQL ([Migration6To7Sql]) against a hand-built version 6 database on
 * SQLite (sqlite-jdbc) and checks the data mapping, plus that the resulting tables match the
 * schema Room exported for version 7.
 */
class Migration6To7Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        createV6Schema()
        seedV6Data()
        Migration6To7Sql.statements.forEach { db.createStatement().use { st -> st.execute(it) } }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun rows(sql: String): List<Map<String, Any?>> = db.createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            val meta = rs.metaData
            buildList {
                while (rs.next()) {
                    add((1..meta.columnCount).associate { meta.getColumnLabel(it) to rs.getObject(it) })
                }
            }
        }
    }

    private fun count(sql: String): Long = (rows(sql)[0]["n"] as Number).toLong()

    private fun event(title: String): Map<String, Any?> =
        rows("SELECT * FROM calendar_events WHERE title = '$title'").single()

    private fun reminders(eventId: Any?): List<Map<String, Any?>> =
        rows("SELECT * FROM event_reminders WHERE eventId = $eventId ORDER BY id")

    @Test
    fun oldTablesAreGoneAndMirrorEventIsNotDuplicated() {
        val tables = rows("SELECT name FROM sqlite_master WHERE type = 'table'").map { it["name"] }
        listOf(
            "tasks", "task_reminders", "routines", "courses", "schedule_slots",
            "schedule_slot_exceptions", "routine_slot_reminders", "alarms", "calendar_events_old", "mig_off"
        ).forEach { assertFalse("$it should be dropped", it in tables) }
        assertTrue("calendar_events" in tables)
        assertTrue("event_reminders" in tables)

        // plain event + moved class + 3 live tasks + 2 slots + 1 standalone routine + 3 alarms
        assertEquals(11L, count("SELECT COUNT(*) AS n FROM calendar_events"))
        assertEquals(1L, count("SELECT COUNT(*) AS n FROM calendar_events WHERE title = 'Exam 1'"))
    }

    @Test
    fun existingEventsKeepIdAndPersonalBecomesEvent() {
        val dentist = event("Dentist")
        assertEquals(5, (dentist["id"] as Number).toInt())
        assertEquals("EVENT", dentist["kind"])
        assertEquals("NONE", dentist["recurrenceFrequency"])
        val moved = event("Moved lecture")
        assertEquals("CLASS", moved["kind"])
    }

    @Test
    fun tasksKeepKindCompletionAndUndatedFlag() {
        val task = event("Write essay")
        assertEquals("TASK", task["kind"])
        assertEquals(1, (task["hasDate"] as Number).toInt())
        assertEquals(task["startAt"], task["endAt"])
        assertEquals("2026-10-10T09:00", task["startAt"])
        assertEquals("Chapter 2", task["notes"])

        val exam = event("Exam 1")
        assertEquals("EXAM", exam["kind"])
        assertEquals("2026-10-12T14:00", exam["startAt"])
        assertEquals("2026-10-12T15:00", exam["endAt"])

        val undated = event("Someday")
        assertEquals(0, (undated["hasDate"] as Number).toInt())
        assertEquals(1, (undated["isCompleted"] as Number).toInt())
        assertEquals("2026-09-01T00:00", undated["startAt"])
        assertTrue((undated["completedAt"] as String).startsWith("1970"))
    }

    @Test
    fun taskRemindersAreMappedAndCappedAtTen() {
        val id = event("Write essay")["id"]
        val list = reminders(id)
        assertEquals(10, list.size)
        assertEquals(10, (list[0]["offsetMinutes"] as Number).toInt())
        assertNull(list[0]["remindAt"])
        // reminder that fired exactly at the due time becomes a 0 minute offset
        assertEquals(0, (list[1]["offsetMinutes"] as Number).toInt())
        assertNull(list[1]["remindAt"])
        // absolute one-off reminder stays absolute
        assertNull(list[2]["offsetMinutes"])
        assertEquals("2026-10-09T18:00", list[2]["remindAt"])
        assertTrue(list.none { (it["deletedAt"]) != null })
    }

    @Test
    fun slotsBecomeWeeklyClassEventsWithExceptions() {
        val lecture = event("Calculus")
        assertEquals("CLASS", lecture["kind"])
        assertEquals("WEEKLY", lecture["recurrenceFrequency"])
        assertEquals("1", lecture["recurrenceWeekdays"])
        assertEquals("2026-12-01", lecture["recurrenceUntil"])
        assertEquals("2026-10-05,2026-10-12", lecture["excludedDatesJson"])
        assertEquals("Room 1", lecture["location"])
        val start = LocalDateTime.parse(lecture["startAt"] as String)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals("09:00", start.toLocalTime().toString())
        assertEquals("10:00", LocalDateTime.parse(lecture["endAt"] as String).toLocalTime().toString())
        assertEquals(1, (lecture["isEnabled"] as Number).toInt())

        val sunday = event("Yoga")
        assertEquals(DayOfWeek.SUNDAY, LocalDateTime.parse(sunday["startAt"] as String).dayOfWeek)
        assertEquals("7", sunday["recurrenceWeekdays"])
    }

    @Test
    fun slotRemindersAreMapped() {
        val list = reminders(event("Calculus")["id"])
        assertEquals(listOf(10, 0), list.map { (it["offsetMinutes"] as Number).toInt() })
    }

    @Test
    fun standaloneRoutineBecomesRoutineEventAndRoutineWithSlotsDoesNot() {
        val routine = event("Stretch")
        assertEquals("ROUTINE", routine["kind"])
        assertEquals("WEEKLY", routine["recurrenceFrequency"])
        assertEquals("1,2,3,4,5", routine["recurrenceWeekdays"])
        assertEquals(0, rows("SELECT * FROM calendar_events WHERE title = 'Timetable'").size)
    }

    @Test
    fun alarmsKeepToneRepeatMaskAndEnabledFlag() {
        val wake = event("Wake up")
        assertEquals("ALARM", wake["kind"])
        assertEquals(62, (wake["alarmRepeatDays"] as Number).toInt())
        assertEquals("WEEKLY", wake["recurrenceFrequency"])
        assertEquals("1,2,3,4,5", wake["recurrenceWeekdays"])
        assertEquals("content://tone", wake["alarmToneUri"])
        assertEquals("07:30", LocalDateTime.parse(wake["startAt"] as String).toLocalTime().toString())

        val nap = event("Nap")
        assertEquals(0, (nap["isEnabled"] as Number).toInt())
        assertEquals(0, (nap["alarmRepeatDays"] as Number).toInt())
        assertEquals("NONE", nap["recurrenceFrequency"])
        assertEquals("", nap["recurrenceWeekdays"])

        val weekend = event("Weekend")
        assertEquals("6,7", weekend["recurrenceWeekdays"]) // Sat (64) + Sun (1)
    }

    @Test
    fun leaveRulesPointAtMergedEventsOnly() {
        val essay = (event("Write essay")["id"] as Number).toLong()
        val exam = (event("Exam 1")["id"] as Number).toLong()
        val rules = rows("SELECT * FROM leave_rules ORDER BY id")
        assertTrue(rules.all { it["refType"] == "CALENDAR_EVENT" })
        assertEquals(listOf(essay, exam, 5L), rules.map { (it["refId"] as Number).toLong() })
    }

    @Test
    fun softDeletedRowsAreNotCopied() {
        assertEquals(0, rows("SELECT * FROM calendar_events WHERE title IN ('Deleted task', 'Old alarm')").size)
    }

    @Test
    fun resultingTablesMatchExportedRoomSchema() {
        val schema = File("schemas/com.ledgerai.app.data.local.room.LedgerDatabase/7.json")
        assertTrue("exported schema missing: ${schema.absolutePath}", schema.exists())
        val entities = JsonParser.parseString(schema.readText()).asJsonObject
            .getAsJsonObject("database").getAsJsonArray("entities")

        val fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            for (el in entities) {
                val entity = el.asJsonObject
                val table = entity.get("tableName").asString
                if (table != "calendar_events" && table != "event_reminders") continue
                fresh.createStatement().use {
                    it.execute(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                }
                for (idx in entity.getAsJsonArray("indices")) {
                    fresh.createStatement().use {
                        it.execute(idx.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                    }
                }
            }
            for (table in listOf("calendar_events", "event_reminders")) {
                assertEquals("columns of $table", describe(fresh, table), describe(db, table))
                assertEquals("indices of $table", indices(fresh, table), indices(db, table))
            }
        } finally {
            fresh.close()
        }
    }

    private fun describe(conn: Connection, table: String): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($table)").use { rs ->
                buildList {
                    while (rs.next()) {
                        add("${rs.getString("name")}|${rs.getString("type")}|${rs.getInt("notnull")}|${rs.getInt("pk")}")
                    }
                }
            }
        }

    private fun indices(conn: Connection, table: String): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA index_list($table)").use { rs ->
                buildList {
                    while (rs.next()) add("${rs.getString("name")}|${rs.getInt("unique")}")
                }.sorted()
            }
        }

    // --- fixture: schema of database version 6 as built by the entities and migrations 2->6 ---

    private fun createV6Schema() {
        exec(
            """CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId TEXT, userId TEXT,
            title TEXT NOT NULL, notes TEXT NOT NULL, location TEXT NOT NULL, links TEXT NOT NULL, dueAt TEXT,
            courseId INTEGER, eventKind TEXT NOT NULL, isCompleted INTEGER NOT NULL, createdAt TEXT NOT NULL,
            updatedAt INTEGER NOT NULL, deletedAt INTEGER)"""
        )
        exec(
            """CREATE TABLE task_reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId TEXT, userId TEXT,
            taskId INTEGER NOT NULL, label TEXT NOT NULL, remindAt TEXT NOT NULL, offsetMinutes INTEGER,
            isEnabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)"""
        )
        exec(
            """CREATE TABLE routines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId TEXT, userId TEXT,
            title TEXT NOT NULL, notes TEXT NOT NULL, location TEXT NOT NULL, repeatRule TEXT NOT NULL,
            isActive INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)"""
        )
        exec(
            """CREATE TABLE alarms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId TEXT, userId TEXT,
            label TEXT NOT NULL, time TEXT NOT NULL, isEnabled INTEGER NOT NULL, repeatDays INTEGER NOT NULL,
            toneUri TEXT, location TEXT NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)"""
        )
        exec("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
        exec(
            """CREATE TABLE schedule_slots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId TEXT, userId TEXT,
            routineId INTEGER NOT NULL, courseId INTEGER, title TEXT NOT NULL, dayOfWeek INTEGER NOT NULL,
            startTime TEXT NOT NULL, endTime TEXT NOT NULL, location TEXT NOT NULL, recurrenceUntil TEXT,
            updatedAt INTEGER NOT NULL, deletedAt INTEGER)"""
        )
        exec(
            """CREATE TABLE schedule_slot_exceptions (slotId INTEGER NOT NULL, exceptionDate TEXT NOT NULL,
            PRIMARY KEY(slotId, exceptionDate))"""
        )
        exec(
            """CREATE TABLE routine_slot_reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId TEXT,
            userId TEXT, slotId INTEGER NOT NULL, label TEXT NOT NULL, remindAt TEXT NOT NULL, offsetMinutes INTEGER,
            isEnabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)"""
        )
        exec(
            """CREATE TABLE calendar_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId TEXT, userId TEXT,
            title TEXT NOT NULL, courseId INTEGER, taskId INTEGER, startAt TEXT NOT NULL, endAt TEXT NOT NULL,
            kind TEXT NOT NULL DEFAULT 'PERSONAL', updatedAt INTEGER NOT NULL DEFAULT 0, deletedAt INTEGER,
            location TEXT NOT NULL DEFAULT '', recurrenceFrequency TEXT NOT NULL DEFAULT 'NONE',
            recurrenceInterval INTEGER NOT NULL DEFAULT 1, recurrenceWeekdays TEXT NOT NULL DEFAULT '',
            specificDatesJson TEXT NOT NULL DEFAULT '', recurrenceUntil TEXT,
            excludedDatesJson TEXT NOT NULL DEFAULT '')"""
        )
        exec(
            """CREATE TABLE leave_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, refType TEXT NOT NULL,
            refId INTEGER NOT NULL, placeLabel TEXT NOT NULL DEFAULT '', lat REAL, lng REAL,
            travelMinutes INTEGER NOT NULL DEFAULT 15, bufferMinutes INTEGER NOT NULL DEFAULT 5,
            enabled INTEGER NOT NULL DEFAULT 1)"""
        )
    }

    private fun seedV6Data() {
        // calendar: plain event (id 5), a moved class instance (id 6), mirror of the exam task (id 7)
        exec("INSERT INTO calendar_events (id, title, startAt, endAt, kind, updatedAt) VALUES (5, 'Dentist', '2026-10-15T10:00', '2026-10-15T11:00', 'PERSONAL', 100)")
        exec("INSERT INTO calendar_events (id, title, startAt, endAt, kind, updatedAt) VALUES (6, 'Moved lecture', '2026-10-14T09:00', '2026-10-14T10:00', 'CLASS', 100)")
        exec("INSERT INTO calendar_events (id, title, taskId, startAt, endAt, kind, updatedAt) VALUES (7, 'Exam 1', 2, '2026-10-12T14:00', '2026-10-12T15:00', 'EXAM', 100)")

        // tasks: 1 dated with 12 reminders, 2 exam, 3 undated and completed, 4 deleted
        exec("INSERT INTO tasks VALUES (1, NULL, 'u1', 'Write essay', 'Chapter 2', 'Library', '', '2026-10-10T09:00', NULL, 'TASK', 0, '2026-10-01T08:00', 100, NULL)")
        exec("INSERT INTO tasks VALUES (2, NULL, 'u1', 'Exam 1', '', 'Hall', '', '2026-10-12T14:00', NULL, 'EXAM', 0, '2026-10-01T08:00', 100, NULL)")
        exec("INSERT INTO tasks VALUES (3, NULL, 'u1', 'Someday', '', '', '', NULL, NULL, 'TASK', 1, '2026-09-01T08:00', 0, NULL)")
        exec("INSERT INTO tasks VALUES (4, NULL, 'u1', 'Deleted task', '', '', '', NULL, NULL, 'TASK', 0, '2026-09-01T08:00', 100, 150)")
        exec("INSERT INTO task_reminders VALUES (1, NULL, 'u1', 1, '10 min before', '2026-10-10T08:50', 10, 1, 100, NULL)")
        exec("INSERT INTO task_reminders VALUES (2, NULL, 'u1', 1, 'At time', '2026-10-10T09:00', NULL, 1, 100, NULL)")
        exec("INSERT INTO task_reminders VALUES (3, NULL, 'u1', 1, 'Evening', '2026-10-09T18:00', NULL, 1, 100, NULL)")
        for (i in 4..12) {
            exec("INSERT INTO task_reminders VALUES ($i, NULL, 'u1', 1, 'Extra $i', '2026-10-09T1${i % 10}:00', 30, 1, 100, NULL)")
        }
        exec("INSERT INTO task_reminders VALUES (13, NULL, 'u1', 1, 'Gone', '2026-10-09T10:00', 5, 1, 100, 120)")

        // routines: 1 timetable (has slots), 2 standalone weekdays, 3 deleted
        exec("INSERT INTO routines VALUES (1, NULL, 'u1', 'Timetable', '', '', 'WEEKLY', 1, 100, NULL)")
        exec("INSERT INTO routines VALUES (2, NULL, 'u1', 'Stretch', 'Morning', '', 'WEEKDAYS', 1, 100, NULL)")
        exec("INSERT INTO routines VALUES (3, NULL, 'u1', 'Old routine', '', '', 'DAILY', 1, 100, 150)")
        exec("INSERT INTO schedule_slots VALUES (1, NULL, 'u1', 1, NULL, 'Calculus', 1, '09:00', '10:00', 'Room 1', '2026-12-01', 100, NULL)")
        exec("INSERT INTO schedule_slots VALUES (2, NULL, 'u1', 1, NULL, 'Yoga', 7, '18:00', '19:00', '', NULL, 100, NULL)")
        exec("INSERT INTO schedule_slot_exceptions VALUES (1, '2026-10-12')")
        exec("INSERT INTO schedule_slot_exceptions VALUES (1, '2026-10-05')")
        exec("INSERT INTO routine_slot_reminders VALUES (1, NULL, 'u1', 1, '10 min before', '2026-10-12T08:50', 10, 1, 100, NULL)")
        exec("INSERT INTO routine_slot_reminders VALUES (2, NULL, 'u1', 1, 'At time', '2026-10-12T09:00', NULL, 1, 100, NULL)")

        // alarms
        exec("INSERT INTO alarms VALUES (1, NULL, 'u1', 'Wake up', '07:30', 1, 62, 'content://tone', '', 100, NULL)")
        exec("INSERT INTO alarms VALUES (2, NULL, 'u1', 'Nap', '14:00', 0, 0, NULL, '', 100, NULL)")
        exec("INSERT INTO alarms VALUES (3, NULL, 'u1', 'Old alarm', '06:00', 1, 0, NULL, '', 100, 150)")
        exec("INSERT INTO alarms VALUES (4, NULL, 'u1', 'Weekend', '09:00', 1, 65, NULL, '', 100, NULL)")

        // leave-by: task rule, mirror event rule, plain event rule, rule of the deleted task
        exec("INSERT INTO leave_rules (id, refType, refId) VALUES (1, 'TASK', 1)")
        exec("INSERT INTO leave_rules (id, refType, refId) VALUES (2, 'CALENDAR_EVENT', 7)")
        exec("INSERT INTO leave_rules (id, refType, refId) VALUES (3, 'CALENDAR_EVENT', 5)")
        exec("INSERT INTO leave_rules (id, refType, refId) VALUES (4, 'TASK', 4)")
    }
}
