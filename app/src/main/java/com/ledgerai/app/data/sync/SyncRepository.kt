package com.ledgerai.app.data.sync

import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.BillDao
import com.ledgerai.app.data.local.room.BudgetDao
import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.DebtDao
import com.ledgerai.app.data.local.room.EventReminderDao
import com.ledgerai.app.data.local.room.GoalDao
import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.local.room.TransactionDao
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.domain.schedule.EventReminderRules
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room ↔ Supabase PostgREST sync with last-write-wins on [updatedAt].
 * Soft deletes via [deletedAt]. Requires a remote user + Bearer JWT in [UserSession].
 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: PostgrestApi,
    private val userSession: UserSession,
    private val cursorStore: SyncCursorStore,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val debtDao: DebtDao,
    private val goalDao: GoalDao,
    private val billDao: BillDao,
    private val eventDao: CalendarEventDao,
    private val eventReminderDao: EventReminderDao,
    private val noteDao: NoteDao,
    private val calendarRepository: CalendarRepository,
    private val extras: Set<@JvmSuppressWildcards ExtraSync>,
) {

    private val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    /**
     * @return false when skipped (missing config / no remote session); true after a sync attempt.
     */
    suspend fun syncAll(): Boolean {
        if (BuildConfig.SUPABASE_URL.isBlank() || anonKey.isBlank()) return false
        val info = userSession.userInfo.first()
        if (!info.hasRemoteUser) return false

        val bearer = "Bearer ${info.accessToken}"
        val userId = info.userId
        val sinceMs = cursorStore.lastSyncMs()
        val startedAt = System.currentTimeMillis()
        val filter = SyncTime.postgrestGtFilter(sinceMs)

        pullTransactions(bearer, filter)
        pullBudgets(bearer, filter)
        pullDebts(bearer, filter)
        pullGoals(bearer, filter)
        pullBills(bearer, filter)
        val touchedEvents = mutableSetOf<Long>()
        pullEvents(bearer, filter, touchedEvents)
        pullEventReminders(bearer, filter, touchedEvents)
        pullNotes(bearer, filter)

        pushTransactions(userId, sinceMs, bearer)
        pushBudgets(userId, sinceMs, bearer)
        pushDebts(userId, sinceMs, bearer)
        pushGoals(userId, sinceMs, bearer)
        pushBills(userId, sinceMs, bearer)
        pushEvents(userId, sinceMs, bearer)
        pushEventReminders(userId, sinceMs, bearer)
        pushNotes(userId, sinceMs, bearer)
        extras.forEach { it.sync(bearer, anonKey, userId, sinceMs, filter) }

        touchedEvents.forEach { calendarRepository.rearmAfterPull(it) }

        cursorStore.saveLastSyncMs(startedAt)
        return true
    }

    private fun newId(): String = UUID.randomUUID().toString()

    /** Fetches ordered pages until a short page is returned, so no row beyond the page size is skipped. */
    private suspend fun <T> forEachPage(fetch: suspend (offset: Int) -> List<T>): List<T> {
        val all = ArrayList<T>()
        var offset = 0
        while (true) {
            val page = fetch(offset)
            all += page
            if (page.size < PostgrestApi.PULL_PAGE_SIZE) break
            offset += page.size
        }
        return all
    }

    // ─── pull (remote wins if remote.updatedAt >= local.updatedAt) ───────────

    private suspend fun pullTransactions(bearer: String, filter: String) {
        forEachPage { offset -> api.pullTransactions(bearer, anonKey, filter, offset = offset) }.forEach { remote ->
            val local = transactionDao.getByRemoteId(remote.id)
            if (local == null) transactionDao.insert(remote.toEntity())
            else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                transactionDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullBudgets(bearer: String, filter: String) {
        forEachPage { offset -> api.pullBudgets(bearer, anonKey, filter, offset = offset) }.forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = budgetDao.getByRemoteId(remote.id)
            if (local == null) budgetDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                budgetDao.update(remote.toEntity(localId = local.id))
            }
        }
    }

    private suspend fun pullDebts(bearer: String, filter: String) {
        forEachPage { offset -> api.pullDebts(bearer, anonKey, filter, offset = offset) }.forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = debtDao.getByRemoteId(remote.id)
            if (local == null) debtDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                debtDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullGoals(bearer: String, filter: String) {
        forEachPage { offset -> api.pullGoals(bearer, anonKey, filter, offset = offset) }.forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = goalDao.getByRemoteId(remote.id)
            if (local == null) goalDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                goalDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullBills(bearer: String, filter: String) {
        forEachPage { offset -> api.pullBills(bearer, anonKey, filter, offset = offset) }.forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = billDao.getByRemoteId(remote.id)
            if (local == null) billDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                billDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    private suspend fun pullEvents(bearer: String, filter: String, touched: MutableSet<Long>) {
        forEachPage { offset -> api.pullEvents(bearer, anonKey, filter, offset = offset) }.forEach { remote ->
            val local = eventDao.getByRemoteId(remote.id)
            if (local == null) {
                val id = eventDao.insert(remote.toEntity())
                touched += id
            } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                eventDao.update(remote.toEntity(localId = local.id))
                touched += local.id
            }
        }
    }

    /** Applies the per-event reminder cap so a bad remote state can never exceed it locally. */
    private suspend fun pullEventReminders(bearer: String, filter: String, touched: MutableSet<Long>) {
        forEachPage { offset -> api.pullEventReminders(bearer, anonKey, filter, offset = offset) }.forEach { remote ->
            val event = eventDao.getByRemoteId(remote.eventId) ?: return@forEach
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = eventReminderDao.getByRemoteId(remote.id)
            val incoming = remote.toEntity(localId = local?.id ?: 0L, localEventId = event.id)
            when {
                local == null -> {
                    val active = eventReminderDao.countActiveForEvent(event.id)
                    if (!EventReminderRules.acceptPulled(active, incoming.deletedAt != null)) return@forEach
                    eventReminderDao.insert(incoming)
                    touched += event.id
                }
                remoteUpdated >= local.updatedAt -> {
                    val reviving = local.deletedAt != null && incoming.deletedAt == null
                    if (reviving &&
                        !EventReminderRules.acceptPulled(eventReminderDao.countActiveForEvent(event.id), false)
                    ) {
                        return@forEach
                    }
                    eventReminderDao.update(incoming)
                    touched += event.id
                }
            }
        }
    }

    private suspend fun pullNotes(bearer: String, filter: String) {
        forEachPage { offset -> api.pullNotes(bearer, anonKey, filter, offset = offset) }.forEach { remote ->
            val remoteUpdated = SyncTime.isoToMillis(remote.updatedAt)
            val local = noteDao.getByRemoteId(remote.id)
            if (local == null) noteDao.insert(remote.toEntity())
            else if (remoteUpdated >= local.updatedAt) {
                noteDao.update(remote.toEntity(localId = local.id).copy(location = local.location))
            }
        }
    }

    // ─── push ────────────────────────────────────────────────────────────────

    private suspend fun pushTransactions(userId: String, sinceMs: Long, bearer: String) {
        val locals = transactionDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteTransactionDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                transactionDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertTransactions(bearer, anonKey, body = rows)
    }

    private suspend fun pushBudgets(userId: String, sinceMs: Long, bearer: String) {
        val locals = budgetDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteBudgetDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                budgetDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertBudgets(bearer, anonKey, body = rows)
    }

    private suspend fun pushDebts(userId: String, sinceMs: Long, bearer: String) {
        val locals = debtDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteDebtDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                debtDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertDebts(bearer, anonKey, body = rows)
    }

    private suspend fun pushGoals(userId: String, sinceMs: Long, bearer: String) {
        val locals = goalDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteGoalDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                goalDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertGoals(bearer, anonKey, body = rows)
    }

    private suspend fun pushBills(userId: String, sinceMs: Long, bearer: String) {
        val locals = billDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteBillDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                billDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertBills(bearer, anonKey, body = rows)
    }

    private suspend fun pushEvents(userId: String, sinceMs: Long, bearer: String) {
        val locals = eventDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteEventDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                eventDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        rows.chunked(PUSH_CHUNK).forEach { api.upsertEvents(bearer, anonKey, body = it) }
    }

    private suspend fun pushEventReminders(userId: String, sinceMs: Long, bearer: String) {
        val locals = eventReminderDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteEventReminderDto>()
        for (local in locals) {
            val event = eventDao.getByIdAny(local.eventId) ?: continue
            val eventRemote = event.remoteId ?: continue
            val rid = local.remoteId ?: run {
                val id = newId()
                eventReminderDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId, eventRemote)
        }
        if (rows.isEmpty()) return
        rows.chunked(PUSH_CHUNK).forEach { api.upsertEventReminders(bearer, anonKey, body = it) }
    }

    private suspend fun pushNotes(userId: String, sinceMs: Long, bearer: String) {
        val locals = noteDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteNoteDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                noteDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertNotes(bearer, anonKey, body = rows)
    }

    private companion object {
        const val PUSH_CHUNK = 200
    }
}
