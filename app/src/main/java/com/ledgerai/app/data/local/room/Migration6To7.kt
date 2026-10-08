package com.ledgerai.app.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Calendar merge. Tasks, task reminders, routines, weekly schedule slots (+ exceptions and reminders)
 * and alarms are copied into `calendar_events` / `event_reminders`, then the old tables are dropped.
 *
 * Ids: existing calendar rows keep their id (mirror rows of tasks are skipped, the task row wins).
 * Every other group gets `base + oldId`, where the bases are cumulative MAX(id) values of the old
 * tables stored in `mig_off`, so ids never collide and `leave_rules` can be remapped in SQL.
 * Soft-deleted rows are not copied.
 */
object Migration6To7Sql {

    private const val EVENT_COLUMNS = """
        id, remoteId, userId, title, notes, location, links, startAt, endAt, allDay, hasDate, kind,
        isCompleted, completedAt, isEnabled, alarmToneUri, alarmRepeatDays, recurrenceFrequency,
        recurrenceInterval, recurrenceWeekdays, specificDatesJson, recurrenceUntil, excludedDatesJson,
        updatedAt, deletedAt
    """

    private const val REMINDER_COLUMNS =
        "remoteId, userId, eventId, label, offsetMinutes, remindAt, isEnabled, updatedAt, deletedAt"

    /** Mask Sun=1 ... Sat=64 to ISO weekdays (Mon=1 ... Sun=7), comma separated. */
    private const val ALARM_WEEKDAYS = """rtrim(
        CASE WHEN a.repeatDays & 2 THEN '1,' ELSE '' END ||
        CASE WHEN a.repeatDays & 4 THEN '2,' ELSE '' END ||
        CASE WHEN a.repeatDays & 8 THEN '3,' ELSE '' END ||
        CASE WHEN a.repeatDays & 16 THEN '4,' ELSE '' END ||
        CASE WHEN a.repeatDays & 32 THEN '5,' ELSE '' END ||
        CASE WHEN a.repeatDays & 64 THEN '6,' ELSE '' END ||
        CASE WHEN a.repeatDays & 1 THEN '7,' ELSE '' END, ',')"""

    val statements: List<String> = listOf(
        """
        CREATE TABLE mig_off (o1 INTEGER NOT NULL, o2 INTEGER NOT NULL, o3 INTEGER NOT NULL, o4 INTEGER NOT NULL)
        """,
        """
        INSERT INTO mig_off
        SELECT e, e + t, e + t + s, e + t + s + r
        FROM (
            SELECT COALESCE((SELECT MAX(id) FROM calendar_events), 0) AS e,
                   COALESCE((SELECT MAX(id) FROM tasks), 0) AS t,
                   COALESCE((SELECT MAX(id) FROM schedule_slots), 0) AS s,
                   COALESCE((SELECT MAX(id) FROM routines), 0) AS r
        )
        """,
        "ALTER TABLE calendar_events RENAME TO calendar_events_old",
        """
        CREATE TABLE calendar_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            remoteId TEXT,
            userId TEXT,
            title TEXT NOT NULL,
            notes TEXT NOT NULL,
            location TEXT NOT NULL,
            links TEXT NOT NULL,
            startAt TEXT NOT NULL,
            endAt TEXT NOT NULL,
            allDay INTEGER NOT NULL,
            hasDate INTEGER NOT NULL,
            kind TEXT NOT NULL,
            isCompleted INTEGER NOT NULL,
            completedAt TEXT,
            isEnabled INTEGER NOT NULL,
            alarmToneUri TEXT,
            alarmRepeatDays INTEGER NOT NULL,
            recurrenceFrequency TEXT NOT NULL,
            recurrenceInterval INTEGER NOT NULL,
            recurrenceWeekdays TEXT NOT NULL,
            specificDatesJson TEXT NOT NULL,
            recurrenceUntil TEXT,
            excludedDatesJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER
        )
        """,
        """
        CREATE TABLE event_reminders (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            remoteId TEXT,
            userId TEXT,
            eventId INTEGER NOT NULL,
            label TEXT NOT NULL,
            offsetMinutes INTEGER,
            remindAt TEXT,
            isEnabled INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER
        )
        """,
        "CREATE INDEX index_calendar_events_startAt ON calendar_events (startAt)",
        "CREATE INDEX index_calendar_events_kind ON calendar_events (kind)",
        "CREATE INDEX index_calendar_events_remoteId ON calendar_events (remoteId)",
        "CREATE INDEX index_calendar_events_updatedAt ON calendar_events (updatedAt)",
        "CREATE INDEX index_event_reminders_eventId ON event_reminders (eventId)",
        "CREATE INDEX index_event_reminders_remoteId ON event_reminders (remoteId)",

        // Existing calendar rows (task mirrors are skipped, the task row is the source of truth).
        """
        INSERT INTO calendar_events ($EVENT_COLUMNS)
        SELECT id, remoteId, userId, title, '', location, '', startAt, endAt, 0, 1,
               CASE kind WHEN 'PERSONAL' THEN 'EVENT' ELSE kind END,
               0, NULL, 1, NULL, 0,
               recurrenceFrequency, recurrenceInterval, recurrenceWeekdays, specificDatesJson,
               recurrenceUntil, excludedDatesJson, updatedAt, NULL
        FROM calendar_events_old
        WHERE deletedAt IS NULL AND taskId IS NULL
        """,

        // Tasks (TASK / EXAM / EVENT).
        """
        INSERT INTO calendar_events ($EVENT_COLUMNS)
        SELECT o.o1 + t.id, NULL, t.userId, t.title, t.notes, t.location, t.links,
               COALESCE(t.dueAt, substr(t.createdAt, 1, 10) || 'T00:00'),
               CASE WHEN t.eventKind = 'TASK' OR t.dueAt IS NULL
                    THEN COALESCE(t.dueAt, substr(t.createdAt, 1, 10) || 'T00:00')
                    ELSE strftime('%Y-%m-%dT%H:%M', t.dueAt, '+1 hour') END,
               0,
               CASE WHEN t.dueAt IS NULL THEN 0 ELSE 1 END,
               CASE t.eventKind WHEN 'EXAM' THEN 'EXAM' WHEN 'EVENT' THEN 'EVENT' ELSE 'TASK' END,
               t.isCompleted,
               CASE WHEN t.isCompleted = 1
                    THEN strftime('%Y-%m-%dT%H:%M', t.updatedAt / 1000, 'unixepoch', 'localtime') END,
               1, NULL, 0,
               'NONE', 1, '', '', NULL, '',
               t.updatedAt, NULL
        FROM tasks t, mig_off o
        WHERE t.deletedAt IS NULL
        """,
        """
        INSERT INTO event_reminders ($REMINDER_COLUMNS)
        SELECT NULL, tr.userId, o.o1 + tr.taskId, tr.label,
               CASE WHEN tr.offsetMinutes IS NOT NULL THEN tr.offsetMinutes
                    WHEN t.dueAt IS NOT NULL AND tr.remindAt = t.dueAt THEN 0 END,
               CASE WHEN tr.offsetMinutes IS NOT NULL THEN NULL
                    WHEN t.dueAt IS NOT NULL AND tr.remindAt = t.dueAt THEN NULL
                    ELSE tr.remindAt END,
               tr.isEnabled, tr.updatedAt, NULL
        FROM task_reminders tr
        JOIN tasks t ON t.id = tr.taskId AND t.deletedAt IS NULL, mig_off o
        WHERE tr.deletedAt IS NULL
          AND (SELECT COUNT(*) FROM task_reminders r2
               WHERE r2.taskId = tr.taskId AND r2.deletedAt IS NULL AND r2.id <= tr.id) <= 10
        """,

        // Weekly schedule slots become recurring CLASS events; slot exceptions become excluded dates.
        """
        INSERT INTO calendar_events ($EVENT_COLUMNS)
        SELECT o.o2 + s.id, NULL, s.userId, s.title, '', s.location, '',
               date('now', 'localtime', 'weekday ' || (s.dayOfWeek % 7), '-28 days') || 'T' || s.startTime,
               date('now', 'localtime', 'weekday ' || (s.dayOfWeek % 7), '-28 days') || 'T' || s.endTime,
               0, 1, 'CLASS', 0, NULL,
               CASE WHEN r.isActive = 0 THEN 0 ELSE 1 END, NULL, 0,
               'WEEKLY', 1, CAST(s.dayOfWeek AS TEXT), '', s.recurrenceUntil,
               COALESCE((SELECT group_concat(x.exceptionDate, ',')
                         FROM (SELECT exceptionDate FROM schedule_slot_exceptions
                               WHERE slotId = s.id ORDER BY exceptionDate) x), ''),
               s.updatedAt, NULL
        FROM schedule_slots s
        LEFT JOIN routines r ON r.id = s.routineId AND r.deletedAt IS NULL, mig_off o
        WHERE s.deletedAt IS NULL
        """,
        """
        INSERT INTO event_reminders ($REMINDER_COLUMNS)
        SELECT NULL, rr.userId, o.o2 + rr.slotId, rr.label, COALESCE(rr.offsetMinutes, 0), NULL,
               rr.isEnabled, rr.updatedAt, NULL
        FROM routine_slot_reminders rr
        JOIN schedule_slots s ON s.id = rr.slotId AND s.deletedAt IS NULL, mig_off o
        WHERE rr.deletedAt IS NULL
          AND (SELECT COUNT(*) FROM routine_slot_reminders r2
               WHERE r2.slotId = rr.slotId AND r2.deletedAt IS NULL AND r2.id <= rr.id) <= 10
        """,

        // Routines without timetable slots become repeating ROUTINE events (blank or custom rule: daily).
        """
        INSERT INTO calendar_events ($EVENT_COLUMNS)
        SELECT o.o3 + r.id, NULL, r.userId, r.title, r.notes, r.location, '',
               date('now', 'localtime') || 'T08:00', date('now', 'localtime') || 'T08:30',
               0, 1, 'ROUTINE', 0, NULL, r.isActive, NULL, 0,
               CASE UPPER(r.repeatRule) WHEN 'WEEKLY' THEN 'WEEKLY' WHEN 'WEEKDAYS' THEN 'WEEKLY' ELSE 'DAILY' END,
               1,
               CASE UPPER(r.repeatRule) WHEN 'WEEKDAYS' THEN '1,2,3,4,5' ELSE '' END,
               '', NULL, '', r.updatedAt, NULL
        FROM routines r, mig_off o
        WHERE r.deletedAt IS NULL
          AND NOT EXISTS (SELECT 1 FROM schedule_slots s WHERE s.routineId = r.id AND s.deletedAt IS NULL)
        """,

        // Alarms.
        """
        INSERT INTO calendar_events ($EVENT_COLUMNS)
        SELECT o.o4 + a.id, NULL, a.userId, CASE WHEN trim(a.label) = '' THEN 'Alarm' ELSE a.label END, '',
               a.location, '',
               date('now', 'localtime') || 'T' || a.time, date('now', 'localtime') || 'T' || a.time,
               0, 1, 'ALARM', 0, NULL, a.isEnabled, a.toneUri, a.repeatDays,
               CASE WHEN a.repeatDays = 0 THEN 'NONE' ELSE 'WEEKLY' END, 1,
               CASE WHEN a.repeatDays = 0 THEN '' ELSE $ALARM_WEEKDAYS END,
               '', NULL, '', a.updatedAt, NULL
        FROM alarms a, mig_off o
        WHERE a.deletedAt IS NULL
        """,

        // Leave-by rules: mirror events and tasks now point at the merged event id.
        """
        UPDATE leave_rules
        SET refId = (SELECT o.o1 + ce.taskId FROM calendar_events_old ce, mig_off o WHERE ce.id = leave_rules.refId),
            refType = 'CALENDAR_EVENT'
        WHERE refType = 'CALENDAR_EVENT'
          AND EXISTS (SELECT 1 FROM calendar_events_old ce WHERE ce.id = leave_rules.refId AND ce.taskId IS NOT NULL)
        """,
        """
        UPDATE leave_rules
        SET refId = refId + (SELECT o1 FROM mig_off), refType = 'CALENDAR_EVENT'
        WHERE refType = 'TASK'
        """,
        "DELETE FROM leave_rules WHERE refId NOT IN (SELECT id FROM calendar_events)",

        "DROP TABLE calendar_events_old",
        "DROP TABLE task_reminders",
        "DROP TABLE tasks",
        "DROP TABLE routine_slot_reminders",
        "DROP TABLE schedule_slot_exceptions",
        "DROP TABLE schedule_slots",
        "DROP TABLE routines",
        "DROP TABLE alarms",
        "DROP TABLE courses",
        "DROP TABLE mig_off"
    ).map { it.trimIndent() }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration6To7Sql.statements.forEach { db.execSQL(it) }
    }
}
