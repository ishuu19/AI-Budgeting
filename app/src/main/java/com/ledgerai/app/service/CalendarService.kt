package com.ledgerai.app.service

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.domain.model.Debt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSession: UserSession
) {
    /**
     * Creates a Google Calendar event on the signed-in Google account's calendar.
     * Returns the event ID if successful, null otherwise.
     */
    suspend fun createDebtReminderEvent(debt: Debt): Long? {
        val dueDate = debt.dueDate ?: return null

        return try {
            val startMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = startMillis + 60 * 60 * 1000 // 1 hour

            // Prefer the signed-in Google account's calendar
            val userInfo = userSession.userInfo.first()
            val calendarId = if (userInfo.googleAccountEmail.isNotBlank()) {
                getCalendarIdForAccount(userInfo.googleAccountEmail)
                    ?: getDefaultCalendarId()
            } else {
                getDefaultCalendarId()
            } ?: return null

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "💰 Debt Due: ${debt.friendName}")
                put(CalendarContract.Events.DESCRIPTION, buildDescription(debt))
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLong()

            // Add reminder alarms at different intervals
            eventId?.let { id ->
                addReminder(id, 10 * 24 * 60)  // 1 week before
                addReminder(id, 5 * 24 * 60)   // 5 days before
                addReminder(id, 24 * 60)        // 1 day before
                addReminder(id, 10 * 60)        // 10 hours before
                addReminder(id, 30)             // 30 minutes before
            }
            eventId
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun deleteDebtReminderEvent(eventId: Long) {
        try {
            val uri = CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendPath(eventId.toString()).build()
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) { /* already deleted */ }
    }

    private fun addReminder(eventId: Long, minutesBefore: Int) {
        try {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                put(CalendarContract.Reminders.MINUTES, minutesBefore)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        } catch (e: Exception) { /* ignore */ }
    }

    /** Find the calendar belonging to the signed-in Google account */
    private fun getCalendarIdForAccount(accountEmail: String): Long? {
        return try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.IS_PRIMARY
            )
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.IS_PRIMARY} = 1",
                arrayOf(accountEmail),
                null
            )
            cursor?.use { if (it.moveToFirst()) it.getLong(0) else null }
        } catch (e: SecurityException) { null }
    }

    private fun getDefaultCalendarId(): Long? {
        return try {
            val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.IS_PRIMARY} = 1",
                null, null
            )
            cursor?.use { if (it.moveToFirst()) it.getLong(0) else null }
        } catch (e: SecurityException) { null }
    }

    private fun buildDescription(debt: Debt): String {
        val direction = if (debt.direction.name == "THEY_OWE")
            "${debt.friendName} owes you $${debt.amount}"
        else
            "You owe ${debt.friendName} $${debt.amount}"

        return buildString {
            appendLine(direction)
            appendLine("Amount: $${debt.amount}")
            if (debt.phone.isNotEmpty()) appendLine("Phone: ${debt.phone}")
            if (debt.note.isNotEmpty()) appendLine("Note: ${debt.note}")
            appendLine("\nManaged by LedgerAI")
        }
    }
}
