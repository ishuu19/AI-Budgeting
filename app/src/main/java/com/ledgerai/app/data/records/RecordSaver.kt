package com.ledgerai.app.data.records

import com.ledgerai.app.data.achievements.AchievementDao
import com.ledgerai.app.data.achievements.AchievementEntity
import com.ledgerai.app.data.dates.BirthdayRepository
import com.ledgerai.app.data.dates.TripRepository
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.wishes.WishDao
import com.ledgerai.app.data.wishes.WishEntity
import com.ledgerai.app.domain.dates.BirthdayLadder
import com.ledgerai.app.domain.dates.LadderSwitches
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.records.AchievementDraft
import com.ledgerai.app.domain.records.AppointmentDraft
import com.ledgerai.app.domain.records.BirthdayDraft
import com.ledgerai.app.domain.records.PasswordGuard
import com.ledgerai.app.domain.records.RecordDraft
import com.ledgerai.app.domain.records.TripDraft
import com.ledgerai.app.domain.records.WishDraft
import com.ledgerai.app.service.ReminderEngine
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Writes a confirmed [RecordDraft]. Called only after the user taps Save on the suggestion. */
@Singleton
class RecordSaver @Inject constructor(
    private val birthdays: BirthdayRepository,
    private val trips: TripRepository,
    private val wishes: WishDao,
    private val achievements: AchievementDao,
    private val calendar: CalendarRepository,
    private val session: UserSession,
    private val engine: ReminderEngine,
) {

    suspend fun save(draft: RecordDraft): String {
        val uid = session.userInfo.first().userId.ifBlank { null }
        val now = System.currentTimeMillis()
        val done = when (draft) {
            is BirthdayDraft -> {
                birthdays.save(null, uid, draft.name, draft.month, draft.day, null, BirthdayLadder.DEFAULT_CELEBRATE_MINUTE, LadderSwitches(), "")
                "Birthday saved"
            }
            is TripDraft -> {
                trips.save(null, uid, "Trip to ${draft.place}", draft.place, draft.start, draft.end, "", true, true)
                "Trip saved"
            }
            is AchievementDraft -> {
                achievements.upsert(AchievementEntity(UUID.randomUUID().toString(), uid, draft.text.take(120), draft.on, "", null, now))
                "Achievement saved"
            }
            is WishDraft -> {
                wishes.upsert(WishEntity(id = UUID.randomUUID().toString(), userId = uid, name = draft.name.take(120), amount = draft.amount, createdAt = now, updatedAt = now))
                "Wish saved"
            }
            is AppointmentDraft -> {
                val id = calendar.upsert(
                    CalendarEvent(
                        title = draft.text.take(120), startAt = draft.start, endAt = draft.start.plusHours(1),
                        kind = CalendarEventKind.APPOINTMENT, status = "UPCOMING",
                    )
                )
                calendar.addReminder(id, "1 day before", 1440)
                calendar.addReminder(id, "1 hour before", 60)
                "Appointment saved"
            }
            PasswordGuard -> error("Passwords are never saved from speech")
        }
        engine.rebuildAndArm()
        return done
    }
}
