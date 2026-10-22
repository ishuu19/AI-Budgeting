package com.ledgerai.app.data.dates

import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.reminders.ReminderRepository
import com.ledgerai.app.data.reminders.ReminderSource
import com.ledgerai.app.domain.dates.BirthdayLadder
import com.ledgerai.app.domain.dates.LadderSwitches
import com.ledgerai.app.domain.dates.Trips
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Owner id used for people rows when nobody is signed in. */
const val LOCAL_OWNER = "local"

@Singleton
class BirthdayRepository @Inject constructor(
    private val dao: BirthdayDao,
    private val people: PeopleRepository,
    private val reminders: ReminderRepository,
) {

    suspend fun names(userId: String?): Map<String, String> =
        people.listPeople(userId ?: LOCAL_OWNER).associate { it.id to it.name }

    /**
     * Saves one birthday. A new name creates the person; a name that matches an existing person
     * (ignoring case) reuses it, so the name is stored once.
     */
    suspend fun save(
        id: String?,
        userId: String?,
        name: String,
        month: Int,
        day: Int,
        birthYear: Int?,
        celebrateMinute: Int,
        switches: LadderSwitches,
        note: String,
        now: Long = System.currentTimeMillis(),
    ): String {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 80) { "Name must be 1 to 80 characters" }
        require(month in 1..12) { "Month must be 1 to 12" }
        require(day in 1..java.time.Month.of(month).length(true)) { "Day is not valid for that month" }
        require(birthYear == null || birthYear in 1900..LocalDate.now().year) { "Birth year is not valid" }
        val owner = userId ?: LOCAL_OWNER
        val existing = id?.let { dao.get(it) }
        val person = people.listPeople(owner).firstOrNull { it.name.equals(clean, ignoreCase = true) }
            ?: people.addPerson(owner, clean, null, null, "")
        val row = BirthdayEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            personId = person.id,
            month = month,
            day = day,
            birthYear = birthYear,
            celebrateMinute = celebrateMinute.coerceIn(0, 1439),
            note = note.trim().take(500),
            r1mo = switches.month1, r2w = switches.week2, r1w = switches.week1, r3d = switches.day3,
            r1d = switches.day1, r5h = switches.hour5, r30m = switches.min30, r10m = switches.min10,
            rAt = switches.atTime,
            updatedAt = now,
        )
        dao.upsert(row)
        reminders.rebuildBirthday(row, person.name)
        return row.id
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
        reminders.clearSource(ReminderSource.BIRTHDAY, id)
    }
}

@Singleton
class TripRepository @Inject constructor(
    private val dao: TripDao,
    private val reminders: ReminderRepository,
) {

    suspend fun save(
        id: String?,
        userId: String?,
        name: String,
        place: String,
        start: LocalDate,
        end: LocalDate,
        notes: String,
        week: Boolean,
        day: Boolean,
        now: Long = System.currentTimeMillis(),
    ): String {
        require(name.trim().length in 1..80) { "Name must be 1 to 80 characters" }
        require(place.trim().length in 1..120) { "Place is required" }
        require(Trips.isValid(start, end)) { "End date must be on or after the start date" }
        val existing = id?.let { dao.get(it) }
        val row = TripEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            name = name.trim(),
            place = place.trim(),
            startDate = start,
            endDate = end,
            notes = notes.trim().take(2000),
            r1w = week,
            r1d = day,
            updatedAt = now,
        )
        dao.upsert(row)
        reminders.rebuildTrip(row)
        return row.id
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
        reminders.clearSource(ReminderSource.TRIP, id)
    }
}
