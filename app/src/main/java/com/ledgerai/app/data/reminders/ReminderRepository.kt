package com.ledgerai.app.data.reminders

import com.ledgerai.app.data.dates.BirthdayDao
import com.ledgerai.app.data.dates.BirthdayEntity
import com.ledgerai.app.data.dates.TripDao
import com.ledgerai.app.data.dates.TripEntity
import android.content.Context
import com.ledgerai.app.data.people.PeopleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ledgerai.app.domain.dates.BirthdayLadder
import com.ledgerai.app.domain.dates.LadderSwitches
import com.ledgerai.app.domain.dates.Trips
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the device-only `reminders` table from birthdays and trips.
 * It only writes rows. Arming the single rolling alarm and delivering notifications is the engine's job.
 *
 * Rebuild is safe to repeat: pending rows for a source are replaced, rows that were already delivered
 * keep their id and are not created again.
 */
@Singleton
class ReminderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminders: ReminderDao,
    private val birthdays: BirthdayDao,
    private val trips: TripDao,
    private val people: PeopleRepository,
) {

    suspend fun rebuildBirthday(
        row: BirthdayEntity,
        name: String,
        now: LocalDateTime = LocalDateTime.now(),
        reminderHour: LocalTime = reminderHour(context),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        reminders.deletePendingForSource(ReminderSource.BIRTHDAY, row.id)
        if (row.deletedAt != null) return
        val celebration = BirthdayLadder.nextCelebration(row.month, row.day, row.celebrateMinute, now)
        val all = BirthdayLadder.instants(celebration, row.switches(), reminderHour)
        val planned = BirthdayLadder.pending(all, now).map { instant ->
            ReminderEntity(
                id = "birthday:${row.id}:${celebration.year}:${instant.rung.key}",
                sourceType = ReminderSource.BIRTHDAY,
                sourceId = row.id,
                rung = instant.rung.key,
                // A caught-up rung fires now, not in the past.
                fireAt = maxOf(instant.at.atZone(zone).toInstant().toEpochMilli(), now.atZone(zone).toInstant().toEpochMilli()),
                title = "$name's birthday",
                body = BirthdayLadder.body(instant.rung, row.birthYear, celebration),
            )
        }
        reminders.insertMissing(planned)
    }

    suspend fun rebuildTrip(
        row: TripEntity,
        now: LocalDateTime = LocalDateTime.now(),
        reminderHour: LocalTime = reminderHour(context),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        reminders.deletePendingForSource(ReminderSource.TRIP, row.id)
        if (row.deletedAt != null) return
        val planned = Trips.reminders(row.startDate, row.r1w, row.r1d, reminderHour)
            .filter { !it.second.isBefore(now) }
            .map { (rung, at) ->
                ReminderEntity(
                    id = "trip:${row.id}:$rung",
                    sourceType = ReminderSource.TRIP,
                    sourceId = row.id,
                    rung = rung,
                    fireAt = at.atZone(zone).toInstant().toEpochMilli(),
                    title = row.name,
                    body = if (rung == "1w") "starts in 1 week · ${row.place}" else "starts tomorrow · ${row.place}",
                )
            }
        reminders.insertMissing(planned)
    }

    /** Daily safety rebuild, and after boot, time change or a timezone change. */
    suspend fun rebuildAll(
        now: LocalDateTime = LocalDateTime.now(),
        reminderHour: LocalTime = reminderHour(context),
    ) {
        val names = HashMap<String, Map<String, String>>()
        for (b in birthdays.listActive()) {
            val owner = b.userId ?: com.ledgerai.app.data.dates.LOCAL_OWNER
            val byId = names.getOrPut(owner) { people.listPeople(owner).associate { it.id to it.name } }
            rebuildBirthday(b, byId[b.personId] ?: "Birthday", now, reminderHour)
        }
        for (t in trips.listActive()) rebuildTrip(t, now, reminderHour)
    }

    suspend fun clearSource(type: String, id: String) = reminders.deleteForSource(type, id)

    companion object {
        private const val PREFS = "ledger_reminders"
        private const val KEY = "hour"

        fun reminderHour(context: Context): LocalTime =
            LocalTime.of(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 9).coerceIn(0, 23), 0)

        fun setReminderHour(context: Context, hour: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, hour.coerceIn(0, 23)).apply()
        }
    }

    private fun BirthdayEntity.switches() = LadderSwitches(
        month1 = r1mo, week2 = r2w, week1 = r1w, day3 = r3d, day1 = r1d,
        hour5 = r5h, min30 = r30m, min10 = r10m, atTime = rAt,
    )
}
